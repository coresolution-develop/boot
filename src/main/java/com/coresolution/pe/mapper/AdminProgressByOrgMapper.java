package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.OrgMemberProgressRow;
import com.coresolution.pe.entity.OrgProgressRow;
import com.coresolution.pe.entity.PendingPairRow;

@Mapper
public interface AdminProgressByOrgMapper {

  @Select("""
      <script>
      WITH
      base_targets AS (
        SELECT t.eval_year, t.user_id AS evaluator_id, t.target_id, t.data_ev, t.data_type
        FROM personnel_evaluation.admin_default_targets t
        WHERE t.eval_year = #{year}
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR t.data_ev = #{ev})
        UNION ALL
        SELECT c.eval_year, c.user_id AS evaluator_id, c.target_id, c.data_ev, c.data_type
        FROM personnel_evaluation.admin_custom_targets c
        WHERE c.eval_year = #{year}
          AND c.is_active = 1
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR c.data_ev = #{ev})
      ),
      dedup_targets AS (
        SELECT eval_year, evaluator_id, target_id, data_ev, data_type
        FROM base_targets
        GROUP BY eval_year, evaluator_id, target_id, data_ev, data_type
      ),
      pairs_with_org AS (
        SELECT d.eval_year,
               d.evaluator_id,
               d.target_id,
               d.data_ev,
               d.data_type,
               u.c_name AS org_name
        FROM dedup_targets d
        JOIN personnel_evaluation.users_${year} u
          ON u.id        = d.target_id
         AND u.eval_year = #{year}
         AND u.del_yn    = 'N'
      ),
      submissions_agg AS (
        SELECT
          s.eval_year,
          s.evaluator_id,
          s.target_id,
          s.data_ev,
          s.data_type,
          1 AS is_completed,
          MAX(s.updated_at) AS last_updated_at
        FROM personnel_evaluation.evaluation_submissions s
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
      SELECT
        o.org_name                                 AS orgName,
        COUNT(*)                                   AS totalPairs,
        SUM(is_completed)                          AS completedPairs,
        (COUNT(*) - SUM(is_completed))             AS pendingPairs,
        ROUND((SUM(is_completed) * 100.0 / COUNT(*)), 1) AS progress,
        MAX(last_updated_at)                       AS updatedAt
      FROM pairs_join_submit o
      WHERE (#{search} IS NULL OR #{search} = '' OR o.org_name LIKE CONCAT('%', #{search}, '%'))
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
      /* 총 기관 수(검색 반영) */
      SELECT COUNT(*) FROM (
        SELECT DISTINCT u.c_name AS org_name
        FROM personnel_evaluation.users_${year} u
        WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
          <if test="search != null and search != ''">
            AND u.c_name LIKE CONCAT('%', #{search}, '%')
          </if>
      ) x
      </script>
      """)
  int countOrgs(@Param("year") int year,
      @Param("search") String search,
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
        LEFT JOIN personnel_evaluation.sub_management sm
          ON sm.eval_year = u.eval_year
         AND sm.sub_code  = u.sub_code
        WHERE u.eval_year = #{year}
          AND u.del_yn    = 'N'
          AND u.c_name    = #{org}
          <if test="search != null and search != ''">
            AND (u.id LIKE CONCAT('%', #{search}, '%')
              OR u.name LIKE CONCAT('%', #{search}, '%'))
          </if>
      ),

      -- 평가자별 "배정된 대상자" 목록 (기본 + 커스텀)
      base_targets AS (
        SELECT
          t.user_id AS evaluator_id,
          t.target_id,
          t.data_ev,
          t.data_type
        FROM personnel_evaluation.admin_default_targets t
        JOIN emp e ON e.id = t.user_id
        WHERE t.eval_year = #{year}
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR t.data_ev = #{ev})

        UNION ALL

        SELECT
          c.user_id AS evaluator_id,
          c.target_id,
          c.data_ev,
          c.data_type
        FROM personnel_evaluation.admin_custom_targets c
        JOIN emp e ON e.id = c.user_id
        WHERE c.eval_year = #{year}
          AND c.is_active = 1
          AND (#{ev} = 'ALL' OR #{ev} IS NULL OR c.data_ev = #{ev})
      ),

      -- evaluator + target 단위로만 유니크 (관계/유형 무시)
      dedup_targets AS (
        SELECT
          evaluator_id,
          target_id
        FROM base_targets
        GROUP BY evaluator_id, target_id
      ),

      -- 완료된 평가도 evaluator + target 단위로만 집계
      completed_pairs AS (
        SELECT
          s.evaluator_id,
          s.target_id,
          MAX(s.updated_at) AS last_updated_at
        FROM personnel_evaluation.evaluation_submissions s
        JOIN emp e ON e.id = s.evaluator_id
        WHERE s.eval_year = #{year}
          AND s.is_active = 1
          AND s.del_yn    = 'N'
          <if test="ev != null and ev != '' and ev != 'ALL'">
            AND s.data_ev = #{ev}
          </if>
        GROUP BY s.evaluator_id, s.target_id
      ),

      agg AS (
        SELECT
          e.id        AS targetId,   -- 평가자 id
          e.name      AS targetName,
          e.position  AS position,
          e.team_code AS teamCode,
          e.sub_code  AS subCode,
          e.dept_name AS deptName,
          COUNT(DISTINCT d.target_id)    AS needPairs,   -- 배정된 사람 수
          COUNT(DISTINCT cp.target_id)   AS donePairs,   -- 실제 평가한 사람 수
          MAX(cp.last_updated_at)        AS lastUpdatedAt
        FROM emp e
        LEFT JOIN dedup_targets d
          ON d.evaluator_id = e.id
        LEFT JOIN completed_pairs cp
          ON cp.evaluator_id = e.id
         AND cp.target_id    = d.target_id
        GROUP BY e.id, e.name, e.position, e.team_code, e.sub_code, e.dept_name
      )
      SELECT
        targetId,
        targetName,
        position,
        teamCode,
        subCode,
        deptName,
        needPairs,
        donePairs,
        (needPairs - donePairs) AS pendingPairs,
        ROUND(100 * donePairs / NULLIF(needPairs, 0), 1) AS progress,
        lastUpdatedAt AS updatedAt
      FROM agg
      ORDER BY
        <choose>
          <when test="sort == 'name_asc'">      targetName ASC </when>
          <when test="sort == 'name_desc'">     targetName DESC </when>
          <when test="sort == 'progress_asc'">  progress ASC,      targetName ASC </when>
          <when test="sort == 'progress_desc'"> progress DESC,     targetName ASC </when>
          <when test="sort == 'pending_asc'">   pendingPairs ASC,  targetName ASC </when>
          <when test="sort == 'pending_desc'">  pendingPairs DESC, targetName ASC </when>
          <when test="sort == 'dept_asc'">      deptName ASC,  targetName ASC </when>
          <when test="sort == 'dept_desc'">     deptName DESC, targetName ASC </when>
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
      SELECT COUNT(*)
      FROM ${usersTable} u
      WHERE u.eval_year = #{year}
        AND u.del_yn = 'N'
        AND u.c_name = #{org}
        <if test="search != null and search != ''">
          AND (u.id LIKE CONCAT('%', #{search}, '%')
           OR  u.name LIKE CONCAT('%', #{search}, '%'))
        </if>
      </script>
      """)
  int countOrgMembers(@Param("year") int year,
      @Param("org") String org,
      @Param("search") String search,
      @Param("usersTable") String usersTable);

  /* 특정 직원의 미완료 페어 상세 (모달/슬라이드에 뿌리기 좋음) */
  @Select("""
      <script>
      WITH need AS (
        SELECT target_id, evaluator_id, data_ev, data_type
        FROM (
          SELECT t.target_id, t.user_id AS evaluator_id, t.data_ev, t.data_type
          FROM personnel_evaluation.admin_default_targets t
          WHERE t.eval_year = #{year}
            AND t.target_id = #{targetId}
            AND (#{ev} = 'ALL' OR #{ev} IS NULL OR t.data_ev = #{ev})
          UNION ALL
          SELECT c.target_id, c.user_id AS evaluator_id, c.data_ev, c.data_type
          FROM personnel_evaluation.admin_custom_targets c
          WHERE c.eval_year = #{year}
            AND c.is_active = 1
            AND c.target_id = #{targetId}
            AND (#{ev} = 'ALL' OR #{ev} IS NULL OR c.data_ev = #{ev})
        ) z
        GROUP BY target_id, evaluator_id, data_ev, data_type
      ),
      done AS (
        SELECT s.target_id, s.evaluator_id, s.data_ev, s.data_type
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{year}
          AND s.target_id = #{targetId}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          <if test="ev != null and ev != '' and ev != 'ALL'">
            AND s.data_ev = #{ev}
          </if>
        GROUP BY s.target_id, s.evaluator_id, s.data_ev, s.data_type
      )
      SELECT n.evaluator_id, n.data_ev, n.data_type
      FROM need n
      LEFT JOIN done d
        ON d.target_id = n.target_id
       AND d.evaluator_id = n.evaluator_id
       AND d.data_ev = n.data_ev
       AND d.data_type = n.data_type
      WHERE d.evaluator_id IS NULL
      ORDER BY n.data_ev, n.evaluator_id
      </script>
      """)
  List<PendingPairRow> selectPendingPairs(
      @Param("year") int year,
      @Param("targetId") String targetId,
      @Param("ev") String ev);
}