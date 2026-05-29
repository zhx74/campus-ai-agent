package com.campus.canteen.controller.admin;

import com.campus.canteen.result.Result;
import com.campus.canteen.service.ReportService;
import com.campus.canteen.vo.OrderReportVO;
import com.campus.canteen.vo.SalesTop10ReportVO;
import com.campus.canteen.vo.TurnoverReportVO;
import com.campus.canteen.vo.UserReportVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;

@RestController
@Tag(name = "数据统计接口")
@RequestMapping("/admin/report")
@Slf4j
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/turnoverStatistics")
    @Operation(summary = "营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin, @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("营业额统计：{}，{}", begin, end);
        return Result.success(reportService.getTurnOverStatistics(begin, end));
    }

    //  用户数据统计
    @GetMapping("/userStatistics")
    @Operation(summary = "用户统计")
    public Result<UserReportVO> userStatistics(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin, @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("用户数据统计：{}, {}", begin, end);

        return  Result.success(reportService.getUserStatistics(begin, end));
    }

    //  订单数据统计
    @GetMapping("/ordersStatistics")
    @Operation(summary = "订单统计")
    public Result<OrderReportVO> ordersStatistics(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin, @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("用户数据统计: {}, {}", begin, end);

        return  Result.success(reportService.getOrderStatistics(begin, end));
    }

    //  销量排名top10
    @GetMapping("/top10")
    @Operation(summary = "销量排名top10")
    public Result<SalesTop10ReportVO> top10(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin, @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("销量排名top10: {}, {}", begin, end);

        return  Result.success(reportService.getSalesTop10(begin, end));
    }

    //  导出运营数据报表
        @GetMapping("/export")
        @Operation(summary = "导出运营数据报表")
        public void exportBusinessData(HttpServletResponse response) {
            // 直接调用 Service
            reportService.exportBusinessData(response);
        }

}
