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
            (sub_name, sub_code, eval_year)
          VALUES
            (#{subName}, #{subCode}, #{evalYear})
      """)
  void getSubExcelUpload(SubManagement sub);

  // 부서 코드/연도 중복 카운트 (Param 이름/바인딩 카멜케이스)
  @Select("""
          SELECT COUNT(*)
            FROM personnel_evaluation_aff.sub_management
           WHERE sub_code  = #{subCode}
             AND eval_year = #{year}
      """)
  int countByCodeAndYear(@Param("subCode") String subCode, @Param("year") int year);

  // 부서 업데이트
  @Update("""
          UPDATE personnel_evaluation_aff.sub_management
             SET sub_name = #{subName}
           WHERE sub_code  = #{subCode}
             AND eval_year = #{evalYear}
      """)
  void subupdate(SubManagement s);

  // 부서 인서트 (별도 메서드 유지)
  @Insert("""
          INSERT INTO personnel_evaluation_aff.sub_management
            (sub_name, sub_code, eval_year)
          VALUES
            (#{subName}, #{subCode}, #{evalYear})
      """)
  void subinsert(SubManagement s);
}
