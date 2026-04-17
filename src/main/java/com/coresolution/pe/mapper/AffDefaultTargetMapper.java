package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.AdminDefaultTarget;
import com.coresolution.pe.entity.AffAdminDefaultTarget;
import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.TargetRow;
import com.coresolution.pe.entity.UserPE;

@Mapper
public interface AffDefaultTargetMapper {

        @Delete("""
                          DELETE FROM personnel_evaluation_aff.admin_default_targets
                           WHERE eval_year = #{year}
                             AND user_id   = #{userId}
                        """)
        int deleteByUserAndYear(@Param("userId") String userId,
                        @Param("year") int year);

        @Insert("""
                        INSERT INTO personnel_evaluation_aff.admin_default_targets
                         (eval_year, user_id, target_id, eval_type_code, data_ev, data_type, updated_at)
                        VALUES
                         (#{evalYear}, #{userId}, #{targetId}, #{evalTypeCode}, #{dataEv}, #{dataType}, NOW())
                        ON DUPLICATE KEY UPDATE
                         eval_type_code = VALUES(eval_type_code),
                         data_ev        = VALUES(data_ev),
                         data_type      = VALUES(data_type),
                         updated_at     = NOW()
                        """)
        int upsert(AffAdminDefaultTarget row);

        // 필요시 조회용들(화면 디버깅)
        @Select("""
                           SELECT  u.*,
                                   s.sub_name  AS subName,
                                   t.team_name AS teamName,
                                   d.eval_type_code AS evalTypeCode
                             FROM personnel_evaluation_aff.admin_default_targets d
                             JOIN personnel_evaluation_aff.users_${year} u
                               ON u.id = d.target_id AND u.eval_year = d.eval_year
                        LEFT JOIN personnel_evaluation_aff.sub_management s
                               ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                        LEFT JOIN personnel_evaluation_aff.team t
                               ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                            WHERE d.user_id = #{userId}
                              AND d.eval_year = #{year} AND u.del_yn = 'N'
                         """)
        @Results(id = "affUserPERes_defaultTargets", value = {
                        @Result(column = "subName", property = "subName"),
                        @Result(column = "teamName", property = "teamName"),
                        @Result(column = "evalTypeCode", property = "evalTypeCode")
        })
        List<UserPE> findDefaultTargetsDetailed(@Param("userId") String userId,
                        @Param("year") int year);

        // 기본 대상의 (타입/메타)만
        @Select("""
                        SELECT  d.target_id      AS targetId,
                                d.eval_type_code AS evalTypeCode,
                                d.data_ev        AS dataEv,
                                d.data_type      AS dataType
                          FROM personnel_evaluation_aff.admin_default_targets d
                         WHERE d.eval_year = #{year}
                           AND d.user_id   = #{userId}
                        """)
        List<PairMeta> findDefaultMeta(@Param("userId") String userId,
                        @Param("year") int year);

        // targetId ↔ eval_type_code 쌍 (병합 때 사용)
        @Select("""
                        SELECT target_id AS targetId, eval_type_code AS evalTypeCode
                          FROM personnel_evaluation_aff.admin_default_targets
                         WHERE eval_year = #{year} AND user_id = #{userId}
                        """)
        List<TargetRow> findTargetsWithType(@Param("userId") String userId,
                        @Param("year") int year);
}
