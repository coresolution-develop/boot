package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.AdminDefaultTarget;
import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.TargetRow;
import com.coresolution.pe.entity.UserPE;

@Mapper
public interface DefaultTargetMapper {
  // 부서별 기본 대상 조회
  @Select("SELECT target_id FROM personnel_evaluation.admin_default_dept_targets WHERE eval_year=#{year}")
  List<String> findDeptTargetIds(@Param("year") String year);

  @Delete("DELETE FROM personnel_evaluation.admin_default_dept_targets WHERE eval_year=#{year}")
  void deleteAllDept(@Param("year") String year);

  @Insert("INSERT INTO personnel_evaluation.admin_default_dept_targets(eval_year, target_id) VALUES(#{year},#{targetId})")
  void insertDeptTarget(@Param("year") String year, @Param("targetId") String targetId);

  // 팀별 기본 대상 조회
  @Select("SELECT target_id FROM personnel_evaluation.admin_default_team_targets WHERE eval_year=#{year}")
  List<String> findTeamTargetIds(@Param("year") String year);

  @Delete("DELETE FROM personnel_evaluation.admin_default_team_targets WHERE eval_year=#{year}")
  void deleteAllTeam(@Param("year") String year);

  @Insert("INSERT INTO personnel_evaluation.admin_default_team_targets(eval_year, target_id) VALUES(#{year},#{targetId})")
  void insertTeamTarget(@Param("year") String year, @Param("targetId") String targetId);

  /** 부서별 기본 대상 전체 삭제 */
  @Delete("DELETE FROM personnel_evaluation.admin_default_dept_targets WHERE eval_year = #{year}")
  void deleteAllForDept(@Param("year") String year);

  /** 팀별 기본 대상 전체 삭제 */
  @Delete("DELETE FROM personnel_evaluation.admin_default_team_targets WHERE eval_year = #{year}")
  void deleteAllForTeam(@Param("year") String year);

  @Delete("DELETE FROM personnel_evaluation.admin_default_targets WHERE eval_year = #{year}")
  void deleteAllForYear(@Param("year") String year);

  @Insert("""
      INSERT INTO personnel_evaluation.admin_default_targets(eval_year, user_id, target_id)
      VALUES(#{year}, #{userId}, #{targetId})
      """)
  void insert(@Param("year") String year,
      @Param("userId") String userId,
      @Param("targetId") String targetId);

  @Delete("""
          DELETE FROM personnel_evaluation.admin_default_targets
           WHERE eval_year = #{year}
             AND user_id  = #{userId}
      """)
  int deleteByUserAndYear(@Param("userId") String userId,
      @Param("year") String year);

  @Insert({
      "<script>",
      "INSERT INTO personnel_evaluation.admin_default_targets (eval_year, user_id, target_id) VALUES",
      "<foreach collection='targetIds' item='tid' separator=','>",
      "(#{year}, #{userId}, #{tid})",
      "</foreach>",
      "</script>"
  })
  int insertAll(@Param("userId") String userId,
      @Param("year") String year,
      @Param("targetIds") List<String> targetIds);

  @Insert("""
          INSERT INTO personnel_evaluation.admin_default_targets
            (eval_year, user_id, target_id, eval_type_code, data_ev, data_type, form_id, updated_at)
          VALUES
            (#{evalYear}, #{userId}, #{targetId}, #{evalTypeCode}, #{dataEv}, #{dataType}, #{formId}, NOW())
          ON DUPLICATE KEY UPDATE
            eval_type_code = VALUES(eval_type_code),
            data_ev       = VALUES(data_ev),
            data_type     = VALUES(data_type),
            form_id       = VALUES(form_id),
            updated_at    = NOW()
      """)
  int upsert(AdminDefaultTarget row);

  @Select("""
        SELECT target_id, data_ev AS dataEv, data_type AS dataType, form_id
          FROM personnel_evaluation.admin_default_targets
         WHERE eval_year = #{year} AND user_id = #{userId}
      """)
  List<Map<String, Object>> findMetaForUser(@Param("userId") String userId,
      @Param("year") String year);

  @Select("""
        SELECT target_id AS targetId,
               data_ev   AS dataEv,
               data_type AS dataType,
               form_id   AS formId
          FROM personnel_evaluation.admin_default_targets
         WHERE eval_year = #{year}
           AND user_id   = #{userId}
      """)
  List<PairMeta> findMetaForUserTyped(@Param("userId") String userId,
      @Param("year") String year);

  @Select("""
        SELECT 1
          FROM personnel_evaluation.admin_default_targets
         WHERE eval_year=#{year}
           AND user_id=#{userId}
           AND target_id=#{targetId}
           AND data_ev=#{dataEv}
           AND data_type=#{dataType}
         LIMIT 1
      """)
  Boolean existsMeta(@Param("userId") String userId,
      @Param("targetId") String targetId,
      @Param("year") String year,
      @Param("dataEv") String dataEv,
      @Param("dataType") String dataType);

  @Select("SELECT eval_year, user_id, target_id, eval_type_code, data_ev, data_type, form_id\n" + //
      "    FROM personnel_evaluation.admin_default_targets\n" + //
      "    WHERE eval_year = #{year}\n" + //
      "      AND user_id  = #{userId}\n" + //
      "    ORDER BY target_id")
  List<AdminDefaultTarget> findByUserAndYear(@Param("userId") String userId, @Param("year") String year);

  @Select("SELECT EXISTS(\n" + //
      "      SELECT 1 FROM personnel_evaluation.admin_default_targets\n" + //
      "       WHERE eval_year = #{year}\n" + //
      "         AND user_id = #{userId}\n" + //
      "         AND target_id = #{targetId}\n" + //
      "    )")
  boolean existsPair(@Param("userId") String userId,
      @Param("year") String year,
      @Param("targetId") String targetId);

  @Select("""
      SELECT
        u.id, u.name, u.position,
        u.sub_code, s.sub_name AS subName,
        u.team_code, t.team_name AS teamName,
        dt.eval_type_code AS evalTypeCode
      FROM personnel_evaluation.admin_default_targets dt
      JOIN personnel_evaluation.users_${year} u
        ON u.id = dt.target_id AND u.eval_year = dt.eval_year
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation.team t
        ON t.team_code = u.team_code AND t.eval_year = u.eval_year
      WHERE dt.eval_year = #{year}
        AND dt.user_id  = #{userId}
      """)
  List<UserPE> findDefaultTargets(@Param("userId") String userId, @Param("year") String year);

  @Select("""
      SELECT d.target_id      AS targetId,
             d.eval_type_code AS evalTypeCode,
             d.data_ev        AS dataEv,
             d.data_type      AS dataType,
             NULL             AS reason
      FROM personnel_evaluation.admin_default_targets d
      WHERE d.eval_year = #{year}
        AND d.user_id   = #{userId}
      """)
  List<PairMeta> findDefaultMeta(@Param("userId") String userId, @Param("year") String year);

  @Select("""
      SELECT  u.*,
              s.sub_name  AS subName,
              t.team_name AS teamName,
              d.eval_type_code AS evalTypeCode
      FROM personnel_evaluation.admin_default_targets d
      JOIN personnel_evaluation.users_${year} u
        ON u.id = d.target_id AND u.eval_year = d.eval_year
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation.team t
        ON t.team_code = u.team_code AND t.eval_year = u.eval_year
      WHERE d.user_id = #{userId}
        AND d.eval_year = #{year}
      """)
  @Results(id = "userPERes_defaultTargets", value = {
      @Result(column = "subName", property = "subName"),
      @Result(column = "teamName", property = "teamName"),
      @Result(column = "evalTypeCode", property = "evalTypeCode")
  })
  List<UserPE> findDefaultTargetsDetailed(@Param("userId") String userId,
      @Param("year") String year);

  @Select("""
          SELECT
          target_id AS targetId, eval_type_code AS evalTypeCode,
          data_ev        AS dataEv,
          data_type      AS dataType
          FROM personnel_evaluation.admin_default_targets
          WHERE eval_year = #{year} AND user_id = #{userId}
      """)
  List<TargetRow> findTargetsWithType(@Param("userId") String userId, @Param("year") String year);
}
