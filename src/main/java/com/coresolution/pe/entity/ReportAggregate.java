package com.coresolution.pe.entity;

// 서비스 반환용 묶음 (컨트롤러가 model에 싣기 쉽게)

public record ReportAggregate(
        ReportVM vm,
        CommentReportDTO report, // null 가능 (JSON 미존재시)
        ObjectiveStats stats, // 차트 데이터
        CommentReportDTO scoreReport // ★ 신규: 점수기반 요약
) {
}