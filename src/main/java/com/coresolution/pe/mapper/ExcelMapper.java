package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.RoleRow;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;

@Mapper
public interface ExcelMapper {

    // ── 단건 UPDATE (레거시 호환) ─────────────────────────────────────────
    @Update("""
            UPDATE personnel_evaluation.users_${evalYear}
               SET c_name    = #{cName},
                   c_name2   = #{cName2},
                   sub_code  = #{subCode},
                   team_code = #{teamCode},
                   position  = #{position},
                   pwd       = #{pwd},
                   name      = #{name},
                   create_at = #{createAt},
                   delete_at = #{deleteAt},
                   phone     = #{phone},
                   del_yn    = #{delYn}
             WHERE id        = #{id}
               AND eval_year = #{evalYear}
            """)
    void getUserExcelUpdate(UserPE u);

    // ── 단건 INSERT (레거시 호환) ─────────────────────────────────────────
    @Insert("""
            INSERT INTO personnel_evaluation.users_${evalYear}
              (c_name, c_name2, sub_code, team_code, position, pwd,
               id, name, create_at, delete_at, phone, eval_year, del_yn)
            VALUES
              (#{cName}, #{cName2}, #{subCode}, #{teamCode}, #{position}, #{pwd},
               #{id}, #{name}, #{createAt}, #{deleteAt}, #{phone}, #{evalYear}, #{delYn})
            """)
    void getUserExcelUpload(UserPE u);

    // ── 배치 UPSERT (신규/수정 동시 처리) ────────────────────────────────
    @Insert({"<script>",
        "INSERT INTO personnel_evaluation.users_${evalYear}",
        "  (c_name, c_name2, sub_code, team_code, position, pwd,",
        "   id, name, create_at, delete_at, phone, eval_year, del_yn)",
        "VALUES",
        "<foreach collection='users' item='u' separator=','>",
        "  (#{u.cName}, #{u.cName2}, #{u.subCode}, #{u.teamCode}, #{u.position}, #{u.pwd},",
        "   #{u.id}, #{u.name}, #{u.createAt}, #{u.deleteAt}, #{u.phone}, #{u.evalYear}, #{u.delYn})",
        "</foreach>",
        "ON DUPLICATE KEY UPDATE",
        "  c_name    = VALUES(c_name),",
        "  c_name2   = VALUES(c_name2),",
        "  sub_code  = VALUES(sub_code),",
        "  team_code = VALUES(team_code),",
        "  position  = VALUES(position),",
        "  name      = VALUES(name),",
        "  create_at = VALUES(create_at),",
        "  delete_at = VALUES(delete_at),",
        "  phone     = VALUES(phone),",
        "  del_yn    = VALUES(del_yn)",
        "</script>"})
    int batchUpsertUsers(@Param("users") List<UserPE> users, @Param("evalYear") int evalYear);

    // ── 역할 전체 삭제 ────────────────────────────────────────────────────
    @Delete("DELETE FROM personnel_evaluation.user_roles_${evalYear} WHERE eval_year = #{evalYear}")
    void getRoleDelete(@Param("evalYear") int evalYear);

    // ── 단건 역할 INSERT (레거시 호환) ───────────────────────────────────
    @Insert("""
            INSERT INTO personnel_evaluation.user_roles_${evalYear}
              (user_id, role, eval_year)
            VALUES
              (#{userId}, #{role}, #{evalYear})
            """)
    void getRoleExcelUpload(@Param("userId") String userId,
                             @Param("role") String role,
                             @Param("evalYear") int evalYear);

    // ── 배치 역할 INSERT ─────────────────────────────────────────────────
    @Insert({"<script>",
        "INSERT INTO personnel_evaluation.user_roles_${evalYear}",
        "  (user_id, role, eval_year) VALUES",
        "<foreach collection='roles' item='r' separator=','>",
        "  (#{r.userId}, #{r.role}, #{r.evalYear})",
        "</foreach>",
        "</script>"})
    int batchInsertRoles(@Param("roles") List<RoleRow> roles, @Param("evalYear") int evalYear);

    // ── 부서 조회/수정/등록 ───────────────────────────────────────────────
    @Select("""
            SELECT COUNT(*)
              FROM personnel_evaluation.sub_management
             WHERE sub_code  = #{subCode}
               AND eval_year = #{year}
            """)
    int countByCodeAndYear(@Param("subCode") String subCode, @Param("year") int year);

    @Update("""
            UPDATE personnel_evaluation.sub_management
               SET sub_name       = #{subName},
                   institution_id = #{institutionId}
             WHERE sub_code  = #{subCode}
               AND eval_year = #{evalYear}
            """)
    void subupdate(SubManagement s);

    @Insert("""
            INSERT INTO personnel_evaluation.sub_management
              (sub_name, sub_code, eval_year, institution_id)
            VALUES
              (#{subName}, #{subCode}, #{evalYear}, #{institutionId})
            """)
    void subinsert(SubManagement s);

    @Select("SELECT * FROM personnel_evaluation.user_roles_${year} WHERE eval_year = #{year}")
    List<SubManagement> getPendingDepartments(@Param("year") int year);

    @Delete("DELETE FROM personnel_evaluation.sub_management WHERE eval_year = #{year}")
    void subDelete(@Param("year") int year);

    @Insert("""
            INSERT INTO personnel_evaluation.sub_management
              (sub_name, sub_code, eval_year)
            VALUES
              (#{subName}, #{subCode}, #{evalYear})
            """)
    void getSubExcelUpload(SubManagement sub);
}
