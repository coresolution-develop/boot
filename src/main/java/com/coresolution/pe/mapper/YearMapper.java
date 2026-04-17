package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface YearMapper {

    @Select("SELECT DISTINCT CAST(eval_year AS CHAR) AS eval_year\n" +
            "      FROM personnel_evaluation.admin_default_targets\n" +
            "     ORDER BY eval_year DESC")
    List<String> selectYearsFromTargets();

    @Select("SELECT DISTINCT CAST(eval_year AS CHAR) AS eval_year\n" +
            "      FROM personnel_evaluation.evaluation_submissions\n" +
            "     ORDER BY eval_year DESC")
    List<String> selectYearsFromUsers();

}
