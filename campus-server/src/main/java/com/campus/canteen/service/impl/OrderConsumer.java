package com.campus.canteen.service.impl;

import com.alibaba.fastjson.JSON;
import com.campus.canteen.constant.MessageConstant;
import com.campus.canteen.context.BaseContext;
import com.campus.canteen.dto.OrdersSubmitDTO;
import com.campus.canteen.entity.OrderDetail;
import com.campus.canteen.entity.Orders;
import com.campus.canteen.entity.ShoppingCart;
import com.campus.canteen.exception.AddressBookBusinessException;
import com.campus.canteen.exception.ShoppingCartBusinessException;
import com.campus.canteen.mapper.AddressBookMapper;
import com.campus.canteen.mapper.OrderDetailMapper;
import com.campus.canteen.mapper.OrderMapper;
import com.campus.canteen.mapper.ShoppingCartMapper;
import com.campus.canteen.service.OrderService;
import com.campus.canteen.vo.OrderSubmitVO;
import com.campus.canteen.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OrderConsumer {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private WebSocketServer websocketServer;
    @Autowired
    private RedissonClient redissonClient;

    @RabbitListener(queues = "order.create.queue")
    public void processOrderCreation(Map<String, Object> message) {
        OrdersSubmitDTO ordersSubmitDTO = (OrdersSubmitDTO) message.get("ordersSubmitDTO");
        Long userId = (Long) message.get("userId");

        // 处理业务异常
        var addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 提取购物车中涉及的菜品/套餐ID，按单品加锁，不相关的菜品互不阻塞
        List<String> lockKeys = shoppingCartList.stream()
                .map(cart -> cart.getDishId() != null
                        ? "order:dish:" + cart.getDishId()
                        : "order:setmeal:" + cart.getSetmealId())
                .distinct()
                .toList();

        RLock[] locks = lockKeys.stream()
                .map(key -> redissonClient.getLock(key))
                .toArray(RLock[]::new);
        RLock multiLock = redissonClient.getMultiLock(locks);
        multiLock.lock();
        try {
            // 向订单表插入数据
            Orders orders = new Orders();
            BeanUtils.copyProperties(ordersSubmitDTO, orders);
            orders.setOrderTime(LocalDateTime.now());
            orders.setPayStatus(Orders.UN_PAID);
            orders.setStatus(Orders.PENDING_PAYMENT);
            orders.setNumber(String.valueOf(System.currentTimeMillis()));
            orders.setPhone(addressBook.getPhone());
            orders.setConsignee(addressBook.getConsignee());
            orders.setUserId(userId);

            orderMapper.insert(orders);

            List<OrderDetail> orderDetailList = new ArrayList<>();
            for (ShoppingCart cart : shoppingCartList) {
                OrderDetail orderDetail = new OrderDetail();
                BeanUtils.copyProperties(cart, orderDetail);
                orderDetail.setOrderId(orders.getId());
                orderDetailList.add(orderDetail);
            }
            orderDetailMapper.insertBatch(orderDetailList);

            // 清空购物车
            shoppingCartMapper.deleteById(userId);
        } finally {
            multiLock.unlock();
        }
    }

    @RabbitListener(queues = "order.payment.queue")
    public void processPaymentSuccess(Map<String, Object> event) {
        String outTradeNo = (String) event.get("outTradeNo");
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // WebSocket推送
        Map<String, Object> push = Map.of("type", 1, "orderId", ordersDB.getId());
        websocketServer.sendToAllClient(JSON.toJSONString(push));
    }
}
