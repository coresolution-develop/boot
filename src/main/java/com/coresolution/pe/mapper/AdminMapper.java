package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.ReleaseGate;

@Mapper
public interface AdminMapper {

  @Select("""
      SELECT idx, d1, d2, d3, eval_year
        FROM personnel_evaluation.evaluation
       WHERE eval_year = #{year}
      """)
  List<Evaluation> getEvaluation(String year);

  @Select("""
          SELECT id, page_key AS pageKey, eval_year AS evalYear, open_at AS openAt, close_at AS closeAt, enabled
          FROM personnel_evaluation.result_release_gate
          WHERE page_key = #{pageKey} AND eval_year = #{evalYear}
          LIMIT 1
      """)
  Optional<ReleaseGate> findOne(@Param("pageKey") String pageKey, @Param("evalYear") int evalYear);

  @Insert("""
          INSERT INTO personnel_evaluation.result_release_gate(page_key, eval_year, open_at, close_at, enabled)
          VALUES(#{pageKey}, #{evalYear}, #{openAt}, #{closeAt}, #{enabled})
          ON DUPLICATE KEY UPDATE
            open_at = VALUES(open_at),
            close_at = VALUES(close_at),
            enabled  = VALUES(enabled)
      """)
  int upsert(ReleaseGate gate);

  @Select("""
        SELECT eval_year
        FROM personnel_evaluation.result_release_gate
        WHERE page_key = #{pageKey}
          AND enabled = 1
          AND open_at <= NOW()
          AND (close_at IS NULL OR close_at > NOW())
        ORDER BY eval_year DESC
        LIMIT 1
      """)
  Integer findLatestOpenYear(@Param("pageKey") String pageKey);
}
