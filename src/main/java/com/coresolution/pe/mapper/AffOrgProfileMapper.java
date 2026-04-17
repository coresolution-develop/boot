package com.coresolution.pe.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AffOrgProfileMapper {
        // 1) 이름으로 조회 (호출 측에서 병원명을 넘길 때 사용)
        @Select("""
                        SELECT c_name, c_name2, mission, vision_json
                          FROM personnel_evaluation_aff.org_profile
                         WHERE c_name = #{name}
                            OR c_name2 = #{name}
                         LIMIT 1
                        """)
        Map<String, Object> selectByName(@Param("name") String name);

        // 2) 공통(모든 병원 동일)일 때 아무 행이나 하나 가져오기
        @Select("""
                        SELECT c_name, c_name2, mission, vision_json
                          FROM personnel_evaluation_aff.org_profile
                         ORDER BY c_name
                         LIMIT 1
                        """)
        Map<String, Object> selectAnyOne();
}