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
import org.springframework.web.bind.annotation.RequestParam;

import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.NoticeVo;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.entity.UserrolePE;

@Mapper
public interface LoginMapper {
  @Select("SELECT * FROM personnel_evaluation.notice")
  List<NoticeVo> findAll();

  // 1) 사번으로 유저 조회
  @Select("SELECT * FROM personnel_evaluation.users_${year} WHERE id = #{id}")
  UserPE findById(@Param("id") String id, @Param("year") int year);

  // 1) 사번+이름으로 유저 조회
  @Select("SELECT COUNT(*) FROM personnel_evaluation.users_${year} WHERE id = #{id} AND name = #{name}")
  int countByIdAndName(@Param("id") String id, @Param("name") String name, @Param("year") int year);

  // 2) 사번에 이미 비밀번호 설정됐는지 확인
  @Select("SELECT COUNT(*) FROM personnel_evaluation.users_${year} WHERE id =#{id} AND pwd IS NOT NULL")
  int countPwdById(@Param("id") String id, @Param("year") int year);

  // 3) 사번+비밀번호로 로그인 체크
  @Select("SELECT COUNT(*) FROM personnel_evaluation.users_${year} WHERE id = #{id} AND pwd = #{pwd}")
  int countByIdAndPwd(@Param("id") String id, @Param("pwd") String pwd, @Param("year") int year);

  // 4) 로그인 성공 시 유저 고유키(idx) 조회
  @Select("SELECT idx FROM personnel_evaluation.users_${year} WHERE id = #{id}")
  Integer findIdxById(@Param("id") String id, @Param("year") int year);

  @Select("SELECT * FROM personnel_evaluation.users_${year} WHERE idx = #{idx}")
  UserPE findUserInfoByIdx(@Param("idx") int idx, @Param("year") int year);

  // 5) 비밀번호 업데이트 (UserPE.evalYear 로 연도 테이블 결정)
  @Update("UPDATE personnel_evaluation.users_${evalYear} SET pwd = #{pwd} WHERE idx = #{idx}")
  int updateUserPassword(UserPE userinfo);

  // 유저 정보 조회
  @Select("""
          SELECT
              u.idx               AS idx,
              u.c_name            AS cName,
              u.c_name2           AS cName2,
              u.sub_code          AS subCode,
              s.sub_name          AS subName,   -- 조인 alias
              u.id                AS id,
              u.position          AS position,
              u.name              AS name,
              u.pwd               AS pwd,
              u.create_at         AS createAt,
              u.delete_at         AS deleteAt,
              u.phone             AS phone,
              u.team_code         AS teamCode,
              t.team_name         AS teamName,  -- 팀명 조인 추가
              u.del_yn            AS delYn,
              u.eval_year         AS evalYear
          FROM personnel_evaluation.users_${year} u
          LEFT JOIN personnel_evaluation.sub_management s
                 ON s.sub_code  = u.sub_code
                AND s.eval_year = u.eval_year
          LEFT JOIN personnel_evaluation.team t
                 ON t.team_code = u.team_code
                AND t.eval_year = u.eval_year
          WHERE u.eval_year = #{year}
          AND u.del_yn = 'N'
      """)
  List<UserPE> getUserList(@Param("year") String year);

  @Select("SELECT sub_code FROM personnel_evaluation.sub_management WHERE sub_name = #{subName} AND eval_year = #{year} LIMIT 1")
  String getSubcode(@Param("subName") String subName, @Param("year") int year);

  @Select("""
      SELECT idx,
      sub_name  AS subName,
      sub_code  AS subCode,
      eval_year AS evalYear
      FROM personnel_evaluation.sub_management WHERE eval_year = #{year}
              """)
  List<SubManagement> getSubManagement(String year);

  /** 롤 테이블에서 연도별 삭제 (관리자 제외) */
  @Delete({
      "<script>",
      "  DELETE FROM personnel_evaluation.user_roles_${year}",
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
      "  DELETE FROM personnel_evaluation.users_${year}",
      "   WHERE eval_year = #{year}",
      "     AND id != #{adminId}",
      "</script>"
  })
  void deleteUsersByYearExcept(
      @Param("year") int year,
      @Param("adminId") String adminId);

  /** 해당 연도의 sub_management 중에서 adminSubCode(관리자 부서)만 제외하고 전부 삭제 */
  @Delete("""
      DELETE FROM personnel_evaluation.sub_management
      WHERE eval_year = #{year}
      AND sub_code  != #{adminSubCode}
      """)
  void deleteByYearExcept(int year, String adminSubCode);

  /** AUTO_INCREMENT 초기화 */
  @Update("""
      ALTER TABLE personnel_evaluation.sub_management
      AUTO_INCREMENT = 1
      """)
  void resetAutoIncrement();

  @Select("SELECT * FROM personnel_evaluation.users_${year} WHERE id = #{userId} and eval_year = #{year}")
  UserPE findByIdandyear(String userId, String year);

  @Select("""
      SELECT DISTINCT
          u.idx           AS idx,
          u.c_name        AS cName,
          u.c_name2       AS cName2,
          u.id            AS id,
          u.name          AS name,
          u.position      AS position,
          u.sub_code      AS subCode,
          u.team_code     AS teamCode,
          u.eval_year     AS evalYear,
          u.create_at     AS createAt,
          u.pwd           AS pwd,
          s.sub_name      AS subName,
          t.team_name     AS teamName
      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.sub_management s
             ON s.sub_code  = u.sub_code
            AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation.team t
             ON t.team_code = u.team_code
            AND t.eval_year = u.eval_year
      WHERE u.id        = #{userId}
        AND u.eval_year = #{year}
      LIMIT 1
      """)
  @Results(id = "userPERes_userInfo", value = {
      // 충돌 방지: 조인 컬럼은 별도 alias로 뽑아서 매핑
      @Result(column = "cName", property = "cName"),
      @Result(column = "cName2", property = "cName2"),
      // 나머지도 되도록 명시(자동매핑 혼선 방지)
      @Result(column = "id", property = "id"),
      @Result(column = "name", property = "name"),
      @Result(column = "position", property = "position"),
      @Result(column = "subCode", property = "subCode"),
      @Result(column = "teamCode", property = "teamCode"),
      @Result(column = "evalYear", property = "evalYear"),
      @Result(column = "createAt", property = "createAt"),
      @Result(column = "subName", property = "subName"),
      @Result(column = "teamName", property = "teamName")
  })
  UserPE findByIdWithNames(@Param("userId") String userId,
      @Param("year") String year);

  @Select("SELECT count(*) FROM personnel_evaluation.users_${year} WHERE id = #{id} and eval_year = #{year} and phone = #{ph}")
  int findByUserIdWithPhone(@Param("id") String id, @Param("year") String year, @Param("ph") String ph);

  @Update("UPDATE personnel_evaluation.users_${year} SET pwd = #{encoded} WHERE id = #{userId} and eval_year = #{year}")
  int changePasswordByUserIdAndYear(String userId, String year, String encoded);

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
           FROM personnel_evaluation.user_roles_${year} r
           WHERE r.user_id = u.id AND r.eval_year = #{year}) AS rolesCsv
      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.sub_management s
             ON s.sub_code  = u.sub_code
            AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation.team t
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
          SELECT 1 FROM personnel_evaluation.user_roles_${year} r2
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
      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.sub_management s
             ON s.sub_code  = u.sub_code
            AND s.eval_year = u.eval_year
      LEFT JOIN personnel_evaluation.team t
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
          SELECT 1 FROM personnel_evaluation.user_roles_${year} r2
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
      FROM personnel_evaluation.sub_management
      WHERE eval_year = #{year}
      ORDER BY sub_name
      """)
  List<Department> getDepartments(@Param("year") String year);

  @Select("""
      SELECT DISTINCT u.c_name
      FROM personnel_evaluation.users_${year} u
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
      FROM personnel_evaluation.sub_management s
      JOIN personnel_evaluation.users_${year} u
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

  // ── 기관 관리자 전용: 특정 기관(c_name)에 속한 부서만 조회 ──────────────

  @Select("""
      SELECT DISTINCT
          s.sub_code  AS subCode,
          s.sub_name  AS subName
      FROM personnel_evaluation.sub_management s
      JOIN personnel_evaluation.users_${year} u
        ON u.sub_code  = s.sub_code
       AND u.eval_year = s.eval_year
      WHERE s.eval_year       = #{year}
        AND u.c_name           = #{institutionName}
      ORDER BY s.sub_name
      """)
  List<Department> getDepartmentsByInstitution(
      @Param("year") String year,
      @Param("institutionName") String institutionName);

  /** 기관 관리자 전용: 특정 기관에 속한 sub_management 목록
   *  institution_id 기준으로 조회 (users 테이블 JOIN 불필요)
   *  신규 등록된 부서(institution_id 있음) + 레거시(institution_id=NULL, c_name 조인) 모두 지원
   */
  @Select("""
      <script>
      SELECT DISTINCT
          s.idx       AS idx,
          s.sub_name  AS subName,
          s.sub_code  AS subCode,
          s.eval_year AS evalYear
      FROM personnel_evaluation.sub_management s
      WHERE s.eval_year = #{year}
        AND (
              s.institution_id = #{institutionId}
              OR (
                  s.institution_id IS NULL
                  AND EXISTS (
                      SELECT 1
                      FROM personnel_evaluation.users_${year} u
                      WHERE u.sub_code  = s.sub_code
                        AND u.eval_year = s.eval_year
                        AND u.c_name    = #{institutionName}
                  )
              )
            )
      ORDER BY s.sub_name
      </script>
      """)
  List<SubManagement> getSubManagementByInstitution(
      @Param("year") String year,
      @Param("institutionId") Integer institutionId,
      @Param("institutionName") String institutionName);

  // ── 기관 관리자 전용: 직원 상세 조회 (기관 범위 검증 포함) ──────────────

  @Select("""
      SELECT
          u.idx       AS idx,
          u.c_name    AS cName,
          u.c_name2   AS cName2,
          u.sub_code  AS subCode,
          s.sub_name  AS subName,
          u.id        AS id,
          u.position  AS position,
          u.name      AS name,
          u.pwd       AS pwd,
          u.create_at AS createAt,
          u.delete_at AS deleteAt,
          u.phone     AS phone,
          u.team_code AS teamCode,
          u.del_yn    AS delYn,
          u.eval_year AS evalYear,
          CASE WHEN COALESCE(u.pwd,'') <> '' THEN 1 ELSE 0 END AS passwordSet,
          (SELECT GROUP_CONCAT(r.role ORDER BY r.role SEPARATOR ',')
           FROM personnel_evaluation.user_roles_${year} r
           WHERE r.user_id = u.id AND r.eval_year = #{year}) AS rolesCsv
      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.sub_management s
             ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
      WHERE u.idx        = #{idx}
        AND u.eval_year  = #{year}
        AND u.c_name     = #{institutionName}
      """)
  UserPE findUserByIdxAndOrg(
      @Param("idx") int idx,
      @Param("year") String year,
      @Param("institutionName") String institutionName);

  /** 기관 관리자 전용: 평가제외 여부 업데이트 */
  @Update("UPDATE personnel_evaluation.users_${year} SET del_yn = #{delYn} WHERE idx = #{idx} AND eval_year = #{year}")
  int updateDelYn(
      @Param("idx") int idx,
      @Param("year") String year,
      @Param("delYn") String delYn);

  /** 기관 관리자 전용: 비밀번호 초기화 (NULL 설정) */
  @Update("UPDATE personnel_evaluation.users_${year} SET pwd = NULL WHERE idx = #{idx} AND eval_year = #{year}")
  int resetPasswordByIdx(
      @Param("idx") int idx,
      @Param("year") String year);

  /** 기관 관리자 전용: 특정 직원의 역할 전체 삭제 */
  @Delete("DELETE FROM personnel_evaluation.user_roles_${year} WHERE user_id = #{userId} AND eval_year = #{year}")
  int deleteRolesByUserId(
      @Param("userId") String userId,
      @Param("year") String year);

  /** 기관 관리자 전용: 역할 단건 추가 */
  @Insert("""
      INSERT INTO personnel_evaluation.user_roles_${year}
        (user_id, role, eval_year)
      VALUES
        (#{userId}, #{role}, #{year})
      """)
  int insertRoleForUser(
      @Param("userId") String userId,
      @Param("role") String role,
      @Param("year") String year);

  // ── 기관 관리자 전용: 기관 범위 데이터 초기화 ────────────────────────

  /** 특정 기관(c_name)의 직원 전체 삭제 (해당 연도) */
  @Delete("DELETE FROM personnel_evaluation.users_${year} WHERE c_name = #{institutionName} AND eval_year = #{year}")
  int deleteUsersByYearAndOrg(
      @Param("year") String year,
      @Param("institutionName") String institutionName);

  /** 특정 기관 직원의 역할 전체 삭제 (다중 테이블 DELETE) */
  @Delete("""
      DELETE r
      FROM   personnel_evaluation.user_roles_${year} r
      JOIN   personnel_evaluation.users_${year} u
             ON  u.id        = r.user_id
             AND u.eval_year = r.eval_year
      WHERE  u.c_name    = #{institutionName}
        AND  u.eval_year = #{year}
      """)
  int deleteRolesByYearAndOrg(
      @Param("year") String year,
      @Param("institutionName") String institutionName);

  // ── 기관 관리자 전용: 평가 대상 자동 생성용 ────────────────────────────

  /** 특정 기관 소속 활성 직원을 역할 CSV 포함하여 조회 */
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
           FROM personnel_evaluation.user_roles_${year} r
           WHERE r.user_id = u.id AND r.eval_year = u.eval_year) AS rolesCsv
      FROM personnel_evaluation.users_${year} u
      LEFT JOIN personnel_evaluation.sub_management s
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

  /** 특정 기관의 모든 평가 대상 삭제 (admin_custom_targets) */
  @Delete("""
      DELETE FROM personnel_evaluation.admin_custom_targets
      WHERE eval_year = #{year}
        AND user_id IN (
          SELECT id
          FROM   personnel_evaluation.users_${year}
          WHERE  c_name    = #{orgName}
            AND  eval_year = #{year}
        )
      """)
  int deleteTargetsByOrg(
      @Param("year") int year,
      @Param("orgName") String orgName);

}
