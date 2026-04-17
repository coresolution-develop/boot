package com.coresolution.pe.entity;

import java.util.List;

import lombok.Builder;
import lombok.Data;

// 객관식 인사이트(강/약점, 한줄요약)
@Data
@Builder
public class ObjectiveInsights {
    List<String> strengths;
    List<String> improvements;
    String headline; // 예: "종합 92점, 구간 평균 85점, 전체 평균 84점"
}
