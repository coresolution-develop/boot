package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.ReleaseWindow;
import com.coresolution.pe.entity.ReleaseWindowRow;

@Mapper
public interface AffReleaseWindowMapper {

  @Select("""
        SELECT id, eval_year AS evalYear, data_type AS dataType, data_ev AS dataEv,
               c_name AS cName, sub_code AS subCode, open_at AS openAt, close_at AS closeAt,
               enabled, del_yn AS delYn, created_by AS createdBy, created_at AS createdAt,
               updated_by AS updatedBy, updated_at AS updatedAt
          FROM personnel_evaluation_aff.evaluation_release_window
         WHERE del_yn='N'
           AND (#{year} IS NULL OR eval_year = #{year})
           AND (#{dataType} IS NULL OR data_type = #{dataType})
           AND (#{dataEv} IS NULL OR data_ev = #{dataEv})
           AND (#{cName} IS NULL OR c_name = #{cName})
           AND (#{subCode} IS NULL OR sub_code = #{subCode})
         ORDER BY eval_year DESC, data_type, data_ev, c_name, sub_code, open_at
         LIMIT #{limit} OFFSET #{offset}
      """)
  List<ReleaseWindowRow> findList(
      @Param("year") Integer year,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv,
      @Param("cName") String cName,
      @Param("subCode") String subCode,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Select("""
        SELECT COUNT(*) FROM personnel_evaluation_aff.evaluation_release_window
         WHERE del_yn='N'
           AND (#{year} IS NULL OR eval_year = #{year})
           AND (#{dataType} IS NULL OR data_type = #{dataType})
           AND (#{dataEv} IS NULL OR data_ev = #{dataEv})
           AND (#{cName} IS NULL OR c_name = #{cName})
           AND (#{subCode} IS NULL OR sub_code = #{subCode})
      """)
  int countList(@Param("year") Integer year,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv,
      @Param("cName") String cName,
      @Param("subCode") String subCode);

  @Select("""
        SELECT id, eval_year AS evalYear, data_type AS dataType, data_ev AS dataEv,
               c_name AS cName, sub_code AS subCode, open_at AS openAt, close_at AS closeAt,
               enabled, del_yn AS delYn, created_by AS createdBy, created_at AS createdAt,
               updated_by AS updatedBy, updated_at AS updatedAt
          FROM personnel_evaluation_aff.evaluation_release_window
         WHERE id = #{id} AND del_yn='N'
      """)
  ReleaseWindowRow findById(@Param("id") long id);

  @Insert("""
        INSERT INTO personnel_evaluation_aff.evaluation_release_window
          (eval_year, data_type, data_ev, c_name, sub_code,
           open_at, close_at, enabled, created_by, updated_by)
        VALUES
          (#{evalYear}, #{dataType}, #{dataEv}, #{cName}, #{subCode},
           #{openAt}, #{closeAt}, #{enabled}, #{createdBy}, #{updatedBy})
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertOne(ReleaseWindowRow row);

  @Update("""
        UPDATE personnel_evaluation_aff.evaluation_release_window
           SET open_at=#{openAt}, close_at=#{closeAt},
               enabled=#{enabled}, updated_by=#{updatedBy}, updated_at=NOW()
         WHERE id=#{id} AND del_yn='N'
      """)
  int updateById(ReleaseWindowRow row);

  // 공지처럼 “키 충돌 시 업데이트” (Upsert)
  @Insert("""
        INSERT INTO personnel_evaluation_aff.evaluation_release_window
          (eval_year, data_type, data_ev, c_name, sub_code,
           open_at, close_at, enabled, created_by, updated_by)
        VALUES
          (#{evalYear}, #{dataType}, #{dataEv}, #{cName}, #{subCode},
           #{openAt}, #{closeAt}, #{enabled}, #{createdBy}, #{updatedBy})
        ON DUPLICATE KEY UPDATE
          open_at=VALUES(open_at),
          close_at=VALUES(close_at),
          enabled=VALUES(enabled),
          updated_by=VALUES(updated_by),
          updated_at=NOW(),
          del_yn='N'
      """)
  int upsert(ReleaseWindowRow row);

  @Update("""
        UPDATE personnel_evaluation_aff.evaluation_release_window
           SET enabled=#{enabled}, updated_by=#{updatedBy}, updated_at=NOW()
         WHERE id=#{id} AND del_yn='N'
      """)
  int toggleEnabled(@Param("id") long id, @Param("enabled") boolean enabled,
      @Param("updatedBy") String updatedBy);

  @Update("""
        UPDATE personnel_evaluation_aff.evaluation_release_window
           SET del_yn='Y', updated_by=#{updatedBy}, updated_at=NOW()
         WHERE id=#{id} AND del_yn='N'
      """)
  int softDelete(@Param("id") long id, @Param("updatedBy") String updatedBy);

  // 게이트 체크용: 가장 구체적인 것 우선(부서>병원>전역)
  @Select("""
        SELECT id, eval_year AS evalYear, data_type AS dataType, data_ev AS dataEv,
               c_name AS cName, sub_code AS subCode, open_at AS openAt, close_at AS closeAt,
               enabled, del_yn AS delYn
          FROM personnel_evaluation_aff.evaluation_release_window
         WHERE del_yn='N'
           AND eval_year = #{year}
           AND data_type = #{dataType}
           AND data_ev   = #{dataEv}
           AND (c_name = #{cName} OR c_name = '')
           AND (sub_code = #{subCode} OR sub_code = '')
           AND enabled = 1
         ORDER BY (sub_code <> '') DESC, (c_name <> '') DESC
         LIMIT 1
      """)
  ReleaseWindowRow findBestMatch(@Param("year") int year,
      @Param("dataType") String dataType,
      @Param("dataEv") String dataEv,
      @Param("cName") String cName,
      @Param("subCode") String subCode);

  @Select("""
      SELECT id, eval_year AS evalYear, data_type AS dataType, data_ev AS dataEv,
             c_name AS cName, sub_code AS subCode, open_at AS openAt, close_at AS closeAt,
             enabled, del_yn AS delYn
      FROM personnel_evaluation_aff.evaluation_release_window
      WHERE del_yn='N'
        AND enabled = 1
        AND eval_year = #{year}
        AND data_type = #{dataType}
        AND (c_name = #{cName} OR c_name = '')
        AND (sub_code = #{subCode} OR sub_code = '')
      ORDER BY (sub_code <> '') DESC, (c_name <> '') DESC, open_at ASC
      LIMIT 1
      """)
  ReleaseWindow findNearest(@Param("year") int year,
      @Param("dataType") String dataType,
      @Param("cName") String cName,
      @Param("subCode") String subCode);
}
