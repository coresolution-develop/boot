package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.entity.EvaluationSubmission;

@Mapper
public interface EvalSubmissionMapper {

  // 활성본 소프트삭제: del_yn='Y', is_active=0
  @Update("""
          UPDATE personnel_evaluation.evaluation_submissions
             SET del_yn    = 'Y',
                 is_active = 0,
                 updated_at = NOW(),
                 updated_by = #{updatedBy}
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
             AND del_yn = 'N'
             AND is_active = 1
      """)
  int softDeleteActive(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv,
      @Param("updatedBy") String updatedBy);

  // 해당 페어의 최대 버전
  @Select("""
          SELECT COALESCE(MAX(version),0)
            FROM personnel_evaluation.evaluation_submissions
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
      """)
  int findMaxVersion(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  // 새 버전 INSERT (활성본으로)
  @Insert("""
          INSERT INTO personnel_evaluation.evaluation_submissions
            (eval_year, evaluator_id, target_id, data_ev, data_type,
             answers_json, answered_count, radio_count, total_score, avg_score,
             version, del_yn, is_active, created_at, updated_at, updated_by)
          VALUES
            (#{s.evalYear}, #{s.evaluatorId}, #{s.targetId}, #{s.dataEv}, #{s.dataType},
             CAST(#{s.answersJson} AS JSON), #{s.answeredCount}, #{s.radioCount},
             #{s.totalScore}, #{s.avgScore},
             #{s.version}, 'N', 1, NOW(), NOW(), #{s.updatedBy})
      """)
  int insertOne(@Param("s") EvalSubmissionRow s);

  // 조회들 (전부 @Param 추가)
  @Select("""
          SELECT id, eval_year, evaluator_id, target_id, data_ev, data_type,
                 answers_json, answered_count, radio_count, total_score, avg_score, version
            FROM personnel_evaluation.evaluation_submissions
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
             AND del_yn='N' AND is_active=1
           LIMIT 1
      """)
  EvalSubmissionRow findActiveSubmission(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Select("""
          SELECT id,
                 eval_year      AS evalYear,
                 evaluator_id   AS evaluatorId,
                 target_id      AS targetId,
                 data_ev        AS dataEv,
                 data_type      AS dataType,
                 answered_count AS answeredCount,
                 total_score    AS totalScore,
                 avg_score      AS avgScore,
                 answers_json   AS answersJson,
                 version,
                 is_active      AS isActive,
                 del_yn         AS delYn,
                 updated_at     AS updatedAt
            FROM personnel_evaluation.evaluation_submissions
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
             AND del_yn='N' AND is_active=1
           ORDER BY version DESC, updated_at DESC
           LIMIT 1
      """)
  EvaluationSubmission findActiveStrict(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Select("""
          SELECT id,
                 eval_year      AS evalYear,
                 evaluator_id   AS evaluatorId,
                 target_id      AS targetId,
                 data_ev        AS dataEv,
                 data_type      AS dataType,
                 answered_count AS answeredCount,
           total_score    AS totalScore,
                 avg_score      AS avgScore,
           answers_json   AS answersJson,
                 version,
                 is_active      AS isActive,
                 del_yn         AS delYn,
                 updated_at     AS updatedAt
            FROM personnel_evaluation.evaluation_submissions
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
             AND del_yn='N'
           ORDER BY is_active DESC, version DESC, updated_at DESC
           LIMIT 1
      """)
  EvaluationSubmission findLatestStrict(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Select("""
          SELECT id,
                 eval_year      AS evalYear,
                 evaluator_id   AS evaluatorId,
                 target_id      AS targetId,
                 data_ev        AS dataEv,
                 data_type      AS dataType,
                 answered_count AS answeredCount,
                 total_score    AS totalScore,
                 avg_score      AS avgScore,
           answers_json   AS answersJson,
                 version,
                 is_active      AS isActive,
                 del_yn         AS delYn,
                 updated_at     AS updatedAt
            FROM personnel_evaluation.evaluation_submissions
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND del_yn='N'
           ORDER BY is_active DESC, version DESC, updated_at DESC
           LIMIT 1
      """)
  EvaluationSubmission findLatestLoose(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId);

  // (선택) 점수/카운트 단일 컬럼 조회도 @Param 통일
  @Select("""
          SELECT answered_count
            FROM personnel_evaluation.evaluation_submissions
           WHERE eval_year=#{year} AND evaluator_id=#{evaluatorId} AND target_id=#{targetId}
             AND data_type=#{dataType} AND data_ev=#{dataEv}
             AND del_yn='N' AND is_active=1
           LIMIT 1
      """)
  Integer findAnsweredCount(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Select("""
          SELECT avg_score
            FROM personnel_evaluation.evaluation_submissions
           WHERE eval_year=#{year} AND evaluator_id=#{evaluatorId} AND target_id=#{targetId}
             AND data_type=#{dataType} AND data_ev=#{dataEv}
             AND del_yn='N' AND is_active=1
           LIMIT 1
      """)
  Double findAvgScore(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Update("""
          UPDATE personnel_evaluation.evaluation_submissions
             SET is_active=0, updated_at=NOW()
           WHERE eval_year    = #{year}
             AND evaluator_id = #{evaluatorId}
             AND target_id    = #{targetId}
             AND data_type    = #{dataType}
             AND data_ev      = #{dataEv}
             AND del_yn       = 'N'
             AND is_active    = 1
      """)
  int deactivateActive(@Param("year") String year,
      @Param("evaluatorId") String evaluatorId,
      @Param("targetId") String targetId,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv);

  @Insert("""
          INSERT INTO personnel_evaluation.evaluation_submissions
          (eval_year, evaluator_id, target_id, data_ev, data_type,
           answered_count, total_score, avg_score, answers_json,
           version, is_active, del_yn, updated_at)
          VALUES
          (#{evalYear}, #{evaluatorId}, #{targetId}, #{dataEv}, #{dataType},
           #{answeredCount}, #{totalScore}, #{avgScore}, #{answersJson},
           #{version}, 1, 'N', NOW())
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertSubmission(EvaluationSubmission sub);

  @Select("""
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
        CAST(s.answers_json AS CHAR) AS answersJson,

        -- ★ 평가자 프로필
        u.name           AS evaluatorName,
        sm.sub_name      AS evaluatorDept,
        u.position       AS evaluatorPos

      FROM personnel_evaluation.evaluation_submissions s
      LEFT JOIN personnel_evaluation.users_${year} u
        ON u.id = s.evaluator_id AND u.eval_year = s.eval_year
      LEFT JOIN personnel_evaluation.sub_management sm
        ON sm.sub_code = u.sub_code AND sm.eval_year = u.eval_year

      WHERE s.eval_year = #{year}
        AND s.target_id = #{targetId}
        AND s.del_yn = 'N'
        AND s.is_active = 1
      ORDER BY s.created_at DESC
      """)
  List<EvaluationSubmission> selectReceivedAll(@Param("year") String year,
      @Param("targetId") String targetId);

  @Select("""
      SELECT COUNT(*)
      FROM personnel_evaluation.evaluation_submissions
      WHERE eval_year = #{year}
        AND target_id = #{targetId}
        AND del_yn = 'N'
        AND is_active = 1
      """)
  int countReceivedList(@Param("year") String year, @Param("targetId") String targetId);

  @Select("""
        SELECT
          id,
          eval_year      AS evalYear,
          evaluator_id   AS evaluatorId,
          target_id      AS targetId,
          data_ev        AS dataEv,
          data_type      AS dataType,
          answered_count AS answeredCount,
          total_score    AS totalScore,
          version,
          is_active      AS isActive,
          del_yn         AS delYn,
          updated_at     AS updatedAt,
          CAST(answers_json AS CHAR) AS answersJson
        FROM personnel_evaluation.evaluation_submissions
        WHERE id = #{id} AND del_yn='N'
      """)
  EvaluationSubmission selectById(@Param("id") Long id);

  @Select("""
      SELECT data_ev   AS dataEv,
             COUNT(*)  AS cnt,
             SUM(total_score) AS totalScoreSum,
             AVG(avg_score)   AS avgRadioAvg
      FROM personnel_evaluation.evaluation_submissions
      WHERE eval_year = #{year}
        AND target_id = #{targetId}
        AND del_yn='N' AND is_active=1
      GROUP BY data_ev
      ORDER BY data_ev
      """)
  List<Map<String, Object>> aggregateByRelation(@Param("year") String year, @Param("targetId") String targetId);
  @Select("""
      SELECT EXISTS(
        SELECT 1
          FROM personnel_evaluation.evaluation_submissions
         WHERE eval_year    = #{year}
           AND evaluator_id = #{evaluatorId}
           AND target_id    = #{targetId}
           AND data_ev      = #{dataEv}
           AND data_type    = #{dataType}
           AND del_yn       = 'N'
           AND is_active    = 1
      )
      """)
  boolean existsActiveByPair(@Param("year") String year,
                             @Param("evaluatorId") String evaluatorId,
                             @Param("targetId") String targetId,
                             @Param("dataEv") String dataEv,
                             @Param("dataType") String dataType);
}