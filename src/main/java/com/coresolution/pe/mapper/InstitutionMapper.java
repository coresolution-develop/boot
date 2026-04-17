package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.Institution;

@Mapper
public interface InstitutionMapper {

    // ── 공통 ResultMap 정의 ───────────────────────────────

    String BASE_SELECT =
        "SELECT id, code, name, is_active AS isActive, created_at AS createdAt " +
        "FROM personnel_evaluation.institutions ";

    // ── 조회 ─────────────────────────────────────────────

    @Select(BASE_SELECT + "ORDER BY name")
    @Results(id = "institutionResult", value = {
        @Result(column = "id",         property = "id"),
        @Result(column = "code",       property = "code"),
        @Result(column = "name",       property = "name"),
        @Result(column = "isActive",   property = "isActive"),
        @Result(column = "createdAt",  property = "createdAt")
    })
    List<Institution> findAll();

    @Select(BASE_SELECT + "WHERE is_active = 1 ORDER BY name")
    List<Institution> findAllActive();

    @Select(BASE_SELECT + "WHERE id = #{id}")
    Institution findById(@Param("id") int id);

    @Select(BASE_SELECT + "WHERE name = #{name}")
    Institution findByName(@Param("name") String name);

    @Select(BASE_SELECT + "WHERE code = #{code}")
    Institution findByCode(@Param("code") String code);

    // ── 생성 ─────────────────────────────────────────────

    @Insert("""
        INSERT INTO personnel_evaluation.institutions (code, name, is_active)
        VALUES (#{code}, #{name}, #{isActive})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Institution institution);

    // ── 수정 ─────────────────────────────────────────────

    @Update("""
        UPDATE personnel_evaluation.institutions
        SET code = #{code}, name = #{name}, is_active = #{isActive}
        WHERE id = #{id}
        """)
    int update(Institution institution);

    /** 기관 비활성화 (소프트 삭제) */
    @Update("UPDATE personnel_evaluation.institutions SET is_active = 0 WHERE id = #{id}")
    int deactivate(@Param("id") int id);

    /** 기관 활성화 */
    @Update("UPDATE personnel_evaluation.institutions SET is_active = 1 WHERE id = #{id}")
    int activate(@Param("id") int id);
}
