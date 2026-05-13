package com.campus.canteen.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // 原有配置（保留）
    @Bean
    public Exchange orderExchange() {
        return ExchangeBuilder.directExchange("order.exchange").durable(true).build();
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable("order.create.queue").build();
    }

    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue()).to(orderExchange()).with("order.create").noargs();
    }

    @Bean
    public Queue orderPaymentQueue() {
        return QueueBuilder.durable("order.payment.queue").build();
    }

    @Bean
    public Binding orderPaymentBinding() {
        return BindingBuilder.bind(orderPaymentQueue()).to(orderExchange()).with("order.payment").noargs();
    }

    // ========== 新增：订单延时队列配置 ==========

    // 延时交换机名称
    public static final String ORDER_DELAYED_EXCHANGE = "order.delayed.exchange";

    // 延时队列名称
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";

    // 路由键
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    /**
     * 创建延时交换机（使用 x-delayed-message 类型）
     */
    @Bean
    public CustomExchange orderDelayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(ORDER_DELAYED_EXCHANGE, "x-delayed-message", true, false, args);
    }

    /**
     * 创建延时队列
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE).build();
    }

    /**
     * 绑定延时队列到延时交换机
     */
    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, CustomExchange orderDelayedExchange) {
        return BindingBuilder.bind(orderDelayQueue)
                .to(orderDelayedExchange)
                .with(ORDER_DELAY_ROUTING_KEY)
                .noargs();
    }
}
