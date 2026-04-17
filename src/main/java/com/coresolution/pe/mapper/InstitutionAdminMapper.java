package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.InstitutionAdmin;

@Mapper
public interface InstitutionAdminMapper {

    // ── 공통 JOIN SELECT ──────────────────────────────────

    String BASE_SELECT = """
        SELECT
            a.id             AS id,
            a.institution_id AS institutionId,
            a.login_id       AS loginId,
            a.pwd            AS pwd,
            a.name           AS name,
            a.is_active      AS isActive,
            a.created_at     AS createdAt,
            i.name           AS institutionName,
            i.code           AS institutionCode
        FROM personnel_evaluation.institution_admins a
        LEFT JOIN personnel_evaluation.institutions i
               ON i.id = a.institution_id
        """;

    // ── 조회 ─────────────────────────────────────────────

    /** 로그인 ID로 관리자 조회 (Security 인증 시 사용) */
    @Select(BASE_SELECT + "WHERE a.login_id = #{loginId}")
    @Results(id = "instAdminResult", value = {
        @Result(column = "id",              property = "id"),
        @Result(column = "institutionId",   property = "institutionId"),
        @Result(column = "loginId",         property = "loginId"),
        @Result(column = "pwd",             property = "pwd"),
        @Result(column = "name",            property = "name"),
        @Result(column = "isActive",        property = "isActive"),
        @Result(column = "createdAt",       property = "createdAt"),
        @Result(column = "institutionName", property = "institutionName"),
        @Result(column = "institutionCode", property = "institutionCode")
    })
    InstitutionAdmin findByLoginId(@Param("loginId") String loginId);

    /** 특정 기관의 관리자 목록 */
    @Select(BASE_SELECT + "WHERE a.institution_id = #{institutionId} ORDER BY a.created_at")
    List<InstitutionAdmin> findByInstitutionId(@Param("institutionId") int institutionId);

    /** 전체 관리자 목록 (슈퍼 어드민 관리 화면용) */
    @Select(BASE_SELECT + "ORDER BY i.name, a.created_at")
    List<InstitutionAdmin> findAll();

    @Select(BASE_SELECT + "WHERE a.id = #{id}")
    InstitutionAdmin findById(@Param("id") int id);

    // ── 생성 ─────────────────────────────────────────────

    @Insert("""
        INSERT INTO personnel_evaluation.institution_admins
            (institution_id, login_id, pwd, name, is_active)
        VALUES
            (#{institutionId}, #{loginId}, #{pwd}, #{name}, #{isActive})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InstitutionAdmin admin);

    // ── 수정 ─────────────────────────────────────────────

    @Update("""
        UPDATE personnel_evaluation.institution_admins
        SET name = #{name}, is_active = #{isActive}
        WHERE id = #{id}
        """)
    int update(InstitutionAdmin admin);

    /** 비밀번호 재설정 */
    @Update("""
        UPDATE personnel_evaluation.institution_admins
        SET pwd = #{pwd}
        WHERE id = #{id}
        """)
    int updatePassword(@Param("id") int id, @Param("pwd") String pwd);

    /** 비활성화 */
    @Update("UPDATE personnel_evaluation.institution_admins SET is_active = 0 WHERE id = #{id}")
    int deactivate(@Param("id") int id);

    /** 활성화 */
    @Update("UPDATE personnel_evaluation.institution_admins SET is_active = 1 WHERE id = #{id}")
    int activate(@Param("id") int id);
}
