package com.coresolution.pe.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.CombinedRow;
import com.coresolution.pe.entity.KpiCohortAvg;
import com.coresolution.pe.entity.KpiPersonal2025;
import com.coresolution.pe.entity.KpiSummaryRow;
import com.coresolution.pe.entity.KyTotal2025Row;
import com.coresolution.pe.entity.MyKpiMultiRow;
import com.coresolution.pe.entity.MyKpiRow;
import com.coresolution.pe.entity.StaffGradeRow;

@Mapper
public interface KpiInfo2025Mapper {

  /**
   * 연도 단위 전체 삭제 (필요 시)
   */
  @Delete("""
      DELETE FROM personnel_evaluation.kpi_personal_2025
       WHERE eval_year = #{year}
      """)
  int deleteByYear(int year);

  /**
   * KPI 행 INSERT
   * - PK(id)는 AUTO_INCREMENT, created_at/updated_at 는 DB 기본값 사용
   * - UNIQUE KEY (eval_year, hospital_name, emp_id) 충돌 시 에러 발생
   * → 같은 사람/연도/병원을 여러 번 올릴 계획이면
   * ON DUPLICATE KEY UPDATE 버전으로 바꾸는 게 좋습니다.
   */
  @Insert("""
      INSERT INTO personnel_evaluation.kpi_personal_2025 (
        eval_year,
        hospital_name,
        emp_id,
        total_score,
        bed_occ_2024_pct,
        bed_occ_2025_pct,
        bed_occ_yoy_pct,
        bed_occ_remarks,
        bed_occ_2024_score,
        bed_occ_yoy_score,
        bed_occ_subtotal_a,
        day_rev_2024,
        day_rev_2025,
        day_rev_yoy_pct,
        day_rev_remarks,
        day_rev_yoy_score,
        day_rev_target_score,
        day_rev_subtotal_b,
        sales_2024,
        sales_2025,
        sales_yoy_pct,
        sales_yoy_score,
        sales_target_score,
        sales_subtotal_c,
        finance_remarks,
        finance_total,
        guardian_sat_5,
        patient_sat_5,
        cs_total_10,
        incident_rate_2024,
        incident_rate_2025,
        incident_diff_pt,
        incident_curr_score,
        incident_diff_score,
        incident_total,
        incident_remarks,
        promo_goal_2025,
        promo_personal_count,
        promo_daily_avg,
        promo_score_1,
        link_goal,
        link_inpatient_cnt,
        link_funeral_cnt,
        link_total_cnt,
        link_personal_score_6,
        link_dept_rate_pct,
        link_dept_score_1,
        link_total_score_7,
        link_remarks,
        act5_goal_2025,
        act5_personal_count,
        act5_score_5,
        qi_topic,
        qi_role,
        qi_role_score_2,
        qi_final_org,
        qi_award,
        qi_dept_award_score_2,
        qi_dept_part_score_1,
        qi_total_score_3,
        qi_remarks,
        edu_goal,
        edu_personal_rate_pct,
        edu_personal_score_2,
        edu_dept_rate_pct,
        edu_dept_score_1,
        edu_total_score_3,
        edu_remarks,
        club_goal,
        club_personal_count,
        club_personal_score_3,
        club_dept_rate_pct,
        club_dept_score_1,
        club_dept_remarks,
        volunteer_score_4,
        book_goal,
        book_attend_count,
        book_attend_score_2,
        book_present_count,
        book_present_score_1,
        book_total_score_3,
        book_remarks
      ) VALUES (
        #{evalYear},
        #{hospitalName},
        #{empId},
        #{totalScore},
        #{bedOcc2024Pct},
        #{bedOcc2025Pct},
        #{bedOccYoyPct},
        #{bedOccRemarks},
        #{bedOcc2024Score},
        #{bedOccYoyScore},
        #{bedOccSubtotalA},
        #{dayRev2024},
        #{dayRev2025},
        #{dayRevYoyPct},
        #{dayRevRemarks},
        #{dayRevYoyScore},
        #{dayRevTargetScore},
        #{dayRevSubtotalB},
        #{sales2024},
        #{sales2025},
        #{salesYoyPct},
        #{salesYoyScore},
        #{salesTargetScore},
        #{salesSubtotalC},
        #{financeRemarks},
        #{financeTotal},
        #{guardianSat5},
        #{patientSat5},
        #{csTotal10},
        #{incidentRate2024},
        #{incidentRate2025},
        #{incidentDiffPt},
        #{incidentCurrScore},
        #{incidentDiffScore},
        #{incidentTotal},
        #{incidentRemarks},
        #{promoGoal2025},
        #{promoPersonalCount},
        #{promoDailyAvg},
        #{promoScore1},
        #{linkGoal},
        #{linkInpatientCnt},
        #{linkFuneralCnt},
        #{linkTotalCnt},
        #{linkPersonalScore6},
        #{linkDeptRatePct},
        #{linkDeptScore1},
        #{linkTotalScore7},
        #{linkRemarks},
        #{act5Goal2025},
        #{act5PersonalCount},
        #{act5Score5},
        #{qiTopic},
        #{qiRole},
        #{qiRoleScore2},
        #{qiFinalOrg},
        #{qiAward},
        #{qiDeptAwardScore2},
        #{qiDeptPartScore1},
        #{qiTotalScore3},
        #{qiRemarks},
        #{eduGoal},
        #{eduPersonalRatePct},
        #{eduPersonalScore2},
        #{eduDeptRatePct},
        #{eduDeptScore1},
        #{eduTotalScore3},
        #{eduRemarks},
        #{clubGoal},
        #{clubPersonalCount},
        #{clubPersonalScore3},
        #{clubDeptRatePct},
        #{clubDeptScore1},
        #{clubDeptRemarks},
        #{volunteerScore4},
        #{bookGoal},
        #{bookAttendCount},
        #{bookAttendScore2},
        #{bookPresentCount},
        #{bookPresentScore1},
        #{bookTotalScore3},
        #{bookRemarks}
      )
      """)
  int insert(KpiPersonal2025 row);

  // 개인 KPI 요약 (TOTAL)
  // 2025 개인 KPI 요약 (TOTAL) - 경혁팀/일반직원 통합
  @Select("""
      SELECT
        u.c_name AS orgName,
        COALESCE(s.sub_name, '-') AS deptName,
        u.id AS empId,
        u.name AS empName,
        u.position AS position,

        /* ───────── KPI 원본(문자열) ───────── */
        g.kcol03 AS total,
        g.kcol04 AS newInConn,
        g.kcol05 AS funeralConn,
        g.kcol06 AS bothConn,
        g.kcol07 AS kpiI,
        g.kcol08 AS scoreI,
        g.kcol09 AS noteI,
        g.kcol10 AS volCnt,
        g.kcol11 AS kpiII,
        g.kcol12 AS scoreII,
        g.kcol13 AS noteII,
        g.kcol14 AS eduRate,
        g.kcol15 AS kpiIII,
        g.kcol16 AS scoreIII,
        g.kcol17 AS noteIII,

        /* KPI 원본 총점(참고용) */
        CAST(NULLIF(g.kcol03,'') AS DECIMAL(10,2)) AS totalCalc,

        /* ───────── ✅ KPI(20/15/15) 원점수 그대로 (가산점 포함 유지) ───────── */
        ROUND(COALESCE(CAST(NULLIF(g.kcol07,'') AS DECIMAL(10,2)),0), 2) AS kpiICalc,
        ROUND(COALESCE(CAST(NULLIF(g.kcol11,'') AS DECIMAL(10,2)),0), 2) AS kpiIICalc,
        ROUND(COALESCE(CAST(NULLIF(g.kcol15,'') AS DECIMAL(10,2)),0), 2) AS kpiIIICalc,

        /* 100점 환산(표시용) — 테이블 값 그대로 */
        CAST(ROUND(COALESCE(CAST(NULLIF(g.kcol08,'') AS DECIMAL(10,2)),0),0) AS SIGNED) AS scoreICalc,
        CAST(ROUND(COALESCE(CAST(NULLIF(g.kcol12,'') AS DECIMAL(10,2)),0),0) AS SIGNED) AS scoreIICalc,
        CAST(ROUND(COALESCE(CAST(NULLIF(g.kcol16,'') AS DECIMAL(10,2)),0),0) AS SIGNED) AS scoreIIICalc,

        /* ───────── 다면평가 (E/F=상사, G=동료) raw 100 ───────── */
        evalAgg.bossEff100  AS bossToStaff100,
        CASE WHEN evalAgg.bossEff100  IS NULL OR evalAgg.bossEff100=0  THEN NULL
            ELSE ROUND(evalAgg.bossEff100  * 0.25, 2) END AS bossToStaff35,   -- (필드명 유지, 값은 25점)

        evalAgg.staffEff100 AS staffToStaff100,
        CASE WHEN evalAgg.staffEff100 IS NULL OR evalAgg.staffEff100=0 THEN NULL
            ELSE ROUND(evalAgg.staffEff100 * 0.25, 2) END AS staffToStaff35,  -- (필드명 유지, 값은 25점)

        evalAgg.evalSum50 AS evalSum70,  -- (필드명 유지, 값은 50점)

        /* ───────── ✅ 종합 총점(100) = KPI(20+15+15) + 다면(50) ───────── */
        ROUND(
            COALESCE(CAST(NULLIF(g.kcol07,'') AS DECIMAL(10,2)),0)
          + COALESCE(CAST(NULLIF(g.kcol11,'') AS DECIMAL(10,2)),0)
          + COALESCE(CAST(NULLIF(g.kcol15,'') AS DECIMAL(10,2)),0)
          + COALESCE(evalAgg.evalSum50, 0)
        , 2) AS totalCalc100

      FROM personnel_evaluation.users_2025 u
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code
      AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation.kpi_info_general_2025 g
        ON g.kcol02 = u.id

      LEFT JOIN (
        /* === 다면: E/F=상사(BOSS), G=동료(STAFF) === */
        WITH norm AS (
          SELECT
            es.target_id,
            CASE
              WHEN es.data_ev IN ('E','F') THEN 'BOSS'
              WHEN es.data_ev = 'G'        THEN 'STAFF'
              ELSE 'OTHER'
            END AS relBucket,
            (CASE
              WHEN es.data_type = 'AA' THEN (es.avg_score / 2.0)  -- 0~10 → 0~5
              ELSE es.avg_score
            END) * 20 AS score100                                -- 0~5 → 0~100
          FROM personnel_evaluation.evaluation_submissions es
          WHERE es.eval_year = #{year}
            AND es.target_id = CAST(#{empId} AS CHAR)
            AND es.del_yn = 'N'
            AND es.is_active = 1
            AND es.data_ev IN ('E','F','G')
            AND es.data_type IN ('AA','AB')
        ),
        bucket_avg AS (
          SELECT
            target_id,
            relBucket,
            ROUND(AVG(score100), 2) AS avg100
          FROM norm
          GROUP BY target_id, relBucket
        ),
        pivot AS (
          SELECT
            target_id,
            MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS boss100,
            MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staff100
          FROM bucket_avg
          GROUP BY target_id
        ),
        eff AS (
          SELECT
            target_id,
            CASE
              WHEN COALESCE(boss100,0)=0 AND COALESCE(staff100,0)>0 THEN staff100
              ELSE boss100
            END AS bossEff100,
            CASE
              WHEN COALESCE(staff100,0)=0 AND COALESCE(boss100,0)>0 THEN boss100
              ELSE staff100
            END AS staffEff100
          FROM pivot
        )
        SELECT
          target_id,
          bossEff100,
          staffEff100,
          CASE
            WHEN COALESCE(bossEff100,0)=0 AND COALESCE(staffEff100,0)=0 THEN NULL
            ELSE ROUND(COALESCE(bossEff100,0)*0.25 + COALESCE(staffEff100,0)*0.25, 2)
          END AS evalSum50
        FROM eff
      ) evalAgg
        ON evalAgg.target_id = u.id

      WHERE u.eval_year = #{year}
        AND u.id = CAST(#{empId} AS CHAR)
        AND COALESCE(u.del_yn,'N') = 'N'
      """)
  MyKpiRow selectMyKpi2025(@Param("year") int year, @Param("empId") int empId);

  // 조직 평균 레이더 (TOTAL)

  @Select("""
      SELECT
        /* KPI (100점 스케일) */
        ROUND(AVG(CAST(NULLIF(g.kcol08,'') AS DECIMAL(10,2))), 1) AS promoAvg100,
        ROUND(AVG(CAST(NULLIF(g.kcol12,'') AS DECIMAL(10,2))), 1) AS volAvg100,
        ROUND(AVG(CAST(NULLIF(g.kcol16,'') AS DECIMAL(10,2))), 1) AS eduAvg100,

        /* 다면평가 (100점 스케일) : multi100 = (E100 + G100) * 0.5 */
        ROUND(AVG(
          CASE
            WHEN ev.e100 IS NULL AND ev.g100 IS NULL THEN NULL
            ELSE (COALESCE(ev.e100,0) + COALESCE(ev.g100,0)) * 0.5
          END
        ), 1) AS multiAvg100

      FROM personnel_evaluation.users_2025 u
      LEFT JOIN personnel_evaluation.kpi_info_general_2025 g
        ON g.kcol02 = u.id

      LEFT JOIN (
        SELECT
          x.target_id,
          ROUND(AVG(CASE WHEN x.data_ev='E' THEN x.score100 END), 1) AS e100,
          ROUND(AVG(CASE WHEN x.data_ev='G' THEN x.score100 END), 1) AS g100
        FROM (
          SELECT
            es.target_id,
            es.data_ev,
            (CASE WHEN es.data_type='AA' THEN (es.avg_score/2.0) ELSE es.avg_score END) * 20 AS score100
          FROM personnel_evaluation.evaluation_submissions es
          WHERE es.eval_year = #{year}
            AND es.del_yn = 'N'
            AND es.is_active = 1
            AND es.data_ev IN ('E','G')
        ) x
        GROUP BY x.target_id
      ) ev
        ON ev.target_id = u.id

      WHERE u.eval_year = #{year}
        AND COALESCE(u.del_yn,'N') = 'N'
        AND (#{orgName} = '' OR u.c_name = #{orgName})
        AND (u.sub_code IS NULL OR u.sub_code NOT LIKE 'A0%')      -- 진료부 A* 제외
        AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'          -- 경혁팀 제외
      """)
  KpiCohortAvg selectOrgRadarAvg2025(@Param("year") int year,
      @Param("orgName") String orgName);

  /**
   * 2025년용: 특정 직원 1명에 대한 팀 기준 KPI 요약
   */
  /**
   * 2025년용: 특정 직원 1명에 대한 팀 기준 KPI 요약
   * - I~IV: kpi_personal_2025 그대로
   * - V(다면평가): evaluation_submissions(B/D/E/F/G, A0% 진료부) 기준 10/5/5 + 소계 20점
   * - total: I(40) + II(15) + III(15) + IV(10) + V(20) 재계산
   */
  @Select("""
      WITH
        /* ───────────────── 다면평가 원천 점수(raw) ───────────────── */
        raw_multi AS (
          SELECT
              s.eval_year,
              s.target_id    AS targetId,
              s.evaluator_id AS evaluatorId,
              s.data_ev      AS dataEv,      -- B/D/E/F/G
              s.data_type    AS dataType,    -- AA / AB
              s.radio_count,
              s.total_score
          FROM personnel_evaluation.evaluation_submissions s
          WHERE s.eval_year = #{year}
            AND s.is_active = 1
            AND s.del_yn    = 'N'
            AND s.data_type IN ('AA','AB')
            AND s.data_ev   IN ('B','D','E','F','G')
        ),

        /* 10점 척도로 통일 (AA: 10문항, AB: 20문항 → 5점척도*2) */
        scored_multi AS (
          SELECT
              r.eval_year,
              r.targetId,
              r.evaluatorId,
              r.dataEv,
              r.dataType,
              r.radio_count,
              CASE
                WHEN r.radio_count IS NULL OR r.radio_count = 0 THEN NULL
                WHEN r.dataType = 'AB' THEN (r.total_score * 1.0 / r.radio_count) * 2
                ELSE (r.total_score * 1.0 / r.radio_count)
              END AS score10          -- 0~10 점
          FROM raw_multi r
        ),

        /* 평가자 정보(부서코드) 붙이기: A0% → 진료부 */
        joined_multi AS (
          SELECT
              sm.*,
              eu.sub_code AS evaluatorSubCode
          FROM scored_multi sm
          JOIN personnel_evaluation.users_2025 eu
            ON eu.eval_year = sm.eval_year
           AND eu.id       = sm.evaluatorId
          WHERE eu.del_yn = 'N'
        ),

        /* targetId 한 명에 대한 다면평가 3버킷 집계 */
        agg AS (
          SELECT
              j.targetId,

              -- 경혁팀간(10점): D 평가 평균 (10점 척도 그대로)
              AVG(CASE WHEN j.dataEv = 'D' THEN j.score10 END) AS inTeam10,
              COUNT(CASE WHEN j.dataEv = 'D' THEN 1 END)       AS inTeamCnt,

              -- 부서원평가(5점): E/F/G 중에서 "진료부(A0%)"가 아닌 평가자
              AVG(CASE
                    WHEN j.dataEv IN ('E','F','G')
                     AND (j.evaluatorSubCode IS NULL
                          OR j.evaluatorSubCode NOT LIKE 'A0%')
                    THEN j.score10
                  END) AS bossToStaff10,
              COUNT(CASE
                      WHEN j.dataEv IN ('E','F','G')
                       AND (j.evaluatorSubCode IS NULL
                            OR j.evaluatorSubCode NOT LIKE 'A0%')
                      THEN 1
                    END) AS bossToStaffCnt,

              -- 진료부평가(5점): 평가자 부서코드가 A0% 인 경우
              AVG(CASE
                    WHEN j.evaluatorSubCode LIKE 'A0%'
                    THEN j.score10
                  END) AS fromClinic10,
              COUNT(CASE
                      WHEN j.evaluatorSubCode LIKE 'A0%'
                      THEN 1
                    END) AS fromClinicCnt
          FROM joined_multi j
          GROUP BY j.targetId
        )

      SELECT
          u.c_name                      AS orgName,
          TRIM(t.team_name)             AS teamName,
          COALESCE(s.sub_name, '-')     AS deptName,
          u.id                          AS empNo,
          u.position                    AS position,
          u.name                        AS name,

          -- I. 재무성과 (40)
          p.finance_total               AS iSubtotal,

          -- II. 고객서비스 (15)
          (p.cs_total_10 + p.incident_total) AS iiSubtotal,

          -- III. 프로세스혁신 (15)
          (p.link_total_score_7 + p.act5_score_5 + p.qi_total_score_3) AS iiiSubtotal,

          -- IV. 학습성장 (10)
          (p.edu_total_score_3
           + p.volunteer_score_4
           + p.book_total_score_3) AS ivSubtotal,

          /* ===== V. 다면평가 (10 / 5 / 5) 상세 ===== */
          -- 경혁팀간(10점): 10점 척도 그대로
          ROUND(COALESCE(a.inTeam10, 0), 2) AS vExperience,

          -- 부서원평가(5점): 10점 → 5점(/2), 점수가 없거나 0이면 4.3으로 대체
          ROUND(
            CASE
              WHEN a.bossToStaff10 IS NULL OR a.bossToStaff10 = 0
                THEN 4.3
              ELSE a.bossToStaff10 / 2
            END
          , 2) AS vBossToStaff,

          -- 진료부평가(5점): 10점 → 5점(/2), 점수가 없거나 0이면 4.3으로 대체
          ROUND(
            CASE
              WHEN a.fromClinic10 IS NULL OR a.fromClinic10 = 0
                THEN 4.3
              ELSE a.fromClinic10 / 2
            END
          , 2) AS vStaffToStaff,

          -- V 소계(20점) = 경혁팀간(10) + 부서원(5) + 진료부(5)
          ROUND(
            COALESCE(a.inTeam10, 0)
            + CASE
                WHEN a.bossToStaff10 IS NULL OR a.bossToStaff10 = 0
                  THEN 4.3
                ELSE a.bossToStaff10 / 2
              END
            + CASE
                WHEN a.fromClinic10 IS NULL OR a.fromClinic10 = 0
                  THEN 4.3
                ELSE a.fromClinic10 / 2
              END
          , 2) AS vSubtotal,

          /* ===== 전체 총점 (100점) =====
             I(40) + II(15) + III(15) + IV(10) + V(20)
          */
          ROUND(
            COALESCE(p.finance_total,       0)   -- I
            + COALESCE(p.cs_total_10,       0)   -- II-1
            + COALESCE(p.incident_total,    0)   -- II-2
            + COALESCE(p.link_total_score_7,0)   -- III-1
            + COALESCE(p.act5_score_5,      0)   -- III-2
            + COALESCE(p.qi_total_score_3,  0)   -- III-3
            + COALESCE(p.edu_total_score_3, 0)   -- IV-1
            + COALESCE(p.volunteer_score_4, 0)   -- IV-2
            + COALESCE(p.book_total_score_3,0)   -- IV-3
            + (
                COALESCE(a.inTeam10, 0)          -- V-1 (10)
                + CASE                             -- V-2 (5)
                    WHEN a.bossToStaff10 IS NULL OR a.bossToStaff10 = 0
                      THEN 4.3
                    ELSE a.bossToStaff10 / 2
                  END
                + CASE                             -- V-3 (5)
                    WHEN a.fromClinic10 IS NULL OR a.fromClinic10 = 0
                      THEN 4.3
                    ELSE a.fromClinic10 / 2
                  END
              )
          , 2) AS total

      FROM personnel_evaluation.kpi_personal_2025 p
      JOIN personnel_evaluation.users_2025 u
        ON u.eval_year = p.eval_year
       AND u.id       = SUBSTRING_INDEX(p.emp_id, '.', 1)
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code
       AND s.eval_year = u.eval_year
      JOIN personnel_evaluation.team t
        ON t.team_code = u.team_code
       AND t.eval_year = u.eval_year
      LEFT JOIN agg a
        ON a.targetId = u.id
      WHERE p.eval_year        = #{year}
        AND u.del_yn          = 'N'
        AND u.c_name          = #{orgName}
        AND TRIM(t.team_name) = #{teamName}
        AND u.id              = #{empNo}
      """)
  KpiSummaryRow selectMyKpiForTeam2025(
      @Param("orgName") String orgName,
      @Param("teamName") String teamName,
      @Param("year") int year,
      @Param("empNo") String empNo);

  @Select("""
      SELECT
          u.c_name                  AS orgName,
          COALESCE(s.sub_name, '-') AS deptName,
          u.id                      AS empNo,
          u.position                AS position,
          u.name                    AS name,

          p.total_score             AS totalScore,

          -- I. 재무성과(40)
          p.bed_occ_subtotal_a      AS bedOccSubtotalA,
          p.day_rev_subtotal_b      AS dayRevSubtotalB,
          p.sales_subtotal_c        AS salesSubtotalC,
          p.finance_total           AS financeTotal,

          -- II. 고객서비스(15)
          p.patient_sat_5           AS patientSat5,
          p.guardian_sat_5          AS guardianSat5,
          p.incident_total          AS incidentTotal,
          p.promo_score_1           AS mealAssist1,      -- 식사수발 항목 있으면 여기 매핑

          -- III. 프로세스혁신(15)
          p.link_total_score_7      AS linkTotalScore7,
          p.act5_score_5            AS act5Score5,
          p.qi_total_score_3        AS qiTotalScore3,

          -- IV. 학습성장(10)
          p.edu_total_score_3       AS eduTotalScore3,
          p.volunteer_score_4       AS volunteerScore4,
          p.book_total_score_3      AS bookTotalScore3,

          -- V. 다면평가(20) – 아직 테이블에 없으면 NULL 유지
          NULL AS multiGh10,
          NULL AS multiDept5,
          NULL AS multiClinic5
      FROM personnel_evaluation.kpi_personal_2025 p
      JOIN personnel_evaluation.users_2025 u
        ON u.eval_year = p.eval_year
       AND u.id       = SUBSTRING_INDEX(p.emp_id, '.', 1)
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code
       AND s.eval_year = u.eval_year
      WHERE p.eval_year = #{year}
        AND SUBSTRING_INDEX(p.emp_id, '.', 1) = #{empId}
      """)
  KyTotal2025Row selectKyTotalDetail2025(
      @Param("year") int year,
      @Param("empId") String empId);

  @Select("""
      WITH
        /* ───────────────── 다면평가 원천 점수(raw) ───────────────── */
        raw_multi AS (
          SELECT
              s.eval_year,
              s.target_id    AS targetId,
              s.evaluator_id AS evaluatorId,
              s.data_ev      AS dataEv,      -- B/D/E/F/G
              s.data_type    AS dataType,    -- AA / AB
              s.radio_count,
              s.total_score
          FROM personnel_evaluation.evaluation_submissions s
          WHERE s.eval_year = #{year}
            AND s.is_active = 1
            AND s.del_yn    = 'N'
            AND s.data_type IN ('AA','AB')
            AND s.data_ev   IN ('B','D','E','F','G')
            AND s.target_id = #{targetId}
        ),

        /* 10점 척도로 통일 (AA: 10문항, AB: 20문항 → 5점척도*2) */
        scored_multi AS (
          SELECT
              r.eval_year,
              r.targetId,
              r.evaluatorId,
              r.dataEv,
              r.dataType,
              r.radio_count,
              CASE
                WHEN r.radio_count IS NULL OR r.radio_count = 0 THEN NULL
                WHEN r.dataType = 'AB' THEN (r.total_score * 1.0 / r.radio_count) * 2
                ELSE (r.total_score * 1.0 / r.radio_count)
              END AS score10          -- 0~10 점
          FROM raw_multi r
        ),

        /* 평가자 정보(부서코드) 붙이기: A0% → 진료부 */
        joined_multi AS (
          SELECT
              sm.*,
              eu.sub_code AS evaluatorSubCode
          FROM scored_multi sm
          JOIN personnel_evaluation.users_2025 eu
            ON eu.eval_year = sm.eval_year
           AND eu.id       = sm.evaluatorId
          WHERE eu.del_yn = 'N'
        ),

        /* targetId 한 명에 대한 버킷별 집계 */
        agg AS (
          SELECT
              j.targetId,

              -- 경혁팀간(10점): D 평가 평균 (10점 척도 그대로)
              AVG(CASE WHEN j.dataEv = 'D' THEN j.score10 END) AS inTeam10,
              COUNT(CASE WHEN j.dataEv = 'D' THEN 1 END)       AS inTeamCnt,

              -- 부서원평가(5점): E/F/G 중에서 "진료부(A0%)"가 아닌 평가자
              AVG(CASE
                    WHEN j.dataEv IN ('E','F','G')
                     AND (j.evaluatorSubCode IS NULL
                          OR j.evaluatorSubCode NOT LIKE 'A0%')
                    THEN j.score10
                  END) AS bossToStaff10,
              COUNT(CASE
                      WHEN j.dataEv IN ('E','F','G')
                       AND (j.evaluatorSubCode IS NULL
                            OR j.evaluatorSubCode NOT LIKE 'A0%')
                      THEN 1
                    END) AS bossToStaffCnt,

              -- 진료부평가(5점): 평가자 부서코드가 A0% 인 경우
              AVG(CASE
                    WHEN j.evaluatorSubCode LIKE 'A0%'
                    THEN j.score10
                  END) AS fromClinic10,
              COUNT(CASE
                      WHEN j.evaluatorSubCode LIKE 'A0%'
                      THEN 1
                    END) AS fromClinicCnt
          FROM joined_multi j
          GROUP BY j.targetId
        )

      SELECT
          u.c_name                      AS orgName,
          TRIM(t.team_name)             AS teamName,
          COALESCE(s.sub_name, '-')     AS deptName,
          u.id                          AS empNo,
          u.position                    AS position,
          u.name                        AS name,

          /* 원점수(10점 척도) & 표본수 */
          ROUND(COALESCE(a.inTeam10, 0), 2)      AS inTeam10,
          a.inTeamCnt                             AS inTeamCnt,

          ROUND(COALESCE(a.bossToStaff10, 0), 2)  AS bossToStaff10Raw,
          a.bossToStaffCnt                        AS bossToStaffCnt,

          ROUND(COALESCE(a.fromClinic10, 0), 2)   AS fromClinic10Raw,
          a.fromClinicCnt                         AS fromClinicCnt,

          /* 5점 척도로 환산 + 0 또는 NULL일 때 4.3으로 보정 */
          ROUND(
            CASE
              WHEN a.bossToStaff10 IS NULL OR a.bossToStaff10 = 0
                THEN 4.3
              ELSE a.bossToStaff10 / 2
            END
          , 2) AS bossToStaff5,

          ROUND(
            CASE
              WHEN a.fromClinic10 IS NULL OR a.fromClinic10 = 0
                THEN 4.3
              ELSE a.fromClinic10 / 2
            END
          , 2) AS fromClinic5,

          /* V 소계(20점): 경혁팀간(10) + 부서원(5) + 진료부(5) */
          ROUND(
            COALESCE(a.inTeam10, 0)
            + CASE
                WHEN a.bossToStaff10 IS NULL OR a.bossToStaff10 = 0
                  THEN 4.3
                ELSE a.bossToStaff10 / 2
              END
            + CASE
                WHEN a.fromClinic10 IS NULL OR a.fromClinic10 = 0
                  THEN 4.3
                ELSE a.fromClinic10 / 2
              END
          , 2) AS multiSubtotal20

      FROM agg a
      JOIN personnel_evaluation.users_2025 u
        ON u.eval_year = #{year}
       AND u.id       = a.targetId
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code
       AND s.eval_year = u.eval_year
      JOIN personnel_evaluation.team t
        ON t.team_code = u.team_code
       AND t.eval_year = u.eval_year
      WHERE u.del_yn = 'N'
        AND (#{orgName} = '' OR u.c_name = #{orgName})
        AND (#{teamName} = '' OR TRIM(t.team_name) = TRIM(#{teamName}))
      """)
  List<MyKpiMultiRow> selectMyKpiMultiForTeam2025(
      @Param("orgName") String orgName,
      @Param("teamName") String teamName,
      @Param("year") int year,
      @Param("targetId") int targetId);

  /**
   * 2025년용: 팀 전체 KPI 요약 (경혁팀 TOTAL 총괄표)
   * - I~IV는 kpi_personal_2025에서 직접 사용
   * - V(다면평가) 10/5/5는 evaluation_submissions에서 집계
   */
  @Select("""
      WITH
        /* ───────────────── 다면평가 원천 점수(raw) ───────────────── */
        raw_multi AS (
          SELECT
              s.eval_year,
              s.target_id    AS targetId,
              s.evaluator_id AS evaluatorId,
              s.data_ev      AS dataEv,      -- B/D/E/F/G
              s.data_type    AS dataType,    -- AA / AB
              s.radio_count,
              s.total_score
          FROM personnel_evaluation.evaluation_submissions s
          WHERE s.eval_year = #{year}
            AND s.is_active = 1
            AND s.del_yn    = 'N'
            AND s.data_type IN ('AA','AB')
            AND s.data_ev   IN ('B','D','E','F','G')
        ),

        /* 10점 척도로 통일 (AA: 10문항, AB: 20문항 → 5점척도*2) */
        scored_multi AS (
          SELECT
              r.eval_year,
              r.targetId,
              r.evaluatorId,
              r.dataEv,
              r.dataType,
              CASE
                WHEN r.radio_count IS NULL OR r.radio_count = 0 THEN NULL
                WHEN r.dataType = 'AB' THEN (r.total_score * 1.0 / r.radio_count) * 2
                ELSE (r.total_score * 1.0 / r.radio_count)
              END AS score10          -- 0~10 점
          FROM raw_multi r
        ),

        /* 평가자 정보(부서코드) 붙이기: A0% → 진료부 */
        joined_multi AS (
          SELECT
              sm.*,
              eu.sub_code AS evaluatorSubCode
          FROM scored_multi sm
          JOIN personnel_evaluation.users_2025 eu
            ON eu.eval_year = sm.eval_year
           AND eu.id       = sm.evaluatorId
          WHERE eu.del_yn = 'N'
        ),

        /* targetId 별로 다면평가 3버킷으로 집계 */
        es_pivot AS (
          SELECT
              j.targetId,

              -- 경혁팀간(10점): D 평가 평균 (10점 척도 그대로)
              AVG(CASE WHEN j.dataEv = 'D' THEN j.score10 END) AS inTeam10,

              -- 부서원평가(5점): E/F/G 중에서 "진료부(A0%)"가 아닌 평가자
              AVG(CASE
                    WHEN j.dataEv IN ('E','F','G')
                     AND (j.evaluatorSubCode IS NULL
                          OR j.evaluatorSubCode NOT LIKE 'A0%')
                    THEN j.score10
                  END) AS bossToStaff10,   -- 나중에 /2 해서 5점 척도

              -- 진료부평가(5점): 평가자 부서코드가 A0% 인 경우
              AVG(CASE
                    WHEN j.evaluatorSubCode LIKE 'A0%'
                    THEN j.score10
                  END) AS fromClinic10     -- 나중에 /2 해서 5점 척도
          FROM joined_multi j
          GROUP BY j.targetId
        )

      SELECT
          u.c_name                      AS orgName,
          TRIM(t.team_name)             AS teamName,
          COALESCE(s.sub_name, '-')     AS deptName,
          u.id                          AS empNo,
          u.position                    AS position,
          u.name                        AS name,

          /* ===== I. 재무성과 (40) ===== */
          p.finance_total               AS iSubtotal,
          p.bed_occ_subtotal_a          AS iBedAvailTxt,    -- 병상가동률(15)
          p.day_rev_subtotal_b          AS iDayChargeTxt,   -- 일당진료비(15)
          p.sales_subtotal_c            AS iSalesTxt,       -- 매출/원가(10)

          /* ===== II. 고객서비스 (15) ===== */
          (p.cs_total_10 + p.incident_total) AS iiSubtotal,
          p.patient_sat_5               AS iiPatientSatTxt,     -- 환자만족도(5)
          p.guardian_sat_5              AS iiProtectorSatTxt,   -- 보호자만족도(5)
          p.incident_total              AS iiAccidentTxt,       -- 환자안전사고(4)
          p.promo_score_1               AS iiMealTxt,           -- 식사수발(1)

          /* ===== III. 프로세스혁신 (15) ===== */
          (p.link_total_score_7 + p.act5_score_5 + p.qi_total_score_3) AS iiiSubtotal,
          p.link_total_score_7          AS iiiLinkTxt,       -- 입원·장례 연계(7)
          p.act5_score_5                AS iiiPromotionTxt,  -- 홍보활동(5)
          p.qi_total_score_3            AS iiiQITxt,         -- QI 질향상(3)

          /* ===== IV. 학습성장 (10) ===== */
          (p.edu_total_score_3
           + p.volunteer_score_4
           + p.book_total_score_3)      AS ivSubtotal,
          p.edu_total_score_3           AS ivEduTxt,         -- 교육이수(3)
          p.volunteer_score_4           AS ivVolunteerTxt,   -- 자원봉사(4)
          p.book_total_score_3          AS ivDiscussTxt,     -- 독서토론(3)

          /* ===== V. 다면평가 (10 / 5 / 5) ===== */
          -- 경혁팀간(10점): 10점 척도 그대로
          ROUND(COALESCE(pvt.inTeam10, 0), 2) AS vExperience,

          -- 부서원평가(5점): 10점 → 5점(/2), 점수가 없거나 0이면 4.3으로 대체
          ROUND(
            CASE
              WHEN pvt.bossToStaff10 IS NULL OR pvt.bossToStaff10 = 0
                THEN 4.3
              ELSE pvt.bossToStaff10 / 2
            END,
            2
          ) AS vBossToStaff,

          -- 진료부평가(5점): 10점 → 5점(/2), 점수가 없거나 0이면 4.3으로 대체
          ROUND(
            CASE
              WHEN pvt.fromClinic10 IS NULL OR pvt.fromClinic10 = 0
                THEN 4.3
              ELSE pvt.fromClinic10 / 2
            END,
            2
          ) AS vStaffToStaff,

          -- V 소계(20점) = 경혁팀간(10) + 부서원(5) + 진료부(5)
          ROUND(
            COALESCE(pvt.inTeam10, 0)
            + CASE
                WHEN pvt.bossToStaff10 IS NULL OR pvt.bossToStaff10 = 0
                  THEN 4.3
                ELSE pvt.bossToStaff10 / 2
              END
            + CASE
                WHEN pvt.fromClinic10 IS NULL OR pvt.fromClinic10 = 0
                  THEN 4.3
                ELSE pvt.fromClinic10 / 2
              END
          , 2) AS vSubtotal,

          /* ===== 전체 총점 (100점 만점) =====
             I(40) + II(15) + III(15) + IV(10) + V(20)
          */
          ROUND(
            COALESCE(p.finance_total, 0)
            + COALESCE(p.cs_total_10, 0)
            + COALESCE(p.incident_total, 0)
            + COALESCE(p.link_total_score_7, 0)
            + COALESCE(p.act5_score_5, 0)
            + COALESCE(p.qi_total_score_3, 0)
            + COALESCE(p.edu_total_score_3, 0)
            + COALESCE(p.volunteer_score_4, 0)
            + COALESCE(p.book_total_score_3, 0)
            + (
                COALESCE(pvt.inTeam10, 0)
                + CASE
                    WHEN pvt.bossToStaff10 IS NULL OR pvt.bossToStaff10 = 0
                      THEN 4.3
                    ELSE pvt.bossToStaff10 / 2
                  END
                + CASE
                    WHEN pvt.fromClinic10 IS NULL OR pvt.fromClinic10 = 0
                      THEN 4.3
                    ELSE pvt.fromClinic10 / 2
                  END
              )
          , 2) AS total

      FROM personnel_evaluation.kpi_personal_2025 p
      JOIN personnel_evaluation.users_2025 u
        ON u.eval_year = p.eval_year
       AND u.id       = SUBSTRING_INDEX(p.emp_id, '.', 1)
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code
       AND s.eval_year = u.eval_year
      JOIN personnel_evaluation.team t
        ON t.team_code = u.team_code
       AND t.eval_year = u.eval_year
      LEFT JOIN es_pivot pvt
        ON pvt.targetId = u.id
      WHERE p.eval_year        = #{year}
        AND u.del_yn          = 'N'
        AND u.c_name          = #{orgName}
        AND TRIM(t.team_name) = #{teamName}
      ORDER BY
        s.sub_name ASC,
        u.position ASC,
        u.name ASC
      """)
  List<KpiSummaryRow> selectKpiForTeam2025(
      @Param("orgName") String orgName,
      @Param("teamName") String teamName,
      @Param("year") int year);

  @Insert("""
        INSERT INTO personnel_evaluation.kpi_eval_result (
        eval_year,
        hospital_name,
        emp_id,
        total_score,
        multi_inteam10,
        multi_boss5,
        multi_clinic5,
        updated_at,
        updated_by
      )
      VALUES (
        #{year},
        #{hospitalName},
        #{empId},
        #{totalScore},
        #{multiInteam10},
        #{multiBoss5},
        #{multiClinic5},
        NOW(),
        #{updatedBy}
      )
      ON DUPLICATE KEY UPDATE
        total_score    = VALUES(total_score),
        multi_inteam10 = VALUES(multi_inteam10),
        multi_boss5    = VALUES(multi_boss5),
        multi_clinic5  = VALUES(multi_clinic5),
        updated_at     = NOW(),
        updated_by     = VALUES(updated_by)
      """)
  int upsertKpiScore(
      @Param("year") int year,
      @Param("hospitalName") String hospitalName,
      @Param("empId") String empId,
      @Param("totalScore") BigDecimal totalScore,
      @Param("multiInteam10") BigDecimal multiInteam10,
      @Param("multiBoss5") BigDecimal multiBoss5,
      @Param("multiClinic5") BigDecimal multiClinic5,
      @Param("updatedBy") String updatedBy);

  /**
   * 직원용 KPI 총괄(병원 단위) – 2025년 이상
   */
  @Select("""
            WITH es AS (
              SELECT
                  s.target_id AS targetId,
                  CASE
                    WHEN s.data_ev IN ('E','F') THEN 'BOSS'
                    WHEN s.data_ev = 'G'        THEN 'STAFF'
                    ELSE 'OTHER'
                  END AS relBucket,
                  ROUND(
                    AVG(
                      CASE
                        WHEN s.data_type = 'AA' THEN (s.avg_score / 2.0)
                        ELSE s.avg_score
                      END * 20
                    ),
                    1
                  ) AS avg100
              FROM personnel_evaluation.evaluation_submissions s
              WHERE s.eval_year = #{year}
                AND s.is_active = 1
                AND s.del_yn    = 'N'
                AND s.data_ev IN ('E','F','G')
                AND s.data_type IN ('AA','AB')
              GROUP BY s.target_id, relBucket
            ),
            es_pivot AS (
              SELECT
                  targetId,
                  MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS bossToStaff100,
                  MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staffToStaff100
              FROM es
              GROUP BY targetId
            ),
            es_eff AS (
              SELECT
                  p.targetId,
                  CASE
                    WHEN COALESCE(p.bossToStaff100, 0) = 0 AND COALESCE(p.staffToStaff100, 0) > 0
                      THEN p.staffToStaff100
                    ELSE p.bossToStaff100
                  END AS bossEff100,
                  CASE
                    WHEN COALESCE(p.staffToStaff100, 0) = 0 AND COALESCE(p.bossToStaff100, 0) > 0
                      THEN p.bossToStaff100
                    ELSE p.staffToStaff100
                  END AS staffEff100
              FROM es_pivot p
            ),
            kpi_parsed AS (
              SELECT
                  u.id AS empId,

                  /* 원본 KPI (20/15/15) */
                  CAST(NULLIF(TRIM(k.kcol07), '') AS DECIMAL(10,2)) AS kpiI20,
                  CAST(NULLIF(TRIM(k.kcol11), '') AS DECIMAL(10,2)) AS kpiII15,
                  CAST(NULLIF(TRIM(k.kcol15), '') AS DECIMAL(10,2)) AS kpiIII15

              FROM personnel_evaluation.users_2025 u
              JOIN personnel_evaluation.kpi_info_general_2025 k
                ON k.kcol02 = u.id
              WHERE u.eval_year = #{year}
            )

      SELECT
        u.c_name                                   AS orgName,
        COALESCE(sm.sub_name, '-')                 AS deptName,
        u.id                                       AS empId,
        u.name                                     AS empName,
        u.position                                 AS position,

        -- KPI (20/15/15) 그대로 사용
        ROUND(COALESCE(p.kpiI20,   0), 2) AS kpiI20Calc,
        ROUND(COALESCE(p.kpiII15,  0), 2) AS kpiII15Calc,
        ROUND(COALESCE(p.kpiIII15, 0), 2) AS kpiIII15Calc,

        -- 100점 환산(표시/레이더용 필요 시)
        ROUND(COALESCE(p.kpiI20,   0) / 20.0 * 100.0, 2) AS scoreI100,
        ROUND(COALESCE(p.kpiII15,  0) / 15.0 * 100.0, 2) AS scoreII100,
        ROUND(COALESCE(p.kpiIII15, 0) / 15.0 * 100.0, 2) AS scoreIII100,

        -- 원본 KPI 표시값
        CAST(k.kcol07 AS DECIMAL(10,2)) AS kpiI,
        CAST(k.kcol08 AS DECIMAL(10,2)) AS scoreI,
        k.kcol09                        AS noteI,

        CAST(k.kcol11 AS DECIMAL(10,2)) AS kpiII,
        CAST(k.kcol12 AS DECIMAL(10,2)) AS scoreII,
        k.kcol13                        AS noteII,

        CAST(k.kcol15 AS DECIMAL(10,2)) AS kpiIII,
        CAST(k.kcol16 AS DECIMAL(10,2)) AS scoreIII,
        k.kcol17                        AS noteIII,

        CAST(k.kcol04 AS DECIMAL(10,2)) AS newInConn,
        CAST(k.kcol05 AS DECIMAL(10,2)) AS funeralConn,
        CAST(k.kcol06 AS DECIMAL(10,2)) AS bothConn,

        CAST(k.kcol10 AS DECIMAL(10,2)) AS volCnt,
        k.kcol14 AS eduRate,

        -- 다면 RAW
        e.bossEff100   AS bossToStaff100,
        e.staffEff100  AS staffToStaff100,

        -- ✅ 25점 환산(호환을 위해 alias는 bossToStaff35 유지)
        -- ✅ 항상 25점 환산 (DB 저장값 무시)
        CASE WHEN e.bossEff100 IS NOT NULL AND e.bossEff100 <> 0
            THEN ROUND(e.bossEff100 * 0.25, 2)
            ELSE NULL
        END AS bossToStaff35,

        CASE WHEN e.staffEff100 IS NOT NULL AND e.staffEff100 <> 0
            THEN ROUND(e.staffEff100 * 0.25, 2)
            ELSE NULL
        END AS staffToStaff35,

        -- ✅ 50점 합산(호환을 위해 alias는 evalSum70 유지)
        CASE
          WHEN (COALESCE(e.bossEff100,0) = 0 AND COALESCE(e.staffEff100,0) = 0)
            THEN 0.00
          ELSE ROUND(COALESCE(e.bossEff100,0) * 0.25 + COALESCE(e.staffEff100,0) * 0.25, 2)
        END AS evalSum70,

        -- ✅ 총점(20/15/15/50): 새 기준으로 계산
        ROUND(
          COALESCE(p.kpiI20,0)
        + COALESCE(p.kpiII15,0)
        + COALESCE(p.kpiIII15,0)
        + (CASE
            WHEN (COALESCE(e.bossEff100,0) = 0 AND COALESCE(e.staffEff100,0) = 0)
              THEN 0.00
            ELSE ROUND(COALESCE(e.bossEff100,0) * 0.25 + COALESCE(e.staffEff100,0) * 0.25, 2)
          END)
        , 2) AS totalCalc,

        -- ✅ 등급/백분위: staff 테이블
        kers.percentile AS p100,
        kers.eval_grade AS evalGrade

      FROM personnel_evaluation.users_2025 u
      JOIN personnel_evaluation.kpi_info_general_2025 k
        ON k.kcol02 = u.id
      LEFT JOIN kpi_parsed p
        ON p.empId = u.id

      -- ✅ 여기만 staff로 변경
      LEFT JOIN personnel_evaluation.kpi_eval_result_staff kers
        ON kers.eval_year = u.eval_year
       AND kers.emp_id    = u.id

      LEFT JOIN personnel_evaluation.sub_management sm
        ON sm.sub_code  = u.sub_code
       AND sm.eval_year = u.eval_year
      LEFT JOIN es_eff e
        ON e.targetId = u.id
      WHERE u.eval_year = #{year}
        AND (#{orgName} = '' OR u.c_name = #{orgName})
        AND u.del_yn = 'N'
        AND (u.sub_code IS NULL OR u.sub_code NOT LIKE 'A0%')
        AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'
      ORDER BY
        CASE
          WHEN ASCII(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 48 AND 57 THEN 0
          WHEN ORD(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 0xAC00 AND 0xD7A3 THEN 1
          WHEN ASCII(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 65 AND 90 THEN 2
          WHEN ASCII(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 97 AND 122 THEN 2
          ELSE 3
        END,
        CASE
          WHEN IFNULL(sm.sub_name, '') REGEXP '^[0-9]+'
            THEN CAST(sm.sub_name AS UNSIGNED)
          ELSE NULL
        END,
        sm.sub_name COLLATE utf8mb4_0900_ai_ci,
        LPAD(u.id, 12, '0')
      """)
  List<CombinedRow> selectCombinedByOrgYear2025(@Param("orgName") String orgName,
      @Param("year") int year);

  /**
   * 2025년 진료부 전용 종합표 조회
   * - 경혁팀 제외
   * - 진료부(부서명에 '진료부' 포함)만
   * - op 파라미터가 비어있지 않으면 직책(u.position) 추가 필터
   */
  @Select("""
      WITH es AS (
        SELECT
            s.target_id AS targetId,
            s.data_ev,
            ROUND(
              AVG(
                CASE
                  WHEN s.data_type = 'AA' THEN (s.avg_score / 2.0)
                  ELSE s.avg_score
                END * 20
              ),
              1
            ) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn    = 'N'
          AND (
                (s.data_ev = 'C' AND s.data_type = 'AA')  -- 경혁팀 → 진료부
             OR (s.data_ev = 'E' AND s.data_type = 'AB')  -- 진료팀장 → 진료부
              )
        GROUP BY s.target_id, s.data_ev
      ),
      es_pivot AS (
        SELECT
            targetId,
            MAX(CASE WHEN data_ev = 'C' THEN avg100 END) AS kyToClinic100,
            MAX(CASE WHEN data_ev = 'E' THEN avg100 END) AS chiefToClinic100
        FROM es
        GROUP BY targetId
      ),
      es_score AS (
        SELECT
            p.targetId,
            p.kyToClinic100,
            p.chiefToClinic100,

            /* ✅ 각 25점 환산 */
            CASE
              WHEN p.kyToClinic100 IS NOT NULL THEN ROUND(p.kyToClinic100 * 0.25, 2)
              ELSE NULL
            END AS kyToClinic25,
            CASE
              WHEN p.chiefToClinic100 IS NOT NULL THEN ROUND(p.chiefToClinic100 * 0.25, 2)
              ELSE NULL
            END AS chiefToClinic25,

            /* ✅ 다면 50점 합계 */
            ROUND(
              COALESCE(p.kyToClinic100, 0)    * 0.25 +
              COALESCE(p.chiefToClinic100, 0) * 0.25,
              2
            ) AS evalSum50
        FROM es_pivot p
      )

      SELECT
          u.c_name                   AS orgName,
          COALESCE(sm.sub_name, '-') AS deptName,
          u.id                       AS empId,
          u.name                     AS empName,
          u.position                 AS position,

          /* I/II/III 원본 */
          CAST(k.kcol04 AS DECIMAL(10,2)) AS newInConn,
          CAST(k.kcol05 AS DECIMAL(10,2)) AS funeralConn,
          CAST(k.kcol06 AS DECIMAL(10,2)) AS bothConn,

          /* ✅ KPI 원점수(20/15/15) */
          CAST(k.kcol07 AS DECIMAL(10,2)) AS kpiI,     -- 0~20
          CAST(k.kcol11 AS DECIMAL(10,2)) AS kpiII,    -- 0~15
          CAST(k.kcol15 AS DECIMAL(10,2)) AS kpiIII,   -- 0~15

          /* (표시용) 100점 환산 점수는 기존 컬럼 유지 */
          CAST(k.kcol08 AS DECIMAL(10,2)) AS scoreI,
          CAST(k.kcol12 AS DECIMAL(10,2)) AS scoreII,
          CAST(k.kcol16 AS DECIMAL(10,2)) AS scoreIII,

          CAST(k.kcol10 AS DECIMAL(10,2)) AS volCnt,
          k.kcol14 AS eduRate,

          /* 다면 RAW(100) */
          s.kyToClinic100    AS bossEff100,
          s.chiefToClinic100 AS staffEff100,

          /* ✅ 호환을 위해 alias는 그대로(하지만 값은 25점 기준) */
          s.kyToClinic25     AS bossToStaff35,
          s.chiefToClinic25  AS staffToStaff35,
          s.evalSum50        AS evalSum70,

          /* ✅ 총점(20/15/15/50) */
          ROUND(
            COALESCE(CAST(k.kcol07 AS DECIMAL(10,2)),0)
          + COALESCE(CAST(k.kcol11 AS DECIMAL(10,2)),0)
          + COALESCE(CAST(k.kcol15 AS DECIMAL(10,2)),0)
          + COALESCE(s.evalSum50, 0)
          , 2
          ) AS totalCalc,

          ker.percentile AS p100,
          ker.eval_grade AS evalGrade

      FROM personnel_evaluation.users_2025 u
      JOIN personnel_evaluation.kpi_info_general_2025 k
        ON k.kcol02 = u.id
      LEFT JOIN personnel_evaluation.kpi_eval_result ker
        ON ker.eval_year = u.eval_year
       AND ker.emp_id    = u.id
      LEFT JOIN personnel_evaluation.sub_management sm
        ON sm.sub_code  = u.sub_code
       AND sm.eval_year = u.eval_year
      LEFT JOIN es_score s
        ON s.targetId = u.id
      WHERE u.eval_year = #{year}
        AND (#{orgName} = '' OR u.c_name = #{orgName})
        AND u.del_yn = 'N'
        AND u.sub_code LIKE 'A0%'
      ORDER BY
        sm.sub_name COLLATE utf8mb4_0900_ai_ci,
        LPAD(u.id, 12, '0')
      """)
  List<CombinedRow> selectCombinedByOrgYear2025Medical(@Param("orgName") String orgName, @Param("year") int year);

  /**
   * 직원 2025 – KPI + 다면평가 점수를 kpi_eval_result에 일괄 Upsert
   * - orgName이 ''이면 전체 병원 대상
   * - orgName이 특정 병원이면 해당 병원만
   *
   * 다면 분류:
   * - 부서원 평가: data_ev IN ('E','G') (부서장→부서원, 부서원→부서원)
   * - 부서장 평가: data_ev = 'F' (부서원→부서장)
   *
   * 부서장 여부는 user_roles_2025 (sub_head, one_person_sub) 기준.
   */
  /**
   * 직원 2025 – KPI + 다면평가 점수를 kpi_eval_result에 일괄 Upsert
   * - orgName이 ''이면 전체 병원 대상
   * - orgName이 특정 병원이면 해당 병원만
   */
  @Insert("""
      INSERT INTO personnel_evaluation.kpi_eval_result (
          eval_year,
          hospital_name,
          emp_id,
          total_score,
          multi_inteam10,
          multi_boss5,
          multi_clinic5,
          created_by,
          updated_by
      )
      SELECT
          #{year} AS eval_year,
          s.hospital_name,
          s.empId,
          -- 총점: I + II + III + 다면(보완된 70점)
          ROUND(
              COALESCE(s.iScore,  0)
            + COALESCE(s.iiScore, 0)
            + COALESCE(s.iiiScore,0)
            + COALESCE(s.evalSum70,0)
          , 1) AS total_score,
          NULL AS multi_inteam10,

          /* ③ 35점 환산: 보완된 100점 기준으로 저장 */
          CASE
            WHEN COALESCE(s.bossEff100, 0) = 0 THEN NULL
            ELSE ROUND(s.bossEff100 * 0.35, 1)
          END AS multi_boss5,

          CASE
            WHEN COALESCE(s.staffEff100, 0) = 0 THEN NULL
            ELSE ROUND(s.staffEff100 * 0.35, 1)
          END AS multi_clinic5,

          #{loginId} AS created_by,
          #{loginId} AS updated_by
      FROM (
          SELECT
              u.c_name AS hospital_name,
              u.id     AS empId,

              /* I / II / III 개인지표 */
              COALESCE(CAST(k.kcol07 AS DECIMAL(6,2)), 0) AS iScore,
              COALESCE(CAST(k.kcol11 AS DECIMAL(6,2)), 0) AS iiScore,
              COALESCE(CAST(k.kcol15 AS DECIMAL(6,2)), 0) AS iiiScore,

              /* 다면평가 100점 평균 (원본: AA+AB, 0~5→0~100) */
              p.bossEff100,
              p.staffEff100,

              /* ② 다면 70점 (보완 후 둘 다 0/NULL이면 31.3) */
              CASE
                WHEN COALESCE(p.bossEff100, 0) = 0
                 AND COALESCE(p.staffEff100, 0) = 0
                  THEN 31.3                     -- 둘 다 결국 0이면 31.3 고정
                ELSE ROUND(
                       COALESCE(p.bossEff100, 0) * 0.35
                     + COALESCE(p.staffEff100, 0) * 0.35
                     , 1)
              END AS evalSum70

          FROM personnel_evaluation.users_2025 u
          JOIN personnel_evaluation.kpi_info_general_2025 k
            ON k.kcol02 = u.id

          /* ← 여기서 es / es_pivot / es_eff 로직을 서브쿼리로 구현 */
          LEFT JOIN (
              SELECT
                  x.target_id,
                  /* ① 서로 채워준 100점 점수 */
                  CASE
                    WHEN COALESCE(x.bossRaw100, 0) = 0
                         AND COALESCE(x.staffRaw100, 0) > 0
                      THEN x.staffRaw100
                    ELSE x.bossRaw100
                  END AS bossEff100,
                  CASE
                    WHEN COALESCE(x.staffRaw100, 0) = 0
                         AND COALESCE(x.bossRaw100, 0) > 0
                      THEN x.bossRaw100
                    ELSE x.staffRaw100
                  END AS staffEff100
              FROM (
                  SELECT
                      t.target_id,
                      MAX(CASE WHEN t.relBucket = 'BOSS'  THEN t.avg100 END) AS bossRaw100,
                      MAX(CASE WHEN t.relBucket = 'STAFF' THEN t.avg100 END) AS staffRaw100
                  FROM (
                      SELECT
                          s.target_id,
                          CASE
                            WHEN s.data_ev IN ('E','F') THEN 'BOSS'   -- 상사 관련 평가
                            WHEN s.data_ev = 'G'        THEN 'STAFF'  -- 부서원→부서원
                            ELSE 'OTHER'
                          END AS relBucket,
                          -- ★ AA(0~10)은 /2 해서 0~5로 맞춘 뒤 0~100 환산
                          ROUND(
                            AVG(
                              CASE
                                WHEN s.data_type = 'AA'
                                  THEN (s.avg_score / 2.0)   -- 0~10 → 0~5
                                ELSE s.avg_score             -- AB는 0~5 그대로
                              END * 20                       -- 0~5 → 0~100
                            ),
                            1
                          ) AS avg100
                      FROM personnel_evaluation.evaluation_submissions s
                      WHERE s.eval_year = #{year}
                        AND s.is_active = 1
                        AND s.del_yn    = 'N'
                        AND s.data_ev   IN ('E','F','G')
                        AND s.data_type IN ('AA','AB')
                      GROUP BY s.target_id, relBucket
                  ) t
                  GROUP BY t.target_id
              ) x
          ) p
            ON p.target_id = u.id

          WHERE u.eval_year = #{year}
            AND (#{orgName} = '' OR u.c_name = #{orgName})
            AND u.del_yn = 'N'
            AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'       -- 경혁팀 제외(등급 대상 아님)
      ) s
      ON DUPLICATE KEY UPDATE
          hospital_name  = VALUES(hospital_name),
          total_score    = VALUES(total_score),
          multi_inteam10 = VALUES(multi_inteam10),
          multi_boss5    = VALUES(multi_boss5),
          multi_clinic5  = VALUES(multi_clinic5),
          updated_by     = VALUES(updated_by)
      """)
  int upsertStaffKpiEvalResult2025(@Param("year") int year,
      @Param("orgName") String orgName,
      @Param("loginId") String loginId);

  @Insert("""
          INSERT INTO personnel_evaluation.kpi_eval_result (
              eval_year,
              hospital_name,
              emp_id,
              total_score,
              multi_inteam10,
              multi_boss5,
              multi_clinic5,
              created_by,
              updated_by
          )
          SELECT
              #{year} AS eval_year,
              s.hospital_name,
              s.empId,

              /* 총점(OLD): KPI(10+10+10) + 다면(70) */
              ROUND(
                  COALESCE(s.kpiI10,   0)
                + COALESCE(s.kpiII10,  0)
                + COALESCE(s.kpiIII10, 0)
                + COALESCE(s.evalSum70,0)
              , 1) AS total_score,

              NULL AS multi_inteam10,

              /* 다면 35/35 */
              CASE
                WHEN COALESCE(s.bossEff100, 0) = 0 THEN NULL
                ELSE ROUND(s.bossEff100 * 0.35, 1)
              END AS multi_boss5,

              CASE
                WHEN COALESCE(s.staffEff100, 0) = 0 THEN NULL
                ELSE ROUND(s.staffEff100 * 0.35, 1)
              END AS multi_clinic5,

              #{loginId} AS created_by,
              #{loginId} AS updated_by
          FROM (
              SELECT
                  u.c_name AS hospital_name,
                  u.id     AS empId,

                  /* KPI 원점수(20/15/15) */
                  CAST(NULLIF(TRIM(k.kcol07), '') AS DECIMAL(10,2)) AS i20,
                  CAST(NULLIF(TRIM(k.kcol11), '') AS DECIMAL(10,2)) AS ii15,
                  CAST(NULLIF(TRIM(k.kcol15), '') AS DECIMAL(10,2)) AS iii15,

                  /* OLD 환산: 10/10/10 */
                  ROUND(COALESCE(CAST(NULLIF(TRIM(k.kcol07), '') AS DECIMAL(10,2)),0) / 20.0 * 10.0, 2)  AS kpiI10,
                  ROUND(COALESCE(CAST(NULLIF(TRIM(k.kcol11), '') AS DECIMAL(10,2)),0) / 15.0 * 10.0, 2) AS kpiII10,
                  ROUND(COALESCE(CAST(NULLIF(TRIM(k.kcol15), '') AS DECIMAL(10,2)),0) / 15.0 * 10.0, 2) AS kpiIII10,

                  /* 다면(보완된 100점) */
                  p.bossEff100,
                  p.staffEff100,

                  /* OLD 다면 70 (둘 다 없으면 31.3 유지) */
                  CASE
                    WHEN COALESCE(p.bossEff100, 0) = 0
                     AND COALESCE(p.staffEff100, 0) = 0
                      THEN 31.3
                    ELSE ROUND(
                           COALESCE(p.bossEff100, 0) * 0.35
                         + COALESCE(p.staffEff100, 0) * 0.35
                         , 1)
                  END AS evalSum70

              FROM personnel_evaluation.users_2025 u
              JOIN personnel_evaluation.kpi_info_general_2025 k
                ON k.kcol02 = u.id

              /* 다면(보완 로직: 한쪽만 있으면 그 값으로 채움) */
              LEFT JOIN (
                  SELECT
                      x.target_id,
                      CASE
                        WHEN COALESCE(x.bossRaw100, 0) = 0
                             AND COALESCE(x.staffRaw100, 0) > 0
                          THEN x.staffRaw100
                        ELSE x.bossRaw100
                      END AS bossEff100,
                      CASE
                        WHEN COALESCE(x.staffRaw100, 0) = 0
                             AND COALESCE(x.bossRaw100, 0) > 0
                          THEN x.bossRaw100
                        ELSE x.staffRaw100
                      END AS staffEff100
                  FROM (
                      SELECT
                          t.target_id,
                          MAX(CASE WHEN t.relBucket = 'BOSS'  THEN t.avg100 END) AS bossRaw100,
                          MAX(CASE WHEN t.relBucket = 'STAFF' THEN t.avg100 END) AS staffRaw100
                      FROM (
                          SELECT
                              s.target_id,
                              CASE
                                WHEN s.data_ev IN ('E','F') THEN 'BOSS'
                                WHEN s.data_ev = 'G'        THEN 'STAFF'
                                ELSE 'OTHER'
                              END AS relBucket,
                              ROUND(
                                AVG(
                                  CASE
                                    WHEN s.data_type = 'AA' THEN (s.avg_score / 2.0)
                                    ELSE s.avg_score
                                  END * 20
                                ), 1
                              ) AS avg100
                          FROM personnel_evaluation.evaluation_submissions s
                          WHERE s.eval_year = #{year}
                            AND s.is_active = 1
                            AND s.del_yn    = 'N'
                            AND s.data_ev   IN ('E','F','G')
                            AND s.data_type IN ('AA','AB')
                          GROUP BY s.target_id, relBucket
                      ) t
                      GROUP BY t.target_id
                  ) x
              ) p ON p.target_id = u.id

              WHERE u.eval_year = #{year}
                AND (#{orgName} = '' OR u.c_name = #{orgName})
                AND u.del_yn = 'N'
                AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'
          ) s
          ON DUPLICATE KEY UPDATE
              hospital_name  = VALUES(hospital_name),
              total_score    = VALUES(total_score),
              multi_inteam10 = VALUES(multi_inteam10),
              multi_boss5    = VALUES(multi_boss5),
              multi_clinic5  = VALUES(multi_clinic5),
              updated_by     = VALUES(updated_by),
              created_by     = IFNULL(created_by, VALUES(created_by));
      """)
  int upsertStaffKpiEvalResult2025Old(@Param("year") int year,
      @Param("orgName") String orgName,
      @Param("loginId") String loginId);

  @Insert("""
      INSERT INTO personnel_evaluation.kpi_eval_result_staff
      (
        eval_year, hospital_name, emp_id,
        kpi_i10, kpi_ii10, kpi_iii10,
        multi_boss35, multi_staff35, multi_sum70,
        total_score,
        created_by, updated_by
      )
      WITH es AS (
        SELECT
            s.target_id AS targetId,
            CASE
              WHEN s.data_ev IN ('E','F') THEN 'BOSS'
              WHEN s.data_ev = 'G'        THEN 'STAFF'
              ELSE 'OTHER'
            END AS relBucket,
            ROUND(
              AVG(
                CASE
                  WHEN s.data_type = 'AA' THEN (s.avg_score / 2.0)
                  ELSE s.avg_score
                END * 20
              ),
              1
            ) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_ev IN ('E','F','G')
          AND s.data_type IN ('AA','AB')
        GROUP BY s.target_id, relBucket
      ),
      es_pivot AS (
        SELECT
            targetId,
            MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS bossToStaff100,
            MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staffToStaff100
        FROM es
        GROUP BY targetId
      ),
      es_eff AS (
        SELECT
            p.targetId,
            CASE
              WHEN COALESCE(p.bossToStaff100,0)=0 AND COALESCE(p.staffToStaff100,0)>0
                THEN p.staffToStaff100
              ELSE p.bossToStaff100
            END AS bossEff100,
            CASE
              WHEN COALESCE(p.staffToStaff100,0)=0 AND COALESCE(p.bossToStaff100,0)>0
                THEN p.bossToStaff100
              ELSE p.staffToStaff100
            END AS staffEff100
        FROM es_pivot p
      ),
      base AS (
        SELECT
            u.eval_year,
            u.c_name AS hospital_name,
            u.id AS emp_id,

            CAST(NULLIF(TRIM(k.kcol07), '') AS DECIMAL(10,2)) AS i20,
            CAST(NULLIF(TRIM(k.kcol11), '') AS DECIMAL(10,2)) AS ii15,
            CAST(NULLIF(TRIM(k.kcol15), '') AS DECIMAL(10,2)) AS iii15,

            e.bossEff100,
            e.staffEff100
        FROM personnel_evaluation.users_2025 u
        JOIN personnel_evaluation.kpi_info_general_2025 k
          ON k.kcol02 = u.id
        LEFT JOIN es_eff e
          ON e.targetId = u.id
        WHERE u.eval_year = #{year}
          AND (#{orgName} = '' OR u.c_name = #{orgName})
          AND u.del_yn = 'N'
          AND (u.sub_code IS NULL OR u.sub_code NOT LIKE 'A0%')     /* 진료부 제외 */
          AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'          /* 경혁팀 제외 */
      )
      SELECT
        b.eval_year,
        b.hospital_name,
        b.emp_id,

        /* NEW: KPI는 원점수 그대로 20/15/15 저장(컬럼명은 기존 kpi_i10 유지) */
        ROUND(COALESCE(b.i20,0), 2)   AS kpi_i10,
        ROUND(COALESCE(b.ii15,0), 2)  AS kpi_ii10,
        ROUND(COALESCE(b.iii15,0), 2) AS kpi_iii10,

        /*otis: 다면 25/25 (컬럼명은 기존 multi_boss35 유지) */
        CASE WHEN b.bossEff100 IS NULL OR b.bossEff100=0 THEN NULL
             ELSE ROUND(b.bossEff100*0.25, 2) END AS multi_boss35,
        CASE WHEN b.staffEff100 IS NULL OR b.staffEff100=0 THEN NULL
             ELSE ROUND(b.staffEff100*0.25, 2) END AS multi_staff35,

        /* NEW: 다면 50 합 */
        ROUND(
          CASE
            WHEN (COALESCE(b.bossEff100,0)=0 AND COALESCE(b.staffEff100,0)=0) THEN 0.00
            ELSE COALESCE(b.bossEff100,0)*0.25 + COALESCE(b.staffEff100,0)*0.25
          END
        , 2) AS multi_sum70,

        /* NEW: 총점 = 20 + 15 + 15 + 50 */
        ROUND(
          COALESCE(b.i20,0)
        + COALESCE(b.ii15,0)
        + COALESCE(b.iii15,0)
        + (CASE
            WHEN (COALESCE(b.bossEff100,0)=0 AND COALESCE(b.staffEff100,0)=0) THEN 0.00
            ELSE COALESCE(b.bossEff100,0)*0.25 + COALESCE(b.staffEff100,0)*0.25
           END)
        , 2) AS total_score,

        #{actor} AS created_by,
        #{actor} AS updated_by
      FROM base b
      ON DUPLICATE KEY UPDATE
        hospital_name  = VALUES(hospital_name),
        kpi_i10        = VALUES(kpi_i10),
        kpi_ii10       = VALUES(kpi_ii10),
        kpi_iii10      = VALUES(kpi_iii10),
        multi_boss35   = VALUES(multi_boss35),
        multi_staff35  = VALUES(multi_staff35),
        multi_sum70    = VALUES(multi_sum70),
        total_score    = VALUES(total_score),
        updated_by     = VALUES(updated_by),
        created_by     = IFNULL(created_by, VALUES(created_by));
      """)
  int upsertStaffEvalResult2025New(@Param("year") int year,
      @Param("orgName") String orgName,
      @Param("actor") String actor);

  @Update("""
      INSERT INTO personnel_evaluation.kpi_eval_result_staff
      (
        eval_year, hospital_name, emp_id,
        kpi_i10, kpi_ii10, kpi_iii10,
        multi_boss35, multi_staff35, multi_sum70,
        total_score,
        created_by, updated_by
      )
      WITH es AS (
        SELECT
            s.target_id AS targetId,
            CASE
              WHEN s.data_ev IN ('E','F') THEN 'BOSS'
              WHEN s.data_ev = 'G'        THEN 'STAFF'
              ELSE 'OTHER'
            END AS relBucket,
            ROUND(
              AVG(
                CASE
                  WHEN s.data_type = 'AA' THEN (s.avg_score / 2.0)
                  ELSE s.avg_score
                END * 20
              ),
              1
            ) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_ev IN ('E','F','G')
          AND s.data_type IN ('AA','AB')
        GROUP BY s.target_id, relBucket
      ),
      es_pivot AS (
        SELECT
            targetId,
            MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS bossToStaff100,
            MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staffToStaff100
        FROM es
        GROUP BY targetId
      ),
      es_eff AS (
        SELECT
            p.targetId,
            /* 한쪽만 있으면 그 값을 서로 대체(직원 로직과 동일 컨셉) */
            CASE
              WHEN COALESCE(p.bossToStaff100,0)=0 AND COALESCE(p.staffToStaff100,0)>0 THEN p.staffToStaff100
              ELSE p.bossToStaff100
            END AS bossEff100,
            CASE
              WHEN COALESCE(p.staffToStaff100,0)=0 AND COALESCE(p.bossToStaff100,0)>0 THEN p.bossToStaff100
              ELSE p.staffToStaff100
            END AS staffEff100
        FROM es_pivot p
      ),
      base AS (
        SELECT
            u.eval_year,
            u.c_name AS hospital_name,
            u.id AS emp_id,

            CAST(NULLIF(TRIM(k.kcol07), '') AS DECIMAL(10,2)) AS i20,
            CAST(NULLIF(TRIM(k.kcol11), '') AS DECIMAL(10,2)) AS ii15,
            CAST(NULLIF(TRIM(k.kcol15), '') AS DECIMAL(10,2)) AS iii15,

            e.bossEff100,
            e.staffEff100
        FROM personnel_evaluation.users_2025 u
        JOIN personnel_evaluation.kpi_info_general_2025 k
          ON k.kcol02 = u.id
        LEFT JOIN es_eff e
          ON e.targetId = u.id
        WHERE u.eval_year = #{year}
          AND (#{orgName} = '' OR u.c_name = #{orgName})
          AND u.del_yn = 'N'
          AND (u.sub_code IS NULL OR u.sub_code NOT LIKE 'A0%')     /* 진료부 제외 */
          AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'          /* 경혁팀 제외 */
      )
      SELECT
        b.eval_year,
        b.hospital_name,
        b.emp_id,

        /* ✅ NEW: KPI 원점수(20/15/15) 그대로 저장 */
        ROUND(COALESCE(b.i20,0), 2)   AS kpi_i10,
        ROUND(COALESCE(b.ii15,0), 2)  AS kpi_ii10,
        ROUND(COALESCE(b.iii15,0), 2) AS kpi_iii10,

        /* ✅ NEW: 다면 25/25 */
        CASE WHEN b.bossEff100 IS NULL OR b.bossEff100=0 THEN NULL
             ELSE ROUND(b.bossEff100*0.25, 2) END AS multi_boss35,
        CASE WHEN b.staffEff100 IS NULL OR b.staffEff100=0 THEN NULL
             ELSE ROUND(b.staffEff100*0.25, 2) END AS multi_staff35,

        /* ✅ NEW: 다면 합계(50) — 컬럼명은 multi_sum70 유지해도 됨(덮어쓰기니까) */
        ROUND(
          CASE
            WHEN (COALESCE(b.bossEff100,0)=0 AND COALESCE(b.staffEff100,0)=0) THEN 0.00
            ELSE COALESCE(b.bossEff100,0)*0.25 + COALESCE(b.staffEff100,0)*0.25
          END
        , 2) AS multi_sum70,

        /* ✅ NEW: 총점 = 20 + 15 + 15 + 50 */
        ROUND(
            COALESCE(b.i20,0)
          + COALESCE(b.ii15,0)
          + COALESCE(b.iii15,0)
          + ROUND(
              CASE
                WHEN (COALESCE(b.bossEff100,0)=0 AND COALESCE(b.staffEff100,0)=0) THEN 0.00
                ELSE COALESCE(b.bossEff100,0)*0.25 + COALESCE(b.staffEff100,0)*0.25
              END
            , 2)
        , 2) AS total_score,

        #{actor} AS created_by,
        #{actor} AS updated_by
      FROM base b
      ON DUPLICATE KEY UPDATE
        hospital_name  = VALUES(hospital_name),
        kpi_i10        = VALUES(kpi_i10),
        kpi_ii10       = VALUES(kpi_ii10),
        kpi_iii10      = VALUES(kpi_iii10),
        multi_boss35   = VALUES(multi_boss35),
        multi_staff35  = VALUES(multi_staff35),
        multi_sum70    = VALUES(multi_sum70),
        total_score    = VALUES(total_score),
        updated_by     = VALUES(updated_by),
        created_by     = IFNULL(created_by, VALUES(created_by));
      """)
  int upsertStaffEvalResult2025(@Param("year") int year,
      @Param("orgName") String orgName,
      @Param("actor") String actor);

  /**
   * kpi_eval_result 기준으로 직원 등급 산출
   * - 병원별 상대평가
   */
  @Update("""
      WITH score_groups AS (
        SELECT
            r.eval_year,
            r.hospital_name,
            r.total_score,
            COUNT(*) AS group_cnt,
            -- 점수 높은 그룹부터 누적 인원
            SUM(COUNT(*)) OVER (
              PARTITION BY r.eval_year, r.hospital_name
              ORDER BY r.total_score DESC
            ) AS cum_cnt,
            -- 병원별 전체 인원
            SUM(COUNT(*)) OVER (
              PARTITION BY r.eval_year, r.hospital_name
            ) AS total_cnt
        FROM personnel_evaluation.kpi_eval_result r
        WHERE r.eval_year = #{year}
          AND (#{orgName} = '' OR r.hospital_name = #{orgName})
        GROUP BY
            r.eval_year,
            r.hospital_name,
            r.total_score
      ),
      ranked AS (
        SELECT
            g.eval_year,
            g.hospital_name,
            g.total_score,
            -- 누적 인원을 기반으로 1~100 퍼센타일 산출
            CAST(
              CEIL(g.cum_cnt * 100.0 / g.total_cnt)
              AS UNSIGNED
            ) AS p100
        FROM score_groups g
      )
      UPDATE personnel_evaluation.kpi_eval_result r
      JOIN ranked x
        ON x.eval_year      = r.eval_year
       AND x.hospital_name  = r.hospital_name
       AND x.total_score    = r.total_score
      SET
        r.percentile = x.p100,
        r.eval_grade = CASE
          WHEN x.p100 <=  4 THEN 'A+'
          WHEN x.p100 <= 11 THEN 'A'
          WHEN x.p100 <= 23 THEN 'B+'
          WHEN x.p100 <= 40 THEN 'B'
          WHEN x.p100 <= 60 THEN 'C+'
          WHEN x.p100 <= 77 THEN 'C'
          WHEN x.p100 <= 89 THEN 'D+'
          WHEN x.p100 <= 96 THEN 'D'
          ELSE 'E'
        END
      WHERE r.eval_year = #{year}
        AND (#{orgName} = '' OR r.hospital_name = #{orgName});
      """)
  int updateEvalGrade(@Param("year") int year,
      @Param("orgName") String orgName);

  @Update("""
      WITH base AS (
        SELECT
          eval_year,
          hospital_name,
          emp_id,
          total_score
        FROM personnel_evaluation.kpi_eval_result_staff
        WHERE eval_year = #{year}
          AND (#{orgName} = '' OR hospital_name = #{orgName})
          AND total_score IS NOT NULL
      ),
      ranked AS (
        SELECT
          b.*,
          ROW_NUMBER() OVER (PARTITION BY eval_year, hospital_name ORDER BY total_score DESC, emp_id) AS rn,
          RANK()       OVER (PARTITION BY eval_year, hospital_name ORDER BY total_score DESC, emp_id) AS rnk,
          COUNT(*)     OVER (PARTITION BY eval_year, hospital_name) AS n
        FROM base b
      ),
      cuts AS (
        SELECT
          eval_year,
          hospital_name,
          MAX(CASE WHEN rn = CEIL(n*0.04) THEN total_score END) AS cut4,
          MAX(CASE WHEN rn = CEIL(n*0.11) THEN total_score END) AS cut11,
          MAX(CASE WHEN rn = CEIL(n*0.23) THEN total_score END) AS cut23,
          MAX(CASE WHEN rn = CEIL(n*0.40) THEN total_score END) AS cut40,
          MAX(CASE WHEN rn = CEIL(n*0.60) THEN total_score END) AS cut60,
          MAX(CASE WHEN rn = CEIL(n*0.77) THEN total_score END) AS cut77,
          MAX(CASE WHEN rn = CEIL(n*0.89) THEN total_score END) AS cut89,
          MAX(CASE WHEN rn = CEIL(n*0.96) THEN total_score END) AS cut96
        FROM ranked
        GROUP BY eval_year, hospital_name
      ),
      graded AS (
        SELECT
          r.eval_year,
          r.hospital_name,
          r.emp_id,
          /* 백분위: 상위 1~100 (동점은 같은 rnk로 동일 백분위) */
          CEIL((r.rnk / r.n) * 100) AS percentile,
          CASE
            WHEN r.total_score >= c.cut4  THEN 'A+'
            WHEN r.total_score >= c.cut11 THEN 'A'
            WHEN r.total_score >= c.cut23 THEN 'B+'
            WHEN r.total_score >= c.cut40 THEN 'B'
            WHEN r.total_score >= c.cut60 THEN 'C+'
            WHEN r.total_score >= c.cut77 THEN 'C'
            WHEN r.total_score >= c.cut89 THEN 'D+'
            WHEN r.total_score >= c.cut96 THEN 'D'
            ELSE 'E'
          END AS eval_grade
        FROM ranked r
        JOIN cuts c
          ON c.eval_year = r.eval_year
         AND c.hospital_name = r.hospital_name
      )
      UPDATE personnel_evaluation.kpi_eval_result_staff ker
      JOIN graded g
        ON g.eval_year = ker.eval_year
       AND g.emp_id    = ker.emp_id
      SET
        ker.percentile = g.percentile,
        ker.eval_grade = g.eval_grade
      WHERE ker.eval_year = #{year}
        AND (#{orgName} = '' OR ker.hospital_name = #{orgName});
      """)
  int applyStaffGrades2025(@Param("year") int year,
      @Param("orgName") String orgName);

  @Select("""
          WITH es AS (
        SELECT
            s.target_id AS targetId,
            CASE
              WHEN s.data_ev = 'F' THEN 'BOSS'          -- 부서장 평가(부서원→부서장)
              WHEN s.data_ev IN ('E','G') THEN 'STAFF'  -- 부서원 평가(부서장→부서원, 부서원→부서원)
              ELSE 'OTHER'
            END AS relBucket,
            -- 다면평가 총점(0~100)을 그대로 평균
            ROUND(AVG(s.total_score), 1) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn    = 'N'
          AND s.data_type IN ('AA','AB')     -- ★ AA + AB 모두 사용
          AND s.data_ev IN ('E','F','G')
        GROUP BY s.target_id, relBucket
      ),
      es_pivot AS (
        SELECT
            targetId,
            MAX(CASE WHEN relBucket = 'BOSS'  THEN avg100 END) AS bossEval100,   -- 부서장 평가(F)
            MAX(CASE WHEN relBucket = 'STAFF' THEN avg100 END) AS staffEval100,  -- 부서원 평가(E,G)
            ROUND(AVG(avg100), 1) AS avg100_all
        FROM es
        GROUP BY targetId
      )
          SELECT
              u.c_name                                   AS orgName,
              COALESCE(sm.sub_name, '-')                AS deptName,
              u.id                                       AS empId,
              u.name                                     AS empName,
              u.position                                 AS position,

              -- 총점: kpi_eval_result.total_score 우선, 없으면 kcol03 fallback
              COALESCE(
                ker.total_score,
                CAST(NULLIF(TRIM(k.kcol03), '') AS DECIMAL(6,2))
              ) AS totalCalc,

              -- I. 홍보공헌활동
              k.kcol04 AS newInConn,
              k.kcol05 AS funeralConn,
              k.kcol06 AS bothConn,
              k.kcol07 AS kpiI,
              k.kcol08 AS scoreI,

              -- II. 자원봉사
              k.kcol10 AS volCnt,
              k.kcol11 AS kpiII,
              k.kcol12 AS scoreII,

              -- III. 교육이수
              CONCAT(IFNULL(k.kcol14,'0'), '%') AS eduRate,
              k.kcol15 AS kpiIII,
              k.kcol16 AS scoreIII,

              -- V. 다면평가 35점 환산
              -- 1순위: kpi_eval_result.multi_boss5 / multi_clinic5
              -- 없으면 evaluation_submissions 기반 실시간 계산값 사용
              COALESCE(
                ker.multi_boss5,
                CASE
                  WHEN mp.bossEval100 IS NOT NULL
                  THEN ROUND(mp.bossEval100 * 0.35, 1)
                  ELSE NULL
                END
              ) AS bossToStaff35,     -- 부서장 평가(F)

              COALESCE(
                ker.multi_clinic5,
                CASE
                  WHEN mp.staffEval100 IS NOT NULL
                  THEN ROUND(mp.staffEval100 * 0.35, 1)
                  ELSE NULL
                END
              ) AS staffToStaff35,    -- 부서원 평가(E,G)

              -- 평가 등급 (kpi_eval_result 기준)
              ker.eval_grade     AS evalGrade
          FROM personnel_evaluation.users_2025 u
          JOIN personnel_evaluation.kpi_info_general_2025 k
            ON k.kcol02 = u.id
          LEFT JOIN personnel_evaluation.kpi_eval_result ker
            ON ker.eval_year = u.eval_year
           AND ker.emp_id    = u.id
          LEFT JOIN es_pivot mp
            ON mp.targetId = u.id
          LEFT JOIN personnel_evaluation.sub_management sm
            ON sm.sub_code  = u.sub_code
           AND sm.eval_year = u.eval_year
          WHERE u.eval_year = #{year}
            AND (#{orgName} = '' OR u.c_name = #{orgName})
            AND u.del_yn = 'N'
            AND (u.sub_code IS NULL OR u.sub_code NOT LIKE 'A%%')               -- 진료부 제외
            AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'                    -- 경혁팀 제외
            -- 부서장도 포함해야 하므로 role 필터는 제거
            -- AND NOT EXISTS (
            --     SELECT 1
            --     FROM personnel_evaluation.user_roles_2025 r
            --     WHERE r.user_id  = u.id
            --       AND r.eval_year = u.eval_year
            --       AND r.role IN ('sub_head','one_person_sub')
            -- )
          ORDER BY
            CASE
              WHEN ASCII(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 48 AND 57 THEN 0
              WHEN ORD(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 0xAC00 AND 0xD7A3 THEN 1
              WHEN ASCII(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 65 AND 90 THEN 2
              WHEN ASCII(SUBSTRING(IFNULL(sm.sub_name, ''), 1, 1)) BETWEEN 97 AND 122 THEN 2
              ELSE 3
            END,
            CASE
              WHEN IFNULL(sm.sub_name, '') REGEXP '^[0-9]+' THEN CAST(sm.sub_name AS UNSIGNED)
              ELSE NULL
            END,
            sm.sub_name COLLATE utf8mb4_0900_ai_ci,
            LPAD(u.id, 12, '0')
          """)
  List<KpiSummaryRow> selectStaffSummary2025(@Param("year") int year,
      @Param("orgName") String orgName);

  @Update("""
      INSERT INTO personnel_evaluation.kpi_eval_clinic
      (
        eval_year, hospital_name, emp_id,
        kpi_i20, kpi_ii15, kpi_iii15,
        multi_gh25, multi_chief25, multi_sum50,
        total_score,
        created_by, updated_by
      )
      WITH es AS (
        /* 진료부 대상자에 대한 C/E 평가를 100점으로 정규화 */
        SELECT
            s.target_id AS targetId,
            CASE
              WHEN s.data_ev = 'C' THEN 'GH'       -- 경혁팀 -> 진료부
              WHEN s.data_ev = 'E' THEN 'CHIEF'    -- 진료팀장 -> 진료부
              ELSE 'OTHER'
            END AS relBucket,
            ROUND(
              AVG(
                CASE
                  WHEN s.data_type = 'AA' THEN (s.avg_score / 2.0)
                  ELSE s.avg_score
                END * 20
              ),
              1
            ) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        JOIN personnel_evaluation.users_2025 tgt
          ON tgt.id = s.target_id AND tgt.eval_year = s.eval_year
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_ev IN ('C','E')
          AND s.data_type IN ('AA','AB')
          AND tgt.sub_code LIKE 'A0%'     /* 진료부 대상자만 */
          AND tgt.del_yn = 'N'
        GROUP BY s.target_id, relBucket
      ),
      es_pivot AS (
        SELECT
            targetId,
            MAX(CASE WHEN relBucket='GH'    THEN avg100 END) AS gh100,
            MAX(CASE WHEN relBucket='CHIEF' THEN avg100 END) AS chief100
        FROM es
        GROUP BY targetId
      ),
      es_eff AS (
        /* ✅ 핵심: 한쪽이 없으면 다른쪽으로 대체
           - 진료팀장(대상자)은 chief100이 비는 경우가 많으므로 gh100을 chiefEff로 채움
           - 반대로 gh100만 비고 chief100만 있는 경우에도 양쪽을 동일하게 맞춤(직원 로직과 동일한 방어) */
        SELECT
            p.targetId,
            CASE
              WHEN COALESCE(p.gh100,0)=0 AND COALESCE(p.chief100,0)>0 THEN p.chief100
              ELSE p.gh100
            END AS ghEff100,
            CASE
              WHEN COALESCE(p.chief100,0)=0 AND COALESCE(p.gh100,0)>0 THEN p.gh100
              ELSE p.chief100
            END AS chiefEff100
        FROM es_pivot p
      ),
      base AS (
        SELECT
            u.eval_year,
            u.c_name AS hospital_name,
            u.id AS emp_id,

            CAST(NULLIF(TRIM(k.kcol07), '') AS DECIMAL(10,2)) AS i20,
            CAST(NULLIF(TRIM(k.kcol11), '') AS DECIMAL(10,2)) AS ii15,
            CAST(NULLIF(TRIM(k.kcol15), '') AS DECIMAL(10,2)) AS iii15,

            e.ghEff100,
            e.chiefEff100
        FROM personnel_evaluation.users_2025 u
        JOIN personnel_evaluation.kpi_info_general_2025 k
          ON k.kcol02 = u.id
        LEFT JOIN es_eff e
          ON e.targetId = u.id
        WHERE u.eval_year = #{year}
          AND (#{orgName} = '' OR u.c_name = #{orgName})
          AND u.del_yn = 'N'
          AND u.sub_code LIKE 'A0%'        /* 진료부만 */
      )
      SELECT
        b.eval_year,
        b.hospital_name,
        b.emp_id,

        /* KPI는 20/15/15 원점수 그대로 저장 */
        ROUND(COALESCE(b.i20,0), 2)   AS kpi_i20,
        ROUND(COALESCE(b.ii15,0), 2)  AS kpi_ii15,
        ROUND(COALESCE(b.iii15,0), 2) AS kpi_iii15,

        /* 다면 25/25 */
        CASE WHEN b.ghEff100 IS NULL OR b.ghEff100=0 THEN NULL
             ELSE ROUND(b.ghEff100 * 0.25, 2) END AS multi_gh25,
        CASE WHEN b.chiefEff100 IS NULL OR b.chiefEff100=0 THEN NULL
             ELSE ROUND(b.chiefEff100 * 0.25, 2) END AS multi_chief25,

        /* 다면 합 50 */
        ROUND(
          CASE
            WHEN (COALESCE(b.ghEff100,0)=0 AND COALESCE(b.chiefEff100,0)=0) THEN 0.00
            ELSE COALESCE(b.ghEff100,0)*0.25 + COALESCE(b.chiefEff100,0)*0.25
          END
        , 2) AS multi_sum50,

        /* 총점 = 20 + 15 + 15 + 50 */
        ROUND(
            COALESCE(b.i20,0)
          + COALESCE(b.ii15,0)
          + COALESCE(b.iii15,0)
          + ROUND(
              CASE
                WHEN (COALESCE(b.ghEff100,0)=0 AND COALESCE(b.chiefEff100,0)=0) THEN 0.00
                ELSE COALESCE(b.ghEff100,0)*0.25 + COALESCE(b.chiefEff100,0)*0.25
              END
            , 2)
        , 2) AS total_score,

        #{actor} AS created_by,
        #{actor} AS updated_by
      FROM base b
      ON DUPLICATE KEY UPDATE
        hospital_name  = VALUES(hospital_name),
        kpi_i20        = VALUES(kpi_i20),
        kpi_ii15       = VALUES(kpi_ii15),
        kpi_iii15      = VALUES(kpi_iii15),
        multi_gh25     = VALUES(multi_gh25),
        multi_chief25  = VALUES(multi_chief25),
        multi_sum50    = VALUES(multi_sum50),
        total_score    = VALUES(total_score),
        updated_by     = VALUES(updated_by),
        created_by     = IFNULL(created_by, VALUES(created_by));
      """)
  int upsertClinicKpiEvalClinic2025(@Param("year") int year,
      @Param("orgName") String orgName,
      @Param("actor") String actor);

  @Select("""
        SELECT
          eval_year      AS evalYear,
          hospital_name  AS hospitalName,
          emp_id         AS empId,
          total_score    AS totalScore,
          percentile     AS percentile,
          eval_grade     AS evalGrade
        FROM personnel_evaluation.kpi_eval_result_staff
        WHERE eval_year = #{year}
          AND emp_id    = #{empId}
      """)
  StaffGradeRow selectMyStaffGrade(@Param("year") int year,
      @Param("empId") String empId);

}