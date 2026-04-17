package com.coresolution.pe.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TableInitMapper {

    /** 테이블 존재 여부 확인 */
    @Select("""
            SELECT COUNT(*)
              FROM INFORMATION_SCHEMA.TABLES
             WHERE TABLE_SCHEMA = #{schema}
               AND TABLE_NAME   = #{tableName}
            """)
    int tableExists(@Param("schema") String schema, @Param("tableName") String tableName);

    /** 평가 구간 규칙 테이블 생성 (연도 무관, 최초 1회) */
    @Update("""
            CREATE TABLE IF NOT EXISTS personnel_evaluation.eval_mapping_rule (
              id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
              eval_year            INT          NOT NULL,
              rule_name            VARCHAR(100) NOT NULL,
              evaluator_role       VARCHAR(50),
              evaluator_sub_prefix VARCHAR(20),
              evaluator_team_code  VARCHAR(50),
              target_role          VARCHAR(50),
              target_sub_prefix    VARCHAR(20),
              target_team_code     VARCHAR(50),
              target_scope         VARCHAR(20)  NOT NULL DEFAULT 'SPECIFIC_ROLE',
              data_ev              VARCHAR(5)   NOT NULL,
              data_type            VARCHAR(5)   NOT NULL,
              eval_type_code       VARCHAR(50),
              c_name               VARCHAR(100),
              exclude_self         TINYINT      NOT NULL DEFAULT 1,
              enabled              TINYINT      NOT NULL DEFAULT 1,
              created_by           VARCHAR(50),
              created_at           DATETIME     DEFAULT NOW(),
              updated_by           VARCHAR(50),
              updated_at           DATETIME     DEFAULT NOW()
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
    void createEvalMappingRuleTable();

    /** users_${year} 테이블 생성 (baseYear 구조 복사) */
    @Update("CREATE TABLE IF NOT EXISTS personnel_evaluation.users_${year} LIKE personnel_evaluation.users_${baseYear}")
    void createUsersTable(@Param("year") int year, @Param("baseYear") int baseYear);

    /** user_roles_${year} 테이블 생성 (baseYear 구조 복사) */
    @Update("CREATE TABLE IF NOT EXISTS personnel_evaluation.user_roles_${year} LIKE personnel_evaluation.user_roles_${baseYear}")
    void createUserRolesTable(@Param("year") int year, @Param("baseYear") int baseYear);

    /** users_${year} 에 adminId 가 존재하는지 확인 */
    @Select("SELECT COUNT(*) FROM personnel_evaluation.users_${year} WHERE id = #{adminId}")
    int adminExistsInUsers(@Param("year") int year, @Param("adminId") String adminId);

    /** baseYear 의 관리자 행을 year 테이블로 복사 */
    @Insert("INSERT INTO personnel_evaluation.users_${year} SELECT * FROM personnel_evaluation.users_${baseYear} WHERE id = #{adminId}")
    void copyAdminUser(@Param("year") int year, @Param("baseYear") int baseYear, @Param("adminId") String adminId);

    /** 복사된 관리자 행의 eval_year 를 year 로 갱신 */
    @Update("UPDATE personnel_evaluation.users_${year} SET eval_year = #{year} WHERE id = #{adminId}")
    void fixAdminEvalYear(@Param("year") int year, @Param("adminId") String adminId);

    /** user_roles_${year} 에 adminId 의 역할이 존재하는지 확인 */
    @Select("SELECT COUNT(*) FROM personnel_evaluation.user_roles_${year} WHERE user_id = #{adminId}")
    int adminExistsInRoles(@Param("year") int year, @Param("adminId") String adminId);

    /** baseYear 의 관리자 역할 행을 year 테이블로 복사 */
    @Insert("INSERT INTO personnel_evaluation.user_roles_${year} SELECT * FROM personnel_evaluation.user_roles_${baseYear} WHERE user_id = #{adminId}")
    void copyAdminRoles(@Param("year") int year, @Param("baseYear") int baseYear, @Param("adminId") String adminId);

    /** 복사된 관리자 역할 행의 eval_year 를 year 로 갱신 */
    @Update("UPDATE personnel_evaluation.user_roles_${year} SET eval_year = #{year} WHERE user_id = #{adminId}")
    void fixAdminRolesEvalYear(@Param("year") int year, @Param("adminId") String adminId);
}
