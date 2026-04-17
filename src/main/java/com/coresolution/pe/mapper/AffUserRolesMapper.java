package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AffUserRolesMapper {

  @Select("""
        SELECT role
          FROM personnel_evaluation_aff.user_roles_${year}
         WHERE user_id = #{userId}
      """)
  List<String> findRolesByUser(@Param("userId") String userId, @Param("year") int year);
}
