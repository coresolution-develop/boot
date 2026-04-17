package com.coresolution.pe.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class KpiPersonal2025 {

    private Long id; // PK (AUTO_INCREMENT)

    private Integer evalYear; // eval_year
    private String hospitalName; // hospital_name
    private String empId; // emp_id
    private BigDecimal totalScore; // total_score

    // ─── 병상가동률 블럭 ─────────────────────────
    private BigDecimal bedOcc2024Pct; // bed_occ_2024_pct
    private BigDecimal bedOcc2025Pct; // bed_occ_2025_pct
    private BigDecimal bedOccYoyPct; // bed_occ_yoy_pct
    private String bedOccRemarks; // bed_occ_remarks
    private BigDecimal bedOcc2024Score; // bed_occ_2024_score
    private BigDecimal bedOccYoyScore; // bed_occ_yoy_score
    private BigDecimal bedOccSubtotalA; // bed_occ_subtotal_a

    // ─── 일당진료비 블럭 ───────────────────────
    private Integer dayRev2024; // day_rev_2024
    private Integer dayRev2025; // day_rev_2025
    private BigDecimal dayRevYoyPct; // day_rev_yoy_pct
    private String dayRevRemarks; // day_rev_remarks
    private BigDecimal dayRevYoyScore; // day_rev_yoy_score
    private BigDecimal dayRevTargetScore; // day_rev_target_score
    private BigDecimal dayRevSubtotalB; // day_rev_subtotal_b

    // ─── 매출 블럭 ────────────────────────────
    private Long sales2024; // sales_2024
    private Long sales2025; // sales_2025
    private BigDecimal salesYoyPct; // sales_yoy_pct
    private BigDecimal salesYoyScore; // sales_yoy_score
    private BigDecimal salesTargetScore; // sales_target_score
    private BigDecimal salesSubtotalC; // sales_subtotal_c

    private String financeRemarks; // finance_remarks
    private BigDecimal financeTotal; // finance_total

    // ─── 만족도 ───────────────────────────────
    private BigDecimal guardianSat5; // guardian_sat_5
    private BigDecimal patientSat5; // patient_sat_5
    private BigDecimal csTotal10; // cs_total_10

    // ─── 위해사건 ────────────────────────────
    private BigDecimal incidentRate2024; // incident_rate_2024
    private BigDecimal incidentRate2025; // incident_rate_2025
    private BigDecimal incidentDiffPt; // incident_diff_pt
    private BigDecimal incidentCurrScore; // incident_curr_score
    private BigDecimal incidentDiffScore; // incident_diff_score
    private BigDecimal incidentTotal; // incident_total
    private String incidentRemarks; // incident_remarks

    // ─── 홍보/참여(1점) ───────────────────────
    private Integer promoGoal2025; // promo_goal_2025
    private Integer promoPersonalCount; // promo_personal_count
    private BigDecimal promoDailyAvg; // promo_daily_avg
    private BigDecimal promoScore1; // promo_score_1

    // ─── 입원/장례 연계 ───────────────────────
    private Integer linkGoal; // link_goal
    private Integer linkInpatientCnt; // link_inpatient_cnt
    private Integer linkFuneralCnt; // link_funeral_cnt
    private Integer linkTotalCnt; // link_total_cnt
    private BigDecimal linkPersonalScore6; // link_personal_score_6
    private BigDecimal linkDeptRatePct; // link_dept_rate_pct
    private BigDecimal linkDeptScore1; // link_dept_score_1
    private BigDecimal linkTotalScore7; // link_total_score_7
    private String linkRemarks; // link_remarks

    // ─── 각종 참여(5점) ───────────────────────
    private Integer act5Goal2025; // act5_goal_2025
    private Integer act5PersonalCount; // act5_personal_count
    private BigDecimal act5Score5; // act5_score_5

    // ─── QI 활동 ────────────────────────────
    private String qiTopic; // qi_topic
    private String qiRole; // qi_role
    private BigDecimal qiRoleScore2; // qi_role_score_2
    private String qiFinalOrg; // qi_final_org
    private String qiAward; // qi_award
    private BigDecimal qiDeptAwardScore2; // qi_dept_award_score_2
    private BigDecimal qiDeptPartScore1; // qi_dept_part_score_1
    private BigDecimal qiTotalScore3; // qi_total_score_3
    private String qiRemarks; // qi_remarks

    // ─── 교육 ───────────────────────────────
    private Integer eduGoal; // edu_goal
    private BigDecimal eduPersonalRatePct; // edu_personal_rate_pct
    private BigDecimal eduPersonalScore2; // edu_personal_score_2
    private BigDecimal eduDeptRatePct; // edu_dept_rate_pct
    private BigDecimal eduDeptScore1; // edu_dept_score_1
    private BigDecimal eduTotalScore3; // edu_total_score_3
    private String eduRemarks; // edu_remarks

    // ─── 동호회/자원봉사 ────────────────────
    private Integer clubGoal; // club_goal
    private Integer clubPersonalCount; // club_personal_count
    private BigDecimal clubPersonalScore3; // club_personal_score_3
    private BigDecimal clubDeptRatePct; // club_dept_rate_pct
    private BigDecimal clubDeptScore1; // club_dept_score_1
    private String clubDeptRemarks; // club_dept_remarks
    private BigDecimal volunteerScore4; // volunteer_score_4

    // ─── 독서토론 ───────────────────────────
    private Integer bookGoal; // book_goal
    private Integer bookAttendCount; // book_attend_count
    private BigDecimal bookAttendScore2; // book_attend_score_2
    private Integer bookPresentCount; // book_present_count
    private BigDecimal bookPresentScore1; // book_present_score_1
    private BigDecimal bookTotalScore3; // book_total_score_3
    private String bookRemarks; // book_remarks

    // ─── 생성/수정일 ────────────────────────
    private LocalDateTime createdAt; // created_at
    private LocalDateTime updatedAt; // updated_at
}
