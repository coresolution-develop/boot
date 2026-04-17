package com.coresolution.pe.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.coresolution.pe.entity.EvalMappingRule;

@Mapper
public interface EvalMappingRuleMapper {

    @Insert("""
            INSERT INTO personnel_evaluation.eval_mapping_rule
              (eval_year, rule_name,
               evaluator_role, evaluator_sub_prefix, evaluator_team_code,
               target_role, target_sub_prefix, target_team_code,
               target_scope, data_ev, data_type, eval_type_code,
               c_name, exclude_self, enabled, created_by, updated_by)
            VALUES
              (#{evalYear}, #{ruleName},
               #{evaluatorRole}, #{evaluatorSubPrefix}, #{evaluatorTeamCode},
               #{targetRole}, #{targetSubPrefix}, #{targetTeamCode},
               #{targetScope}, #{dataEv}, #{dataType}, #{evalTypeCode},
               #{cName}, #{excludeSelf}, #{enabled}, #{createdBy}, #{updatedBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalMappingRule rule);

    @Update("""
            UPDATE personnel_evaluation.eval_mapping_rule
               SET rule_name           = #{ruleName},
                   evaluator_role      = #{evaluatorRole},
                   evaluator_sub_prefix= #{evaluatorSubPrefix},
                   evaluator_team_code = #{evaluatorTeamCode},
                   target_role         = #{targetRole},
                   target_sub_prefix   = #{targetSubPrefix},
                   target_team_code    = #{targetTeamCode},
                   target_scope        = #{targetScope},
                   data_ev             = #{dataEv},
                   data_type           = #{dataType},
                   eval_type_code      = #{evalTypeCode},
                   c_name              = #{cName},
                   exclude_self        = #{excludeSelf},
                   enabled             = #{enabled},
                   updated_by          = #{updatedBy},
                   updated_at          = NOW()
             WHERE id = #{id}
            """)
    int update(EvalMappingRule rule);

    @Delete("DELETE FROM personnel_evaluation.eval_mapping_rule WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 연도별 전체 규칙 (활성 여부 무관) */
    @Select("SELECT id, eval_year AS evalYear, rule_name AS ruleName, evaluator_role AS evaluatorRole, evaluator_sub_prefix AS evaluatorSubPrefix, evaluator_team_code AS evaluatorTeamCode, target_role AS targetRole, target_sub_prefix AS targetSubPrefix, target_team_code AS targetTeamCode, target_scope AS targetScope, data_ev AS dataEv, data_type AS dataType, eval_type_code AS evalTypeCode, c_name AS cName, exclude_self AS excludeSelf, enabled, created_by AS createdBy, created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt FROM personnel_evaluation.eval_mapping_rule WHERE eval_year = #{year} ORDER BY id")
    List<EvalMappingRule> findAllByYear(@Param("year") int year);

    /** 대상 생성 시 사용: 활성 규칙만 */
    @Select("SELECT id, eval_year AS evalYear, rule_name AS ruleName, evaluator_role AS evaluatorRole, evaluator_sub_prefix AS evaluatorSubPrefix, evaluator_team_code AS evaluatorTeamCode, target_role AS targetRole, target_sub_prefix AS targetSubPrefix, target_team_code AS targetTeamCode, target_scope AS targetScope, data_ev AS dataEv, data_type AS dataType, eval_type_code AS evalTypeCode, c_name AS cName, exclude_self AS excludeSelf, enabled, created_by AS createdBy, created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt FROM personnel_evaluation.eval_mapping_rule WHERE eval_year = #{year} AND enabled = 1 ORDER BY id")
    List<EvalMappingRule> findActiveByYear(@Param("year") int year);

    /** 전년도 규칙 복사 (enabled=1인 것만) */
    @Insert("""
            INSERT INTO personnel_evaluation.eval_mapping_rule
              (eval_year, rule_name,
               evaluator_role, evaluator_sub_prefix, evaluator_team_code,
               target_role, target_sub_prefix, target_team_code,
               target_scope, data_ev, data_type, eval_type_code,
               c_name, exclude_self, enabled, created_by, updated_by)
            SELECT #{toYear}, rule_name,
               evaluator_role, evaluator_sub_prefix, evaluator_team_code,
               target_role, target_sub_prefix, target_team_code,
               target_scope, data_ev, data_type, eval_type_code,
               c_name, exclude_self, 1, #{operator}, #{operator}
              FROM personnel_evaluation.eval_mapping_rule
             WHERE eval_year = #{fromYear} AND enabled = 1
            """)
    int copyFromYear(@Param("fromYear") int fromYear,
                     @Param("toYear") int toYear,
                     @Param("operator") String operator);

    /** 기존 연도 규칙 전체 삭제 (재세팅 시) */
    @Delete("DELETE FROM personnel_evaluation.eval_mapping_rule WHERE eval_year = #{year}")
    int deleteAllByYear(@Param("year") int year);

    /** 기본 규칙 8개 일괄 시딩 */
    @Insert({
        "<script>",
        "INSERT INTO personnel_evaluation.eval_mapping_rule",
        "  (eval_year, rule_name, evaluator_role, evaluator_sub_prefix, evaluator_team_code,",
        "   target_role, target_sub_prefix, target_team_code,",
        "   target_scope, data_ev, data_type, eval_type_code,",
        "   c_name, exclude_self, enabled, created_by, updated_by)",
        "VALUES",
        "<foreach collection='rules' item='r' separator=','>",
        "  (#{r.evalYear}, #{r.ruleName}, #{r.evaluatorRole}, #{r.evaluatorSubPrefix}, #{r.evaluatorTeamCode},",
        "   #{r.targetRole}, #{r.targetSubPrefix}, #{r.targetTeamCode},",
        "   #{r.targetScope}, #{r.dataEv}, #{r.dataType}, #{r.evalTypeCode},",
        "   #{r.cName}, #{r.excludeSelf}, #{r.enabled}, #{r.createdBy}, #{r.updatedBy})",
        "</foreach>",
        "</script>"
    })
    int insertAll(@Param("rules") List<EvalMappingRule> rules);
}
