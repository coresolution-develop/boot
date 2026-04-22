package com.coresolution.pe.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.EndLetter;

@Mapper
public interface AffEndLetterMapper {

    @Select("""
            SELECT id, eval_year AS evalYear, institution_name AS institutionName,
                   content, bg_image_url AS bgImageUrl, updated_at AS updatedAt
              FROM personnel_evaluation_aff.end_letter
             WHERE eval_year = #{year}
               AND institution_name = #{institution}
             LIMIT 1
            """)
    EndLetter findByYearAndInstitution(@Param("year") int year,
                                       @Param("institution") String institution);

    @Insert("""
            INSERT INTO personnel_evaluation_aff.end_letter
                   (eval_year, institution_name, content, bg_image_url)
            VALUES (#{evalYear}, #{institutionName}, #{content}, #{bgImageUrl})
            ON DUPLICATE KEY UPDATE
                   content      = VALUES(content),
                   bg_image_url = VALUES(bg_image_url),
                   updated_at   = CURRENT_TIMESTAMP
            """)
    int upsert(EndLetter letter);
}
