package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.AdminCustomTarget;
import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.TargetRow;
import com.coresolution.pe.entity.UserPE;

@Mapper
public interface CustomTargetMapper {

  @Select("SELECT * FROM personnel_evaluation.admin_custom_targets "
      + "WHERE eval_year=#{year} AND user_id=#{userId}")
  List<AdminCustomTarget> findByUserAndYear(@Param("userId") String userId,
      @Param("year") String year);

  // @Insert("INSERT INTO
  // personnel_evaluation.admin_custom_targets(eval_year,user_id,target_id,action)
  // "
  // + "VALUES(#{year},#{userId},#{targetId},#{action}) "
  // + "ON DUPLICATE KEY UPDATE is_action=#{action}")
  // void upsert(@Param("year") String year,
  // @Param("userId") String userId,
  // @Param("targetId") String targetId,
  // @Param("action") String action);

  @Delete("DELETE FROM personnel_evaluation.admin_custom_targets "
      + "WHERE eval_year=#{year} AND user_id=#{userId}")
  void deleteAllForUser(@Param("userId") String userId,
      @Param("year") String year);

  /** ADD 된 대상 ID 만 */
  @Select("SELECT target_id FROM personnel_evaluation.admin_custom_targets "
      + "WHERE eval_year=#{year} AND user_id=#{userId} AND is_active=1")
  List<String> findAddTargetIds(
      @Param("userId") String userId,
      @Param("year") String year);

  /** REMOVE 된 대상 ID 만 */
  @Select("SELECT target_id FROM personnel_evaluation.admin_custom_targets "
      + "WHERE eval_year=#{year} AND user_id=#{userId} AND is_active=0")
  List<String> findRemoveTargetIds(
      @Param("userId") String userId,
      @Param("year") String year);

  // ADD: is_active=1 인 커스텀 추가 대상 ID 목록
  @Select("""
      SELECT ct.target_id
      FROM personnel_evaluation.admin_custom_targets ct
      WHERE ct.eval_year = #{year}
        AND ct.user_id  = #{userId}
        AND ct.is_active = 1
      """)
  List<String> findCustomAdds(
      @Param("userId") String userId,
      @Param("year") String year);

  // REMOVE: is_active=0 인 제외 대상 ID 목록
  @Select("""
      SELECT target_id AS targetId
        FROM personnel_evaluation.admin_custom_targets
       WHERE eval_year = #{year}
         AND user_id   = #{userId}
         AND is_active = 0
      """)
  List<String> findCustomRemoves(
      @Param("userId") String userId,
      @Param("year") String year);

  // @Insert("""
  // INSERT INTO admin_custom_targets(eval_year, user_id, target_id, action)
  // VALUES (#{year}, #{userId}, #{targetId}, #{action})
  // ON DUPLICATE KEY UPDATE is_active = #{action}
  // """)
  // void insertCustomTarget(@Param("userId") String userId,
  // @Param("targetId") String targetId,
  // @Param("year") String year,
  // @Param("action") String action);

  @Delete("DELETE FROM personnel_evaluation.admin_custom_targets WHERE eval_year=#{year}")
  int deleteAllByYear(@Param("year") String year);

  // @Insert("INSERT INTO personnel_evaluation.admin_custom_targets\n" + //
  // " (eval_year, user_id, target_id, action, data_ev, data_type, form_id,
  // reason, created_at, updated_at)\n" + //
  // " VALUES\n" + //
  // " (#{year}, #{userId}, #{targetId}, #{action}, #{dataEv}, #{dataType},
  // #{formId}, #{reason}, NOW(), NOW())\n"
  // + //
  // " ON DUPLICATE KEY UPDATE\n" + //
  // " action = VALUES(action),\n" + //
  // " data_ev = VALUES(data_ev),\n" + //
  // " data_type= VALUES(data_type),\n" + //
  // " form_id = VALUES(form_id),\n" + //
  // " reason = VALUES(reason),\n" + //
  // " updated_at = NOW()")
  // int upsertCustom(@Param("userId") String userId,
  // @Param("year") String year,
  // @Param("targetId") String targetId,
  // @Param("action") String action, // ADD | REMOVE | OVERRIDE
  // @Param("dataEv") String dataEv, // nullable
  // @Param("dataType") String dataType, // nullable
  // @Param("formId") Long formId, // nullable
  // @Param("reason") String reason); // nullable

  @Delete("DELETE FROM personnel_evaluation.admin_custom_targets\n" + //
      "    WHERE eval_year = #{year}\n" + //
      "      AND user_id  = #{userId}\n" + //
      "      AND target_id = #{targetId}")
  int deleteCustom(@Param("userId") String userId,
      @Param("year") String year,
      @Param("targetId") String targetId);

  @Insert("""
      INSERT INTO personnel_evaluation.admin_custom_targets
        (eval_year, user_id, target_id, eval_type_code, is_active, data_ev, data_type, reason, created_at, updated_at)
      VALUES
        (#{year}, #{userId}, #{targetId}, #{evalTypeCode}, 1, #{dataEv}, #{dataType}, #{reason}, NOW(), NOW())
      ON DUPLICATE KEY UPDATE
        is_active = 1,
      eval_type_code = VALUES(eval_type_code),
        data_ev   = VALUES(data_ev),
        data_type = VALUES(data_type),
        reason    = VALUES(reason),
        updated_at = NOW()
      """)
  int insertCustom(
      @Param("userId") String userId,
      @Param("year") String year,
      @Param("targetId") String targetId,
      @Param("evalTypeCode") String evalTypeCode, // ★ 추가
      @Param("dataEv") String dataEv,
      @Param("dataType") String dataType,
      @Param("reason") String reason);

  // 커스텀 대상 -> UserPE 목록
  @Select("""
      SELECT u.*
      FROM personnel_evaluation.admin_custom_targets c
      JOIN personnel_evaluation.users_${year} u
        ON u.id = c.target_id AND u.eval_year = c.eval_year
      WHERE c.eval_year = #{year}
        AND c.user_id   = #{userId}
        AND c.is_active = 1
      """)
  List<UserPE> findCustomTargets(@Param("userId") String userId, @Param("year") String year);

  // 커스텀 대상 상세 (부서명, 직위, 평가유형 포함)
  @Select("""
      SELECT u.*,
             s.sub_name        AS subName,
             c.eval_type_code  AS evalTypeCode
        FROM personnel_evaluation.admin_custom_targets c
        JOIN personnel_evaluation.users_${year} u
          ON u.id = c.target_id AND u.eval_year = c.eval_year
   LEFT JOIN personnel_evaluation.sub_management s
          ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
       WHERE c.eval_year = #{year}
         AND c.user_id   = #{userId}
         AND c.is_active = 1
      """)
  List<UserPE> findCustomTargetsDetailed(@Param("userId") String userId, @Param("year") String year);

  // 메타(유형/사유 등) 조회
  @Select("""
      SELECT c.target_id       AS targetId,
             c.eval_type_code  AS evalTypeCode,
             c.data_ev         AS dataEv,
             c.data_type       AS dataType,
             c.reason          AS reason
      FROM personnel_evaluation.admin_custom_targets c
      WHERE c.eval_year = #{year}
        AND c.user_id   = #{userId}
        AND c.is_active = 1
      """)
  List<PairMeta> findCustomMeta(@Param("userId") String userId, @Param("year") String year);

  @Select("""
      SELECT c.target_id
        FROM personnel_evaluation.admin_custom_targets c
       WHERE c.eval_year = #{year}
         AND c.user_id   = #{userId}
         AND c.is_active = 0
      """)
  List<String> findCustomRemoveIds(@Param("userId") String userId,
      @Param("year") String year);

  // upsert(ADD)
  @Insert("""
      INSERT INTO personnel_evaluation.admin_custom_targets
        (eval_year, user_id, target_id, eval_type_code, is_active, data_ev, data_type, reason, created_at, updated_at)
      VALUES
        (#{year}, #{userId}, #{targetId}, #{evalTypeCode}, 1, #{dataEv}, #{dataType}, #{reason}, NOW(), NOW())
      ON DUPLICATE KEY UPDATE
        eval_type_code = VALUES(eval_type_code),
        is_active      = 1,
        data_ev        = VALUES(data_ev),
        data_type      = VALUES(data_type),
        reason         = VALUES(reason),
        updated_at     = NOW()
      """)
  int upsertCustomAdd(@Param("userId") String userId,
      @Param("year") String year,
      @Param("targetId") String targetId,
      @Param("evalTypeCode") String evalTypeCode,
      @Param("dataEv") String dataEv,
      @Param("dataType") String dataType,
      @Param("reason") String reason);

  // upsert(REMOVE) : 카테고리 제거하고 is_active=0
  @Insert("""
      INSERT INTO personnel_evaluation.admin_custom_targets
        (eval_year, user_id, target_id, eval_type_code, is_active, reason, created_at, updated_at)
      VALUES
        (#{year}, #{userId}, #{targetId}, NULL, 0, #{reason}, NOW(), NOW())
      ON DUPLICATE KEY UPDATE
        eval_type_code = NULL,
        is_active      = 0,
        reason         = VALUES(reason),
        updated_at     = NOW()
      """)
  int upsertCustomRemove(@Param("userId") String userId,
      @Param("year") String year,
      @Param("targetId") String targetId,
      @Param("reason") String reason);

  @Select("""
      SELECT  u.*,
              s.sub_name  AS subName,
              t.team_name AS teamName,
              c.eval_type_code AS evalTypeCode
      FROM personnel_evaluation.admin_custom_targets c
      JOIN personnel_evaluation.users_${year} u
        ON u.id = c.target_id AND u.eval_year = c.eval_year
      LEFT JOIN personnel_evaluation.sub_management s
        ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation.team t
        ON t.team_code = u.team_code AND t.eval_year = u.eval_year
      WHERE c.user_id   = #{userId}
        AND c.eval_year = #{year}
        AND c.is_active = 1
      """)
  @Results(id = "userPERes_customTargets", value = {
      @Result(column = "subName", property = "subName"),
      @Result(column = "teamName", property = "teamName"),
      @Result(column = "evalTypeCode", property = "evalTypeCode")
  })
  List<UserPE> findCustomAddsDetailed(@Param("userId") String userId,
      @Param("year") String year);

  // ★ 커스텀 추가 대상 비활성화: is_active=0으로 UPDATE (REMOVE 기록 유지)
  @Update("""
      UPDATE personnel_evaluation.admin_custom_targets
         SET is_active  = 0,
             reason     = #{reason},
             updated_at = NOW()
       WHERE eval_year  = #{year}
         AND user_id    = #{userId}
         AND target_id  = #{targetId}
      """)
  int deactivateCustom(@Param("userId") String userId,
      @Param("year") String year,
      @Param("targetId") String targetId,
      @Param("reason") String reason);

  // (선택) 물리삭제가 필요하면 추가
  @Delete("""
      DELETE FROM personnel_evaluation.admin_custom_targets
       WHERE eval_year = #{year}
         AND user_id   = #{userId}
         AND target_id = #{targetId}
      """)
  int hardDeleteCustom(@Param("userId") String userId,
      @Param("year") String year,
      @Param("targetId") String targetId);

  @Select("""
          SELECT target_id AS targetId,
        eval_type_code AS evalTypeCode,
        data_ev        AS dataEv,
        data_type      AS dataType
            FROM personnel_evaluation.admin_custom_targets
          WHERE eval_year = #{year}
            AND user_id   = #{userId}
            AND is_active = 1
      """)
  List<TargetRow> findActiveAddsWithType(@Param("userId") String userId, @Param("year") String year);

  @Select("""
          SELECT target_id AS targetId
          FROM personnel_evaluation.admin_custom_targets
          WHERE eval_year = #{year}
            AND user_id   = #{userId}
            AND is_active = 0
      """)
  List<String> findActiveRemoves(@Param("userId") String userId, @Param("year") String year);

  /** 기관 소속 평가자별 활성 대상 수 집계 (inst-admin 화면용) */
  @Select("""
      SELECT
          u.id        AS id,
          u.name      AS name,
          u.position  AS position,
          s.sub_name  AS subName,
          (SELECT COUNT(*)
           FROM   personnel_evaluation.admin_custom_targets ct2
           WHERE  ct2.user_id   = u.id
             AND  ct2.eval_year = #{year}
             AND  ct2.is_active = 1) AS targetCount
      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.sub_management s
             ON s.sub_code  = u.sub_code
            AND s.eval_year = u.eval_year
      WHERE u.eval_year = #{year}
        AND u.del_yn    = 'N'
        AND u.c_name    = #{orgName}
      ORDER BY s.sub_name, u.name
      """)
  List<UserPE> getEvaluatorsWithTargetCount(
      @Param("year") String year,
      @Param("orgName") String orgName);

  /** 기관 전체 활성 평가 대상 쌍 수 */
  @Select("""
      SELECT COUNT(*)
      FROM   personnel_evaluation.admin_custom_targets ct
      INNER JOIN personnel_evaluation.users_${year} u
             ON  u.id        = ct.user_id
            AND  u.eval_year = ct.eval_year
      WHERE  ct.eval_year = #{year}
        AND  ct.is_active  = 1
        AND  u.c_name      = #{orgName}
      """)
  int countTargetsByOrg(
      @Param("year") String year,
      @Param("orgName") String orgName);
}
