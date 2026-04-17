package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.OrgMemberProgressRow;
import com.coresolution.pe.entity.OrgProgressRow;
import com.coresolution.pe.entity.PendingPairRow;

@Mapper
public interface AffAdminProgressByOrgMapper {
  @Select("""
      <script>
      WITH
      /* 1) 올해 기준 '직원이 있는' 모든 기관 목록 */
      orgs AS (
        SELECT DISTINCT u.c_name AS org_name
        FROM personnel_evaluation_aff.users_${year} u
        WHERE u.eval_year = #{year}
          AND u.del_yn    = 'N'
          <if test="search != null and search != ''">
            AND u.c_name LIKE CONCAT('%', #{search}, '%')
          </if>
      ),
      /* 2) 기본/커스텀 타깃 페어 (평가자, 피평가자, ev, type) */
      base_targets AS (
        SELECT t.eval_year, t.user_id AS evaluator_id, t.target_id, t.data_ev, t.data_type
        FROM personnel_evaluation_aff.admin_default_targets t
        WHERE t.eval_year = #{year}
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR t.data_ev = #{ev})
        UNION ALL
        SELECT c.eval_year, c.user_id AS evaluator_id, c.target_id, c.data_ev, c.data_type
        FROM personnel_evaluation_aff.admin_custom_targets c
        WHERE c.eval_year = #{year}
          AND c.is_active = 1
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR c.data_ev = #{ev})
      ),
      dedup_targets AS (
        SELECT eval_year, evaluator_id, target_id, data_ev, data_type
        FROM base_targets
        GROUP BY eval_year, evaluator_id, target_id, data_ev, data_type
      ),
      /* 3) '평가자' 기준으로 기관명 붙이기 */
      pairs_with_org AS (
        SELECT d.eval_year,
               d.evaluator_id,
               d.target_id,
               d.data_ev,
               d.data_type,
               u.c_name AS org_name
        FROM dedup_targets d
        JOIN personnel_evaluation_aff.users_${year} u
          ON u.id        = d.evaluator_id   -- ★ 평가자 소속 기관
         AND u.eval_year = #{year}
         AND u.del_yn    = 'N'
      ),
      /* 4) 완료된 제출 모음 */
      submissions_agg AS (
        SELECT
          s.eval_year,
          s.evaluator_id,
          s.target_id,
          s.data_ev,
          s.data_type,
          1 AS is_completed,
          MAX(s.updated_at) AS last_updated_at
        FROM personnel_evaluation_aff.evaluation_submissions s
        JOIN dedup_targets d
          ON d.eval_year    = s.eval_year
         AND d.evaluator_id = s.evaluator_id
         AND d.target_id    = s.target_id
         AND d.data_ev      = s.data_ev
         AND d.data_type    = s.data_type
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn    = 'N'
        GROUP BY
          s.eval_year,
          s.evaluator_id,
          s.target_id,
          s.data_ev,
          s.data_type
      ),
      /* 5) 페어 + 완료여부 합치기 */
      pairs_join_submit AS (
        SELECT
          p.org_name,
          p.eval_year,
          p.evaluator_id,
          p.target_id,
          p.data_ev,
          p.data_type,
          COALESCE(sa.is_completed, 0) AS is_completed,
          sa.last_updated_at
        FROM pairs_with_org p
        LEFT JOIN submissions_agg sa
          ON sa.eval_year    = p.eval_year
         AND sa.evaluator_id = p.evaluator_id
         AND sa.target_id    = p.target_id
         AND sa.data_ev      = p.data_ev
         AND sa.data_type    = p.data_type
      )
      /* 6) 기관 기준 집계 (그 기관에 소속된 평가자 기준) */
      SELECT
        o.org_name AS orgName,
        COUNT(p.org_name)                                         AS totalPairs,
        COALESCE(SUM(p.is_completed), 0)                          AS completedPairs,
        (COUNT(p.org_name) - COALESCE(SUM(p.is_completed), 0))    AS pendingPairs,
        CASE
          WHEN COUNT(p.org_name) = 0 THEN 0.0
          ELSE ROUND((COALESCE(SUM(p.is_completed), 0) * 100.0 / COUNT(p.org_name)), 1)
        END                                                       AS progress,
        MAX(p.last_updated_at)                                    AS updatedAt
      FROM orgs o
      LEFT JOIN pairs_join_submit p
        ON p.org_name = o.org_name
      GROUP BY o.org_name
      ORDER BY
        <choose>
          <when test="sort == 'name_asc'">      o.org_name ASC </when>
          <when test="sort == 'name_desc'">     o.org_name DESC </when>
          <when test="sort == 'progress_asc'">  progress ASC,  o.org_name ASC </when>
          <otherwise>                           progress DESC, o.org_name ASC </otherwise>
        </choose>
      LIMIT #{size} OFFSET #{offset}
      </script>
      """)
  List<OrgProgressRow> selectOrgProgressPairs(
      @Param("year") int year,
      @Param("ev") String ev,
      @Param("search") String search,
      @Param("sort") String sort,
      @Param("size") int size,
      @Param("offset") int offset,
      @Param("usersTable") String usersTable);

  @Select("""
      <script>
      WITH emp AS (
        SELECT
          u.id,
          u.name,
          u.position,
          u.team_code,
          u.sub_code,
          COALESCE(sm.sub_name, '미지정') AS dept_name,
          u.c_name
        FROM ${usersTable} u
        LEFT JOIN personnel_evaluation_aff.sub_management sm
          ON sm.eval_year = u.eval_year
         AND sm.sub_code  = u.sub_code
        WHERE u.eval_year = #{year}
          AND u.del_yn    = 'N'
          AND u.c_name    = #{org}
          <if test="search != null and search != ''">
            AND (u.id LIKE CONCAT('%', #{search}, '%') OR u.name LIKE CONCAT('%', #{search}, '%'))
          </if>
      ),
      /* 평가자 기준 기본/커스텀 타깃 */
      base_targets AS (
        SELECT t.user_id AS evaluator_id, t.target_id, t.data_ev, t.data_type
        FROM personnel_evaluation_aff.admin_default_targets t
        JOIN emp e ON e.id = t.user_id           -- ★ 평가자 = emp
        WHERE t.eval_year = #{year}
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR t.data_ev = #{ev})
        UNION ALL
        SELECT c.user_id AS evaluator_id, c.target_id, c.data_ev, c.data_type
        FROM personnel_evaluation_aff.admin_custom_targets c
        JOIN emp e ON e.id = c.user_id           -- ★ 평가자 = emp
        WHERE c.eval_year = #{year}
          AND c.is_active = 1
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR c.data_ev = #{ev})
      ),
      dedup_targets AS (
        SELECT evaluator_id, target_id, data_ev, data_type
        FROM base_targets
        GROUP BY evaluator_id, target_id, data_ev, data_type
      ),
      /* 평가자가 실제로 제출한 페어 */
      completed_pairs AS (
        SELECT s.evaluator_id, s.target_id, s.data_ev, s.data_type,
               MAX(s.updated_at) AS last_updated_at
        FROM personnel_evaluation_aff.evaluation_submissions s
        JOIN emp e ON e.id = s.evaluator_id      -- ★ 평가자 기준
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn    = 'N'
          <if test="ev != null and ev != '' and ev != 'ALL'">
            AND s.data_ev = #{ev}
          </if>
        GROUP BY s.evaluator_id, s.target_id, s.data_ev, s.data_type
      ),
      agg AS (
        SELECT
          e.id        AS targetId,   -- 실제로는 '평가자 ID'
          e.name      AS targetName, -- 평가자 이름
          e.position  AS position,
          e.team_code AS teamCode,
          e.sub_code  AS subCode,
          e.dept_name AS deptName,
          /* 이 평가자가 평가해야 하는 (타깃, ev, type) 페어 수 */
          COUNT(DISTINCT CONCAT(d.target_id,'|',d.data_ev,'|',d.data_type)) AS needPairs,
          /* 이 평가자가 실제로 완료한 (타깃, ev, type) 페어 수 */
          COUNT(DISTINCT CONCAT(cp.target_id,'|',cp.data_ev,'|',cp.data_type)) AS donePairs,
          MAX(cp.last_updated_at) AS lastUpdatedAt
        FROM emp e
        LEFT JOIN dedup_targets d
          ON d.evaluator_id = e.id               -- ★ 평가자 = e.id
        LEFT JOIN completed_pairs cp
          ON cp.evaluator_id = e.id
         AND cp.target_id    = d.target_id
         AND cp.data_ev      = d.data_ev
         AND cp.data_type    = d.data_type
        GROUP BY e.id, e.name, e.position, e.team_code, e.sub_code, e.dept_name
      )
      SELECT
        targetId, targetName, position, teamCode, subCode, deptName,
        needPairs, donePairs,
        (needPairs - donePairs) AS pendingPairs,
        ROUND(100 * donePairs / NULLIF(needPairs, 0), 1) AS progress,
        lastUpdatedAt AS updatedAt
      FROM agg
      ORDER BY
        <choose>
          <when test="sort == 'dept_asc'"> deptName ASC, teamCode ASC, targetName ASC </when>
          <when test="sort == 'dept_desc'"> deptName DESC, teamCode ASC, targetName ASC </when>
          <when test="sort == 'team_asc'"> teamCode ASC, targetName ASC </when>
          <when test="sort == 'name_asc'"> targetName ASC </when>
          <when test="sort == 'name_desc'"> targetName DESC </when>
          <when test="sort == 'progress_asc'"> progress ASC, targetName ASC </when>
          <otherwise> deptName ASC, progress DESC, targetName ASC </otherwise>
        </choose>
      </script>
      """)
  List<OrgMemberProgressRow> selectOrgMembers(
      @Param("year") int year,
      @Param("org") String org,
      @Param("ev") String ev,
      @Param("search") String search,
      @Param("sort") String sort,
      @Param("usersTable") String usersTable);

  @Select("""
      <script>
      WITH need AS (
        SELECT evaluator_id, target_id, data_ev, data_type
        FROM (
          SELECT t.user_id AS evaluator_id, t.target_id, t.data_ev, t.data_type
          FROM personnel_evaluation_aff.admin_default_targets t
          WHERE t.eval_year = #{year}
            AND t.user_id   = #{targetId}   -- ★ 이 직원이 '평가자'
            AND (#{ev} = 'ALL' OR #{ev} IS NULL OR t.data_ev = #{ev})
          UNION ALL
          SELECT c.user_id AS evaluator_id, c.target_id, c.data_ev, c.data_type
          FROM personnel_evaluation_aff.admin_custom_targets c
          WHERE c.eval_year = #{year}
            AND c.is_active = 1
            AND c.user_id   = #{targetId}   -- ★ 평가자
            AND (#{ev} = 'ALL' OR #{ev} IS NULL OR c.data_ev = #{ev})
        ) z
        GROUP BY evaluator_id, target_id, data_ev, data_type
      ),
      done AS (
        SELECT s.evaluator_id, s.target_id, s.data_ev, s.data_type
        FROM personnel_evaluation_aff.evaluation_submissions s
        WHERE s.eval_year    = #{year}
          AND s.evaluator_id = #{targetId}   -- ★ 이 직원이 '평가자'
          AND s.is_active    = 1
          AND s.del_yn       = 'N'
          <if test="ev != null and ev != '' and ev != 'ALL'">
            AND s.data_ev = #{ev}
          </if>
        GROUP BY s.evaluator_id, s.target_id, s.data_ev, s.data_type
      )
      SELECT
        n.target_id AS evaluatorId,   -- ★ 여기 원래 evaluator_id 컬럼에 target_id를 내려줌
        n.data_ev   AS dataEv,
        n.data_type AS dataType
      FROM need n
      LEFT JOIN done d
        ON d.evaluator_id = n.evaluator_id
       AND d.target_id    = n.target_id
       AND d.data_ev      = n.data_ev
       AND d.data_type    = n.data_type
      WHERE d.target_id IS NULL
      ORDER BY n.data_ev, n.target_id
      </script>
      """)
  List<PendingPairRow> selectPendingPairs(
      @Param("year") int year,
      @Param("targetId") String targetId, // 실제 의미: evaluatorId
      @Param("ev") String ev);
}