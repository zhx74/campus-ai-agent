package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {

    // 新增菜品和对应的口味
    public void savaWithFlavor(DishDTO dishDTO);

    // 菜品分页查询
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    // 菜品批量删除
    void deleteBatch(List<Long> ids);

    // 根据ID查询菜品
    DishVO getByIdWithFlavor(Long id);

    // 修改菜品基本信息和口味
    void updateWithFlavor(DishDTO dishDTO);
}
