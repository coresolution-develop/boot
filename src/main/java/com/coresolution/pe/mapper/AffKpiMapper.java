package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.CombinedRow;
import com.coresolution.pe.entity.EvalSummaryRow;

@Mapper
public interface AffKpiMapper {

  @Select("""
      SELECT DISTINCT u.c_name
      FROM personnel_evaluation_aff.users_${year} u
      WHERE u.eval_year = #{year}
      ORDER BY u.c_name
      """)
  List<String> selectOrgList(int year);

  @Select("""
        WITH es AS (
          SELECT
            s.target_id AS targetId,
            CASE
              WHEN s.data_ev='C' AND s.data_type='AC' THEN 'STAFF_TO_BOSS'   -- 부서원 -> 부서장
              WHEN s.data_ev='A' AND s.data_type='AC' THEN 'BOSS_TO_BOSS'    -- 부서장 -> 부서장
              WHEN s.data_ev='B' AND s.data_type IN ('AD','AE') THEN 'BOSS_TO_STAFF'  -- 부서장 -> 부서원
              WHEN s.data_ev='D' AND s.data_type IN ('AD','AE') THEN 'STAFF_TO_STAFF' -- 부서원 -> 부서원
              ELSE NULL
            END AS bucket,
            ROUND(AVG(s.total_score), 2) AS avg100  -- ✅ 이미 0~100 총점이므로 total_score 평균이 가장 안전
          FROM personnel_evaluation_aff.evaluation_submissions s
          WHERE s.eval_year = #{year}
            AND s.is_active = 1
            AND s.del_yn = 'N'
            AND (
                 (s.data_ev='C' AND s.data_type='AC')
              OR (s.data_ev='A' AND s.data_type='AC')
              OR (s.data_ev='B' AND s.data_type IN ('AD','AE'))
              OR (s.data_ev='D' AND s.data_type IN ('AD','AE'))
            )
          GROUP BY s.target_id, s.data_ev, s.data_type
        ),
        pivot AS (
          SELECT
            targetId,
            MAX(CASE WHEN bucket='STAFF_TO_BOSS'  THEN avg100 END) AS staffToBoss100,
            MAX(CASE WHEN bucket='BOSS_TO_BOSS'   THEN avg100 END) AS bossToBoss100,
            MAX(CASE WHEN bucket='BOSS_TO_STAFF'  THEN avg100 END) AS bossToStaff100,
            MAX(CASE WHEN bucket='STAFF_TO_STAFF' THEN avg100 END) AS staffToStaff100
          FROM es
          WHERE bucket IS NOT NULL
          GROUP BY targetId
        )
        SELECT
          u.c_name                   AS orgName,
          u.c_name2                   AS orgName2,
          COALESCE(sm.sub_name, '-') AS deptName,
          u.id                       AS empId,
          u.name                     AS empName,
          u.position                 AS position,

          p.staffToBoss100,
          p.bossToBoss100,
          p.bossToStaff100,
          p.staffToStaff100,

          -- ✅ 총점: 존재하는(Null 아닌) 항목 개수로 나눈 평균
          ROUND(
            (
              COALESCE(p.staffToBoss100, 0) +
              COALESCE(p.bossToBoss100,  0) +
              COALESCE(p.bossToStaff100, 0) +
              COALESCE(p.staffToStaff100,0)
            )
            /
            NULLIF(
              (CASE WHEN p.staffToBoss100  IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN p.bossToBoss100   IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN p.bossToStaff100  IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN p.staffToStaff100 IS NULL THEN 0 ELSE 1 END),
              0
            ),
            2
          ) AS totalAvg100

        FROM personnel_evaluation_aff.users_${year} u
        LEFT JOIN personnel_evaluation_aff.sub_management sm
          ON sm.sub_code = u.sub_code
         AND sm.eval_year = u.eval_year
        LEFT JOIN pivot p
          ON p.targetId = u.id
        WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
          AND (#{orgName} = '' OR u.c_name = #{orgName})
          AND (#{op} IS NULL OR #{op} = '' OR u.position = #{op})
        ORDER BY sm.sub_name COLLATE utf8mb4_0900_ai_ci, LPAD(u.id, 12, '0')
      """)
  List<EvalSummaryRow> selectEvalSummaryByOrgYear(
      @Param("orgName") String orgName,
      @Param("year") int year,
      @Param("op") String op);

  @Select("""
        WITH es AS (
          SELECT
            s.target_id AS targetId,
            CASE
              WHEN s.data_ev='C' AND s.data_type='AC' THEN 'STAFF_TO_BOSS'
              WHEN s.data_ev='A' AND s.data_type='AC' THEN 'BOSS_TO_BOSS'
              WHEN s.data_ev='B' AND s.data_type IN ('AD','AE') THEN 'BOSS_TO_STAFF'
              WHEN s.data_ev='D' AND s.data_type IN ('AD','AE') THEN 'STAFF_TO_STAFF'
              ELSE NULL
            END AS bucket,
            ROUND(AVG(s.total_score), 2) AS avg100
          FROM personnel_evaluation_aff.evaluation_submissions s
          WHERE s.eval_year = #{year}
            AND s.target_id = #{empId}
            AND s.is_active = 1
            AND s.del_yn = 'N'
            AND (
                 (s.data_ev='C' AND s.data_type='AC')
              OR (s.data_ev='A' AND s.data_type='AC')
              OR (s.data_ev='B' AND s.data_type IN ('AD','AE'))
              OR (s.data_ev='D' AND s.data_type IN ('AD','AE'))
            )
          GROUP BY s.target_id, s.data_ev, s.data_type
        ),
        p AS (
          SELECT
            MAX(CASE WHEN bucket='STAFF_TO_BOSS'  THEN avg100 END) AS staffToBoss100,
            MAX(CASE WHEN bucket='BOSS_TO_BOSS'   THEN avg100 END) AS bossToBoss100,
            MAX(CASE WHEN bucket='BOSS_TO_STAFF'  THEN avg100 END) AS bossToStaff100,
            MAX(CASE WHEN bucket='STAFF_TO_STAFF' THEN avg100 END) AS staffToStaff100
          FROM es
          WHERE bucket IS NOT NULL
        )
        SELECT
          staffToBoss100,
          bossToBoss100,
          bossToStaff100,
          staffToStaff100,

          ROUND(
            (
              COALESCE(staffToBoss100, 0) +
              COALESCE(bossToBoss100,  0) +
              COALESCE(bossToStaff100, 0) +
              COALESCE(staffToStaff100,0)
            )
            /
            NULLIF(
              (CASE WHEN staffToBoss100  IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN bossToBoss100   IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN bossToStaff100  IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN staffToStaff100 IS NULL THEN 0 ELSE 1 END),
              0
            ),
            2
          ) AS totalAvg100
        FROM p
      """)
  Map<String, Object> selectMyEvalSummary(@Param("empId") String empId, @Param("year") int year);

  @Select("""
        WITH es AS (
          SELECT
            s.target_id AS targetId,
            CASE
              WHEN s.data_ev='C' AND s.data_type='AC' THEN 'STAFF_TO_BOSS'
              WHEN s.data_ev='A' AND s.data_type='AC' THEN 'BOSS_TO_BOSS'
              WHEN s.data_ev='B' AND s.data_type IN ('AD','AE') THEN 'BOSS_TO_STAFF'
              WHEN s.data_ev='D' AND s.data_type IN ('AD','AE') THEN 'STAFF_TO_STAFF'
              ELSE NULL
            END AS bucket,
            ROUND(AVG(s.total_score), 2) AS avg100
          FROM personnel_evaluation_aff.evaluation_submissions s
          WHERE s.eval_year = #{year}
            AND s.target_id = #{empId}
            AND s.is_active = 1
            AND s.del_yn='N'
            AND (
                 (s.data_ev='C' AND s.data_type='AC')
              OR (s.data_ev='A' AND s.data_type='AC')
              OR (s.data_ev='B' AND s.data_type IN ('AD','AE'))
              OR (s.data_ev='D' AND s.data_type IN ('AD','AE'))
            )
          GROUP BY s.target_id, s.data_ev, s.data_type
        ),
        p AS (
          SELECT
            MAX(CASE WHEN bucket='STAFF_TO_BOSS'  THEN avg100 END) AS staffToBoss100,
            MAX(CASE WHEN bucket='BOSS_TO_BOSS'   THEN avg100 END) AS bossToBoss100,
            MAX(CASE WHEN bucket='BOSS_TO_STAFF'  THEN avg100 END) AS bossToStaff100,
            MAX(CASE WHEN bucket='STAFF_TO_STAFF' THEN avg100 END) AS staffToStaff100
          FROM es
          WHERE bucket IS NOT NULL
        )
        SELECT
          staffToBoss100,
          bossToBoss100,
          bossToStaff100,
          staffToStaff100,

          ROUND(
            (
              COALESCE(staffToBoss100, 0) +
              COALESCE(bossToBoss100,  0) +
              COALESCE(bossToStaff100, 0) +
              COALESCE(staffToStaff100,0)
            )
            /
            NULLIF(
              (CASE WHEN staffToBoss100  IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN bossToBoss100   IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN bossToStaff100  IS NULL THEN 0 ELSE 1 END) +
              (CASE WHEN staffToStaff100 IS NULL THEN 0 ELSE 1 END),
              0
            ),
            2
          ) AS totalAvg100
        FROM p
      """)
  Map<String, Object> selectEvalSummaryForEmp(@Param("empId") String empId, @Param("year") int year);

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
        FROM personnel_evaluation_aff.evaluation_submissions s
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
          FROM personnel_evaluation_aff.users_${year} u
          LEFT JOIN personnel_evaluation_aff.sub_management sm
            ON sm.sub_code = u.sub_code AND sm.eval_year = u.eval_year
          LEFT JOIN personnel_evaluation_aff.kpi_info_sub kis
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
              SELECT 1 FROM personnel_evaluation_aff.user_roles_${year} r
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

}
