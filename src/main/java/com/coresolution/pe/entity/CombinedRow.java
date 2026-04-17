package com.coresolution.pe.entity;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class CombinedRow {
    private String deptName, empId, empName, position;

    private String total;
    private String newInConn, funeralConn, bothConn;
    private String kpiI;
    private String scoreI;
    private String volCnt;
    private String kpiII;
    private String scoreII;
    private String eduRate;
    private String kpiIII;
    private String scoreIII;
    private String noteI, noteII, noteIII;

    // V
    private BigDecimal bossToStaff100;
    private BigDecimal bossToStaff35;
    private BigDecimal staffToStaff100;
    private BigDecimal staffToStaff35;
    private BigDecimal evalSum70;
    private String evalGrade;

    // I 계산
    private BigDecimal kpiICalc;
    private Integer scoreICalc;

    // II 계산
    private BigDecimal kpiIICalc;
    private Integer scoreIICalc;

    // III 계산 ✅ 추가
    private BigDecimal kpiIIICalc;
    private Integer scoreIIICalc;

    // 총점
    private BigDecimal totalCalc;

    /*
     * =========================
     * ✅ 신규(20/15/15) KPI 원점수 + 100점 환산 표시용 (추가만)
     * =========================
     */

    // 신규 KPI 원점수(20/15/15) - SQL alias: kpiI20Calc, kpiII15Calc, kpiIII15Calc
    private BigDecimal kpiI20Calc;
    private BigDecimal kpiII15Calc;
    private BigDecimal kpiIII15Calc;

    // 신규 KPI 100점 환산 - SQL alias: scoreI100, scoreII100, scoreIII100
    private BigDecimal scoreI100;
    private BigDecimal scoreII100;
    private BigDecimal scoreIII100;

    // (선택) 화면에서 orgName/p100도 쓰면 같이 받기
    private String orgName; // SQL alias: orgName
    private String orgName2; // SQL alias: orgName
    private BigDecimal p100; // SQL alias: p100
    private BigDecimal eval100; // 다면 통합 0~100
}
