package com.campus.canteen.service;

import com.campus.canteen.constant.StatusConstant;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.DishFlavor;
import com.campus.canteen.exception.DeletionNotAllowedException;
import com.campus.canteen.mapper.DishFlavorMapper;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.SetmealDishMapper;
import com.campus.canteen.service.impl.DishServiceImpl;
import com.campus.canteen.vo.DishVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DishServiceImpl 单元测试
 * 使用 Mockito mock 数据库层，验证核心业务逻辑和缓存方法行为
 */
@ExtendWith(MockitoExtension.class)
class DishServiceImplTest {

    @Mock
    private DishMapper dishMapper;

    @Mock
    private DishFlavorMapper dishFlavorMapper;

    @Mock
    private SetmealDishMapper setmealDishMapper;

    @InjectMocks
    private DishServiceImpl dishService;

    // ========== listWithFlavor: 带缓存的菜品+口味查询（A/B压测核心方法） ==========

    @Test
    @DisplayName("listWithFlavor: 根据分类查询菜品及口味 - 正常返回")
    void testListWithFlavor() {
        // 准备测试数据
        Dish dish1 = Dish.builder().id(1L).name("宫保鸡丁").categoryId(1L).price(new BigDecimal("28.00")).status(StatusConstant.ENABLE).build();
        Dish dish2 = Dish.builder().id(2L).name("鱼香肉丝").categoryId(1L).price(new BigDecimal("26.00")).status(StatusConstant.ENABLE).build();

        DishFlavor flavor1 = DishFlavor.builder().id(10L).dishId(1L).name("辣度").value("[\"微辣\",\"中辣\",\"特辣\"]").build();
        DishFlavor flavor2 = DishFlavor.builder().id(11L).dishId(2L).name("辣度").value("[\"微辣\",\"中辣\"]").build();

        // Mock: mapper 返回菜品列表和对应口味
        when(dishMapper.list(any(Dish.class))).thenReturn(Arrays.asList(dish1, dish2));
        when(dishFlavorMapper.getByDishId(1L)).thenReturn(Collections.singletonList(flavor1));
        when(dishFlavorMapper.getByDishId(2L)).thenReturn(Collections.singletonList(flavor2));

        // 执行
        Dish query = Dish.builder().categoryId(1L).build();
        List<DishVO> result = dishService.listWithFlavor(query);

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("宫保鸡丁", result.get(0).getName());
        assertEquals(1, result.get(0).getFlavors().size());
        assertEquals("辣度", result.get(0).getFlavors().get(0).getName());

        assertEquals("鱼香肉丝", result.get(1).getName());
        assertEquals(1, result.get(1).getFlavors().size());

        // 验证 mapper 被调用了正确的次数（N+1: 1次菜品查询 + 2次口味查询）
        verify(dishMapper, times(1)).list(any(Dish.class));
        verify(dishFlavorMapper, times(2)).getByDishId(anyLong());
    }

    @Test
    @DisplayName("listWithFlavor: 分类下无菜品 - 返回空列表")
    void testListWithFlavor_Empty() {
        when(dishMapper.list(any(Dish.class))).thenReturn(Collections.emptyList());

        Dish query = Dish.builder().categoryId(99L).build();
        List<DishVO> result = dishService.listWithFlavor(query);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        // 没有菜品时不应查询口味
        verify(dishFlavorMapper, never()).getByDishId(anyLong());
    }

    // ========== list: 根据分类ID查询菜品列表（带缓存） ==========

    @Test
    @DisplayName("list: 根据分类ID查询起售菜品")
    void testList() {
        Dish dish = Dish.builder().id(1L).name("宫保鸡丁").categoryId(1L).status(StatusConstant.ENABLE).build();
        when(dishMapper.list(any(Dish.class))).thenReturn(Collections.singletonList(dish));

        List<Dish> result = dishService.list(1L);

        assertEquals(1, result.size());
        assertEquals("宫保鸡丁", result.get(0).getName());
        verify(dishMapper).list(argThat(d ->
                d.getCategoryId().equals(1L) && d.getStatus().equals(StatusConstant.ENABLE)
        ));
    }

    // ========== deleteBatch: 批量删除菜品（含业务校验） ==========

    @Test
    @DisplayName("deleteBatch: 删除起售中的菜品 - 应抛异常")
    void testDeleteBatch_DishOnSale() {
        // 菜品状态为起售（ENABLE）
        Dish onSaleDish = Dish.builder().id(1L).name("热卖菜").status(StatusConstant.ENABLE).build();
        when(dishMapper.getById(1L)).thenReturn(onSaleDish);

        List<Long> ids = Collections.singletonList(1L);

        // 应抛出 DeletionNotAllowedException
        DeletionNotAllowedException ex = assertThrows(
                DeletionNotAllowedException.class,
                () -> dishService.deleteBatch(ids)
        );
        assertTrue(ex.getMessage().contains("起售"));

        // 不应执行删除操作
        verify(dishMapper, never()).deleteByIds(anyList());
        verify(dishFlavorMapper, never()).deleteByDishIds(anyList());
    }

    @Test
    @DisplayName("deleteBatch: 删除被套餐关联的菜品 - 应抛异常")
    void testDeleteBatch_RelatedBySetmeal() {
        // 菜品已停售，可以删除
        Dish stoppedDish = Dish.builder().id(1L).name("停售菜").status(StatusConstant.DISABLE).build();
        when(dishMapper.getById(1L)).thenReturn(stoppedDish);
        // 但该菜品被套餐关联
        when(setmealDishMapper.getSetmealIdsByDishIds(anyList())).thenReturn(Arrays.asList(100L, 200L));

        List<Long> ids = Collections.singletonList(1L);

        DeletionNotAllowedException ex = assertThrows(
                DeletionNotAllowedException.class,
                () -> dishService.deleteBatch(ids)
        );
        assertTrue(ex.getMessage().contains("套餐"));

        verify(dishMapper, never()).deleteByIds(anyList());
    }

    @Test
    @DisplayName("deleteBatch: 正常删除停售且无关联的菜品")
    void testDeleteBatch_Success() {
        Dish stoppedDish = Dish.builder().id(1L).name("可删菜品").status(StatusConstant.DISABLE).build();
        when(dishMapper.getById(1L)).thenReturn(stoppedDish);
        when(setmealDishMapper.getSetmealIdsByDishIds(anyList())).thenReturn(Collections.emptyList());

        dishService.deleteBatch(Collections.singletonList(1L));

        // 验证确实执行了删除
        verify(dishMapper).deleteByIds(eq(Collections.singletonList(1L)));
        verify(dishFlavorMapper).deleteByDishIds(eq(Collections.singletonList(1L)));
    }

    // ========== getByIdWithFlavor: 根据ID查菜品详情 ==========

    @Test
    @DisplayName("getByIdWithFlavor: 查询菜品详情及口味")
    void testGetByIdWithFlavor() {
        Dish dish = Dish.builder().id(1L).name("宫保鸡丁").categoryId(1L).price(new BigDecimal("28.00")).build();
        DishFlavor flavor = DishFlavor.builder().id(10L).dishId(1L).name("辣度").value("[\"微辣\",\"中辣\"]").build();

        when(dishMapper.getById(1L)).thenReturn(dish);
        when(dishFlavorMapper.getByDishId(1L)).thenReturn(Collections.singletonList(flavor));

        DishVO result = dishService.getByIdWithFlavor(1L);

        assertNotNull(result);
        assertEquals("宫保鸡丁", result.getName());
        assertEquals(new BigDecimal("28.00"), result.getPrice());
        assertEquals(1, result.getFlavors().size());
        assertEquals("辣度", result.getFlavors().get(0).getName());
    }

    // ========== startOrStop: 起售/停售 ==========

    @Test
    @DisplayName("startOrStop: 停售菜品时联动停售关联套餐")
    void testStartOrStop_StopDish() {
        // 停售菜品时，关联的套餐也要停售
        when(setmealDishMapper.getSetmealIdsByDishIds(anyList())).thenReturn(Arrays.asList(100L));

        dishService.startOrStop(StatusConstant.DISABLE, 1L);

        // 验证菜品状态被更新
        verify(dishMapper).update(argThat(d ->
                d.getId().equals(1L) && d.getStatus().equals(StatusConstant.DISABLE)
        ));
        // 验证关联套餐也被停售
        verify(setmealDishMapper).update(argThat(s ->
                s.getId().equals(100L) && s.getStatus().equals(StatusConstant.DISABLE)
        ));
    }
}
