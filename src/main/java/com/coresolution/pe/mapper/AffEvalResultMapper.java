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
public interface AffEvalResultMapper {
  // 내가 특정 대상에게 특정 유형(AA/AB)으로 답한 문항 수
  @Select("""
          SELECT COUNT(*) FROM personnel_evaluation_aff.evaluation_results
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
          SELECT AVG(q_score) FROM personnel_evaluation_aff.evaluation_results
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
          SELECT * FROM personnel_evaluation_aff.evaluation_results
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
          INSERT INTO personnel_evaluation_aff.evaluation_results
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
          DELETE FROM personnel_evaluation_aff.evaluation_results
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
      FROM personnel_evaluation_aff.evaluation_submissions s
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
        FROM personnel_evaluation_aff.evaluation
        WHERE eval_year = #{evalYear}
          AND d3 <> '주관식'
      ORDER BY FIELD(d3,'섬김','배움','키움','나눔','목표관리'), idx
        """)
  List<QuestionMeta> selectQuestionBank(@Param("evalYear") int evalYear);

  // (C) 이 대상자에게 실제 존재하는 data_ev 코드를 구함 (예: F, D, …)
  /**
   * 해당 연도 + 대상자에 대해 실제로 존재하는 평가구간(data_ev: A/B/C/D)을 조회
   * - A: 부서장 > 부서장
   * - B: 부서장 > 부서원
   * - C: 부서원 > 부서장
   * - D: 부서원 > 부서원
   */
  @Select("""
      SELECT DISTINCT s.data_ev
      FROM personnel_evaluation_aff.evaluation_submissions s
      WHERE s.eval_year = #{year}
        AND s.target_id = #{targetId}
        AND s.del_yn    = 'N'
        AND s.is_active = 1
        AND s.data_ev IN ('A','B','C','D')
      ORDER BY s.data_ev
      """)
  List<String> selectExistingDataEv(@Param("year") int year,
      @Param("targetId") String targetId);

  @Select("""
      <script>
      SELECT
        s.id, s.eval_year AS evalYear, s.evaluator_id AS evaluatorId,
        s.target_id AS targetId, s.data_ev AS dataEv, s.data_type AS dataType,
        s.answered_count AS answeredCount, s.total_score AS totalScore,
        s.version, s.is_active AS isActive, s.del_yn AS delYn,
        s.updated_at AS updatedAt, CAST(s.answers_json AS CHAR) AS answersJson
      FROM personnel_evaluation_aff.evaluation_submissions s
      WHERE s.eval_year = #{evalYear}
        AND s.target_id = #{targetId}
        AND s.del_yn = 'N'
        AND s.is_active = 1
        AND s.data_ev = #{dataEv}
      ORDER BY s.created_at DESC
      </script>
      """)
  List<EvalSubmissionRow> selectReceivedAllByOneEv(@Param("evalYear") int evalYear,
      @Param("targetId") String targetId,
      @Param("dataEv") String dataEv);

  @Select("""
        SELECT idx AS idx, d3 AS areaLabel, d2 AS typeCode, d1 AS labelText
        FROM personnel_evaluation_aff.evaluation
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
    public static String selectAllTargetsForYear(@Param("year") int year) {
      String userTable = "personnel_evaluation_aff.users_" + year; // 동적 테이블
      return """
          SELECT DISTINCT
                 es.target_id              AS empId,
                 u.name                    AS empName,
                 COALESCE(sm.sub_name,'')  AS deptName,
                 u.position                AS position
            FROM personnel_evaluation_aff.evaluation_submissions es
            JOIN %s u
              ON u.id = es.target_id
            LEFT JOIN personnel_evaluation_aff.sub_management sm
              ON sm.sub_code = u.sub_code
              AND sm.eval_year = #{year}
           WHERE es.eval_year = #{year}
             AND es.del_yn = 'N'
          """.formatted(userTable);
    }

    public static String selectAllTargetsForYearAndOrg(@Param("year") int year,
        @Param("orgName") String orgName) {

      String userTable = "personnel_evaluation_aff.users_" + year;
      String subTable = "personnel_evaluation_aff.sub_management";

      // 1) 안쪽: DISTINCT만 적용 (정렬 없음)
      String inner = ("""
          SELECT DISTINCT
                 es.target_id             AS empId,
                 u.name                   AS empName,
                 COALESCE(sm.sub_name,'') AS deptName,
                 u.position               AS position
            FROM personnel_evaluation_aff.evaluation_submissions es
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

      String userTable = "personnel_evaluation_aff.users_" + evalYear;
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
            FROM personnel_evaluation_aff.evaluation_submissions s
            JOIN %s u ON u.id = s.target_id
            WHERE s.eval_year = #{evalYear}
              AND s.data_ev   = #{dataEv}
              AND s.del_yn    = 'N'
              AND s.is_active = 1
              AND u.sub_code  = #{subCode}
          """.formatted(userTable);
    }

    public static String selectAllSubmissionsByYearAndDept(
        @Param("evalYear") int evalYear,
        @Param("subCode") String subCode) {

      String userTable = "personnel_evaluation_aff.users_" + evalYear;
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
            FROM personnel_evaluation_aff.evaluation_submissions s
            JOIN %s u ON u.id = s.target_id
            WHERE s.eval_year = #{evalYear}
              AND s.del_yn    = 'N'
              AND s.is_active = 1
              AND u.sub_code  = #{subCode}
          """.formatted(userTable);
    }
  }

  // 기관명 드롭다운용 (선택지)
  @Select("""
          SELECT DISTINCT u.c_name
          FROM personnel_evaluation_aff.users_${year} u
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
            FROM personnel_evaluation_aff.evaluation
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
          s.eval_year   AS evalYear,
          s.evaluator_id AS evaluatorId,
          s.target_id    AS targetId,
          s.data_ev      AS dataEv,
          s.data_type    AS dataType,
          s.is_active    AS isActive,
          s.del_yn       AS delYn,
          CAST(s.answers_json AS CHAR) AS answersJson
        FROM personnel_evaluation_aff.evaluation_submissions s
        WHERE s.eval_year = #{evalYear}
          AND s.data_ev   = #{dataEv}
          AND s.del_yn    = 'N'
          AND s.is_active = 1
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
        FROM personnel_evaluation_aff.evaluation_submissions s
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
            FROM personnel_evaluation_aff.evaluation
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

  /**
   * 특정 대상자 + 특정 EV코드의 100점 환산 평균 및 응답 수
   * (reportsummary 용)
   * AC(10문항형): avg_score/2.0 * 20, AD/AE(20문항형): avg_score * 20
   */
  @Select("""
      WITH agg AS (
        SELECT
          s.target_id AS targetId,
          s.data_ev   AS dataEv,
          ROUND(AVG(CASE WHEN s.data_type = 'AC' THEN (s.avg_score / 2.0)
                         ELSE s.avg_score END) * 20, 2) AS score100,
          COUNT(*)    AS resp
        FROM personnel_evaluation_aff.evaluation_submissions s
        WHERE s.eval_year = #{evalYear}
          AND s.is_active = 1
          AND s.del_yn    = 'N'
          AND s.target_id = #{targetId}
        GROUP BY s.target_id, s.data_ev
      )
      SELECT targetId, dataEv, score100, resp
        FROM agg
       WHERE dataEv = #{dataEv}
       LIMIT 1
      """)
  EvalSubmissionRow selectEvAggOne(@Param("evalYear") int evalYear,
      @Param("targetId") String targetId,
      @Param("dataEv") String dataEv);

}
