package com.coresolution.pe.entity;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class KpiScoreSaveReq {
    private int year;
    private String hospitalName; // 필요 없으면 제거
    private String empId;
    private BigDecimal totalScore;
    private BigDecimal multiInteam10;
    private BigDecimal multiBoss5;
    private BigDecimal multiClinic5;

}
