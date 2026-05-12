package com.campus.canteen.service;

import com.campus.canteen.vo.OrderReportVO;
import com.campus.canteen.vo.SalesTop10ReportVO;
import com.campus.canteen.vo.TurnoverReportVO;
import com.campus.canteen.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {
    // 统计指定时间区间营业�?
    TurnoverReportVO getTurnOverStatistics(LocalDate begin, LocalDate end);

    // 统计指定区间内用户数�?
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    // 统计指定时间区间内的订单数据
    OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end);

    // 统计指定时间区间内的销量排名前�?
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    void exportBusinessData(HttpServletResponse response);
}






