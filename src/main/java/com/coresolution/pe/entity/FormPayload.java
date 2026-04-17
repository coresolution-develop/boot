package com.coresolution.pe.entity;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true) // ← 이 줄이 있어야 FormPayload.builder() 사용 가능
public class FormPayload {
    private UserPE target; // 대상자
    private List<Evaluation> questions; // 문항 리스트
    private Map<String, String> answerMap; // 프리필: name → value (라디오/주관식 통합)
    private Integer answered; // 제출된 문항 수
    private Integer total; // 총 문항 수
    private boolean completed; // 완료 여부
    private Double avgScore; // 평균 점수(객관식 기준)
    private Integer totalScore;

    // ★ 편집용: 뷰에서 바로 쓰는 JSON 문자열
    private String answersBundleJson;
    private Map<Integer, String> nameMap;

    // ✅ 뷰 편의: 서비스에서 만들어 전달
    private Map<String, List<Evaluation>> grouped; // d3별 객관식 그룹 (예: 근무태도/처리능력/업무실적)
    private List<String> order; // 그룹 표시 순서
    private List<Evaluation> essay; // 주관식 목록
}
