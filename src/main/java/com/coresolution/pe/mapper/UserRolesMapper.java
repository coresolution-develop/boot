package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRolesMapper {
    /** 1) 특정 직원이 가진 모든 역할 */
    @Select("""
                SELECT role FROM personnel_evaluation.user_roles_${year}
                WHERE user_id = #{userId} AND eval_year = #{year}
            """)
    List<String> findRolesByUser(@Param("userId") String userId, @Param("year") String year);

    /** 2) 특정 역할(role)에 속해 있는 모든 사번 */
    @Select("SELECT user_id FROM personnel_evaluation.user_roles_${year} WHERE role = #{role}")
    List<String> findUserIdsByRole(
            @Param("role") String role,
            @Param("year") String year);

    @Select({
            "<script>",
            "SELECT ur.user_id",
            "  FROM personnel_evaluation.user_roles_${year} ur",
            "  JOIN personnel_evaluation.users_${year} u",
            "    ON u.id = ur.user_id AND u.eval_year = ur.eval_year",
            " WHERE u.sub_code = #{subCode}",
            "   AND ur.role IN",
            "   <foreach collection='roles' item='r' open='(' separator=',' close=')'>",
            "     #{r}",
            "   </foreach>",
            "</script>"
    })
    List<String> findUserIdsBySubAndRoles(
            @Param("subCode") String subCode,
            @Param("year") String year,
            @Param("roles") List<String> roles);

    @Select("""
                SELECT ur.user_id
                FROM personnel_evaluation.user_roles_${year} ur
                JOIN personnel_evaluation.users_${year} u
                  ON u.id = ur.user_id AND u.eval_year = ur.eval_year
                WHERE ur.role = #{role} AND ur.eval_year = #{year}
                  AND u.c_name = #{cName} AND u.c_name2 = #{cName2}
            """)
    List<String> findUserIdsByRoleInOrg(@Param("role") String role, @Param("year") String year,
            @Param("cName") String cName, @Param("cName2") String cName2);

    /**
     * 연도 전체 역할을 한 번에 로드 — 규칙 엔진 전용 (N+1 방지).
     * 반환: [{userId: "12345", role: "sub_head"}, ...]
     */
    @Select("""
                SELECT user_id AS userId, role
                  FROM personnel_evaluation.user_roles_${year}
                 WHERE eval_year = #{year}
            """)
    List<Map<String, String>> findAllRolesForYear(@Param("year") String year);

}