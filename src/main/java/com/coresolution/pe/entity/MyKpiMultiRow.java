package com.coresolution.pe.entity;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class MyKpiMultiRow {

    // ── 기본 정보 ─────────────────────
    private String orgName; // 기관명 (c_name)
    private String teamName; // 팀명
    private String deptName; // 부서명 (sub_name)
    private String empNo; // 사번 (id)
    private String position; // 직책
    private String name; // 성명

    // ── 다면평가 원점수(10점 척도) & 표본 수 ─────
    /** 경혁팀간 평균(10점 척도) */
    private BigDecimal inTeam10;
    /** 경혁팀간 평가자 수 */
    private Integer inTeamCnt;

    /** 부서원평가 평균(10점 척도 raw) */
    private BigDecimal bossToStaff10Raw;
    /** 부서원평가 평가자 수 */
    private Integer bossToStaffCnt;

    /** 진료부평가 평균(10점 척도 raw) */
    private BigDecimal fromClinic10Raw;
    /** 진료부평가 평가자 수 */
    private Integer fromClinicCnt;

    // ── 5점 척도(표에 찍힐 값, 0 또는 NULL → 4.3 보정 적용 후) ─────
    /** 부서원평가 5점 척도 */
    private BigDecimal bossToStaff5;

    /** 진료부평가 5점 척도 */
    private BigDecimal fromClinic5;

    // ── V. 다면평가 소계 (20점 만점) ─────
    /** 경혁팀간(10) + 부서원평가(5) + 진료부평가(5) */
    private BigDecimal multiSubtotal20;
}