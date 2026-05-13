package com.campus.canteen.mq;

import com.campus.canteen.config.RabbitMQConfig;
import com.campus.canteen.entity.Orders;
import com.campus.canteen.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单延时队列消费者 - 处理超时未支付订单
 */
@Component
@Slf4j
public class OrderDelayConsumer {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 监听延时队列，处理超时订单
     * 
     * @param orderId 订单ID
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_DELAY_QUEUE)
    public void handleOrderTimeout(Long orderId) {
        log.info("收到订单超时消息，订单ID: {}", orderId);
        
        try {
            // 查询订单
            Orders order = orderMapper.getById(orderId);
            
            if (order == null) {
                log.warn("订单不存在，订单ID: {}", orderId);
                return;
            }
            
            // 检查订单状态：如果仍然是"待支付"（status=1），则取消订单
            if (order.getStatus() == 1) {
                log.info("订单超时未支付，开始取消订单，订单ID: {}", orderId);
                
                // 更新订单状态为"已取消"（status=6）
                order.setStatus(6);
                order.setCancelReason("订单超时，自动取消");
                order.setCancelTime(LocalDateTime.now());
                
                orderMapper.update(order);
                
                log.info("订单已自动取消，订单ID: {}", orderId);
            } else {
                log.info("订单状态已变更（可能已支付），无需处理，订单ID: {}, 当前状态: {}", 
                    orderId, order.getStatus());
            }
        } catch (Exception e) {
            log.error("处理超时订单失败，订单ID: {}", orderId, e);
            // 这里可以添加重试逻辑或告警
        }
    }
}
