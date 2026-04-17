package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import com.coresolution.pe.entity.EvalAnswer;
import com.coresolution.pe.entity.EvalResult;
import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.Org;
import com.coresolution.pe.entity.QuestionMeta;
import com.coresolution.pe.entity.TargetLight;

@Mapper
public interface EvalResultMapper {
  // 내가 특정 대상에게 특정 유형(AA/AB)으로 답한 문항 수
  @Select("""
          SELECT COUNT(*) FROM personnel_evaluation.evaluation_results
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
      """)
  int countAnswered(@Param("year") int year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  // 평균 점수(점수 있는 문항만)
  @Select("""
          SELECT AVG(q_score) FROM personnel_evaluation.evaluation_results
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
             AND q_score IS NOT NULL
      """)
  Double avgScoreForPair(@Param("year") int year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Select("""
          SELECT * FROM personnel_evaluation.evaluation_results
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
      """)
  List<EvalResult> findByPair(@Param("year") int year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Insert("""
          INSERT INTO personnel_evaluation.evaluation_results
            (eval_year, evaluator_id, target_id, data_ev, data_type,
             q_name, q_label, q_score, text_answer, created_at)
          VALUES
            (#{r.evalYear}, #{r.evaluatorId}, #{r.targetId}, #{r.dataEv}, #{r.dataType},
             #{r.qName}, #{r.qLabel}, #{r.qScore}, #{r.textAnswer}, NOW())
      """)
  int insertOne(@Param("r") EvalAnswer r);

  // 배치는 default 루프로
  default int insertAll(List<EvalAnswer> list) {
    int sum = 0;
    if (list == null)
      return 0;
    for (EvalAnswer r : list)
      sum += insertOne(r);
    return sum;
  }

  @Delete("""
          DELETE FROM personnel_evaluation.evaluation_results
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
      """)
  int deleteByPair(@Param("year") int year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  // (A) data_ev 리스트를 받아 해당 관계의 제출만 조회
  @Select("""
      <script>
      SELECT
        s.id,
        s.eval_year      AS evalYear,
        s.evaluator_id   AS evaluatorId,
        s.target_id      AS targetId,
        s.data_ev        AS dataEv,
        s.data_type      AS dataType,
        s.answered_count AS answeredCount,
        s.total_score    AS totalScore,
        s.version,
        s.is_active      AS isActive,
        s.del_yn         AS delYn,
        s.updated_at     AS updatedAt,
        CAST(s.answers_json AS CHAR) AS answersJson
      FROM personnel_evaluation.evaluation_submissions s
      WHERE s.eval_year = #{evalYear}
        AND s.target_id = #{targetId}
        AND s.del_yn = 'N'
        AND s.is_active = 1
        <if test="dataEvList != null and dataEvList.size() > 0">
          AND s.data_ev IN
          <foreach collection="dataEvList" item="ev" open="(" separator="," close=")">
            #{ev}
          </foreach>
        </if>
      ORDER BY s.created_at DESC
      </script>
      """)
  List<EvaluationSubmission> selectReceivedAllByEv(
      @Param("evalYear") int evalYear,
      @Param("targetId") String targetId,
      @Param("dataEvList") List<String> dataEvList);

  // (B) 문제은행 메타(주관식 제외)
  @Select("""
        SELECT
          idx        AS idx,
          d3         AS areaLabel,   -- 섬김/배움/키움/나눔/목표관리/주관식
          d2         AS typeCode,    -- AA / AB
          d1         AS labelText
        FROM personnel_evaluation.evaluation
        WHERE eval_year = #{evalYear}
          AND d3 <> '주관식'
      ORDER BY FIELD(d3,'섬김','배움','키움','나눔','목표관리'), idx
        """)
  List<QuestionMeta> selectQuestionBank(@Param("evalYear") int evalYear);

  // (C) 이 대상자에게 실제 존재하는 data_ev 코드를 구함 (예: F, D, …)
  @Select("""
          SELECT DISTINCT
            CASE
              /* 진료부(A0%) → 경혁팀(GH_TEAM) 인데 C/D 로 들어온 것들 보정 */
              WHEN es.data_type = 'AA'
               AND eval.sub_code LIKE 'A0%'       -- 평가자 진료부
               AND tgt.team_code = 'GH_TEAM'      -- 피평가자 경혁팀
               AND es.data_ev IN ('B','C','D')    -- 잘못 찍힌 C/D 포함
              THEN 'B'                            -- 진료부 > 경혁팀

              /* 그 외는 원래 코드 유지 (A,C,D,E,F,G 등) */
              ELSE es.data_ev
            END AS ev_code
          FROM personnel_evaluation.evaluation_submissions es
          JOIN personnel_evaluation.users_${year} tgt
            ON tgt.id        = es.target_id
           AND tgt.eval_year = es.eval_year
          JOIN personnel_evaluation.users_${year} eval
            ON eval.id        = es.evaluator_id
           AND eval.eval_year = es.eval_year
          WHERE es.eval_year = #{year}
            AND es.target_id = #{targetId}
            AND es.del_yn    = 'N'
            AND es.is_active = 1
      """)
  List<String> selectExistingDataEv(@Param("year") int year,
      @Param("targetId") String targetId);

  @Select("""
      WITH norm AS (
        SELECT
          s.target_id AS targetId,
          CASE
            WHEN s.data_type = 'AA'
             AND eval.sub_code LIKE 'A0%'
             AND tgt.team_code = 'GH_TEAM'
             AND s.data_ev IN ('B','C','D')
            THEN 'B'
            ELSE s.data_ev
          END AS evNorm,
          s.data_type,
          s.avg_score
        FROM personnel_evaluation.evaluation_submissions s
        JOIN personnel_evaluation.users_${evalYear} tgt
          ON tgt.id = s.target_id AND tgt.eval_year = s.eval_year
        JOIN personnel_evaluation.users_${evalYear} eval
          ON eval.id = s.evaluator_id AND eval.eval_year = s.eval_year
        WHERE s.eval_year = #{evalYear}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_type IN ('AA','AB')
          AND s.target_id = #{targetId}
      ),
      agg AS (
        SELECT
          targetId,
          evNorm,
          ROUND(AVG(CASE WHEN data_type='AA' THEN (avg_score/2.0) ELSE avg_score END) * 20, 2) AS score100,
          COUNT(*) AS resp
        FROM norm
        GROUP BY targetId, evNorm
      )
      SELECT
        targetId,
        evNorm AS dataEv,
        score100,
        resp
      FROM agg
      WHERE evNorm = #{dataEv}
      LIMIT 1
      """)
  List<EvalSubmissionRow> selectReceivedAllByOneEv(@Param("evalYear") int evalYear,
      @Param("targetId") String targetId,
      @Param("dataEv") String dataEv);

  @Select("""
        SELECT idx AS idx, d3 AS areaLabel, d2 AS typeCode, d1 AS labelText
        FROM personnel_evaluation.evaluation
        WHERE eval_year = #{evalYear}
          AND d3 <> '주관식'
          AND d2 = #{typeCode}
      ORDER BY FIELD(d3,'섬김','배움','키움','나눔','목표관리'), idx
        """)
  List<QuestionMeta> selectQuestionBankByType(@Param("evalYear") int evalYear,
      @Param("typeCode") String typeCode);

  @SelectProvider(type = Sqls.class, method = "selectAllTargetsForYear")
  @Results(id = "TargetLightMap", value = {
      @Result(column = "empId", property = "empId"),
      @Result(column = "empName", property = "empName"),
      @Result(column = "deptName", property = "deptName"),
      @Result(column = "position", property = "position")
  })
  List<TargetLight> selectAllTargetsForYear(@Param("year") int year);

  class Sqls {
    private static String leaderPredicate(String alias) {
      return """
          (
            %1$s.position LIKE '%%팀장%%'
            OR %1$s.position LIKE '%%부원장%%'
            OR %1$s.position LIKE '%%실장%%'
            OR %1$s.position LIKE '%%부장%%'
            OR %1$s.position LIKE '%%과장%%'
            OR %1$s.position LIKE '%%차장%%'
            OR %1$s.position LIKE '%%센터장%%'
            OR %1$s.position LIKE '%%원장%%'
            OR %1$s.position LIKE '%%병동장%%'
          )
          """.formatted(alias);
    }

    public static String selectAllSubmissionsByEvAndDeptAndTargetRole(Map<String, Object> p) {
      int evalYear = (int) p.get("evalYear");
      String userTable = "personnel_evaluation.users_" + evalYear;

      String targetRole = String.valueOf(p.get("targetRole"));
      String targetRoleWhere = "";
      if ("LEADER".equalsIgnoreCase(targetRole)) {
        targetRoleWhere = " AND " + leaderPredicate("tgt") + " ";
      } else if ("STAFF".equalsIgnoreCase(targetRole)) {
        targetRoleWhere = " AND NOT " + leaderPredicate("tgt") + " ";
      }

      return ("""
            SELECT
              s.id,
              s.eval_year    AS evalYear,
              s.evaluator_id AS evaluatorId,
              s.target_id    AS targetId,
              s.data_ev      AS dataEv,
              s.data_type    AS dataType,
              s.is_active    AS isActive,
              s.del_yn       AS delYn,
              CAST(s.answers_json AS CHAR) AS answersJson
            FROM personnel_evaluation.evaluation_submissions s
            JOIN %s tgt
              ON tgt.id        = s.target_id
             AND tgt.eval_year = s.eval_year
            JOIN %s eval
              ON eval.id        = s.evaluator_id
             AND eval.eval_year = s.eval_year
            WHERE s.eval_year = #{evalYear}
              AND s.del_yn    = 'N'
              AND s.is_active = 1
              AND tgt.sub_code = #{subCode}
              AND CASE
                    WHEN s.data_type = 'AA'
                     AND eval.sub_code LIKE 'A0%%'
                     AND tgt.team_code = 'GH_TEAM'
                     AND s.data_ev IN ('B','C','D')
                    THEN 'B'
                    ELSE s.data_ev
                  END = #{dataEv}
            %s
          """).formatted(userTable, userTable, targetRoleWhere);
    }

    public static String selectAllSubmissionsByEvAndDeptAndRolePair(Map<String, Object> p) {
      int evalYear = (int) p.get("evalYear");
      String userTable = "personnel_evaluation.users_" + evalYear;

      String evaluatorRole = String.valueOf(p.get("evaluatorRole"));
      String targetRole = String.valueOf(p.get("targetRole"));

      String evalRoleWhere = "";
      if ("LEADER".equalsIgnoreCase(evaluatorRole)) {
        evalRoleWhere = " AND " + leaderPredicate("eval") + " ";
      } else if ("STAFF".equalsIgnoreCase(evaluatorRole)) {
        evalRoleWhere = " AND NOT " + leaderPredicate("eval") + " ";
      }

      String tgtRoleWhere = "";
      if ("LEADER".equalsIgnoreCase(targetRole)) {
        tgtRoleWhere = " AND " + leaderPredicate("tgt") + " ";
      } else if ("STAFF".equalsIgnoreCase(targetRole)) {
        tgtRoleWhere = " AND NOT " + leaderPredicate("tgt") + " ";
      }

      return ("""
            SELECT
              s.id,
              s.eval_year    AS evalYear,
              s.evaluator_id AS evaluatorId,
              s.target_id    AS targetId,
              s.data_ev      AS dataEv,
              s.data_type    AS dataType,
              s.is_active    AS isActive,
              s.del_yn       AS delYn,
              CAST(s.answers_json AS CHAR) AS answersJson
            FROM personnel_evaluation.evaluation_submissions s
            JOIN %s tgt
              ON tgt.id        = s.target_id
             AND tgt.eval_year = s.eval_year
            JOIN %s eval
              ON eval.id        = s.evaluator_id
             AND eval.eval_year = s.eval_year
            WHERE s.eval_year = #{evalYear}
              AND s.del_yn    = 'N'
              AND s.is_active = 1
              AND tgt.sub_code = #{subCode}
              AND CASE
                    WHEN s.data_type = 'AA'
                     AND eval.sub_code LIKE 'A0%%'
                     AND tgt.team_code = 'GH_TEAM'
                     AND s.data_ev IN ('B','C','D')
                    THEN 'B'
                    ELSE s.data_ev
                  END = #{dataEv}
            %s
            %s
          """).formatted(userTable, userTable, evalRoleWhere, tgtRoleWhere);
    }

    public static String selectAllTargetsForYear(@Param("year") int year) {
      String userTable = "personnel_evaluation.users_" + year; // 동적 테이블
      return """
          SELECT DISTINCT
                 es.target_id              AS empId,
                 u.name                    AS empName,
                 COALESCE(sm.sub_name,'')  AS deptName,
                 u.position                AS position
            FROM personnel_evaluation.evaluation_submissions es
            JOIN %s u
              ON u.id = es.target_id
            LEFT JOIN personnel_evaluation.sub_management sm
              ON sm.sub_code = u.sub_code
              AND sm.eval_year = #{year}
           WHERE es.eval_year = #{year}
             AND es.del_yn = 'N'
          """.formatted(userTable);
    }

    public static String selectAllTargetsForYearAndOrg(@Param("year") int year,
        @Param("orgName") String orgName) {

      String userTable = "personnel_evaluation.users_" + year;
      String subTable = "personnel_evaluation.sub_management";

      // 1) 안쪽: DISTINCT만 적용 (정렬 없음)
      String inner = ("""
          SELECT DISTINCT
                 es.target_id             AS empId,
                 u.name                   AS empName,
                 COALESCE(sm.sub_name,'') AS deptName,
                 u.position               AS position
            FROM personnel_evaluation.evaluation_submissions es
            JOIN %s u ON u.id = es.target_id
            LEFT JOIN %s sm ON sm.sub_code = u.sub_code
                           AND sm.eval_year = #{year}
           WHERE es.eval_year = #{year}
             AND es.del_yn = 'N'
          """).formatted(userTable, subTable);

      StringBuilder where = new StringBuilder(inner);
      if (orgName != null && !orgName.isBlank()) {
        where.append(" AND u.c_name = #{orgName} ");
      }

      // 2) 바깥: 정렬 전용 (안쪽 SELECT의 별칭만 사용!)
      String outer = """
          SELECT *
          FROM (
            %s
          ) x
          ORDER BY
            /* 그룹: 숫자(0) > 한글(1) > 영어(2) > 기타(3) */
            CASE
              WHEN SUBSTRING(x.deptName,1,1) BETWEEN '0' AND '9' THEN 0
              WHEN SUBSTRING(x.deptName,1,1) COLLATE utf8mb4_0900_as_cs BETWEEN '가' AND '힣' THEN 1
              WHEN UPPER(SUBSTRING(x.deptName,1,1)) BETWEEN 'A' AND 'Z' THEN 2
              ELSE 3
            END,
            /* 숫자 시작이면 숫자값으로 */
            CASE
              WHEN SUBSTRING(x.deptName,1,1) BETWEEN '0' AND '9'
                THEN CAST(x.deptName AS UNSIGNED)
              ELSE NULL
            END,
            /* 일반 사전순 */
            x.deptName COLLATE utf8mb4_0900_ai_ci,
            /* 동부서/동명이 정렬 */
            x.empName  COLLATE utf8mb4_0900_ai_ci
          """;

      return outer.formatted(where.toString());
    }

    public static String selectAllSubmissionsByEvAndDept(
        @Param("evalYear") int evalYear,
        @Param("dataEv") String dataEv,
        @Param("subCode") String subCode) {

      String userTable = "personnel_evaluation.users_" + evalYear;
      return """
            SELECT
              s.id,
              s.eval_year    AS evalYear,
              s.evaluator_id AS evaluatorId,
              s.target_id    AS targetId,
              s.data_ev      AS dataEv,
              s.data_type    AS dataType,
              s.is_active    AS isActive,
              s.del_yn       AS delYn,
              CAST(s.answers_json AS CHAR) AS answersJson
            FROM personnel_evaluation.evaluation_submissions s
            JOIN %s tgt
              ON tgt.id        = s.target_id
             AND tgt.eval_year = s.eval_year
            JOIN %s eval
              ON eval.id        = s.evaluator_id
             AND eval.eval_year = s.eval_year
            WHERE s.eval_year = #{evalYear}
              AND s.del_yn    = 'N'
              AND s.is_active = 1
              AND tgt.sub_code = #{subCode}
              AND CASE
                    WHEN s.data_type = 'AA'
                     AND eval.sub_code LIKE 'A0%%'
                     AND tgt.team_code = 'GH_TEAM'
                     AND s.data_ev IN ('B','C','D')
                    THEN 'B'
                    ELSE s.data_ev
                  END = #{dataEv}
          """.formatted(userTable, userTable);
    }

    public static String selectAllSubmissionsByYearAndDept(
        @Param("evalYear") int evalYear,
        @Param("subCode") String subCode) {

      String userTable = "personnel_evaluation.users_" + evalYear;
      return """
            SELECT
              s.id,
              s.eval_year   AS evalYear,
              s.evaluator_id AS evaluatorId,
              s.target_id    AS targetId,
              s.data_ev      AS dataEv,
              s.data_type    AS dataType,
              s.is_active    AS isActive,
              s.del_yn       AS delYn,
              CAST(s.answers_json AS CHAR) AS answersJson
            FROM personnel_evaluation.evaluation_submissions s
            JOIN %s u ON u.id = s.target_id
            WHERE s.eval_year = #{evalYear}
              AND s.del_yn    = 'N'
              AND s.is_active = 1
              AND u.sub_code  = #{subCode}
          """.formatted(userTable);
    }

    // ✅ users_<year> 테이블에서 dept/org/team 조회
    public static String selectUserContext(Map<String, Object> p) {
      int y = (int) p.get("evalYear");
      String userTable = "personnel_evaluation.users_" + y;

      return ("""
              SELECT
                u.sub_code  AS deptCode,
                u.c_name    AS orgName,
                u.team_code AS teamCode
              FROM %s u
              WHERE u.eval_year = #{evalYear}
                AND u.id = #{userId}
              LIMIT 1
          """).formatted(userTable);
    }

    // ✅ F: 기관 평균 + targetRole(LEADER) 필터
    public static String selectAllSubmissionsByEvAndOrgAndTargetRole(Map<String, Object> p) {
      int y = (int) p.get("evalYear");
      String usersTable = "personnel_evaluation.users_" + y;
      String rolesTable = "personnel_evaluation.user_roles_" + y;

      return ("""
              SELECT
                es.id,
                es.eval_year      AS evalYear,
                es.evaluator_id   AS evaluatorId,
                es.target_id      AS targetId,
                es.data_ev        AS dataEv,
                es.data_type      AS dataType,
                es.answers_json   AS answersJson
              FROM personnel_evaluation.evaluation_submissions es
              JOIN %s tu
                ON tu.eval_year = es.eval_year
               AND tu.id        = es.target_id
              JOIN %s ur
                ON ur.eval_year = es.eval_year
               AND ur.user_id   = es.target_id
              WHERE es.eval_year = #{evalYear}
                AND es.data_ev   = #{dataEv}
                AND es.del_yn    = 'N'
                AND es.is_active = 1
                AND tu.c_name    = #{orgName}
                AND ur.role      = #{targetRole}
          """).formatted(usersTable, rolesTable);
    }

    // ✅ D: 팀 평균 (경혁팀 team_code) + ✅ CASE(논리 EV) 동일 적용
    public static String selectAllSubmissionsByEvAndTeam(Map<String, Object> p) {
      int y = (int) p.get("evalYear");
      String userTable = "personnel_evaluation.users_" + y;

      return ("""
              SELECT
                s.id,
                s.eval_year      AS evalYear,
                s.evaluator_id   AS evaluatorId,
                s.target_id      AS targetId,
                s.data_ev        AS dataEv,
                s.data_type      AS dataType,
                s.is_active      AS isActive,
                s.del_yn         AS delYn,
                CAST(s.answers_json AS CHAR) AS answersJson
              FROM personnel_evaluation.evaluation_submissions s
              JOIN %s tgt
                ON tgt.id        = s.target_id
               AND tgt.eval_year = s.eval_year
              JOIN %s eval
                ON eval.id        = s.evaluator_id
               AND eval.eval_year = s.eval_year
              WHERE s.eval_year = #{evalYear}
                AND s.del_yn    = 'N'
                AND s.is_active = 1
                AND tgt.team_code = #{teamCode}
                AND CASE
                      WHEN s.data_type = 'AA'
                       AND eval.sub_code LIKE 'A0%%'
                       AND tgt.team_code = 'GH_TEAM'
                       AND s.data_ev IN ('B','C','D')
                      THEN 'B'
                      ELSE s.data_ev
                    END = #{dataEv}
          """).formatted(userTable, userTable);
    }
  }

  // 기관명 드롭다운용 (선택지) - 연도별 동적 테이블
  @Select("""
          SELECT DISTINCT u.c_name
          FROM personnel_evaluation.users_${year} u
          WHERE u.c_name IS NOT NULL AND u.c_name <> ''
          ORDER BY u.c_name
      """)
  List<String> selectAllOrgNames(@Param("year") int year);

  // 대상자(직원) 목록: 년도 + (선택) 기관명(c_name)
  @SelectProvider(type = Sqls.class, method = "selectAllTargetsForYearAndOrg")
  List<TargetLight> selectAllTargetsForYearAndOrg(
      @Param("year") int year,
      @Param("orgName") String orgName);

  @Select("""
          SELECT idx
            FROM personnel_evaluation.evaluation
           WHERE eval_year = #{evalYear}
             AND d2 = #{typeCode}
             AND d3 = '주관식'
           ORDER BY idx DESC
           LIMIT 1
      """)
  Integer selectEssayIdx(@Param("evalYear") int evalYear,
      @Param("typeCode") String typeCode);

  @Select("""
        SELECT
          s.id,
          s.eval_year    AS evalYear,
          s.evaluator_id AS evaluatorId,
          s.target_id    AS targetId,
          /* 논리 코드도 함께 보고 싶으면 CASE 결과를 별도 컬럼으로 내려도 됨 */
          s.data_ev      AS dataEv,
          s.data_type    AS dataType,
          s.is_active    AS isActive,
          s.del_yn       AS delYn,
          CAST(s.answers_json AS CHAR) AS answersJson
        FROM personnel_evaluation.evaluation_submissions s
        JOIN personnel_evaluation.users_${evalYear} tgt
          ON tgt.id        = s.target_id
         AND tgt.eval_year = s.eval_year
        JOIN personnel_evaluation.users_${evalYear} eval
          ON eval.id        = s.evaluator_id
         AND eval.eval_year = s.eval_year
        WHERE s.eval_year = #{evalYear}
          AND s.del_yn    = 'N'
          AND s.is_active = 1
          AND CASE
                WHEN s.data_type = 'AA'
                 AND eval.sub_code LIKE 'A0%'
                 AND tgt.team_code = 'GH_TEAM'
                 AND s.data_ev IN ('B','C','D')
                THEN 'B'
                ELSE s.data_ev
              END = #{dataEv}
      """)
  List<EvalSubmissionRow> selectAllSubmissionsByEv(@Param("evalYear") int evalYear,
      @Param("dataEv") String dataEv);

  @Select("""
        SELECT
          s.id,
          s.eval_year   AS evalYear,
          s.evaluator_id AS evaluatorId,
          s.target_id    AS targetId,
          s.data_ev      AS dataEv,
          s.data_type    AS dataType,
          s.is_active    AS isActive,
          s.del_yn       AS delYn,
          CAST(s.answers_json AS CHAR) AS answersJson
        FROM personnel_evaluation.evaluation_submissions s
        WHERE s.eval_year = #{evalYear}
          AND s.del_yn    = 'N'
          AND s.is_active = 1
      """)
  List<EvalSubmissionRow> selectAllSubmissionsByYear(@Param("evalYear") int evalYear);

  @Select("""
            SELECT
              idx        AS idx,
              d3         AS areaLabel,
              d2         AS typeCode,
              d1         AS labelText
            FROM personnel_evaluation.evaluation
            WHERE eval_year = #{evalYear}
              AND d3 <> '주관식'
      ORDER BY FIELD(d3,'섬김','배움','키움','나눔','목표관리','주관식'), idx
        """)
  List<QuestionMeta> selectQuestionBankAll(@Param("evalYear") int evalYear);

  // 같은 data_ev + 같은 부서만
  @SelectProvider(type = Sqls.class, method = "selectAllSubmissionsByEvAndDept")
  List<EvalSubmissionRow> selectAllSubmissionsByEvAndDept(
      @Param("evalYear") int evalYear,
      @Param("dataEv") String dataEv,
      @Param("subCode") String subCode);

  // 같은 연도 + 같은 부서(전직원 평균의 부서판)
  @SelectProvider(type = Sqls.class, method = "selectAllSubmissionsByYearAndDept")
  List<EvalSubmissionRow> selectAllSubmissionsByYearAndDept(
      @Param("evalYear") int evalYear,
      @Param("subCode") String subCode);

  // F: 부서원 → 부서장 평균(타겟이 리더인 제출만)
  @SelectProvider(type = Sqls.class, method = "selectAllSubmissionsByEvAndDeptAndTargetRole")
  List<EvalSubmissionRow> selectAllSubmissionsByEvAndDeptAndTargetRole(
      @Param("evalYear") int evalYear,
      @Param("dataEv") String dataEv,
      @Param("subCode") String subCode,
      @Param("targetRole") String targetRole);

  // G: 역할쌍(평가자/피평가자)으로 평균 모집단 제한 (예: 리더↔리더)
  @SelectProvider(type = Sqls.class, method = "selectAllSubmissionsByEvAndDeptAndRolePair")
  List<EvalSubmissionRow> selectAllSubmissionsByEvAndDeptAndRolePair(
      @Param("evalYear") int evalYear,
      @Param("dataEv") String dataEv,
      @Param("subCode") String subCode,
      @Param("evaluatorRole") String evaluatorRole,
      @Param("targetRole") String targetRole);

  // ✅ F: 기관 평균(부서장 target만)
  @SelectProvider(type = Sqls.class, method = "selectAllSubmissionsByEvAndOrgAndTargetRole")
  List<EvalSubmissionRow> selectAllSubmissionsByEvAndOrgAndTargetRole(@Param("evalYear") int evalYear,
      @Param("dataEv") String dataEv,
      @Param("orgName") String orgName,
      @Param("targetRole") String targetRole);

  @SelectProvider(type = Sqls.class, method = "selectAllSubmissionsByEvAndTeam")
  List<EvalSubmissionRow> selectAllSubmissionsByEvAndTeam(@Param("evalYear") int evalYear,
      @Param("dataEv") String dataEv,
      @Param("teamCode") String teamCode);

  // ✅ targetId 기준으로 부서/기관/팀 컨텍스트 조회
  @SelectProvider(type = Sqls.class, method = "selectUserContext")
  Map<String, Object> selectUserContext(@Param("evalYear") int evalYear,
      @Param("userId") String userId);

  @Select("""
      SELECT
        s.id,
        s.eval_year      AS evalYear,
        s.evaluator_id   AS evaluatorId,
        s.target_id      AS targetId,
        s.data_ev        AS dataEv,
        s.data_type      AS dataType,
        s.is_active      AS isActive,
        s.del_yn         AS delYn,
        CAST(s.answers_json AS CHAR) AS answersJson
      FROM personnel_evaluation.evaluation_submissions s
      JOIN personnel_evaluation.users_${year} tgt
        ON tgt.id = s.target_id AND tgt.eval_year = s.eval_year
      JOIN personnel_evaluation.users_${year} eval
        ON eval.id = s.evaluator_id AND eval.eval_year = s.eval_year
      WHERE s.eval_year = #{year}
        AND s.del_yn = 'N'
        AND s.is_active = 1
        AND s.data_type = 'AA'
        AND tgt.team_code = #{teamCode}              -- 'GH_TEAM'
        AND eval.sub_code LIKE 'A0%'                 -- 진료부(패턴)
        AND s.data_ev IN ('B','C','D')               -- CASE에서 B로 묶이는 원본들
        AND (#{orgName} IS NULL OR #{orgName} = '' OR tgt.c_name = #{orgName})
      """)
  List<EvalSubmissionRow> selectMedicalToTeamCohort(
      @Param("year") int year,
      @Param("teamCode") String teamCode,
      @Param("orgName") String orgName);

  @Select("""
      WITH norm AS (
        SELECT
          s.target_id AS targetId,
          CASE
            WHEN s.data_type = 'AA'
             AND eval.sub_code LIKE 'A0%'
             AND tgt.team_code = 'GH_TEAM'
             AND s.data_ev IN ('B','C','D')
            THEN 'B'
            ELSE s.data_ev
          END AS evNorm,
          s.data_type,
          s.avg_score
        FROM personnel_evaluation.evaluation_submissions s
        JOIN personnel_evaluation.users_${evalYear} tgt
          ON tgt.id = s.target_id AND tgt.eval_year = s.eval_year
        JOIN personnel_evaluation.users_${evalYear} eval
          ON eval.id = s.evaluator_id AND eval.eval_year = s.eval_year
        WHERE s.eval_year = #{evalYear}
          AND s.is_active = 1
          AND s.del_yn = 'N'
          AND s.data_type IN ('AA','AB')
          AND s.target_id = #{targetId}
      ),
      agg AS (
        SELECT
          targetId,
          evNorm,
          ROUND(AVG(CASE WHEN data_type='AA' THEN (avg_score/2.0) ELSE avg_score END) * 20, 2) AS score100,
          COUNT(*) AS resp
        FROM norm
        GROUP BY targetId, evNorm
      )
      SELECT
        targetId,
        evNorm AS dataEv,
        score100,
        resp
      FROM agg
      WHERE evNorm = #{dataEv}
      LIMIT 1
      """)
  EvalSubmissionRow selectEvAggOne(@Param("evalYear") int evalYear,
      @Param("targetId") String targetId,
      @Param("dataEv") String dataEv);

}
