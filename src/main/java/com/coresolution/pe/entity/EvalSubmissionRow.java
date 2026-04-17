package com.coresolution.pe.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalSubmissionRow {
    private Long id;
    private int evalYear;
    private String evaluatorId;
    private String targetId;
    private String dataEv;
    private String dataType;

    private String answersJson; // JSON 문자열
    private int answeredCount; // 전체 응답 수
    private int radioCount; // 객관식 수
    private int totalScore; // 가중치 반영 총점
    private double avgScore; // totalScore / radioCount

    private int version;
    private String updatedBy; // 업데이트한 사용자 ID

    private java.math.BigDecimal score100; // 100점 환산 점수
    private Integer resp;
}