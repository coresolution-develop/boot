package com.coresolution.pe.entity;

import java.util.List;
import java.util.Map;

import com.coresolution.pe.service.EvalReportService.RowAgg;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportVM {
    private String dataEv;
    private String relationPath;
    private String typeCode;
    private List<RowAgg> rows;

    private Double overallAvg; // (기존) 응답 가중 평균 (5/10점 스케일)
    private Double overallScore100; // (기존) 100점 환산

    private Double questionAvg5; // 5점 만점 기준
    private Double questionAvg100; // 100점 환산 (= questionAvg5 * 20)
    private Double questionAvg; // 문항평균 점수 (AA=최대 10, AB=최대 5)

    private Integer overallResp;
    private Map<String, Double> ratio;
    private List<String> essayList;

    private String essaySummary; // 요약 결과 (AI or 로컬)
    private int essayCount; // 코멘트 개수
}
