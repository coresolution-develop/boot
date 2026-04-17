package com.coresolution.pe.entity;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class KyTotal2025Row {
    // 공통 메타
    private String orgName;
    private String deptName;
    private String empNo;
    private String position;
    private String name;

    private BigDecimal totalScore; // 총점 (100)

    // I. 재무성과(40)
    private BigDecimal bedOccSubtotalA; // 병상가동률(15)
    private BigDecimal dayRevSubtotalB; // 일당진료비(15)
    private BigDecimal salesSubtotalC; // 매출/원가(10)
    private BigDecimal financeTotal; // 소계 40

    // II. 고객서비스(15)
    private BigDecimal patientSat5; // 환자만족도(5)
    private BigDecimal guardianSat5; // 보호자만족도(5)
    private BigDecimal incidentTotal; // 환자안전사고(4)
    private BigDecimal mealAssist1; // 식사수발(1) - 있으면

    // III. 프로세스혁신(15)
    private BigDecimal linkTotalScore7; // 입원·장례연계(7)
    private BigDecimal act5Score5; // 홍보활동/참여5점(5)
    private BigDecimal qiTotalScore3; // QI 질향상(3)

    // IV. 학습성장(10)
    private BigDecimal eduTotalScore3; // 교육이수(3)
    private BigDecimal volunteerScore4; // 자원봉사(4)
    private BigDecimal bookTotalScore3; // 독서토론(3)

    // V. 다면평가(20) – 필요 시 3분할
    private BigDecimal multiGh10;
    private BigDecimal multiDept5;
    private BigDecimal multiClinic5;
    private BigDecimal multiTotal;
}