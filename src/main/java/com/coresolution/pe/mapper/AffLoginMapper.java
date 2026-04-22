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

import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.NoticeVo;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;

@Mapper
public interface AffLoginMapper {

  // 1) 사번으로 유저 조회
  @Select("SELECT * FROM personnel_evaluation_aff.users_${year} WHERE id = #{id}")
  UserPE findById(@Param("id") String id, @Param("year") int year);

  // 1) 사번+이름으로 유저 조회
  @Select("SELECT COUNT(*) FROM personnel_evaluation_aff.users_${year} WHERE id = #{id} AND name = #{name}")
  int countByIdAndName(@Param("id") String id, @Param("name") String name, @Param("year") int year);

  // 2) 사번에 이미 비밀번호 설정됐는지 확인
  @Select("SELECT COUNT(*) FROM personnel_evaluation_aff.users_${year} WHERE id =#{id} AND pwd IS NOT NULL")
  int countPwdById(@Param("id") String id, @Param("year") int year);

  @Select("SELECT * FROM personnel_evaluation_aff.users_${year} WHERE idx = #{idx}")
  UserPE findUserInfoByIdx(@Param("idx") int idx, @Param("year") int year);

  // 4) 로그인 성공 시 유저 고유키(idx) 조회
  @Select("SELECT idx FROM personnel_evaluation_aff.users_${year} WHERE id = #{id}")
  Integer findIdxById(@Param("id") String id, @Param("year") int year);

  @Select("""
      <script>
      SELECT
          u.idx               AS idx,
          u.c_name            AS cName,
          u.c_name2           AS cName2,
          u.sub_code          AS subCode,
          s.sub_name          AS subName,
          u.id                AS id,
          u.position          AS position,
          u.name              AS name,
          u.pwd               AS pwd,
          u.create_at         AS createAt,
          u.delete_at         AS deleteAt,
          u.phone             AS phone,
          u.team_code         AS teamCode,
          t.team_name         AS teamName,
          u.del_yn            AS delYn,
          u.eval_year         AS evalYear,
          CASE WHEN COALESCE(u.pwd,'') &lt;&gt; '' THEN 1 ELSE 0 END AS passwordSet,
          (SELECT GROUP_CONCAT(r.role ORDER BY r.role SEPARATOR ',')
           FROM personnel_evaluation_aff.user_roles_${year} r
           WHERE r.user_id = u.id AND r.eval_year = #{year}) AS rolesCsv
      FROM personnel_evaluation_aff.users_${year} u
      LEFT JOIN personnel_evaluation_aff.sub_management s
             ON s.sub_code  = u.sub_code
            AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation_aff.team t
             ON t.team_code = u.team_code
            AND t.eval_year = u.eval_year
      WHERE u.eval_year = #{year}
      <if test="delYn != null and delYn != ''">
        AND u.del_yn = #{delYn}
      </if>
      <if test="q != null and q != ''">
        AND (
          u.id        LIKE CONCAT('%', #{q}, '%')
          OR u.name   LIKE CONCAT('%', #{q}, '%')
          OR s.sub_name  LIKE CONCAT('%', #{q}, '%')
          OR t.team_name LIKE CONCAT('%', #{q}, '%')
          OR u.position  LIKE CONCAT('%', #{q}, '%')
        )
      </if>
      <if test="org != null and org != ''">
        AND u.c_name = #{org}
      </if>
      <if test="dept != null and dept != ''">
        AND u.sub_code = #{dept}
      </if>
      <if test="pwd == 'set'">
        AND COALESCE(u.pwd,'') &lt;&gt; ''
      </if>
      <if test="pwd == 'unset'">
        AND (u.pwd IS NULL OR u.pwd = '')
      </if>
      <if test="role != null and role != ''">
        AND EXISTS (
          SELECT 1 FROM personnel_evaluation_aff.user_roles_${year} r2
          WHERE r2.user_id = u.id AND r2.role = #{role} AND r2.eval_year = #{year}
        )
      </if>
      ORDER BY s.sub_name ASC, u.name ASC
      LIMIT #{size} OFFSET #{offset}
      </script>
      """)
  List<UserPE> getUserListpage(
      @Param("year") String year,
      @Param("q") String q,
      @Param("dept") String dept,
      @Param("pwd") String pwd,
      @Param("org") String org,
      @Param("delYn") String delYn,
      @Param("role") String role,
      @Param("offset") int offset,
      @Param("size") int size);

  @Select("""
      <script>
      SELECT COUNT(1)
      FROM personnel_evaluation_aff.users_${year} u
      LEFT JOIN personnel_evaluation_aff.sub_management s
             ON s.sub_code  = u.sub_code
            AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation_aff.team t
             ON t.team_code = u.team_code
            AND t.eval_year = u.eval_year
      WHERE u.eval_year = #{year}
      <if test="delYn != null and delYn != ''">
        AND u.del_yn = #{delYn}
      </if>
      <if test="q != null and q != ''">
        AND (
          u.id        LIKE CONCAT('%', #{q}, '%')
          OR u.name   LIKE CONCAT('%', #{q}, '%')
          OR s.sub_name  LIKE CONCAT('%', #{q}, '%')
          OR t.team_name LIKE CONCAT('%', #{q}, '%')
          OR u.position  LIKE CONCAT('%', #{q}, '%')
        )
      </if>
      <if test="org != null and org != ''">
        AND u.c_name = #{org}
      </if>
      <if test="dept != null and dept != ''">
        AND u.sub_code = #{dept}
      </if>
      <if test="pwd == 'set'">
        AND COALESCE(u.pwd,'') &lt;&gt; ''
      </if>
      <if test="pwd == 'unset'">
        AND (u.pwd IS NULL OR u.pwd = '')
      </if>
      <if test="role != null and role != ''">
        AND EXISTS (
          SELECT 1 FROM personnel_evaluation_aff.user_roles_${year} r2
          WHERE r2.user_id = u.id AND r2.role = #{role} AND r2.eval_year = #{year}
        )
      </if>
      </script>
      """)
  int countUsers(
      @Param("year") String year,
      @Param("q") String q,
      @Param("dept") String dept,
      @Param("pwd") String pwd,
      @Param("org") String org,
      @Param("delYn") String delYn,
      @Param("role") String role);

  @Select("""
      SELECT sub_code AS subCode, sub_name AS subName
      FROM personnel_evaluation_aff.sub_management
      WHERE eval_year = #{year}
      ORDER BY sub_name
      """)
  List<Department> getDepartments(@Param("year") String year);

  @Select("""
      SELECT DISTINCT u.c_name
      FROM personnel_evaluation_aff.users_${year} u
      WHERE u.eval_year = #{year}
        AND u.c_name IS NOT NULL AND u.c_name <> ''
      ORDER BY u.c_name
      """)
  List<String> getOrganizations(@Param("year") String year);

  @Select("""
      <script>
      SELECT DISTINCT
          s.sub_code  AS subCode,
          s.sub_name  AS subName
      FROM personnel_evaluation_aff.sub_management s
      JOIN personnel_evaluation_aff.users_${year} u
        ON u.sub_code = s.sub_code
       AND u.eval_year = s.eval_year
      WHERE s.eval_year = #{year}
      <if test="org != null and org != ''">
        AND u.c_name = #{org}
      </if>
      ORDER BY s.sub_name
      </script>
      """)
  List<Department> getDepartmentsByOrg(
      @Param("year") String year,
      @Param("org") String org);

  @Select("""
      SELECT idx,
      sub_name  AS subName,
      sub_code  AS subCode,
      eval_year AS evalYear
      FROM personnel_evaluation_aff.sub_management WHERE eval_year = #{year}
              """)
  List<SubManagement> getSubManagement(String year);

  @Select("SELECT sub_code FROM personnel_evaluation_aff.sub_management WHERE sub_name = #{subName} AND eval_year = #{year} LIMIT 1")
  String getSubcode(@Param("subName") String subName, @Param("year") int year);

  /** 롤 테이블에서 연도별 삭제 (관리자 제외) */
  @Delete({
      "<script>",
      "  DELETE FROM personnel_evaluation_aff.user_roles_${year}",
      "   WHERE eval_year = #{year}",
      "     AND user_id != #{adminId}",
      "</script>"
  })
  void deleteRolesByYearExcept(
      @Param("year") int year,
      @Param("adminId") String adminId);

  /** 사용자 테이블에서 연도별 삭제 (관리자 제외) */
  @Delete({
      "<script>",
      // ${} 로 넣어야 테이블명이 문자열 그대로 치환됩니다.
      "  DELETE FROM personnel_evaluation_aff.users_${year}",
      "   WHERE eval_year = #{year}",
      "     AND id != #{adminId}",
      "</script>"
  })
  void deleteUsersByYearExcept(
      @Param("year") int year,
      @Param("adminId") String adminId);

  /** 해당 연도의 sub_management 중에서 adminSubCode(관리자 부서)만 제외하고 전부 삭제 */
  @Delete("""
      DELETE FROM personnel_evaluation_aff.sub_management
      WHERE eval_year = #{year}
      AND sub_code  != #{adminSubCode}
      """)
  void deleteByYearExcept(int year, String adminSubCode);

  /** AUTO_INCREMENT 초기화 */
  @Update("""
      ALTER TABLE personnel_evaluation_aff.sub_management
      AUTO_INCREMENT = 1
      """)
  void resetAutoIncrement();

  // 5) 비밀번호 업데이트 (UserPE.evalYear 로 연도 테이블 결정)
  @Update("UPDATE personnel_evaluation_aff.users_${evalYear} SET pwd = #{pwd} WHERE idx = #{idx}")
  int updateUserPassword(UserPE userinfo);

  @Select("SELECT * FROM personnel_evaluation_aff.users_${year} WHERE del_yn='N'")
  List<UserPE> getUserList(@Param("year") int year);

  @Select("""
        SELECT
          u.idx        AS idx,
          u.c_name     AS cName,
          u.c_name2    AS cName2,
          u.id         AS id,
          u.name       AS name,
          u.position   AS position,
          u.sub_code   AS subCode,
          u.team_code  AS teamCode,
          u.eval_year  AS evalYear,
          u.create_at  AS createAt,
          u.pwd        AS pwd,
          s.sub_name   AS subName,
          t.team_name  AS teamName
        FROM personnel_evaluation_aff.users_${year} u
        LEFT JOIN personnel_evaluation_aff.sub_management s
               ON s.sub_code  = u.sub_code
              AND s.eval_year = u.eval_year
        LEFT JOIN personnel_evaluation_aff.team t
               ON t.team_code = u.team_code
              AND t.eval_year = u.eval_year
        WHERE u.id = #{userId}
          AND u.eval_year = #{year}
          AND u.del_yn = 'N'
        LIMIT 1
      """)
  UserPE findByIdWithNames(@Param("userId") String userId, @Param("year") int year);

  @Select("SELECT count(*) FROM personnel_evaluation_aff.users_${year} WHERE id = #{id} and eval_year = #{year} and phone = #{ph}")
  int findByUserIdWithPhone(@Param("id") String id, @Param("year") int year, @Param("ph") String ph);

  @Update("UPDATE personnel_evaluation_aff.users_${year} SET pwd = #{encoded} WHERE id = #{userId} and eval_year = #{year}")
  int changePasswordByUserIdAndYear(String userId, String year, String encoded);

  // ── 기관 관리자 전용 ──────────────────────────────────

  /** idx + 기관명으로 직원 조회 (기관 범위 보안 체크) */
  @Select("""
      SELECT u.idx, u.c_name AS cName, u.c_name2 AS cName2,
             u.id, u.name, u.position, u.phone,
             u.sub_code AS subCode, u.team_code AS teamCode,
             u.del_yn AS delYn, u.eval_year AS evalYear,
             u.pwd,
             CASE WHEN COALESCE(u.pwd,'') <> '' THEN 1 ELSE 0 END AS passwordSet,
             s.sub_name AS subName, t.team_name AS teamName,
             (SELECT GROUP_CONCAT(r.role ORDER BY r.role SEPARATOR ',')
              FROM personnel_evaluation_aff.user_roles_${year} r
              WHERE r.user_id = u.id AND r.eval_year = #{year}) AS rolesCsv
      FROM personnel_evaluation_aff.users_${year} u
      LEFT JOIN personnel_evaluation_aff.sub_management s
             ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation_aff.team t
             ON t.team_code = u.team_code AND t.eval_year = u.eval_year
      WHERE u.idx       = #{idx}
        AND u.eval_year = #{year}
        AND u.c_name    = #{org}
      LIMIT 1
      """)
  UserPE findUserByIdxAndOrg(
      @Param("idx") int idx,
      @Param("year") String year,
      @Param("org") String org);

  /** 평가제외 여부 업데이트 */
  @Update("UPDATE personnel_evaluation_aff.users_${year} SET del_yn = #{delYn} WHERE idx = #{idx} AND eval_year = #{year}")
  int updateDelYn(
      @Param("idx") int idx,
      @Param("year") String year,
      @Param("delYn") String delYn);

  /** 비밀번호 초기화 (NULL) */
  @Update("UPDATE personnel_evaluation_aff.users_${year} SET pwd = NULL WHERE idx = #{idx} AND eval_year = #{year}")
  int resetPasswordByIdx(
      @Param("idx") int idx,
      @Param("year") String year);

  /** 특정 직원의 역할 전체 삭제 */
  @Delete("DELETE FROM personnel_evaluation_aff.user_roles_${year} WHERE user_id = #{userId} AND eval_year = #{year}")
  int deleteRolesByUserId(
      @Param("userId") String userId,
      @Param("year") String year);

  /** 역할 단건 추가 */
  @Insert("""
      INSERT INTO personnel_evaluation_aff.user_roles_${year}
        (user_id, role, eval_year)
      VALUES
        (#{userId}, #{role}, #{year})
      """)
  int insertRoleForUser(
      @Param("userId") String userId,
      @Param("role") String role,
      @Param("year") String year);

  /** 기관 소속 직원 + 역할(rolesCsv) 조회 (targets 자동 생성용) */
  @Select("""
      SELECT
          u.idx        AS idx,
          u.c_name     AS cName,
          u.sub_code   AS subCode,
          u.team_code  AS teamCode,
          u.id         AS id,
          u.name       AS name,
          u.position   AS position,
          s.sub_name   AS subName,
          (SELECT GROUP_CONCAT(r.role ORDER BY r.role SEPARATOR ',')
           FROM personnel_evaluation_aff.user_roles_${year} r
           WHERE r.user_id = u.id AND r.eval_year = u.eval_year) AS rolesCsv
      FROM personnel_evaluation_aff.users_${year} u
      LEFT JOIN personnel_evaluation_aff.sub_management s
             ON s.sub_code  = u.sub_code
            AND s.eval_year = u.eval_year
      WHERE u.eval_year = #{year}
        AND u.del_yn    = 'N'
        AND u.c_name    = #{orgName}
      ORDER BY s.sub_name, u.name
      """)
  List<UserPE> getUsersWithRolesByOrg(
      @Param("year") String year,
      @Param("orgName") String orgName);

  /** 특정 기관의 모든 평가 대상 비활성화 (targets 초기화용) */
  @Update("""
      UPDATE personnel_evaluation_aff.admin_custom_targets ct
      INNER JOIN personnel_evaluation_aff.users_${year} u
             ON  u.id = ct.user_id AND u.eval_year = ct.eval_year
         SET ct.is_active = 0, ct.updated_at = NOW()
       WHERE ct.eval_year = #{year}
         AND u.c_name     = #{orgName}
      """)
  int deactivateTargetsByOrg(
      @Param("year") int year,
      @Param("orgName") String orgName);

  /** 특정 기관(c_name)의 역할 전체 삭제 */
  @Delete("""
      DELETE r
      FROM   personnel_evaluation_aff.user_roles_${year} r
      JOIN   personnel_evaluation_aff.users_${year} u
             ON  u.id        = r.user_id
             AND u.eval_year = r.eval_year
      WHERE  u.c_name    = #{orgName}
        AND  u.eval_year = #{year}
      """)
  int deleteRolesByOrg(
      @Param("year") int year,
      @Param("orgName") String orgName);

  /** 특정 기관(c_name)의 직원 전체 삭제 */
  @Delete("""
      DELETE FROM personnel_evaluation_aff.users_${year}
      WHERE c_name    = #{orgName}
        AND eval_year = #{year}
      """)
  int deleteUsersByOrg(
      @Param("year") int year,
      @Param("orgName") String orgName);
}
