package com.sky.service;

import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {
    // 统计指定时间区间营业额
    TurnoverReportVO getTurnOverStatistics(LocalDate begin, LocalDate end);

    // 统计指定区间内用户数据
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    // 统计指定时间区间内的订单数据
    OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end);

    // 统计指定时间区间内的销量排名前十
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);
}
