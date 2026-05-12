package com.campus.canteen.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单概览数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderOverViewVO implements Serializable {
    //待接单数�?
    private Integer waitingOrders;

    //待派送数�?
    private Integer deliveredOrders;

    //已完成数�?
    private Integer completedOrders;

    //已取消数�?
    private Integer cancelledOrders;

    //全部订单
    private Integer allOrders;
}






