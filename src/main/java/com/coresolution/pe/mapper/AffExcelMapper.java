package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;

@Mapper
public interface AffExcelMapper {
  // 사용자 업데이트 (파라미터 바인딩은 UserPE의 getter 기준: cName, subCode, evalYear ...)
  @Update("""
          UPDATE personnel_evaluation_aff.users_${evalYear}
             SET c_name    = #{cName},
                 c_name2   = #{cName2},
                 sub_code  = #{subCode},
                 team_code = #{teamCode},
                 position  = #{position},
                 pwd       = #{pwd},
                 name      = #{name},
                 create_at = #{createAt},
                 delete_at = #{deleteAt},
                 phone     = #{phone}
           WHERE id = #{id}
             AND eval_year = #{evalYear}
      """)
  void getUserExcelUpdate(UserPE u);

  // 사용자 인서트
  @Insert("""
          INSERT INTO personnel_evaluation_aff.users_${evalYear}
            (c_name, c_name2, sub_code, team_code, position, pwd, id, name, create_at, delete_at, phone, eval_year)
          VALUES
            (#{cName}, #{cName2}, #{subCode}, #{teamCode}, #{position}, #{pwd}, #{id}, #{name}, #{createAt}, #{deleteAt}, #{phone}, #{evalYear})
      """)
  void getUserExcelUpload(UserPE u);

  // 역할 전체 삭제 (해당 연도)
  @Delete("DELETE FROM personnel_evaluation_aff.user_roles_${evalYear} WHERE eval_year = #{evalYear}")
  void getRoleDelete(@Param("evalYear") int evalYear);

  // 역할 업로드 (파라미터명 카멜케이스로 변경)
  @Insert("""
          INSERT INTO personnel_evaluation_aff.user_roles_${evalYear}
            (user_id, role, eval_year)
          VALUES
            (#{userId}, #{role}, #{evalYear})
      """)
  void getRoleExcelUpload(@Param("userId") String userId, @Param("role") String role,
      @Param("evalYear") int evalYear);

  @Select("SELECT * FROM personnel_evaluation_aff.user_roles_${year} WHERE eval_year = #{year}")
  List<SubManagement> getPendingDepartments(@Param("year") int year);

  // 부서 전체 삭제 (해당 연도)
  @Delete("DELETE FROM personnel_evaluation_aff.sub_management WHERE eval_year = #{year}")
  void subDelete(@Param("year") int year);

  // 부서 업로드 (파라미터는 SubManagement의 카멜케이스 필드에 맞춤)
  @Insert("""
          INSERT INTO personnel_evaluation_aff.sub_management
            (sub_name, sub_code, eval_year, institution_id)
          VALUES
            (#{subName}, #{subCode}, #{evalYear}, #{institutionId})
      """)
  void getSubExcelUpload(SubManagement sub);

  /**
   * 부서 코드/연도/기관 중복 카운트.
   * institutionId 가 null 이면 기관 무관 카운트(레거시 호환).
   */
  @Select("""
          <script>
          SELECT COUNT(*)
            FROM personnel_evaluation_aff.sub_management
           WHERE sub_code  = #{subCode}
             AND eval_year = #{year}
           <if test="institutionId != null">
             AND institution_id = #{institutionId}
           </if>
          </script>
      """)
  int countByCodeAndYear(@Param("subCode") String subCode,
                         @Param("year") int year,
                         @Param("institutionId") Integer institutionId);

  /**
   * 부서 업데이트 — institution 단위로 스코프 (다른 기관의 동일 sub_code 보호).
   * institutionId 가 null 이면 레거시 경로 (기관 무관 UPDATE).
   */
  @Update("""
          <script>
          UPDATE personnel_evaluation_aff.sub_management
             SET sub_name = #{subName}
           WHERE sub_code  = #{subCode}
             AND eval_year = #{evalYear}
           <if test="institutionId != null">
             AND institution_id = #{institutionId}
           </if>
          </script>
      """)
  void subupdate(SubManagement s);

  // 부서 인서트 (institution_id 포함)
  @Insert("""
          INSERT INTO personnel_evaluation_aff.sub_management
            (sub_name, sub_code, eval_year, institution_id)
          VALUES
            (#{subName}, #{subCode}, #{evalYear}, #{institutionId})
      """)
  void subinsert(SubManagement s);
}
