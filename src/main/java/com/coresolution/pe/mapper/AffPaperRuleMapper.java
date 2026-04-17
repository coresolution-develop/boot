package com.coresolution.pe.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

@Mapper
public interface AffPaperRuleMapper {
    @Select("""
              SELECT paper_type
                FROM personnel_evaluation_aff.paper_rule
               WHERE c_name = #{cName}
                 AND eval_type = #{evalType}
            """)
    String findPaperTypeByCName(@Param("cName") String cName,
            @Param("evalType") String evalType);
}
