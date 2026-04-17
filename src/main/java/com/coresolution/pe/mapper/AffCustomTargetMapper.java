package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.TargetRow;
import com.coresolution.pe.entity.UserPE;

@Mapper
public interface AffCustomTargetMapper {

    // 커스텀 ADD(활성) 상세
    @Select("""
               SELECT  u.*,
                       s.sub_name  AS subName,
                       t.team_name AS teamName,
                       c.eval_type_code AS evalTypeCode
                 FROM personnel_evaluation_aff.admin_custom_targets c
                 JOIN personnel_evaluation_aff.users_${year} u
                   ON u.id = c.target_id AND u.eval_year = c.eval_year
            LEFT JOIN personnel_evaluation_aff.sub_management s
                   ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
            LEFT JOIN personnel_evaluation_aff.team t
                   ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                WHERE c.eval_year = #{year}
                  AND c.user_id   = #{userId}
                  AND c.is_active = 1
                  AND u.del_yn = 'N'
               """)
    List<UserPE> findCustomTargetsDetailed(@Param("userId") String userId,
            @Param("year") int year);

    // 커스텀 ADD: targetId + type
    @Select("""
            SELECT target_id AS targetId, eval_type_code AS evalTypeCode
              FROM personnel_evaluation_aff.admin_custom_targets
             WHERE eval_year = #{year}
               AND user_id   = #{userId}
               AND is_active = 1
            """)
    List<TargetRow> findActiveAddsWithType(@Param("userId") String userId,
            @Param("year") int year);

    // 커스텀 REMOVE: targetId 만
    @Select("""
            SELECT target_id
              FROM personnel_evaluation_aff.admin_custom_targets
             WHERE eval_year = #{year}
               AND user_id   = #{userId}
               AND is_active = 0
            """)
    List<String> findActiveRemoves(@Param("userId") String userId,
            @Param("year") int year);

    // 커스텀 메타(사유 포함)
    @Select("""
            SELECT c.target_id      AS targetId,
                   c.eval_type_code AS evalTypeCode,
                   c.data_ev        AS dataEv,
                   c.data_type      AS dataType,
                   c.reason         AS reason
              FROM personnel_evaluation_aff.admin_custom_targets c
             WHERE c.eval_year = #{year}
               AND c.user_id   = #{userId}
               AND c.is_active = 1
            """)
    List<PairMeta> findCustomMeta(@Param("userId") String userId,
            @Param("year") int year);

    // ✅ 커스텀 대상 INSERT (ON DUPLICATE KEY UPDATE로 upsert)
    @Insert("""
            INSERT INTO personnel_evaluation_aff.admin_custom_targets
                 (eval_year,
                  user_id,
                  target_id,
                  eval_type_code,
                  data_ev,
                  data_type,
                  reason,
                  is_active,
                  updated_at)
            VALUES (#{year},
                    #{userId},
                    #{targetId},
                    #{evalTypeCode},
                    #{dataEv},
                    #{dataType},
                    #{reason},
                    1,
                    NOW())
            ON DUPLICATE KEY UPDATE
                eval_type_code = VALUES(eval_type_code),
                data_ev        = VALUES(data_ev),
                data_type      = VALUES(data_type),
                reason         = VALUES(reason),
                is_active      = 1,
                updated_at     = NOW()
            """)
    int insertCustom(@Param("userId") String userId,
            @Param("year") int year,
            @Param("targetId") String targetId,
            @Param("evalTypeCode") String evalTypeCode,
            @Param("dataEv") String dataEv,
            @Param("dataType") String dataType,
            @Param("reason") String reason);

    // ✅ 커스텀 대상 비활성화 (삭제 대신 is_active=0)
    @Update("""
            UPDATE personnel_evaluation_aff.admin_custom_targets
               SET is_active = 0,
                   reason    = #{reason},
                   updated_at = NOW()
             WHERE eval_year = #{year}
               AND user_id   = #{userId}
               AND target_id = #{targetId}
            """)
    int deactivateCustom(@Param("userId") String userId,
            @Param("year") int year,
            @Param("targetId") String targetId,
            @Param("reason") String reason);
}