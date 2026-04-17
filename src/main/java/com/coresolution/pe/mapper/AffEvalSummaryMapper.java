package com.coresolution.pe.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AffEvalSummaryMapper {

  @Select("""
        SELECT eval_year, target_id, data_ev, kind,
               input_hash, essay_count, submission_count,
               locale, model, summary, status, error_msg,
               created_at, updated_at
          FROM personnel_evaluation_aff.evaluation_comment_summary
         WHERE eval_year = #{year}
           AND target_id = #{targetId}
           AND data_ev   = #{dataEv}
           AND kind      = #{kind}
      """)
  Map<String, Object> selectOne(
      @Param("year") int year,
      @Param("targetId") String targetId,
      @Param("dataEv") String dataEv,
      @Param("kind") String kind);

  @Insert("""
        INSERT INTO personnel_evaluation_aff.evaluation_comment_summary
          (eval_year, target_id, data_ev, kind,
           input_hash, essay_count, submission_count,
           locale, model, summary, status, error_msg)
        VALUES
          (#{year}, #{targetId}, #{dataEv}, #{kind},
           #{inputHash}, #{essayCount}, #{submissionCount},
           #{locale}, #{model}, #{summary}, #{status}, #{errorMsg})
        ON DUPLICATE KEY UPDATE
          input_hash        = VALUES(input_hash),
          essay_count       = VALUES(essay_count),
          submission_count  = VALUES(submission_count),
          locale            = VALUES(locale),
          model             = VALUES(model),
          summary           = VALUES(summary),
          status            = VALUES(status),
          error_msg         = VALUES(error_msg)
      """)
  int upsert(
      @Param("year") int year,
      @Param("targetId") String targetId,
      @Param("dataEv") String dataEv,
      @Param("kind") String kind, // 'ESSAY' or 'SCORE'
      @Param("inputHash") String inputHash,
      @Param("essayCount") int essayCount,
      @Param("submissionCount") int submissionCount,
      @Param("locale") String locale,
      @Param("model") String model,
      @Param("summary") String summary,
      @Param("status") String status,
      @Param("errorMsg") String errorMsg);
}
