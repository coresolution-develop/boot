package com.coresolution.pe.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AffTableInitMapper {

    /** 테이블 존재 여부 확인 */
    @Select("""
            SELECT COUNT(*)
              FROM INFORMATION_SCHEMA.TABLES
             WHERE TABLE_SCHEMA = #{schema}
               AND TABLE_NAME   = #{tableName}
            """)
    int tableExists(@Param("schema") String schema, @Param("tableName") String tableName);

    /** users_${year} 테이블 생성 (baseYear 구조 복사) */
    @Update("CREATE TABLE IF NOT EXISTS personnel_evaluation_aff.users_${year} LIKE personnel_evaluation_aff.users_${baseYear}")
    void createUsersTable(@Param("year") int year, @Param("baseYear") int baseYear);

    /** user_roles_${year} 테이블 생성 (baseYear 구조 복사) */
    @Update("CREATE TABLE IF NOT EXISTS personnel_evaluation_aff.user_roles_${year} LIKE personnel_evaluation_aff.user_roles_${baseYear}")
    void createUserRolesTable(@Param("year") int year, @Param("baseYear") int baseYear);

    /** users_${year} 에 adminId 가 존재하는지 확인 */
    @Select("SELECT COUNT(*) FROM personnel_evaluation_aff.users_${year} WHERE id = #{adminId}")
    int adminExistsInUsers(@Param("year") int year, @Param("adminId") String adminId);

    /** baseYear 의 관리자 행을 year 테이블로 복사 */
    @Insert("INSERT INTO personnel_evaluation_aff.users_${year} SELECT * FROM personnel_evaluation_aff.users_${baseYear} WHERE id = #{adminId}")
    void copyAdminUser(@Param("year") int year, @Param("baseYear") int baseYear, @Param("adminId") String adminId);

    /** 복사된 관리자 행의 eval_year 를 year 로 갱신 */
    @Update("UPDATE personnel_evaluation_aff.users_${year} SET eval_year = #{year} WHERE id = #{adminId}")
    void fixAdminEvalYear(@Param("year") int year, @Param("adminId") String adminId);

    /** user_roles_${year} 에 adminId 의 역할이 존재하는지 확인 */
    @Select("SELECT COUNT(*) FROM personnel_evaluation_aff.user_roles_${year} WHERE user_id = #{adminId}")
    int adminExistsInRoles(@Param("year") int year, @Param("adminId") String adminId);

    /** baseYear 의 관리자 역할 행을 year 테이블로 복사 */
    @Insert("INSERT INTO personnel_evaluation_aff.user_roles_${year} SELECT * FROM personnel_evaluation_aff.user_roles_${baseYear} WHERE user_id = #{adminId}")
    void copyAdminRoles(@Param("year") int year, @Param("baseYear") int baseYear, @Param("adminId") String adminId);

    /** 복사된 관리자 역할 행의 eval_year 를 year 로 갱신 */
    @Update("UPDATE personnel_evaluation_aff.user_roles_${year} SET eval_year = #{year} WHERE user_id = #{adminId}")
    void fixAdminRolesEvalYear(@Param("year") int year, @Param("adminId") String adminId);
}
