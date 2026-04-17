package com.coresolution.pe.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EvalSummaryRow {
    private String orgName;
    private String orgName2;
    private String deptName;
    private String empId;
    private String empName;
    private String position;

    // 0~100 환산 평균
    private Double staffToBoss100; // C + AA
    private Double bossToBoss100; // A + AA
    private Double bossToStaff100; // B + AE
    private Double staffToStaff100; // D + AE

    private Double totalAvg100; // ✅ 추가: 받은 항목 개수로 나눈 평균(100점)
}
