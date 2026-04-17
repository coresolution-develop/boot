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
public interface AffEvaluationMapper {
    // --- 유니크키가 있을 때: 깔끔한 업서트 ---
    @Insert("""
            INSERT INTO personnel_evaluation_aff.evaluation (d1, d2, d3, eval_year)
            VALUES (#{d1}, #{d2}, #{d3}, #{eval_year}) AS new
            ON DUPLICATE KEY UPDATE d3 = new.d3
            """)
    int upsert(Evaluation q);

    @Insert("""
                INSERT INTO personnel_evaluation_aff.evaluation (d1, d2, d3, eval_year)
                VALUES (#{d1}, #{d2}, #{d3}, #{eval_year})
            """)
    int insert(Evaluation q);

    @Select("""
                SELECT idx FROM personnel_evaluation_aff.evaluation
                WHERE eval_year = #{year} AND d2 = #{d2} AND d1 = #{d1} LIMIT 1
            """)
    Integer findIdxByYearTypeAndText(@Param("year") int year,
            @Param("d2") String d2,
            @Param("d1") String d1);

    @Update("UPDATE personnel_evaluation_aff.evaluation SET d3 = #{d3} WHERE idx = #{idx}")
    int updateOnlyD3(Evaluation q);

    @Delete("DELETE FROM personnel_evaluation_aff.evaluation WHERE eval_year = #{year}")
    int deleteByYear(@Param("year") int year);

    @Select("""
              SELECT COUNT(*) FROM personnel_evaluation_aff.evaluation
               WHERE eval_year = #{year} AND d2 = #{type}
            """)
    int countByType(@Param("year") int year, @Param("type") String type);

    @Select("""
              SELECT
                e.idx,
                e.d1,
                e.d2,
                e.d3,
                e.eval_year AS evalYear
              FROM personnel_evaluation_aff.evaluation e
              WHERE e.eval_year = #{year}
                AND e.d2       = #{dataType}
              ORDER BY e.idx
            """)
    List<Evaluation> selectByType(@Param("year") int year,
            @Param("dataType") String dataType);

    @Select("""
                SELECT COUNT(*)
                FROM personnel_evaluation_aff.evaluation
                WHERE eval_year = #{year}
                  AND d2 = #{dataType}
                  AND d3 <> '주관식'
            """)
    int countRadioByType(@Param("year") int year,
            @Param("dataType") String dataType);
}
