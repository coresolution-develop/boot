package com.coresolution.pe.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import com.coresolution.pe.entity.UserPE;

@Mapper
public interface AffUserMapper {

       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.eval_year = #{year}
                           AND u.del_yn = 'N'
                      """)
       @Results(id = "affUserWithNames", value = {
                     // 기본 키
                     @Result(column = "idx", property = "idx", id = true),

                     // users_* 컬럼들
                     @Result(column = "c_name", property = "cName"),
                     @Result(column = "c_name2", property = "cName2"),
                     @Result(column = "sub_code", property = "subCode"),
                     @Result(column = "team_code", property = "teamCode"),
                     @Result(column = "position", property = "position"),
                     @Result(column = "id", property = "id"),
                     @Result(column = "pwd", property = "pwd"),
                     @Result(column = "name", property = "name"),
                     @Result(column = "create_at", property = "createAt"),
                     @Result(column = "delete_at", property = "deleteAt"),
                     @Result(column = "phone", property = "phone"),
                     @Result(column = "del_yn", property = "delYn"),
                     @Result(column = "eval_year", property = "evalYear"),

                     // JOIN 컬럼들
                     @Result(column = "subName", property = "subName"),
                     @Result(column = "teamName", property = "teamName")
       })
       List<UserPE> getUserList(@Param("year") int year);

       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.id = #{id}
                           AND u.eval_year = #{year}
                           AND u.del_yn = 'N'
                         LIMIT 1
                      """)
       @ResultMap("affUserWithNames")
       UserPE findById(@Param("id") String id, @Param("year") int year);

       @Select("""
                       SELECT sub_code
                         FROM personnel_evaluation_aff.users_${year}
                        WHERE id = #{id} AND eval_year = #{year}
                        LIMIT 1
                     """)
       String findSubCodeByUser(@Param("id") String id, @Param("year") int year);

       // 같은 기관
       // @Select("""
       // SELECT u.*,
       // s.sub_name AS subName,
       // t.team_name AS teamName
       // FROM personnel_evaluation_aff.users_${year} u
       // LEFT JOIN personnel_evaluation_aff.sub_management s
       // ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
       // LEFT JOIN personnel_evaluation_aff.team t
       // ON t.team_code = u.team_code AND t.eval_year = u.eval_year
       // WHERE u.eval_year = #{year}
       // AND u.c_name = #{cName}
       // AND u.del_yn = 'N'
       // """)
       // @ResultMap("affUserWithNames")
       // List<UserPE> findByOrg(@Param("cName") String cName, @Param("year") int
       // year);

       // 같은 기관 + 같은 소속(c_name2)
       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.eval_year = #{year}
                           AND u.c_name    = #{cName}
                           AND u.c_name2   = #{cName2}
                           AND u.del_yn    = 'N'
                      """)
       @ResultMap("affUserWithNames")
       List<UserPE> findByOrgPair(@Param("cName") String cName,
                     @Param("cName2") String cName2,
                     @Param("year") int year);

       // 같은 기관쌍 + 같은 부서코드
       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.eval_year = #{year}
                           AND u.c_name    = #{cName}
                           AND u.c_name2   = #{cName2}
                           AND u.sub_code  = #{subCode}
                           AND u.del_yn    = 'N'
                      """)
       @ResultMap("affUserWithNames")
       List<UserPE> findBySubInOrgPair(@Param("subCode") String subCode,
                     @Param("cName") String cName,
                     @Param("cName2") String cName2,
                     @Param("year") int year);

       // 소속(c_name2) 기준
       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.c_name2  = #{org2}
                           AND u.eval_year = #{year}
                           AND u.del_yn = 'N'
                      """)
       @ResultMap("affUserWithNames")
       List<UserPE> findByOrg2(@Param("org2") String org2, @Param("year") int year);

       // ✅ 부서명으로 조회(조인 후 s.sub_name 기준)
       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE s.sub_name = #{subName}
                           AND u.eval_year = #{year}
                           AND u.del_yn = 'N'
                      """)
       @ResultMap("affUserWithNames")
       List<UserPE> findBySubName(@Param("subName") String subName, @Param("year") int year);

       // ✅ 같은 기관 + 부서명으로 조회(조인 후 s.sub_name 기준)
       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.c_name   = #{cName}
                           AND s.sub_name = #{subName}
                           AND u.eval_year = #{year}
                           AND u.del_yn = 'N'
                      """)
       @ResultMap("affUserWithNames")
       List<UserPE> findByOrgAndSubName(@Param("cName") String cName,
                     @Param("subName") String subName,
                     @Param("year") int year);

       // ✅ 같은 기관 + 같은 소속 + 부서코드로 조회 (서비스에서 사용)
       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.c_name    = #{cName}
                           AND u.c_name2   = #{groupName}
                           AND u.sub_code  = #{subCode}
                           AND u.eval_year = #{year}
                           AND u.del_yn    = 'N'
                      """)
       @ResultMap("affUserWithNames")
       List<UserPE> findBySubCodeInOrg(@Param("subCode") String subCode,
                     @Param("year") int year,
                     @Param("cName") String cName,
                     @Param("groupName") String groupName);

       // sub_code -> sub_name 매핑
       @Select("""
                       SELECT sub_name
                         FROM personnel_evaluation_aff.sub_management
                        WHERE eval_year = #{year}
                          AND sub_code  = #{subCode}
                        LIMIT 1
                     """)
       String findSubNameByCode(@Param("subCode") String subCode, @Param("year") int year);

       // 같은 기관 + 같은 소속(c_name2)
       @Select("""
                        SELECT u.*,
                               s.sub_name  AS subName,
                               t.team_name AS teamName
                          FROM personnel_evaluation_aff.users_${year} u
                     LEFT JOIN personnel_evaluation_aff.sub_management s
                            ON s.sub_code = u.sub_code AND s.eval_year = u.eval_year
                     LEFT JOIN personnel_evaluation_aff.team t
                            ON t.team_code = u.team_code AND t.eval_year = u.eval_year
                         WHERE u.eval_year = #{year}
                           AND u.c_name    = #{cName}
                           AND u.c_name2   = #{groupName}
                           AND u.del_yn    = 'N'
                      """)
       @ResultMap("affUserWithNames")
       List<UserPE> findByOrgAndGroup(@Param("cName") String cName,
                     @Param("groupName") String groupName,
                     @Param("year") int year);

       // 🔴 새로 추가: org 단위 전체
       @Select("""
                     SELECT *
                     FROM personnel_evaluation_aff.users_${year}
                     WHERE eval_year = #{year}
                       AND IFNULL(del_yn, 'N') = 'N'
                       AND c_name = #{org}
                     """)
       List<UserPE> findByOrg(@Param("org") String org,
                     @Param("year") int year);

       // 🔴 새로 추가: org + group + subName
       @Select("""
                     SELECT *
                     FROM personnel_evaluation_aff.users_${year}
                     WHERE eval_year = #{year}
                       AND IFNULL(del_yn, 'N') = 'N'
                       AND c_name  = #{org}
                       AND c_name2 = #{group}
                       AND sub_name = #{subName}
                     """)
       List<UserPE> findByOrgGroupAndSubName(@Param("org") String org,
                     @Param("group") String group,
                     @Param("subName") String subName,
                     @Param("year") int year);

       // 🔴 새로 추가: org + group + subCode
       @Select("""
                     SELECT *
                     FROM personnel_evaluation_aff.users_${year}
                     WHERE eval_year = #{year}
                       AND IFNULL(del_yn, 'N') = 'N'
                       AND c_name  = #{org}
                       AND c_name2 = #{group}
                       AND sub_code = #{subCode}
                     """)
       List<UserPE> findByOrgGroupAndSubCode(@Param("org") String org,
                     @Param("group") String group,
                     @Param("subCode") String subCode,
                     @Param("year") int year);

       @Select("""
                     SELECT *
                     FROM personnel_evaluation_aff.users_${year}
                     WHERE eval_year = #{year}
                       AND IFNULL(del_yn, 'N') = 'N'
                       AND c_name   = #{org}
                       AND sub_code = #{subCode}
                     """)
       List<UserPE> findByOrgAndSubCode(@Param("org") String org,
                     @Param("subCode") String subCode,
                     @Param("year") int year);
}
