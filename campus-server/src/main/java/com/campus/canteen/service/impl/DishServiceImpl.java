package com.campus.canteen.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.canteen.constant.MessageConstant;
import com.campus.canteen.constant.StatusConstant;
import com.campus.canteen.dto.DishDTO;
import com.campus.canteen.dto.DishPageQueryDTO;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.DishFlavor;
import com.campus.canteen.entity.Setmeal;
import com.campus.canteen.exception.DeletionNotAllowedException;
import com.campus.canteen.mapper.DishFlavorMapper;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.SetmealDishMapper;
import com.campus.canteen.result.PageResult;
import com.campus.canteen.service.DishService;
import com.campus.canteen.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    // 新增菜品和对应的口味
    @Override
    @Transactional
    @CacheEvict(value = {"dishes", "dishWithFlavors"}, allEntries = true)
    public void savaWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        // 菜品表插入数�?
        dishMapper.insert(dish);

        // 获取insert语句生成的主键�?
        Long dishId = dish.getId();

        // 向口味表插入一条或多条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            // 向口味表插入n条数�?
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    // 菜品分页查询
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {

        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());

        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);//固定的分页查询用page<T>来接受mapper返回�?

        return new PageResult(page.getTotal(), page.getResult());
    }

    // 菜品批量删除
    @Transactional
    @Override
    @CacheEvict(value = {"dishes", "dishWithFlavors"}, allEntries = true)
    public void deleteBatch(List<Long> ids) {
        // 判断当前菜品是否能够删除---是否存在起售�?
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish.getStatus() == StatusConstant.ENABLE) {
                // 当前菜品处于起售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        // 判断当前菜品是否能够删除---是否被套餐关�?
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && !setmealIds.isEmpty()) {
            // 当前菜品被套餐关联，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 删除菜品表中的菜品数�?
        /*for(Long id : ids) {
            dishMapper.deleteById(id);
            // 删除菜品关联口味
            dishFlavorMapper.deleteByDishId(id);
        }*/
        //因为两个方法调用的对象不同了 一个id 一个ids所以不删除之前的sql语句也不影响

        // 删除菜品表中的菜品数�?
        dishMapper.deleteByIds(ids);

        // 删除菜品关联口味
        dishFlavorMapper.deleteByDishIds(ids);

    }

    // 根据ID查询菜品
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 根据id查询菜品数据
        Dish dish = dishMapper.getById(id);

        // 根据菜品id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        // 将查询到的数据封装到VO
        DishVO dishVO = new DishVO();

        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    // 修改菜品基本信息和口�?
    @Override
    @CacheEvict(value = {"dishes", "dishWithFlavors"}, allEntries = true)
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        // 修改菜品基本信息
        dishMapper.update(dish);

        // 删除原有的口味数�?
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        // 重新插入口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            // 向口味表插入n条数�?
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    // 菜品起售停售
    @Override
    @CacheEvict(value = {"dishes", "dishWithFlavors"}, allEntries = true)
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);

        if(status == StatusConstant.DISABLE) {
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            // 如果停售状�?
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);

            if(setmealIds != null && !setmealIds.isEmpty()) {
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .build();
                    setmealDishMapper.update(setmeal);
                }
            }
        }
    }

    // 根据分类id来查询菜�?
    @Cacheable(value = "dishes", key = "#categoryId")
    @Override
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();

        return dishMapper.list(dish);
    }

    /**
            * 条件查询菜品和口�?
     * @param dish
     * @return
             */
    @Cacheable(value = "dishWithFlavors", key = "#dish.categoryId")
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口�?
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}
