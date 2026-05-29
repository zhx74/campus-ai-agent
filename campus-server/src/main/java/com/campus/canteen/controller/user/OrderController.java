package com.campus.canteen.controller.user;

import com.campus.canteen.dto.OrdersPaymentDTO;
import com.campus.canteen.dto.OrdersSubmitDTO;
import com.campus.canteen.result.PageResult;
import com.campus.canteen.result.Result;
import com.campus.canteen.service.OrderService;
import com.campus.canteen.vo.OrderPaymentVO;
import com.campus.canteen.vo.OrderSubmitVO;
import com.campus.canteen.vo.OrderVO;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Tag(name = "用户端订单相关接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    // 用户下单
    @PostMapping("/submit")
    @Operation(summary = "用户下单")
    @RateLimiter(name = "orderSubmit", fallbackMethod = "fallback")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单，参数为：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);

        return Result.success(orderSubmitVO);
    }

    public Result<OrderSubmitVO> fallback(OrdersSubmitDTO ordersSubmitDTO, Throwable t) {
        return Result.error("请求过于频繁，请稍后重试");
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @Operation(summary = "订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单查询
     *
     * @param page
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @GetMapping("/historyOrders")
    @Operation(summary = "历史订单查询")
    public Result<PageResult> page(int page, int pageSize, Integer status) {
        PageResult pageResult = orderService.pageQuery4User(page, pageSize, status);
        return Result.success(pageResult);
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @Operation(summary = "查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }

    /**
     * 用户取消订单
     *
     * @return
     */
    @PutMapping("/cancel/{id}")
    @Operation(summary = "取消订单")
    public Result cancel(@PathVariable("id") Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单
     *
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @Operation(summary = "再来一单")
    public Result repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }

    // 催单
    @GetMapping("/reminder/{id}")
    @Operation(summary = "客户催单")
    public Result reminder(@PathVariable Long id) {

        orderService.reminder(id);

        return Result.success();
    }

}
