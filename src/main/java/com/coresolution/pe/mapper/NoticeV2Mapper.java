package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.NoticeV2;

@Mapper
public interface NoticeV2Mapper {

  @Select("""
      SELECT id, eval_year AS evalYear, title, body_md AS bodyMd, pinned, sort_order AS sortOrder,
             publish_from AS publishFrom, publish_to AS publishTo, is_active AS isActive,
             version_tag AS versionTag, created_at AS createdAt, updated_at AS updatedAt
      FROM personnel_evaluation.notice_v2
      WHERE is_active = 1
        AND (eval_year = #{evalYear} OR #{evalYear} = 0)
        AND (publish_from IS NULL OR publish_from <= NOW())
        AND (publish_to   IS NULL OR publish_to   >= NOW())
      ORDER BY pinned DESC, sort_order ASC, id DESC
      """)
  List<NoticeV2> findActive(@Param("evalYear") int evalYear);

  @Select("""
      SELECT id, eval_year AS evalYear, title, body_md AS bodyMd, pinned, sort_order AS sortOrder,
             publish_from AS publishFrom, publish_to AS publishTo, is_active AS isActive,
             version_tag AS versionTag, created_at AS createdAt, updated_at AS updatedAt
      FROM personnel_evaluation.notice_v2
      ORDER BY is_active DESC, pinned DESC, sort_order ASC, id DESC
      """)
  List<NoticeV2> findAllForAdmin();

  @Select("""
        SELECT id,
               eval_year     AS evalYear,
               title,
               body_md       AS bodyMd,
               pinned,
               sort_order    AS sortOrder,
               publish_from  AS publishFrom,
               publish_to    AS publishTo,
               is_active     AS isActive,
               version_tag   AS versionTag,
               created_at    AS createdAt,
               updated_at    AS updatedAt
        FROM personnel_evaluation.notice_v2
        ORDER BY is_active DESC, pinned DESC, sort_order ASC, id DESC
      """)
  List<NoticeV2> findAll();

  @Select("""
        SELECT id,
               eval_year     AS evalYear,
               title,
               body_md       AS bodyMd,
               pinned,
               sort_order    AS sortOrder,
               publish_from  AS publishFrom,
               publish_to    AS publishTo,
               is_active     AS isActive,
               version_tag   AS versionTag,
               created_at    AS createdAt,
               updated_at    AS updatedAt
        FROM personnel_evaluation.notice_v2 WHERE id = #{id}
      """)
  NoticeV2 findById(@Param("id") int id);

  @Insert("""
        INSERT INTO personnel_evaluation.notice_v2
          (eval_year, title, body_md, pinned, sort_order, publish_from, publish_to, is_active, version_tag)
        VALUES
          (#{evalYear}, #{title}, #{bodyMd}, #{pinned}, #{sortOrder}, #{publishFrom}, #{publishTo}, #{isActive}, #{versionTag})
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(NoticeV2 n);

  @Update("""
        UPDATE personnel_evaluation.notice_v2 SET
          eval_year=#{evalYear}, title=#{title}, body_md=#{bodyMd}, pinned=#{pinned},
          sort_order=#{sortOrder}, publish_from=#{publishFrom}, publish_to=#{publishTo},
          is_active=#{isActive}, version_tag=#{versionTag}
        WHERE id=#{id}
      """)
  int update(NoticeV2 n);

  @Delete("DELETE FROM personnel_evaluation.notice_v2 WHERE id=#{id}")
  int delete(@Param("id") int id);
}