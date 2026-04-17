package com.coresolution.pe.entity;

import java.math.BigDecimal;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KpiSummaryRow {
    // 직원정보
    private String deptName;
    private String empNo;
    private String position;
    private String name;

    // Ⅰ 텍스트 + 숫자 + 소계
    private String iBedAvailTxt, iDayChargeTxt, iSalesTxt;
    private BigDecimal iBedAvailNum, iDayChargeNum, iSalesNum;
    private BigDecimal iSubtotal;

    // Ⅱ 텍스트 + 숫자 + 소계
    private String iiPatientSatTxt, iiProtectorSatTxt, iiAccidentTxt, iiMealTxt;
    private BigDecimal iiPatientSatNum, iiProtectorSatNum, iiAccidentNum;
    private BigDecimal iiSubtotal;

    // Ⅲ 텍스트 + 숫자 + 소계
    private String iiiLinkTxt, iiiPromotionTxt, iiiQITxt;
    private BigDecimal iiiLinkNum, iiiPromotionNum, iiiQINum;
    private BigDecimal iiiSubtotal;

    // Ⅳ 텍스트 + 숫자 + 소계
    private String ivEduTxt, ivVolunteerTxt, ivDiscussTxt;
    private BigDecimal ivEduNum, ivVolunteerNum, ivDiscussNum;
    private BigDecimal ivSubtotal;

    // Ⅴ 다면평가
    private BigDecimal vExperience, vBossToStaff, vStaffToStaff, vSubtotal;

    // 총점/등급
    private BigDecimal total;
    private String evalGrade;
    private String grade;

    private String orgName;
    private String teamName;
}
