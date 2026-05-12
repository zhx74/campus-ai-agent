package com.campus.canteen.service;

import com.campus.canteen.dto.ShoppingCartDTO;
import com.campus.canteen.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    // 添加购物�?
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    // 查看购物�?
    List<ShoppingCart> showShoppingCart();

    // 清空购物�?
    void cleanShoppingCart();

    // 删除购物车中一个商�?
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);
}






