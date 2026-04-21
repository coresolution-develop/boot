package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.Evaluation;

@Mapper
public interface EvaluationMapper {
  // --- 유니크키가 있을 때: 깔끔한 업서트 ---
  @Insert("""
          INSERT INTO personnel_evaluation.evaluation (d1, d2, d3, eval_year)
          VALUES (#{d1}, #{d2}, #{d3}, #{eval_year})
          ON DUPLICATE KEY UPDATE d3 = VALUES(d3)
      """)
  int upsert(Evaluation q);

  @Insert("""
          INSERT INTO personnel_evaluation.evaluation (d1, d2, d3, eval_year)
          VALUES (#{d1}, #{d2}, #{d3}, #{eval_year})
      """)
  int insert(Evaluation q);

  @Select("""
          SELECT idx FROM personnel_evaluation.evaluation
          WHERE eval_year = #{year} AND d2 = #{d2} AND d1 = #{d1} LIMIT 1
      """)
  Integer findIdxByYearTypeAndText(@Param("year") int year,
      @Param("d2") String d2,
      @Param("d1") String d1);

  @Update("UPDATE personnel_evaluation.evaluation SET d3 = #{d3} WHERE idx = #{idx}")
  int updateOnlyD3(Evaluation q);

  @Delete("DELETE FROM personnel_evaluation.evaluation WHERE eval_year = #{year}")
  int deleteByYear(@Param("year") int year);

  @Delete("DELETE FROM personnel_evaluation.evaluation WHERE eval_year = #{year} AND d2 = #{type}")
  int deleteByYearAndType(@Param("year") int year, @Param("type") String type);

  @Select("""
        SELECT COUNT(*) FROM personnel_evaluation.evaluation
         WHERE eval_year = #{year} AND d2 = #{type}
      """)
  int countByType(@Param("year") String year, @Param("type") String type);

  @Select("""
        SELECT * FROM personnel_evaluation.evaluation
         WHERE eval_year = #{year}
           AND d2 = #{type}
        ORDER BY idx
      """)
  List<Evaluation> findByYearAndType(@Param("year") String year, @Param("type") String type);
}
