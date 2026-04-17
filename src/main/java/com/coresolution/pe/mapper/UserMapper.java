package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.UserPE;

@Mapper
public interface UserMapper {
    /** 0) 단일 사용자 조회 (사번 + 연도) */
    @Select("""
                SELECT
                  id,
                  name,
                  c_name  AS cName,
                  c_name2 AS cName2,
                  sub_code AS subCode,
                  team_code AS teamCode,
                  eval_year AS evalYear,
                  phone, position, pwd, create_at AS createAt, delete_at AS deleteAt
                FROM personnel_evaluation.users_${year}
                WHERE id = #{id} AND eval_year = #{year} AND del_yn ='N'
                LIMIT 1
            """)
    UserPE findById(@Param("id") String id, @Param("year") String year);

    // 로그인 전용: del_yn 상관없이 한 줄 다 가져오기
    @Select("""
                SELECT id,
                       name,
                       c_name  AS cName,
                       c_name2 AS cName2,
                       sub_code AS subCode,
                       team_code AS teamCode,
                       eval_year AS evalYear,
                       phone,
                       position,
                       pwd,
                       del_yn  AS delYn,
                       create_at AS createAt,
                       delete_at AS deleteAt
                  FROM personnel_evaluation.users_${year}
                 WHERE id = #{id}
                   AND eval_year = #{year}
                 LIMIT 1
            """)
    UserPE findByIdForLogin(@Param("id") String id, @Param("year") String year);

    @Select("""
                SELECT id,
                  name,
                  c_name  AS cName,
                  c_name2 AS cName2,
                  sub_code AS subCode,
                  team_code AS teamCode,
                  eval_year AS evalYear,
                  phone, position, pwd, create_at AS createAt, delete_at AS deleteAt
                  FROM personnel_evaluation.users_${year}
                WHERE id = #{userId} AND eval_year = #{year}
                  AND c_name = #{cName} AND c_name2 = #{cName2} AND del_yn ='N'
            """)
    UserPE findByIdInOrg(@Param("userId") String userId, @Param("year") String year,
            @Param("cName") String cName, @Param("cName2") String cName2);

    // 부서코드 in (같은 기관)
    @Select("""
                <script>
              SELECT id,
                     name,
                     c_name  AS cName,
                     c_name2 AS cName2,
                     sub_code AS subCode,
                     team_code AS teamCode,
                     eval_year AS evalYear,
                     phone, position, pwd,
                     create_at AS createAt,
                     delete_at AS deleteAt
                FROM personnel_evaluation.users_${year}
               WHERE eval_year = #{year}
                 AND c_name   = #{cName}
                 AND c_name2  = #{cName2}
                 AND del_yn ='N'
                 AND sub_code IN
                 <foreach collection="subs" item="s" open="(" separator="," close=")">
                   #{s}
                 </foreach>
              </script>
            """)
    List<UserPE> findBySubCodesInOrg(@Param("subs") List<String> subs, @Param("year") String year,
            @Param("cName") String cName, @Param("cName2") String cName2);

    // 동일 부서(같은 기관)
    @Select("""
               SELECT id,
                     name,
                     c_name  AS cName,
                     c_name2 AS cName2,
                     sub_code AS subCode,
                     team_code AS teamCode,
                     eval_year AS evalYear,
                     phone, position, pwd,
                     create_at AS createAt,
                     delete_at AS deleteAt
                FROM personnel_evaluation.users_${year}
               WHERE eval_year = #{year}
                 AND sub_code = #{subCode}
                 AND c_name   = #{cName}
                 AND c_name2  = #{cName2}
                 AND del_yn   = 'N'
            """)
    List<UserPE> findBySubCodeInOrg(@Param("subCode") String subCode, @Param("year") String year,
            @Param("cName") String cName, @Param("cName2") String cName2);

    // 팀코드(같은 기관)
    @Select("""
                 SELECT id,
                     name,
                     c_name  AS cName,
                     c_name2 AS cName2,
                     sub_code AS subCode,
                     team_code AS teamCode,
                     eval_year AS evalYear,
                     phone, position, pwd,
                     create_at AS createAt,
                     delete_at AS deleteAt
                FROM personnel_evaluation.users_${year}
               WHERE eval_year = #{year}
                 AND team_code = #{teamCode}
                 AND c_name   = #{cName}
                 AND c_name2  = #{cName2}
                 AND del_yn ='N'
            """)
    List<UserPE> findByTeamCodeInOrg(@Param("teamCode") String teamCode, @Param("year") String year,
            @Param("cName") String cName, @Param("cName2") String cName2);

    /** 1) 여러 부서 코드에 해당하는 직원 리스트 */
    @Select({
            "<script>",
            "SELECT *",
            "  FROM personnel_evaluation.users_${year}",
            " WHERE sub_code IN",
            "   <foreach collection='asList' item='code' open='(' separator=',' close=')'>",
            "     #{code}",
            "   </foreach>",
            "</script>"
    })
    List<UserPE> findBySubCodes(
            @Param("asList") List<String> asList,
            @Param("year") String year);

    /** 2) 특정 팀 코드(예: GH_TEAM 또는 NO_TEAM)에 속한 직원 */
    @Select("SELECT * FROM personnel_evaluation.users_${year} WHERE team_code = #{teamCode}")
    List<UserPE> findByTeamCode(
            @Param("teamCode") String teamCode,
            @Param("year") String year);

    /** 3) 한 직원의 부서 코드 조회 */
    @Select("""
                SELECT sub_code FROM personnel_evaluation.users_${year}
                WHERE id = #{userId} AND eval_year = #{year} AND del_yn = 'N'
            """)
    String findSubCodeByUser(@Param("userId") String userId, @Param("year") String year);

    /** 4) 단일 부서 코드에 해당하는 직원 리스트 */
    @Select("SELECT * FROM personnel_evaluation.users_${year} WHERE sub_code = #{subCode}")
    List<UserPE> findBySubCode(
            @Param("subCode") String subCode,
            @Param("year") String year);

    /** 5) 사번 리스트로 조회 (1인 부서 직원 등) */
    @Select({
            "<script>",
            "SELECT *",
            "  FROM personnel_evaluation.users_${year}",
            " WHERE id IN",
            "   <foreach collection='onePersonIds' item='id' open='(' separator=',' close=')'>",
            "     #{id}",
            "   </foreach>",
            "</script>"
    })
    List<UserPE> findByIds(
            @Param("onePersonIds") List<String> onePersonIds,
            @Param("year") String year);

    @Select("SELECT DISTINCT sub_code FROM personnel_evaluation.users_${year} WHERE sub_code LIKE 'A%'")
    List<String> findJinryuSubCodes(String year);

    @Select("""
                SELECT team_code FROM personnel_evaluation.users_${year}
                WHERE id = #{userId} AND eval_year = #{year}
                LIMIT 1
            """)
    String findTeamCodeByUser(@Param("userId") String userId, @Param("year") String year);

    @Select("""
            SELECT
            u.c_name        AS cName,
            u.c_name2       AS cName2,
            u.id            AS id,
            u.name          AS name,
            u.position      AS position,
            u.sub_code      AS subCode,
            u.team_code     AS teamCode,
            u.eval_year     AS evalYear,
            u.create_at     AS createAt,
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
            AND u.del_yn ='N'
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

    @Select("""
              SELECT c_name
              FROM personnel_evaluation.users_${year}
              WHERE id = #{userId} AND eval_year = #{year}
            """)
    String findOrgCodeByUser(@Param("userId") String userId, @Param("year") String year);

    @Select("""
                SELECT role FROM personnel_evaluation.user_roles_${year}
                WHERE user_id = #{userId} AND eval_year = #{year}
            """)
    List<String> findRolesByUser(@Param("userId") String userId, @Param("year") String year);

    @Select("""
                SELECT u.* FROM personnel_evaluation.users_${year} u
                WHERE u.eval_year = #{year}
                  AND u.sub_code  = #{subCode}
                  AND u.c_name    = #{cName}
                  AND u.c_name2   = #{cName2}
            """)
    List<UserPE> findUsersBySubAndOrg(@Param("subCode") String subCode,
            @Param("year") String year,
            @Param("cName") String cName,
            @Param("cName2") String cName2);
}
