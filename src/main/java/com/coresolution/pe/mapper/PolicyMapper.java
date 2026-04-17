package com.coresolution.pe.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.EvalPolicy;

@Mapper
public interface PolicyMapper {
    @Select("""
              SELECT c_name, leader_scope, leader_sublist
              FROM personnel_evaluation.admin_eval_policy
              WHERE eval_year = #{year} AND c_name = #{orgCode}
            """)
    EvalPolicy findPolicy(@Param("year") String year, @Param("orgCode") String orgCode);
}
