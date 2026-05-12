package com.campus.canteen.mapper;

import com.github.pagehelper.Page;
import com.campus.canteen.annotation.AutoFill;
import com.campus.canteen.dto.DishPageQueryDTO;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.enumeration.OperationType;
import com.campus.canteen.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    // 插入菜品数据
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    // 分页查询菜品
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    // 根据ID查询菜品
    @Select("select  * from dish where id = #{id}")
    Dish getById(Long id);

    // 根据主键删除菜品数据
    @Delete("delete from dish where id = #{id}")//但是用这种方法如果ids有很多那执行sql语句就会执行很多�? 所以要吧sql语句放在mapper.xml中改为动态加个循环就好了 只执行一�?
    //但是不擅除也不影响的因为对象不一样了一个是id  一个是ids�?
    void deleteById(Long id);

    // 根据菜品ID批量删除菜品
    void deleteByIds(List<Long> ids);

    // 修改菜品基本信息和口�?
    @AutoFill(OperationType.UPDATE)
    void update(Dish dish);

    // 根据分类id查询菜品(可能多个)
    List<Dish> list(Dish dish);

    /**
     * 根据套餐id查询菜品
     * @param setmealId
     * @return
     */
    @Select("select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = #{setmealId}")
    List<Dish> getBySetmealId(Long setmealId);

    /**
     * 根据条件统计菜品数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}






