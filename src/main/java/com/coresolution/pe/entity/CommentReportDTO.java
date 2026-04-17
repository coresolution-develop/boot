package com.coresolution.pe.entity;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // ★ 추가
public class CommentReportDTO {
    private String title;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    private String tone;
    private Metrics metrics;
    private List<String> next_actions;
    private Org org;
    // ─────────────────────────────
    // 새로 추가되는 필드들
    // ─────────────────────────────

    @JsonProperty("overall_comparison")
    private OverallComparison overallComparison;

    // "I 재무성과(40)→100" 같은 키를 그대로 받기 위해 Map 사용
    private Map<String, Double> kpi;

    @JsonProperty("by_area")
    private Map<String, AreaDetail> byArea;

    @Data
    public static class Metrics {
        private Integer responses;
        private Integer essays;
        private Double score_overall;
    }

    @Data
    public static class OverallComparison {
        private Double my;

        @JsonProperty("same_period")
        private Double samePeriod;
    }

    @Data
    public static class AreaDetail {
        private Double my;

        @JsonProperty("same_period")
        private Double samePeriod;

        private String summary;
        private List<String> strengths;
        private List<String> improvements;
    }

    @Data
    public static class Org { // ★추가
        private String name; // 병원명
        private String mission; // 미션 문구
        private java.util.List<String> visionList; // ★추가(선택)
        private String vision; // 비전 문구
        private String ciWordmarkUrl; // 워드마크 이미지 URL
        private String ciSymbolUrl; // 심볼 이미지 URL (있으면)
    }
}