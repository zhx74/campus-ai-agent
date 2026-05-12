package com.campus.canteen.mapper;

import com.campus.canteen.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    // 批量插入口味数据
    void insertBatch(List<DishFlavor> flavors);

    // 根据菜品ID来删除口�?
    @Delete("delete from dish_flavor where dish_id = #{dishId}")
    void deleteByDishId(Long dishId);

    // 根据菜品ID集合来批量删除口�?
    void deleteByDishIds(List<Long> dishIds);

    // 根据菜品ID来查询口味数�?
    @Select("select * from dish_flavor where dish_id = #{dishId}")
    List<DishFlavor> getByDishId(Long dishId);
}






