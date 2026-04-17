package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.CombinedRow;
import com.coresolution.pe.entity.KpiCohortAvg;
import com.coresolution.pe.entity.KpiSummaryRow;
import com.coresolution.pe.entity.MyKpiMultiRow;
import com.coresolution.pe.entity.MyKpiRow;

@Mapper
public interface KpiMapper {

  @Select("""
      WITH es AS (
        SELECT
          s.target_id AS targetId,
          CASE
            WHEN s.data_ev IN ('E') THEN 'BOSS'   -- 부서장→부서원
            WHEN s.data_ev IN ('G') THEN 'STAFF'  -- 부서원→부서원
            ELSE 'OTHER'
          END AS relBucket,
          ROUND(AVG(s.avg_score * 20), 1) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_ev IN ('E','G')
        GROUP BY s.target_id, relBucket
      ),
      es_pivot AS (
        SELECT
          targetId,
          MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS bossToStaff100,
          MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staffToStaff100,
          ROUND(AVG(avg100), 1) AS avg100_all
        FROM es
        GROUP BY targetId
      )

      /* ===================== 상대평가 적용 ===================== */
      SELECT
        o.*,
        CASE
          WHEN o.p100 <=  4 THEN 'A+'
          WHEN o.p100 <= 11 THEN 'A'
          WHEN o.p100 <= 23 THEN 'B+'
          WHEN o.p100 <= 40 THEN 'B'
          WHEN o.p100 <= 60 THEN 'C+'
          WHEN o.p100 <= 77 THEN 'C'
          WHEN o.p100 <= 89 THEN 'D+'
          WHEN o.p100 <= 96 THEN 'D'
          ELSE 'E'
        END AS evalGrade
      FROM (
        SELECT
          t.*,
          /* 병원(orgName) 내 분포 기준 백분위 */
          NTILE(100) OVER (PARTITION BY t.orgName ORDER BY t.totalCalc DESC) AS p100
        FROM (
          /* ===================== 점수/총점 계산(원본 유지) ===================== */
          SELECT
            u.c_name AS orgName,                               -- ★ 상대평가 파티션용
            COALESCE(sm.sub_name,'-') AS deptName,
            u.id   AS empId,
            u.name AS empName,
            u.position AS position,

            -- 원본
            kis.kis03 AS total,
            kis.kis04 AS newInConn,  kis.kis05 AS funeralConn, kis.kis06 AS bothConn,
            kis.kis07 AS kpiI,       kis.kis08 AS scoreI,
            kis.kis10 AS volCnt,     kis.kis11 AS kpiII,       kis.kis12 AS scoreII,
            kis.kis14 AS eduRate,    kis.kis15 AS kpiIII,      kis.kis16 AS scoreIII,
            kis.kis09 AS noteI,      kis.kis13 AS noteII,      kis.kis17 AS noteIII,

            -- 다면평가(100/35/합계)
            p.bossToStaff100,
            CASE WHEN p.bossToStaff100 IS NOT NULL THEN ROUND(p.bossToStaff100 * 0.35, 1) END AS bossToStaff35,
            p.staffToStaff100,
            CASE WHEN p.staffToStaff100 IS NOT NULL THEN ROUND(p.staffToStaff100 * 0.35, 1) END AS staffToStaff35,
            CASE
              WHEN p.bossToStaff100 IS NOT NULL OR p.staffToStaff100 IS NOT NULL
              THEN ROUND(COALESCE(p.bossToStaff100,0)*0.35 + COALESCE(p.staffToStaff100,0)*0.35, 1)
              ELSE NULL
            END AS evalSum70,

            -- I: 홍보공헌(입원+장례연계건수 기반)
            CAST(
              CASE
                WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                  THEN 10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)
                ELSE 0
              END AS DECIMAL(4,1)
            ) AS kpiICalc,
            CAST(
              CASE
                WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                  THEN ROUND((10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)) * 10)
                ELSE 0
              END AS UNSIGNED
            ) AS scoreICalc,

            -- II: 자원봉사(1건=10, 2=10.67, 3=11.33, 4이상=12)
            CAST(
              CASE
                WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                ELSE 0.00
              END AS DECIMAL(4,2)
            ) AS kpiIICalc,
            CAST(ROUND(
              CASE
                WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                ELSE 0.00
              END * 10) AS UNSIGNED
            ) AS scoreIICalc,

            -- III: 교육이수 10점 만점 환산
            CAST(
              CASE
                WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                  THEN ROUND(CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10, 2)
                ELSE NULL
              END AS DECIMAL(4,2)
            ) AS kpiIIICalc,
            CAST(
              CASE
                WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                  THEN ROUND((CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10) * 10)
                ELSE NULL
              END AS UNSIGNED
            ) AS scoreIIICalc,

            -- ★ 최종 총점: I + II + III(10점기준) + V(최대 70)
            ROUND(
              COALESCE(           -- I
                (CASE
                  WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                    THEN 10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)
                  ELSE 0
                END), 0)
              + COALESCE(         -- II
                (CASE
                  WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                  WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                  WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                  WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                  ELSE 0.00
                END), 0)
              + COALESCE(         -- III(10점 환산)
                (CASE
                  WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                    THEN ROUND(CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10, 2)
                  ELSE 0
                END), 0)
              + COALESCE(         -- V(35+35)
                (CASE
                  WHEN p.bossToStaff100 IS NOT NULL OR p.staffToStaff100 IS NOT NULL
                    THEN COALESCE(p.bossToStaff100,0)*0.35 + COALESCE(p.staffToStaff100,0)*0.35
                  ELSE 0
                END), 0)
            , 1) AS totalCalc
          FROM personnel_evaluation.users_${year} u
          LEFT JOIN personnel_evaluation.sub_management sm
            ON sm.sub_code = u.sub_code AND sm.eval_year = u.eval_year
          LEFT JOIN personnel_evaluation.kpi_info_sub kis
            ON kis.kis02 = u.id
           AND (kis.kis01 = #{orgName} OR #{orgName} = '')
          LEFT JOIN es_pivot p
            ON p.targetId = u.id
          WHERE (#{orgName} = '' OR u.c_name = #{orgName})
            AND u.eval_year = #{year}
            AND u.del_yn = 'N'
            AND (u.sub_code IS NULL OR u.sub_code NOT LIKE 'A%')          -- ★ 진료부(코드 A~) 제외
            AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'              -- ★ 경혁팀 제외 (GH_TEAM)
            AND NOT EXISTS (
              SELECT 1 FROM personnel_evaluation.user_roles_${year} r
               WHERE r.user_id = u.id AND r.eval_year = u.eval_year
                 AND r.role IN ('sub_head','one_person_sub')
            )
          ) t
      ) o
      /* 원래 정렬 유지 */
      ORDER BY
        /* 1) 그룹: 숫자(0) > 한글(1) > 영어(2) > 기타(3) */
        CASE
          WHEN ASCII(SUBSTRING(IFNULL(o.deptName,''), 1, 1)) BETWEEN 48 AND 57 THEN 0        -- '0'~'9'
          WHEN ORD(SUBSTRING(IFNULL(o.deptName,''), 1, 1)) BETWEEN 0xAC00 AND 0xD7A3 THEN 1 -- 가~힣
          WHEN ASCII(SUBSTRING(IFNULL(o.deptName,''), 1, 1)) BETWEEN 65 AND 90 THEN 2       -- A~Z
          WHEN ASCII(SUBSTRING(IFNULL(o.deptName,''), 1, 1)) BETWEEN 97 AND 122 THEN 2      -- a~z
          ELSE 3
        END,
        /* 2) 숫자면 숫자값으로 오름차순 */
        CASE
          WHEN IFNULL(o.deptName,'') REGEXP '^[0-9]+'
            THEN CAST(o.deptName AS UNSIGNED)
          ELSE NULL
        END,
        /* 3) 나머이는 사전순 */
        o.deptName COLLATE utf8mb4_0900_ai_ci,
        /* 4) 동률이면 사번 */
        LPAD(o.empId, 12, '0')
      """)
  List<CombinedRow> selectCombinedByOrgYear(@Param("orgName") String orgName, @Param("year") int year);

  @Select("""
          SELECT DISTINCT u.c_name
          FROM personnel_evaluation.users_${year} u
          WHERE u.eval_year = #{year}
          ORDER BY u.c_name
      """)
  List<String> selectOrgList(@Param("year") int year);

  @Select("""
      WITH es AS (
        SELECT
          s.target_id AS targetId,
          s.data_ev   AS dtype,
          ROUND(AVG(s.avg_score*20),1) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_ev IN ('B','D','E','F','G')
        GROUP BY s.target_id, s.data_ev
      ),
      role_map AS (
        SELECT
          u.id AS targetId,
          CASE
            WHEN EXISTS (
              SELECT 1
              FROM personnel_evaluation.evaluation_submissions s2
              WHERE s2.eval_year = #{year}
                AND s2.is_active = 1
                AND s2.del_yn = 'N'
                AND s2.data_ev = 'F'        -- 부서원 → 부서장
                AND s2.target_id = u.id
            ) THEN 'MANAGER' ELSE 'STAFF' END AS role,
          TRIM(t.team_name) AS teamName
        FROM personnel_evaluation.users_${year} u
        JOIN personnel_evaluation.team t
          ON t.team_code = u.team_code AND t.eval_year = u.eval_year
        WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
      ),
      es_pivot AS (
        SELECT
          r.targetId,
          MAX(CASE WHEN e.dtype='B' THEN e.avg100 END) AS fromClinicToTeam100,
          MAX(CASE WHEN e.dtype='D' THEN e.avg100 END) AS inTeamToTeam100,
          CASE
            WHEN r.teamName = '경혁팀' AND r.role = 'STAFF' THEN
              (
                COALESCE(MAX(CASE WHEN e.dtype='E' THEN e.avg100 END), 0) +
                COALESCE(MAX(CASE WHEN e.dtype='G' THEN e.avg100 END), 0)
              ) / NULLIF(
                (CASE WHEN MAX(CASE WHEN e.dtype='E' THEN 1 END)=1 THEN 1 ELSE 0 END) +
                (CASE WHEN MAX(CASE WHEN e.dtype='G' THEN 1 END)=1 THEN 1 ELSE 0 END)
              , 0)
            ELSE
              MAX(CASE WHEN e.dtype='F' THEN e.avg100 END)
          END AS bossToStaff100,
          CASE
            WHEN r.teamName = '경혁팀' AND r.role = 'STAFF' THEN NULL
            ELSE MAX(CASE WHEN e.dtype='G' THEN e.avg100 END)
          END AS staffToStaff100
        FROM role_map r
        LEFT JOIN es e ON e.targetId = r.targetId
        GROUP BY r.targetId, r.teamName, r.role
      ),

      /* -------------------------------------------------------------
       * b0: 원천 컬럼 정규화(+ 병원/팀명 포함)
       * ----------------------------------------------------------- */
      b0 AS (
        SELECT
          /* 직원/조직 정보 */
          u.c_name                  AS orgName,
          TRIM(t.team_name)         AS teamName,
          COALESCE(sm.sub_name,'-') AS deptName,
          u.id                      AS empNo,
          u.position                AS position,
          u.name                    AS name,

          /* ===== Ⅰ. 재무성과 ===== */
          REGEXP_REPLACE(COALESCE(kis.kcol09,''), '[,▲▼]', '') AS iBedAvailTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol16,''), '[,▲▼]', '') AS iDayChargeTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol22,''), '[,▲▼]', '') AS iSalesTxt,

          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol09),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iBedAvail_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol16),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iDayCharge_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol22),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iSales_s,

          /* ===== Ⅱ. 고객서비스 ===== */
          REGEXP_REPLACE(COALESCE(kis.kcol26,''), '[,▲▼]', '') AS iiPatientSatTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol25,''), '[,▲▼]', '') AS iiProtectorSatTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol33,''), '[,▲▼]', '') AS iiAccidentTxt,
          '' AS iiMealTxt,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol26),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iiPatientSat_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol25),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iiProtectorSat_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol33),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iiAccident_s,

          /* ===== Ⅲ. 프로세스혁신 ===== */
          REGEXP_REPLACE(COALESCE(kis.kcol46,''), '[,▲▼]', '') AS iiiLinkTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol50,''), '[,▲▼]', '') AS iiiPromotionTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol58,''), '[,▲▼]', '') AS iiiQITxt,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol46),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^^0-9.-]', ''
          ) AS iiiLink_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol50),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iiiPromotion_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol58),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS iiiQI_s,

          /* ===== Ⅳ. 학습성장 ===== */
          REGEXP_REPLACE(COALESCE(kis.kcol65,''), '[,%▲▼]', '') AS ivEduTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol73,''), '[,▲▼]',  '') AS ivVolunteerTxt,
          REGEXP_REPLACE(COALESCE(kis.kcol79,''), '[,▲▼]',  '') AS ivDiscussTxt,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol65),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS ivEdu_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol73),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS ivVolunteer_s,
          REGEXP_REPLACE(
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol79),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
            '[^0-9.-]', ''
          ) AS ivDiscuss_s,

          /* ===== Ⅴ. 다면평가 (경혁팀 규칙) ===== */
          CAST(COALESCE(p.inTeamToTeam100,     0) * 0.10 AS DECIMAL(10,2)) AS vExperience,   -- D
          CAST(COALESCE(p.bossToStaff100,      0) * 0.05 AS DECIMAL(10,2)) AS vBossToStaff,  -- F 또는 E/G 평균
          CAST(COALESCE(p.fromClinicToTeam100, 0) * 0.05 AS DECIMAL(10,2)) AS vStaffToStaff, -- B
          CAST(
            COALESCE(p.inTeamToTeam100,     0) * 0.10 +
            COALESCE(p.bossToStaff100,      0) * 0.05 +
            COALESCE(p.fromClinicToTeam100, 0) * 0.05
          AS DECIMAL(10,2)) AS vSubtotal
        FROM personnel_evaluation.users_${year} u
        JOIN personnel_evaluation.team t
          ON t.team_code = u.team_code AND t.eval_year = u.eval_year
        LEFT JOIN personnel_evaluation.sub_management sm
          ON sm.sub_code = u.sub_code AND sm.eval_year = u.eval_year
        LEFT JOIN personnel_evaluation.kpi_info kis
          ON kis.kcol02 = u.id
          AND (#{orgName} = '' OR kis.kcol01 = #{orgName})
        LEFT JOIN es_pivot p
          ON p.targetId = u.id
        WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
          AND (#{orgName} = '' OR u.c_name = #{orgName})
          AND (#{teamName} = '' OR TRIM(t.team_name) = TRIM(#{teamName}))
      ),

      /* -------------------------------------------------------------
       * b1: 숫자변환 및 소계
       * ----------------------------------------------------------- */
      b1 AS (
        SELECT
          b0.*,

          /* Ⅰ */
          CASE
            WHEN b0.iBedAvail_s IS NULL OR b0.iBedAvail_s='' THEN 0
            WHEN INSTR(b0.iBedAvail_s,'.')=0 THEN CAST(b0.iBedAvail_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1)))
          END AS iBedAvailNum,
          CASE
            WHEN b0.iDayCharge_s IS NULL OR b0.iDayCharge_s='' THEN 0
            WHEN INSTR(b0.iDayCharge_s,'.')=0 THEN CAST(b0.iDayCharge_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1)))
          END AS iDayChargeNum,
          CASE
            WHEN b0.iSales_s IS NULL OR b0.iSales_s='' THEN 0
            WHEN INSTR(b0.iSales_s,'.')=0 THEN CAST(b0.iSales_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iSales_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iSales_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iSales_s,'.',-1)))
          END AS iSalesNum,
          CAST(
            COALESCE(
              CASE WHEN b0.iBedAvail_s IS NULL OR b0.iBedAvail_s='' THEN 0
                   WHEN INSTR(b0.iBedAvail_s,'.')=0 THEN CAST(b0.iBedAvail_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.iDayCharge_s IS NULL OR b0.iDayCharge_s='' THEN 0
                   WHEN INSTR(b0.iDayCharge_s,'.')=0 THEN CAST(b0.iDayCharge_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.iSales_s IS NULL OR b0.iSales_s='' THEN 0
                   WHEN INSTR(b0.iSales_s,'.')=0 THEN CAST(b0.iSales_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iSales_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iSales_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iSales_s,'.',-1)))
              END, 0
            )
          AS DECIMAL(10,2)) AS iSubtotal,

          /* Ⅱ */
          CASE
            WHEN b0.iiPatientSat_s IS NULL OR b0.iiPatientSat_s='' THEN 0
            WHEN INSTR(b0.iiPatientSat_s,'.')=0 THEN CAST(b0.iiPatientSat_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1)))
          END AS iiPatientSatNum,
          CASE
            WHEN b0.iiProtectorSat_s IS NULL OR b0.iiProtectorSat_s='' THEN 0
            WHEN INSTR(b0.iiProtectorSat_s,'.')=0 THEN CAST(b0.iiProtectorSat_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1)))
          END AS iiProtectorSatNum,
          CASE
            WHEN b0.iiAccident_s IS NULL OR b0.iiAccident_s='' THEN 0
            WHEN INSTR(b0.iiAccident_s,'.')=0 THEN CAST(b0.iiAccident_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1)))
          END AS iiAccidentNum,
          CAST(
            COALESCE(
              CASE WHEN b0.iiPatientSat_s IS NULL OR b0.iiPatientSat_s='' THEN 0
                   WHEN INSTR(b0.iiPatientSat_s,'.')=0 THEN CAST(b0.iiPatientSat_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.iiProtectorSat_s IS NULL OR b0.iiProtectorSat_s='' THEN 0
                   WHEN INSTR(b0.iiProtectorSat_s,'.')=0 THEN CAST(b0.iiProtectorSat_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.iiAccident_s IS NULL OR b0.iiAccident_s='' THEN 0
                   WHEN INSTR(b0.iiAccident_s,'.')=0 THEN CAST(b0.iiAccident_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1)))
              END, 0
            )
          AS DECIMAL(10,2)) AS iiSubtotal,

          /* Ⅲ */
          CASE
            WHEN b0.iiiLink_s IS NULL OR b0.iiiLink_s='' THEN 0
            WHEN INSTR(b0.iiiLink_s,'.')=0 THEN CAST(b0.iiiLink_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1)))
          END AS iiiLinkNum,
          CASE
            WHEN b0.iiiPromotion_s IS NULL OR b0.iiiPromotion_s='' THEN 0
            WHEN INSTR(b0.iiiPromotion_s,'.')=0 THEN CAST(b0.iiiPromotion_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1)))
          END AS iiiPromotionNum,
          CASE
            WHEN b0.iiiQI_s IS NULL OR b0.iiiQI_s='' THEN 0
            WHEN INSTR(b0.iiiQI_s,'.')=0 THEN CAST(b0.iiiQI_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1)))
          END AS iiiQINum,
          CAST(
            COALESCE(
              CASE WHEN b0.iiiLink_s IS NULL OR b0.iiiLink_s='' THEN 0
                   WHEN INSTR(b0.iiiLink_s,'.')=0 THEN CAST(b0.iiiLink_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.iiiPromotion_s IS NULL OR b0.iiiPromotion_s='' THEN 0
                   WHEN INSTR(b0.iiiPromotion_s,'.')=0 THEN CAST(b0.iiiPromotion_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.iiiQI_s IS NULL OR b0.iiiQI_s='' THEN 0
                   WHEN INSTR(b0.iiiQI_s,'.')=0 THEN CAST(b0.iiiQI_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1)))
              END, 0
            )
          AS DECIMAL(10,2)) AS iiiSubtotal,

          /* Ⅳ */
          CASE
            WHEN b0.ivEdu_s IS NULL OR b0.ivEdu_s='' THEN 0
            WHEN INSTR(b0.ivEdu_s,'.')=0 THEN CAST(b0.ivEdu_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1)))
          END AS ivEduNum,
          CASE
            WHEN b0.ivVolunteer_s IS NULL OR b0.ivVolunteer_s='' THEN 0
            WHEN INSTR(b0.ivVolunteer_s,'.')=0 THEN CAST(b0.ivVolunteer_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1)))
          END AS ivVolunteerNum,
          CASE
            WHEN b0.ivDiscuss_s IS NULL OR b0.ivDiscuss_s='' THEN 0
            WHEN INSTR(b0.ivDiscuss_s,'.')=0 THEN CAST(b0.ivDiscuss_s AS DECIMAL(18,6))
            ELSE CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',1) AS DECIMAL(18,6))
                 + CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1) AS UNSIGNED)
                   / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1)))
          END AS ivDiscussNum,
          CAST(
            COALESCE(
              CASE WHEN b0.ivEdu_s IS NULL OR b0.ivEdu_s='' THEN 0
                   WHEN INSTR(b0.ivEdu_s,'.')=0 THEN CAST(b0.ivEdu_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.ivVolunteer_s IS NULL OR b0.ivVolunteer_s='' THEN 0
                   WHEN INSTR(b0.ivVolunteer_s,'.')=0 THEN CAST(b0.ivVolunteer_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1)))
              END, 0
            )
            + COALESCE(
              CASE WHEN b0.ivDiscuss_s IS NULL OR b0.ivDiscuss_s='' THEN 0
                   WHEN INSTR(b0.ivDiscuss_s,'.')=0 THEN CAST(b0.ivDiscuss_s AS DECIMAL(18,6))
                   ELSE CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1)))
              END, 0
            )
          AS DECIMAL(10,2)) AS ivSubtotal
        FROM b0
      ),

      /* -------------------------------------------------------------
       * b2: 총점(0~100) 및 분위 산출
       * ----------------------------------------------------------- */
      b2 AS (
        SELECT
          b1.*,
          CAST(
            COALESCE(b1.iSubtotal,  0) +
            COALESCE(b1.iiSubtotal, 0) +
            COALESCE(b1.iiiSubtotal,0) +
            COALESCE(b1.ivSubtotal, 0) +
            COALESCE(b1.vSubtotal,  0)
          AS DECIMAL(10,2)) AS total,
          NTILE(100) OVER (PARTITION BY b1.orgName  /* 병원 단위 분포 */
                           ORDER BY
                             CAST(
                               COALESCE(b1.iSubtotal,0)+COALESCE(b1.iiSubtotal,0)+
                               COALESCE(b1.iiiSubtotal,0)+COALESCE(b1.ivSubtotal,0)+
                               COALESCE(b1.vSubtotal,0)
                             AS DECIMAL(10,2)
                             ) DESC
                          ) AS p100
          /* 팀 기준으로 분포를 나누고 싶다면 위 PARTITION BY를
             PARTITION BY b1.orgName, b1.teamName 로 변경 */
        FROM b1
      )

      SELECT
        b2.*,
        CASE
          WHEN p100 <=  4 THEN 'A+'
          WHEN p100 <= 11 THEN 'A'
          WHEN p100 <= 23 THEN 'B+'
          WHEN p100 <= 40 THEN 'B'
          WHEN p100 <= 60 THEN 'C+'
          WHEN p100 <= 77 THEN 'C'
          WHEN p100 <= 89 THEN 'D+'
          WHEN p100 <= 96 THEN 'D'
          ELSE 'E'
        END AS evalGrade
      FROM b2
      ORDER BY
        /* 1) 첫 글자 그룹: 숫자(0) > 한글(1) > 영어(2) > 기타(3) */
        CASE
          WHEN ASCII(SUBSTRING(IFNULL(b2.deptName,''),1,1)) BETWEEN 48 AND 57 THEN 0
          WHEN ORD(SUBSTRING(IFNULL(b2.deptName,''),1,1)) BETWEEN 0xAC00 AND 0xD7A3 THEN 1
          WHEN ASCII(SUBSTRING(IFNULL(b2.deptName,''),1,1)) BETWEEN 65 AND 90 THEN 2
          WHEN ASCII(SUBSTRING(IFNULL(b2.deptName,''),1,1)) BETWEEN 97 AND 122 THEN 2
          ELSE 3
        END,
        /* 2) 숫자로 시작하면 숫자값 기준 정렬 */
        CASE WHEN IFNULL(b2.deptName,'') REGEXP '^[0-9]+' THEN CAST(b2.deptName AS UNSIGNED) ELSE NULL END,
        /* 3) 사전순 */
        b2.deptName COLLATE utf8mb4_0900_ai_ci,
        /* 4) 사번 */
        LPAD(b2.empNo, 12, '0')
      """)
  List<KpiSummaryRow> selectKpiForTeam(String orgName, String teamName, int year);

  @Select("""
      WITH es AS (
        SELECT s.target_id AS targetId,
               s.data_ev   AS dtype,
               ROUND(AVG(s.avg_score * 20), 1) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_ev IN ('B','D','E','F','G')
        GROUP BY s.target_id, s.data_ev
      ),
      role_map AS (
        SELECT u.id AS targetId,
               TRIM(t.team_name) AS teamName,
               CASE WHEN EXISTS (
                      SELECT 1
                      FROM personnel_evaluation.evaluation_submissions s2
                      WHERE s2.eval_year = #{year}
                        AND s2.is_active = 1
                        AND s2.del_yn = 'N'
                        AND s2.data_ev = 'F'
                        AND s2.target_id = u.id
                    ) THEN 'MANAGER' ELSE 'STAFF' END AS role
        FROM personnel_evaluation.users_${year} u
        JOIN personnel_evaluation.team t
          ON t.team_code = u.team_code AND t.eval_year = u.eval_year
        WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
      ),
      es_pivot AS (
        SELECT r.targetId,
               r.teamName,
               r.role,
               MAX(CASE WHEN e.dtype='B' THEN e.avg100 END) AS B_fromClinicToTeam100,
               MAX(CASE WHEN e.dtype='D' THEN e.avg100 END) AS D_inTeamToTeam100,
               MAX(CASE WHEN e.dtype='E' THEN e.avg100 END) AS E_bossToStaff100,
               MAX(CASE WHEN e.dtype='F' THEN e.avg100 END) AS F_staffToBoss100,
               MAX(CASE WHEN e.dtype='G' THEN e.avg100 END) AS G_staffToStaff100,
               MAX(CASE WHEN e.dtype='D' THEN e.avg100 END) AS baseD,
               MAX(CASE WHEN e.dtype='F' THEN e.avg100 END) AS baseF,
               MAX(CASE WHEN e.dtype='B' THEN e.avg100 END) AS baseB,
               CASE
                 WHEN r.teamName = '경혁팀' AND r.role = 'STAFF' THEN
                   (
                     COALESCE(MAX(CASE WHEN e.dtype='E' THEN e.avg100 END), 0) +
                     COALESCE(MAX(CASE WHEN e.dtype='G' THEN e.avg100 END), 0)
                   ) / NULLIF(
                     (CASE WHEN MAX(CASE WHEN e.dtype='E' THEN 1 END)=1 THEN 1 ELSE 0 END) +
                     (CASE WHEN MAX(CASE WHEN e.dtype='G' THEN 1 END)=1 THEN 1 ELSE 0 END)
                   ,0)
                 ELSE
                   MAX(CASE WHEN e.dtype='F' THEN e.avg100 END)
               END AS specialF_or_EG
        FROM role_map r
        LEFT JOIN es e ON e.targetId = r.targetId
        GROUP BY r.targetId, r.teamName, r.role
      ),
      v_choice AS (
        SELECT p.targetId,
               CASE
                 WHEN p.baseD IS NOT NULL THEN p.baseD
                 WHEN p.teamName <> '경혁팀' THEN p.G_staffToStaff100
                 ELSE NULL
               END AS preferD,
               CASE
                 WHEN p.specialF_or_EG IS NOT NULL THEN p.specialF_or_EG
                 WHEN p.baseF IS NOT NULL THEN p.baseF
                 ELSE p.E_bossToStaff100
               END AS preferF,
               CASE
                 WHEN p.baseB IS NOT NULL THEN p.baseB
                 ELSE p.G_staffToStaff100
               END AS preferB,
               p.B_fromClinicToTeam100, p.D_inTeamToTeam100, p.E_bossToStaff100,
               p.F_staffToBoss100, p.G_staffToStaff100
        FROM es_pivot p
      ),

      /* ---------- 분포 기반 등급 산출용: 전 직원(동일 연도) 점수 집합 ---------- */
      base_all AS (
        SELECT u.id AS userId,
               u.c_name AS orgName,
               /* 100점 만점 총점 계산식: 아래 t.totalCalc100과 동일식 */
               ROUND(
                 COALESCE( (CASE WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                                 THEN 10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)
                                 ELSE 0 END), 0)
                 + COALESCE( (CASE
                     WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                     WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                     WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                     WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                     ELSE 0.00 END), 0)
                 + COALESCE( (CASE WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                                   THEN CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10
                                   ELSE 0 END), 0)
                 + COALESCE( (COALESCE(vc.E_bossToStaff100,0)*0.35
                            + COALESCE(vc.G_staffToStaff100,0)*0.35), 0)
               , 1) AS totalCalc100
        FROM personnel_evaluation.users_${year} u
        LEFT JOIN personnel_evaluation.kpi_info_sub kis
          ON kis.kis02 = u.id
        LEFT JOIN v_choice vc
          ON vc.targetId = u.id
        WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
      ),
      ranked AS (
        SELECT userId,
               orgName,
               NTILE(100) OVER (PARTITION BY orgName ORDER BY totalCalc100 DESC) AS p100
        FROM base_all
      ),
      mapped AS (
        SELECT userId,
               CASE
                 WHEN p100 <=  4 THEN 'A+'
                 WHEN p100 <= 11 THEN 'A'
                 WHEN p100 <= 23 THEN 'B+'
                 WHEN p100 <= 40 THEN 'B'
                 WHEN p100 <= 60 THEN 'C+'
                 WHEN p100 <= 77 THEN 'C'
                 WHEN p100 <= 89 THEN 'D+'
                 WHEN p100 <= 96 THEN 'D'
                 ELSE 'E'
               END AS evalGrade
        FROM ranked
      )

      /* ---------- 최종: 기존 t 결과 + 분포 등급 조인 ---------- */
      SELECT
        t.*,
        m.evalGrade
      FROM (
        SELECT
          u.c_name                   AS orgName,
          COALESCE(sm.sub_name,'-')  AS deptName,
          u.id                       AS empId,
          u.name                     AS empName,
          u.position                 AS position,

          /* 원본 KPI */
          kis.kis03 AS total,
          kis.kis04 AS newInConn,  kis.kis05 AS funeralConn, kis.kis06 AS bothConn,
          kis.kis07 AS kpiI,       kis.kis08 AS scoreI,
          kis.kis10 AS volCnt,     kis.kis11 AS kpiII,       kis.kis12 AS scoreII,
          kis.kis14 AS eduRate,    kis.kis15 AS kpiIII,      kis.kis16 AS scoreIII,
          kis.kis09 AS noteI,      kis.kis13 AS noteII,      kis.kis17 AS noteIII,

          /* 다면평가 원시값 */
          vc.B_fromClinicToTeam100,
          vc.D_inTeamToTeam100,
          vc.E_bossToStaff100,
          vc.F_staffToBoss100,
          vc.G_staffToStaff100,

          /* 최종 선택값 */
          vc.preferD,
          vc.preferF,
          vc.preferB,

          /* 35점 환산/요약 */
          CAST(COALESCE(vc.E_bossToStaff100, 0) * 0.35 AS DECIMAL(10,2)) AS bossToStaff35,
          CAST(COALESCE(vc.G_staffToStaff100, 0) * 0.35 AS DECIMAL(10,2)) AS staffToStaff35,
          CAST(
            CASE
              WHEN vc.E_bossToStaff100 IS NOT NULL OR vc.G_staffToStaff100 IS NOT NULL
                THEN ROUND(COALESCE(vc.E_bossToStaff100,0)*0.35 + COALESCE(vc.G_staffToStaff100,0)*0.35, 1)
              ELSE NULL
            END
          AS DECIMAL(10,2)) AS evalSum70,

          /* V(가중 20) 상세 */
          CAST(COALESCE(vc.preferD, 0) * 0.10 AS DECIMAL(10,2)) AS vExperience,
          CAST(COALESCE(vc.preferF, 0) * 0.05 AS DECIMAL(10,2)) AS vBossToStaff,
          CAST(COALESCE(vc.preferB, 0) * 0.05 AS DECIMAL(10,2)) AS vStaffToStaff,
          CAST(
            COALESCE(vc.preferD, 0) * 0.10 +
            COALESCE(vc.preferF, 0) * 0.05 +
            COALESCE(vc.preferB, 0) * 0.05
          AS DECIMAL(10,2)) AS evalSum20,

          /* I */
          CAST(
            CASE WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                 THEN 10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)
                 ELSE 0 END AS DECIMAL(4,1)
          ) AS kpiICalc,
          CAST(
            CASE WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                 THEN ROUND((10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)) * 10)
                 ELSE 0 END AS UNSIGNED
          ) AS scoreICalc,

          /* II */
          CAST(
            CASE
              WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
              WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
              WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
              WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
              ELSE 0.00
            END AS DECIMAL(4,2)
          ) AS kpiIICalc,
          CAST(ROUND(
            CASE
              WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
              WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
              WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
              WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
              ELSE 0.00
            END * 10) AS UNSIGNED
          ) AS scoreIICalc,

          /* III (10점 환산) */
          CAST(
            CASE WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                 THEN ROUND(CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10, 2)
                 ELSE NULL END AS DECIMAL(4,2)
          ) AS kpiIIICalc,
          CAST(
            CASE WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                 THEN ROUND((CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10) * 10)
                 ELSE NULL END AS UNSIGNED
          ) AS scoreIIICalc,

          /* 최종 총점(0~40) */
          ROUND(
            COALESCE( (CASE WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                            THEN 10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)
                            ELSE 0 END), 0)
            + COALESCE( (CASE
                WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                ELSE 0.00 END), 0)
            + COALESCE( (CASE WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                              THEN CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10
                              ELSE 0 END), 0)
            + COALESCE( (COALESCE(vc.preferD,0) * 0.10
                       + COALESCE(vc.preferF,0) * 0.05
                       + COALESCE(vc.preferB,0) * 0.05), 0)
          , 1) AS totalCalc,

          /* 100점 만점 총점 */
          ROUND(
            COALESCE( (CASE WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                            THEN 10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)
                            ELSE 0 END), 0)
            + COALESCE( (CASE WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                              WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                              WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                              WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                              ELSE 0.00 END), 0)
            + COALESCE( (CASE WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                              THEN CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10
                              ELSE 0 END), 0)
            + COALESCE( (COALESCE(vc.E_bossToStaff100,0)*0.35
                       + COALESCE(vc.G_staffToStaff100,0)*0.35), 0)
          , 1) AS totalCalc100

        FROM personnel_evaluation.users_${year} u
        LEFT JOIN personnel_evaluation.sub_management sm
          ON sm.sub_code = u.sub_code AND sm.eval_year = u.eval_year
        LEFT JOIN personnel_evaluation.kpi_info_sub kis
          ON kis.kis02 = u.id
        LEFT JOIN v_choice vc
          ON vc.targetId = u.id
        WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
          AND u.id = #{userId}
      ) t
      LEFT JOIN mapped m ON m.userId = t.empId
      """)
  MyKpiRow selectMyKpi(@Param("userId") int userId, @Param("year") int year);

  @Select("""
      SELECT
        AVG(CASE
              WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                THEN 100 * (10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)) / 10
              ELSE 0
            END)                                           AS promoAvg100,
        AVG(100 * (
              CASE
                WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                ELSE 0.00
              END
            ) / 12 )                                       AS volAvg100,
        AVG(100 * (
              CASE
                WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                  THEN ROUND(CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10, 2)
                ELSE 0
              END
            ) / 10 )                                       AS eduAvg100,
        AVG(
            CASE
              WHEN p.bossToStaff100 IS NOT NULL OR p.staffToStaff100 IS NOT NULL
                THEN (COALESCE(p.bossToStaff100,0)*0.35 + COALESCE(p.staffToStaff100,0)*0.35)
              ELSE 0
            END
        ) / 70 * 100                                       AS multiAvg100
      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.kpi_info_sub kis
        ON kis.kis02 = u.id
      LEFT JOIN (
        /* 기존 es/es_pivot과 동일 로직으로 만든 p(targetId별 bossToStaff100, staffToStaff100) 뷰 */
        SELECT targetId,
               MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS bossToStaff100,
               MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staffToStaff100
        FROM (
          SELECT s.target_id AS targetId,
                 CASE WHEN s.data_ev='E' THEN 'BOSS'
                      WHEN s.data_ev='G' THEN 'STAFF' ELSE 'OTHER' END AS relBucket,
                 ROUND(AVG(s.avg_score*20),1) AS avg100
          FROM personnel_evaluation.evaluation_submissions s
          WHERE s.eval_year=#{year} AND s.is_active=1 AND s.del_yn='N' AND s.data_ev IN('E','G')
          GROUP BY s.target_id, relBucket
        ) es
        GROUP BY targetId
      ) p ON p.targetId = u.id
      WHERE u.eval_year=#{year}
        AND u.del_yn='N'
        AND u.sub_code = #{deptCode}
      """)
  KpiCohortAvg selectDeptRadarAvg(@Param("year") int year, @Param("deptCode") String deptCode);

  @Select("""
      /* 전체 직원 평균 레이더 (조직 전체, 선택적 orgName 필터) */
      WITH es AS (
        SELECT
          s.target_id AS targetId,
          CASE
            WHEN s.data_ev='E' THEN 'BOSS'   -- 부서장→부서원
            WHEN s.data_ev='G' THEN 'STAFF'  -- 부서원→부서원
            ELSE 'OTHER'
          END AS relBucket,
          ROUND(AVG(s.avg_score * 20), 1) AS avg100
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_ev IN ('E','G')
        GROUP BY s.target_id, relBucket
      ),
      es_pivot AS (
        SELECT
          targetId,
          MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS bossToStaff100,
          MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staffToStaff100
        FROM es
        GROUP BY targetId
      )
      SELECT
        /* I: 홍보공헌 (10 만점 → 100 환산) */
        AVG(
          CASE
            WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
              THEN 100 * (10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)) / 10
            ELSE 0
          END
        ) AS promoAvg100,

        /* II: 자원봉사 (12 만점 → 100 환산) */
        AVG(
          100 * (
            CASE
              WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
              WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
              WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
              WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
              ELSE 0.00
            END
          ) / 12
        ) AS volAvg100,

        /* III: 교육이수 (10 만점 → 100 환산) */
        AVG(
          100 * (
            CASE
              WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                THEN ROUND(CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10, 2)
              ELSE 0
            END
          ) / 10
        ) AS eduAvg100,

        /* V: 다면평가 (boss/staff 각 35점 합 → 70 만점, 100 환산) */
        AVG(
          CASE
            WHEN p.bossToStaff100 IS NOT NULL OR p.staffToStaff100 IS NOT NULL
              THEN (COALESCE(p.bossToStaff100,0)*0.35 + COALESCE(p.staffToStaff100,0)*0.35)
            ELSE 0
          END
        ) / 70 * 100 AS multiAvg100

      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.kpi_info_sub kis
        ON kis.kis02 = u.id
      LEFT JOIN es_pivot p
        ON p.targetId = u.id
      WHERE u.eval_year = #{year}
        AND u.del_yn = 'N'
        /* 병원(조직)별 평균을 보고 싶으면 orgName 전달, 전체면 '' 전달 */
        AND (#{orgName} = '' OR u.c_name = #{orgName})
        /* (선택) 기존 전사 공통 제외 조건을 유지하려면 아래 두 줄 유지/조정 */
        AND (u.sub_code IS NULL OR u.sub_code NOT LIKE 'A0%')      -- 진료부 A* 제외
        AND COALESCE(TRIM(u.team_code), '') <> 'GH_TEAM'          -- 경혁팀 제외
        AND NOT EXISTS (
          SELECT 1 FROM personnel_evaluation.user_roles_${year} r
          WHERE r.user_id = u.id AND r.eval_year = u.eval_year
            AND r.role IN ('sub_head','one_person_sub')
        )
      """)
  KpiCohortAvg selectOrgRadarAvg(@Param("year") int year, @Param("orgName") String orgName);

  @Select("""
      SELECT
        AVG(CASE
              WHEN CAST(kis.kis06 AS UNSIGNED) >= 1
                THEN 100 * (10 + 0.1 * (CAST(kis.kis06 AS UNSIGNED) - 1)) / 10
              ELSE 0
            END)                                           AS promoAvg100,
        AVG(100 * (
              CASE
                WHEN CAST(kis.kis10 AS UNSIGNED) >= 4 THEN 12.00
                WHEN CAST(kis.kis10 AS UNSIGNED) = 3 THEN 11.33
                WHEN CAST(kis.kis10 AS UNSIGNED) = 2 THEN 10.67
                WHEN CAST(kis.kis10 AS UNSIGNED) = 1 THEN 10.00
                ELSE 0.00
              END
            ) / 12 )                                       AS volAvg100,
        AVG(100 * (
              CASE
                WHEN kis.kis15 IS NOT NULL AND kis.kis15 <> ''
                  THEN ROUND(CAST(kis.kis15 AS DECIMAL(5,2)) / 15 * 10, 2)
                ELSE 0
              END
            ) / 10 )                                       AS eduAvg100,
        AVG(
            CASE
              WHEN p.bossToStaff100 IS NOT NULL OR p.staffToStaff100 IS NOT NULL
                THEN (COALESCE(p.bossToStaff100,0)*0.35 + COALESCE(p.staffToStaff100,0)*0.35)
              ELSE 0
            END
        ) / 70 * 100                                       AS multiAvg100
      FROM personnel_evaluation.users_${year} u
      JOIN personnel_evaluation.team t
        ON t.team_code=u.team_code AND t.eval_year=u.eval_year
      LEFT JOIN personnel_evaluation.kpi_info_sub kis
        ON kis.kis02 = u.id
      LEFT JOIN ( /* 동일 p */
        SELECT targetId,
               MAX(CASE WHEN relBucket='BOSS'  THEN avg100 END) AS bossToStaff100,
               MAX(CASE WHEN relBucket='STAFF' THEN avg100 END) AS staffToStaff100
        FROM (
          SELECT s.target_id AS targetId,
                 CASE WHEN s.data_ev='E' THEN 'BOSS'
                      WHEN s.data_ev='G' THEN 'STAFF' ELSE 'OTHER' END AS relBucket,
                 ROUND(AVG(s.avg_score*20),1) AS avg100
          FROM personnel_evaluation.evaluation_submissions s
          WHERE s.eval_year=#{year} AND s.is_active=1 AND s.del_yn='N' AND s.data_ev IN('E','G')
          GROUP BY s.target_id, relBucket
        ) es
        GROUP BY targetId
      ) p ON p.targetId = u.id
      WHERE u.eval_year=#{year}
        AND u.del_yn='N'
        AND TRIM(t.team_name) = TRIM(#{teamName})
      """)
  KpiCohortAvg selectTeamRadarAvg(@Param("year") int year, @Param("teamName") String teamName);

  @Select("""
      WITH
        params AS (
          SELECT
            COALESCE(NULLIF(#{orgName},  ''), NULL) AS pOrg,
            COALESCE(NULLIF(#{teamName}, ''), NULL) AS pTeam,
            #{year}  AS pYear,
            #{userId} AS pUserId
        ),
        es AS (
          SELECT s.target_id AS targetId, s.data_ev AS dtype, ROUND(AVG(s.avg_score*20),1) AS avg100
          FROM personnel_evaluation.evaluation_submissions s, params p
          WHERE s.eval_year = p.pYear
            AND s.is_active = 1 AND s.del_yn = 'N'
            AND s.data_ev IN ('B','D','E','F','G')
          GROUP BY s.target_id, s.data_ev
        ),
        role_map AS (
          SELECT
            u.id AS targetId,
            CASE WHEN EXISTS (
              SELECT 1 FROM personnel_evaluation.evaluation_submissions s2, params p
              WHERE s2.eval_year=p.pYear AND s2.is_active=1 AND s2.del_yn='N'
                AND s2.data_ev='F' AND s2.target_id=u.id
            ) THEN 'MANAGER' ELSE 'STAFF' END AS role,
            TRIM(t.team_name) AS teamName
          FROM personnel_evaluation.users_${year} u
          JOIN personnel_evaluation.team t
            ON t.team_code=u.team_code AND t.eval_year=u.eval_year
          JOIN params p
          WHERE u.eval_year=p.pYear AND u.del_yn='N'
        ),
        es_pivot AS (
          SELECT
            r.targetId, r.teamName, r.role,
            MAX(CASE WHEN e.dtype='B' THEN e.avg100 END) AS fromClinicToTeam100,
            MAX(CASE WHEN e.dtype='D' THEN e.avg100 END) AS inTeamToTeam100,
            CASE
              WHEN r.teamName='경혁팀' AND r.role='STAFF' THEN
                ( COALESCE(MAX(CASE WHEN e.dtype='E' THEN e.avg100 END),0)
                + COALESCE(MAX(CASE WHEN e.dtype='G' THEN e.avg100 END),0) )
                / NULLIF(
                    (CASE WHEN MAX(CASE WHEN e.dtype='E' THEN 1 END)=1 THEN 1 ELSE 0 END) +
                    (CASE WHEN MAX(CASE WHEN e.dtype='G' THEN 1 END)=1 THEN 1 ELSE 0 END), 0)
              ELSE MAX(CASE WHEN e.dtype='F' THEN e.avg100 END)
            END AS bossToStaff100,
            CASE WHEN r.teamName='경혁팀' AND r.role='STAFF' THEN NULL
                ELSE MAX(CASE WHEN e.dtype='G' THEN e.avg100 END) END AS staffToStaff100
          FROM role_map r
          LEFT JOIN es e ON e.targetId=r.targetId
          GROUP BY r.targetId, r.teamName, r.role
        ),

        /* === 한 명 대상 행(b0): 표에서 쓰는 텍스트/정규화 컬럼 모두 포함 === */
        b0 AS (
          SELECT
            u.c_name                  AS orgName,
            TRIM(t.team_name)         AS teamName,
            COALESCE(sm.sub_name,'-') AS deptName,
            u.id                      AS empNo,
            u.position                AS position,
            u.name                    AS name,

            /* I. 재무성과 - 표출용 텍스트 */
            REGEXP_REPLACE(COALESCE(kis.kcol09,''), '[,▲▼]', '') AS iBedAvailTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol16,''), '[,▲▼]', '') AS iDayChargeTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol22,''), '[,▲▼]', '') AS iSalesTxt,
            /* I. 재무성과 - 숫자 파싱용 문자열 */
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol09),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iBedAvail_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol16),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iDayCharge_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol22),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iSales_s,

            /* II. 고객서비스 - 표출용 텍스트 */
            REGEXP_REPLACE(COALESCE(kis.kcol26,''), '[,▲▼]', '') AS iiPatientSatTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol25,''), '[,▲▼]', '') AS iiProtectorSatTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol33,''), '[,▲▼]', '') AS iiAccidentTxt,
            '' AS iiMealTxt,  /* 필요시 실제 소스로 교체 */

            /* II. 고객서비스 - 숫자 파싱용 문자열 */
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol26),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iiPatientSat_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol25),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iiProtectorSat_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol33),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iiAccident_s,

            /* III. 프로세스혁신 - 표출용 텍스트 */
            REGEXP_REPLACE(COALESCE(kis.kcol46,''), '[,▲▼]', '') AS iiiLinkTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol50,''), '[,▲▼]', '') AS iiiPromotionTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol58,''), '[,▲▼]', '') AS iiiQITxt,
            /* III. 프로세스혁신 - 숫자 파싱용 문자열 */
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol46),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iiiLink_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol50),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iiiPromotion_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol58),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS iiiQI_s,

            /* IV. 학습성장 - 표출용 텍스트 */
            REGEXP_REPLACE(COALESCE(kis.kcol65,''), '[,%▲▼]', '') AS ivEduTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol73,''), '[,▲▼]',  '') AS ivVolunteerTxt,
            REGEXP_REPLACE(COALESCE(kis.kcol79,''), '[,▲▼]',  '') AS ivDiscussTxt,
            /* IV. 학습성장 - 숫자 파싱용 문자열 */
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol65),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS ivEdu_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol73),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS ivVolunteer_s,
            REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(kis.kcol79),'·','.'),'ㆍ','.'),'．','.'),'・','.'),'∙','.'),'•','.'),'⋅','.' ),
              '[^0-9.-]', ''
            ) AS ivDiscuss_s,

            /* V. 다면평가(가중) */
            CAST(COALESCE(pvt.inTeamToTeam100,     0) * 0.10 AS DECIMAL(10,2)) AS vExperience,
            CAST(COALESCE(pvt.bossToStaff100,      0) * 0.05 AS DECIMAL(10,2)) AS vBossToStaff,
            CAST(COALESCE(pvt.fromClinicToTeam100, 0) * 0.05 AS DECIMAL(10,2)) AS vStaffToStaff

          FROM personnel_evaluation.users_${year} u
          JOIN personnel_evaluation.team t
            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
          LEFT JOIN personnel_evaluation.sub_management sm
            ON sm.sub_code = u.sub_code AND sm.eval_year = u.eval_year
          LEFT JOIN personnel_evaluation.kpi_info kis
            ON kis.kcol02 = u.id
          AND kis.kcol01 = COALESCE((SELECT pOrg FROM params), kis.kcol01)
          LEFT JOIN es_pivot pvt
            ON pvt.targetId = u.id
          JOIN params p
          WHERE u.eval_year = p.pYear
            AND u.del_yn   = 'N'
            AND u.id       = p.pUserId
            AND u.c_name   = COALESCE(p.pOrg,  u.c_name)
            AND TRIM(t.team_name) = COALESCE(TRIM(p.pTeam), TRIM(t.team_name))
        ),

                /* === 숫자 소계(b1): I~IV은 일단 0으로(실제 산식 있으면 여기에 대체) === */
                b1 AS (
          SELECT
            b0.*,

            /* I. 재무성과 */
            /* 문자열→숫자 */
            CASE
              WHEN b0.iBedAvail_s IS NULL OR b0.iBedAvail_s='' THEN 0
              WHEN INSTR(b0.iBedAvail_s,'.')=0 THEN CAST(b0.iBedAvail_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1)))
            END AS iBedAvailNum,
            CASE
              WHEN b0.iDayCharge_s IS NULL OR b0.iDayCharge_s='' THEN 0
              WHEN INSTR(b0.iDayCharge_s,'.')=0 THEN CAST(b0.iDayCharge_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1)))
            END AS iDayChargeNum,
            CASE
              WHEN b0.iSales_s IS NULL OR b0.iSales_s='' THEN 0
              WHEN INSTR(b0.iSales_s,'.')=0 THEN CAST(b0.iSales_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iSales_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iSales_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iSales_s,'.',-1)))
            END AS iSalesNum,

            CAST(
              COALESCE(
                CASE
                  WHEN b0.iBedAvail_s IS NULL OR b0.iBedAvail_s='' THEN 0
                  WHEN INSTR(b0.iBedAvail_s,'.')=0 THEN CAST(b0.iBedAvail_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iBedAvail_s,'.',-1)))
                END, 0
              )
              +
              COALESCE(
                CASE
                  WHEN b0.iDayCharge_s IS NULL OR b0.iDayCharge_s='' THEN 0
                  WHEN INSTR(b0.iDayCharge_s,'.')=0 THEN CAST(b0.iDayCharge_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iDayCharge_s,'.',-1)))
                END, 0
              )
              +
              COALESCE(
                CASE
                  WHEN b0.iSales_s IS NULL OR b0.iSales_s='' THEN 0
                  WHEN INSTR(b0.iSales_s,'.')=0 THEN CAST(b0.iSales_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iSales_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iSales_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iSales_s,'.',-1)))
                END, 0
              )
            AS DECIMAL(10,2)) AS iSubtotal,

            /* II. 고객서비스 */
            CASE
              WHEN b0.iiPatientSat_s IS NULL OR b0.iiPatientSat_s='' THEN 0
              WHEN INSTR(b0.iiPatientSat_s,'.')=0 THEN CAST(b0.iiPatientSat_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1)))
            END AS iiPatientSatNum,
            CASE
              WHEN b0.iiProtectorSat_s IS NULL OR b0.iiProtectorSat_s='' THEN 0
              WHEN INSTR(b0.iiProtectorSat_s,'.')=0 THEN CAST(b0.iiProtectorSat_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1)))
            END AS iiProtectorSatNum,
            CASE
              WHEN b0.iiAccident_s IS NULL OR b0.iiAccident_s='' THEN 0
              WHEN INSTR(b0.iiAccident_s,'.')=0 THEN CAST(b0.iiAccident_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1)))
            END AS iiAccidentNum,

            CAST(
              COALESCE(
                CASE
                  WHEN b0.iiPatientSat_s IS NULL OR b0.iiPatientSat_s='' THEN 0
                  WHEN INSTR(b0.iiPatientSat_s,'.')=0 THEN CAST(b0.iiPatientSat_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiPatientSat_s,'.',-1)))
                END, 0
              ) +
              COALESCE(
                CASE
                  WHEN b0.iiProtectorSat_s IS NULL OR b0.iiProtectorSat_s='' THEN 0
                  WHEN INSTR(b0.iiProtectorSat_s,'.')=0 THEN CAST(b0.iiProtectorSat_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiProtectorSat_s,'.',-1)))
                END, 0
              ) +
              COALESCE(
                CASE
                  WHEN b0.iiAccident_s IS NULL OR b0.iiAccident_s='' THEN 0
                  WHEN INSTR(b0.iiAccident_s,'.')=0 THEN CAST(b0.iiAccident_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiAccident_s,'.',-1)))
                END, 0
              )
            AS DECIMAL(10,2)) AS iiSubtotal,

            /* III. 프로세스혁신 */
            CASE
              WHEN b0.iiiLink_s IS NULL OR b0.iiiLink_s='' THEN 0
              WHEN INSTR(b0.iiiLink_s,'.')=0 THEN CAST(b0.iiiLink_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1)))
            END AS iiiLinkNum,
            CASE
              WHEN b0.iiiPromotion_s IS NULL OR b0.iiiPromotion_s='' THEN 0
              WHEN INSTR(b0.iiiPromotion_s,'.')=0 THEN CAST(b0.iiiPromotion_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1)))
            END AS iiiPromotionNum,
            CASE
              WHEN b0.iiiQI_s IS NULL OR b0.iiiQI_s='' THEN 0
              WHEN INSTR(b0.iiiQI_s,'.')=0 THEN CAST(b0.iiiQI_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1)))
            END AS iiiQINum,

            CAST(
              COALESCE(
                CASE
                  WHEN b0.iiiLink_s IS NULL OR b0.iiiLink_s='' THEN 0
                  WHEN INSTR(b0.iiiLink_s,'.')=0 THEN CAST(b0.iiiLink_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiLink_s,'.',-1)))
                END, 0
              ) +
              COALESCE(
                CASE
                  WHEN b0.iiiPromotion_s IS NULL OR b0.iiiPromotion_s='' THEN 0
                  WHEN INSTR(b0.iiiPromotion_s,'.')=0 THEN CAST(b0.iiiPromotion_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiPromotion_s,'.',-1)))
                END, 0
              ) +
              COALESCE(
                CASE
                  WHEN b0.iiiQI_s IS NULL OR b0.iiiQI_s='' THEN 0
                  WHEN INSTR(b0.iiiQI_s,'.')=0 THEN CAST(b0.iiiQI_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.iiiQI_s,'.',-1)))
                END, 0
              )
            AS DECIMAL(10,2)) AS iiiSubtotal,

            /* IV. 학습성장 */
            CASE
              WHEN b0.ivEdu_s IS NULL OR b0.ivEdu_s='' THEN 0
              WHEN INSTR(b0.ivEdu_s,'.')=0 THEN CAST(b0.ivEdu_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1)))
            END AS ivEduNum,
            CASE
              WHEN b0.ivVolunteer_s IS NULL OR b0.ivVolunteer_s='' THEN 0
              WHEN INSTR(b0.ivVolunteer_s,'.')=0 THEN CAST(b0.ivVolunteer_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1)))
            END AS ivVolunteerNum,
            CASE
              WHEN b0.ivDiscuss_s IS NULL OR b0.ivDiscuss_s='' THEN 0
              WHEN INSTR(b0.ivDiscuss_s,'.')=0 THEN CAST(b0.ivDiscuss_s AS DECIMAL(18,6))
              ELSE CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',1) AS DECIMAL(18,6))
                  + CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1) AS UNSIGNED)
                    / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1)))
            END AS ivDiscussNum,

            CAST(
              COALESCE(
                CASE
                  WHEN b0.ivEdu_s IS NULL OR b0.ivEdu_s='' THEN 0
                  WHEN INSTR(b0.ivEdu_s,'.')=0 THEN CAST(b0.ivEdu_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivEdu_s,'.',-1)))
                END, 0
              ) +
              COALESCE(
                CASE
                  WHEN b0.ivVolunteer_s IS NULL OR b0.ivVolunteer_s='' THEN 0
                  WHEN INSTR(b0.ivVolunteer_s,'.')=0 THEN CAST(b0.ivVolunteer_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivVolunteer_s,'.',-1)))
                END, 0
              ) +
              COALESCE(
                CASE
                  WHEN b0.ivDiscuss_s IS NULL OR b0.ivDiscuss_s='' THEN 0
                  WHEN INSTR(b0.ivDiscuss_s,'.')=0 THEN CAST(b0.ivDiscuss_s AS DECIMAL(18,6))
                  ELSE CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',1) AS DECIMAL(18,6))
                      + CAST(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1) AS UNSIGNED)
                        / POW(10, CHAR_LENGTH(SUBSTRING_INDEX(b0.ivDiscuss_s,'.',-1)))
                END, 0
              )
            AS DECIMAL(10,2)) AS ivSubtotal,

            /* V. 다면평가 소계 */
            CAST(COALESCE(b0.vExperience,0) + COALESCE(b0.vBossToStaff,0) + COALESCE(b0.vStaffToStaff,0)
              AS DECIMAL(10,2)) AS vSubtotal

          FROM b0
        ),

        /* === 총점 + 백분위 === */
        b2 AS (
          SELECT
            x.*,
            NTILE(100) OVER (PARTITION BY orgName, teamName ORDER BY total DESC) AS p100
          FROM (
            SELECT
              b1.*,
              CAST(
                COALESCE(iSubtotal,0) + COALESCE(iiSubtotal,0) +
                COALESCE(iiiSubtotal,0)+ COALESCE(ivSubtotal,0) +
                COALESCE(vSubtotal,0)
              AS DECIMAL(10,2)) AS total
            FROM b1
          ) x
        )
        SELECT
          b2.*,
          CASE
            WHEN p100 <=  4 THEN 'A+'
            WHEN p100 <= 11 THEN 'A'
            WHEN p100 <= 23 THEN 'B+'
            WHEN p100 <= 40 THEN 'B'
            WHEN p100 <= 60 THEN 'C+'
            WHEN p100 <= 77 THEN 'C'
            WHEN p100 <= 89 THEN 'D+'
            WHEN p100 <= 96 THEN 'D'
            ELSE 'E'
          END AS evalGrade
        FROM b2
      """)
  KpiSummaryRow selectMyKpiForTeam(@Param("orgName") String orgName,
      @Param("teamName") String teamName,
      @Param("year") int year,
      @Param("userId") int userId);

  @Select("""
      WITH
        /* ───────────────── 다면평가 원천 점수(raw) ───────────────── */
        raw_multi AS (
          SELECT
              s.eval_year,
              s.target_id    AS targetId,
              s.evaluator_id AS evaluatorId,
              s.data_ev      AS dataEv,      -- B/D/E/F/G
              s.avg_score
          FROM personnel_evaluation.evaluation_submissions s
          WHERE s.eval_year = #{year}
            AND s.is_active = 1
            AND s.del_yn    = 'N'
            AND s.data_ev   IN ('B','D','E','F','G')
            AND s.target_id = #{targetId}
        ),

        /* 5점 척도(avg_score)를 10점 척도로 통일 */
        scored_multi AS (
          SELECT
              r.eval_year,
              r.targetId,
              r.evaluatorId,
              r.dataEv,
              CASE
                WHEN r.avg_score IS NULL THEN NULL
                ELSE r.avg_score * 2
              END AS score10          -- 0~10 점
          FROM raw_multi r
        ),

        /* 평가자 정보(부서코드) 붙이기: A0% → 진료부 */
        joined_multi AS (
          SELECT
              sm.*,
              eu.sub_code AS evaluatorSubCode
          FROM scored_multi sm
          JOIN personnel_evaluation.users_${year} eu
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
      JOIN personnel_evaluation.users_${year} u
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
  List<MyKpiMultiRow> selectKyMultiDetailFromEs(
      @Param("orgName") String orgName,
      @Param("teamName") String teamName,
      @Param("year") int year,
      @Param("targetId") int targetId);

}
