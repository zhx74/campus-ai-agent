package com.sky.service;

import com.sky.vo.TurnoverReportVO;

import java.time.LocalDate;

public interface ReportService {
    // 统计指定时间区间营业额
    TurnoverReportVO getTurnOverStatistics(LocalDate begin, LocalDate end);
}
