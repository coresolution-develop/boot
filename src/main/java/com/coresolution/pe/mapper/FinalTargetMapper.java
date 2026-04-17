package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.UserPE;

@Mapper
public interface FinalTargetMapper {
    @Select("""
              SELECT target_id
                FROM personnel_evaluation.admin_default_targets
               WHERE eval_year = #{year}
                 AND user_id = #{userId}
               ORDER BY target_id
            """)
    List<String> findTargetIds(@Param("userId") String userId, @Param("year") String year);

    @Select("""
            SELECT  u.*,
                    s.sub_name  AS subName,
                    t.team_name AS teamName
            FROM personnel_evaluation.admin_default_targets d
            JOIN personnel_evaluation.users_${year} u
              ON u.id = d.target_id
             AND u.eval_year = d.eval_year
            LEFT JOIN personnel_evaluation.sub_management s
              ON s.sub_code  = u.sub_code
             AND s.eval_year = d.eval_year     -- ★ 여기 추가
            LEFT JOIN personnel_evaluation.team t
              ON t.team_code = u.team_code
              AND t.eval_year = d.eval_year   -- (team 테이블도 연도 구분이면 이 줄도 추가)
            WHERE d.eval_year = #{year}
              AND d.user_id   = #{userId}
            ORDER BY d.target_id
            """)
    @Results(id = "userPERes", value = {
            @Result(column = "subName", property = "subName"),
            @Result(column = "teamName", property = "teamName")
    })
    List<UserPE> findTargetsWithNames(@Param("userId") String userId,
            @Param("year") String year);
}
