package com.campus.canteen.dto;

import com.campus.canteen.entity.OrderDetail;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrdersDTO implements Serializable {

    private Long id;

    //订单�?
    private String number;

    //订单状�?1待付款，2待派送，3已派送，4已完成，5已取�?
    private Integer status;

    //下单用户id
    private Long userId;

    //地址id
    private Long addressBookId;

    //下单时间
    private LocalDateTime orderTime;

    //结账时间
    private LocalDateTime checkoutTime;

    //支付方式 1微信�?支付�?
    private Integer payMethod;

    //实收金额
    private BigDecimal amount;

    //备注
    private String remark;

    //用户�?
    private String userName;

    //手机�?
    private String phone;

    //地址
    private String address;

    //收货�?
    private String consignee;

    private List<OrderDetail> orderDetails;

}






