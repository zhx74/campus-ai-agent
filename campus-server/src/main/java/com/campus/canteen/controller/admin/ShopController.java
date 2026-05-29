package com.campus.canteen.controller.admin;

import com.campus.canteen.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Tag(name = "店铺相关接口")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    // 设置店铺的营业状态
    @PutMapping("/{status}")
    @Operation(summary = "设置店铺营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置店铺的营业状态为:{}", status == 1 ? "营业" : "打烊");

        redisTemplate.opsForValue().set(KEY, status);

        return Result.success();
    }

    // 查询店铺的营业状态
    @GetMapping("/status")
    @Operation(summary = "获取店铺的营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        log.info("获取到店铺的营业状态为：{}", status == 1 ? "营业" : "打烊");

        return Result.success(status);
    }
}
