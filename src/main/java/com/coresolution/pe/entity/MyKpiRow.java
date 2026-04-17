package com.coresolution.pe.entity;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class MyKpiRow {
    // 소속/기본
    private String orgName;
    private String deptName;
    private String empId;
    private String empName;
    private String position;

    // 원본 KPI 텍스트/수치
    private String total;
    private String newInConn; // I-입원연계
    private String funeralConn; // I-장례연계
    private String bothConn;
    private String kpiI;
    private String scoreI;
    private String volCnt;
    private String kpiII;
    private String scoreII;
    private String eduRate;
    private String kpiIII;
    private String scoreIII;
    private String noteI, noteII, noteIII;

    // 다면평가
    private BigDecimal bossToStaff100;
    private BigDecimal bossToStaff35;
    private BigDecimal staffToStaff100;
    private BigDecimal staffToStaff35;
    private BigDecimal evalSum70;

    // 계산 필드
    private BigDecimal kpiICalc;
    private Integer scoreICalc;
    private BigDecimal kpiIICalc;
    private Integer scoreIICalc;
    private BigDecimal kpiIIICalc;
    private Integer scoreIIICalc;

    private BigDecimal totalCalc;
    private String evalGrade;
    // ─ 다면평가(원점수 100) ─
    private BigDecimal bFromClinicToTeam100; // B_fromClinicToTeam100
    private BigDecimal dInTeamToTeam100; // D_inTeamToTeam100
    private BigDecimal eBossToStaff100; // E_bossToStaff100
    private BigDecimal fStaffToBoss100; // F_staffToBoss100
    private BigDecimal gStaffToStaff100; // G_staffToStaff100

    // ─ prefer 값 & 가중 반영 ─
    private BigDecimal preferD; // preferD
    private BigDecimal preferF; // preferF
    private BigDecimal preferB; // preferB
    private BigDecimal vExperience; // vExperience
    private BigDecimal vBossToStaff; // vBossToStaff
    private BigDecimal vStaffToStaff; // vStaffToStaff
    private BigDecimal evalSum20; // evalSum20
    private BigDecimal totalCalc100;
}
