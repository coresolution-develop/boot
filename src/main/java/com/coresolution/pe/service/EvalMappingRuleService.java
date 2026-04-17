package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.AdminDefaultTarget;
import com.coresolution.pe.entity.EvalMappingRule;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.DefaultTargetMapper;
import com.coresolution.pe.mapper.EvalMappingRuleMapper;
import com.coresolution.pe.mapper.LoginMapper;
import com.coresolution.pe.mapper.UserRolesMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvalMappingRuleService {

    private final EvalMappingRuleMapper ruleMapper;
    private final DefaultTargetMapper   defMap;
    private final LoginMapper           loginMapper;
    private final UserRolesMapper       rolesMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    // ─── CRUD ────────────────────────────────────────────────────────────

    public List<EvalMappingRule> listByYear(int year) {
        return ruleMapper.findAllByYear(year);
    }

    @Transactional
    public EvalMappingRule save(EvalMappingRule rule, String operator) {
        rule.setCreatedBy(operator);
        rule.setUpdatedBy(operator);
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.update(rule);
        }
        return rule;
    }

    @Transactional
    public void delete(Long id) {
        ruleMapper.delete(id);
    }

    // ─── 연도 복사 ────────────────────────────────────────────────────────

    @Transactional
    public int copyFromYear(int fromYear, int toYear, String operator) {
        if (fromYear == toYear) throw new IllegalArgumentException("원본과 대상 연도가 같습니다.");
        return ruleMapper.copyFromYear(fromYear, toYear, operator);
    }

    // ─── 기본 규칙 시딩 ───────────────────────────────────────────────────

    /**
     * 연도에 규칙이 없을 때 표준 8개 규칙을 자동으로 생성.
     * 이미 규칙이 존재하면 아무것도 하지 않음.
     */
    @Transactional
    public int seedDefaultRules(int year, String operator) {
        if (!ruleMapper.findAllByYear(year).isEmpty()) {
            return 0; // 이미 있으면 스킵
        }
        List<EvalMappingRule> defaults = buildDefaultRules(year, operator);
        return ruleMapper.insertAll(defaults);
    }

    private List<EvalMappingRule> buildDefaultRules(int year, String operator) {
        List<EvalMappingRule> list = new ArrayList<>();
        list.add(rule(year, "경혁팀 → 경혁팀",
                null, null, "GH_TEAM",
                null, null, "GH_TEAM",
                "SPECIFIC_TEAM", "D", "AA", "GH_TO_GH", true, operator));
        list.add(rule(year, "경혁팀 → 진료부",
                null, null, "GH_TEAM",
                null, "A", null,
                "ALL_PREFIX", "C", "AA", "GH_TO_MEDICAL", true, operator));
        list.add(rule(year, "진료부 → 경혁팀",
                null, "A", null,
                null, null, "GH_TEAM",
                "SPECIFIC_TEAM", "B", "AA", "MEDICAL_TO_GH", true, operator));
        list.add(rule(year, "부서장 → 부서원",
                "sub_head", null, null,
                "sub_member", null, null,
                "SAME_DEPT", "E", "AB", "SUB_HEAD_TO_MEMBER", true, operator));
        list.add(rule(year, "부서원 → 부서장",
                "sub_member", null, null,
                "sub_head", null, null,
                "SAME_DEPT", "F", "AA", "SUB_MEMBER_TO_HEAD", true, operator));
        list.add(rule(year, "부서원 → 부서원",
                "sub_member", null, null,
                "sub_member", null, null,
                "SAME_DEPT", "G", "AB", "SUB_MEMBER_TO_MEMBER", true, operator));
        list.add(rule(year, "경혁팀장 → 1인부서",
                "team_head", null, null,
                "one_person_sub", null, null,
                "SPECIFIC_ROLE", "E", "AA", "TEAM_HEAD_TO_ONE_PERSON", true, operator));
        list.add(rule(year, "진료팀장 → 진료부(같은 부서)",
                "medical_leader", "A", null,
                null, "A", null,
                "SAME_DEPT", "A", "AB", "SUB_HEAD_TO_MEMBER", true, operator));
        return list;
    }

    private EvalMappingRule rule(int year, String name,
            String evRole, String evSubPfx, String evTeam,
            String tgRole, String tgSubPfx, String tgTeam,
            String scope, String dataEv, String dataType, String typeCode,
            boolean excludeSelf, String operator) {
        EvalMappingRule r = new EvalMappingRule();
        r.setEvalYear(year);
        r.setRuleName(name);
        r.setEvaluatorRole(evRole);
        r.setEvaluatorSubPrefix(evSubPfx);
        r.setEvaluatorTeamCode(evTeam);
        r.setTargetRole(tgRole);
        r.setTargetSubPrefix(tgSubPfx);
        r.setTargetTeamCode(tgTeam);
        r.setTargetScope(scope);
        r.setDataEv(dataEv);
        r.setDataType(dataType);
        r.setEvalTypeCode(typeCode);
        r.setExcludeSelf(excludeSelf);
        r.setEnabled(true);
        r.setCreatedBy(operator);
        r.setUpdatedBy(operator);
        return r;
    }

    // ─── 규칙 적용 (대상 재생성) ──────────────────────────────────────────

    /**
     * 연도의 활성 규칙을 모두 적용하여 admin_default_targets 를 재생성.
     * 기존 트랜잭션 밖에서 실행 — 대규모 루프 커밋 전략 유지.
     *
     * @return 생성된 (evaluator, target) 쌍 총 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int applyRules(String year) {
        List<EvalMappingRule> rules = ruleMapper.findActiveByYear(Integer.parseInt(year));
        log.info("[RuleEngine] year={}, activeRules={}", year, rules.size());
        if (rules.isEmpty()) return 0;

        // 전체 직원 로드 (del_yn='N')
        List<UserPE> allUsers = loginMapper.getUserList(year);
        if (allUsers == null || allUsers.isEmpty()) return 0;
        log.info("[RuleEngine] users={}", allUsers.size());

        // 역할 벌크 로드 → userId → roles 맵
        Map<String, List<String>> rolesByUser = buildRoleMap(year);

        // 각 유저에 roles 세팅
        allUsers.forEach(u -> u.setRoles(rolesByUser.getOrDefault(u.getId(), List.of())));

        // 기존 기본 대상 전체 삭제
        defMap.deleteAllForYear(year);

        int total = 0;
        for (EvalMappingRule rule : rules) {
            total += applyOneRule(rule, allUsers, year);
        }
        log.info("[RuleEngine] done. totalInserted={}", total);
        return total;
    }

    private Map<String, List<String>> buildRoleMap(String year) {
        List<Map<String, String>> raw = rolesMapper.findAllRolesForYear(year);
        Map<String, List<String>> map = new HashMap<>();
        for (Map<String, String> row : raw) {
            String userId = row.get("userId");
            String role   = row.get("role");
            if (userId != null && role != null) {
                map.computeIfAbsent(userId, k -> new ArrayList<>()).add(role);
            }
        }
        return map;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int applyOneRule(EvalMappingRule rule, List<UserPE> allUsers, String year) {
        List<UserPE> evaluators = allUsers.stream()
                .filter(u -> matchesEvaluator(u, rule))
                .collect(Collectors.toList());

        int cnt = 0;
        for (UserPE evaluator : evaluators) {
            List<UserPE> targets = findTargets(evaluator, allUsers, rule);
            for (UserPE target : targets) {
                if (rule.isExcludeSelf() && evaluator.getId().equals(target.getId())) continue;

                AdminDefaultTarget row = new AdminDefaultTarget();
                row.setEvalYear(Integer.parseInt(year));
                row.setUserId(evaluator.getId());
                row.setTargetId(target.getId());
                row.setEvalTypeCode(rule.getEvalTypeCode());
                row.setDataEv(rule.getDataEv());
                row.setDataType(rule.getDataType());
                cnt += defMap.upsert(row);
            }
        }
        log.debug("[RuleEngine] rule='{}' → {} pairs", rule.getRuleName(), cnt);
        return cnt;
    }

    // ─── 필터링 헬퍼 ─────────────────────────────────────────────────────

    private boolean matchesEvaluator(UserPE u, EvalMappingRule rule) {
        // 역할 조건
        if (hasText(rule.getEvaluatorRole())) {
            if (u.getRoles() == null || !u.getRoles().contains(rule.getEvaluatorRole())) return false;
        }
        // 부서코드 접두어 조건
        if (hasText(rule.getEvaluatorSubPrefix())) {
            if (u.getSubCode() == null || !u.getSubCode().startsWith(rule.getEvaluatorSubPrefix())) return false;
        }
        // 팀코드 조건
        if (hasText(rule.getEvaluatorTeamCode())) {
            if (!rule.getEvaluatorTeamCode().equals(u.getTeamCode())) return false;
        }
        // 기관 조건
        if (hasText(rule.getCName())) {
            if (!rule.getCName().equals(u.getCName())) return false;
        }
        return true;
    }

    private List<UserPE> findTargets(UserPE evaluator, List<UserPE> allUsers, EvalMappingRule rule) {
        Stream<UserPE> stream = allUsers.stream();

        // 같은 기관(기본 원칙)
        if (hasText(evaluator.getCName())) {
            stream = stream.filter(u -> evaluator.getCName().equals(u.getCName()));
        }

        // 대상자 역할 필터
        if (hasText(rule.getTargetRole())) {
            stream = stream.filter(u -> u.getRoles() != null && u.getRoles().contains(rule.getTargetRole()));
        }

        // 대상자 팀코드 필터
        if (hasText(rule.getTargetTeamCode())) {
            stream = stream.filter(u -> rule.getTargetTeamCode().equals(u.getTeamCode()));
        }

        // 범위(scope) 적용
        String scope = rule.getTargetScope() != null ? rule.getTargetScope() : "SPECIFIC_ROLE";
        switch (scope) {
            case "SAME_DEPT" -> {
                String evalSub = evaluator.getSubCode();
                stream = stream.filter(u -> evalSub != null && evalSub.equals(u.getSubCode()));
            }
            case "ALL_PREFIX" -> {
                String pfx = rule.getTargetSubPrefix();
                if (hasText(pfx)) stream = stream.filter(u -> u.getSubCode() != null && u.getSubCode().startsWith(pfx));
            }
            case "SPECIFIC_TEAM" -> { /* teamCode 필터로 이미 처리 */ }
            default -> { /* SPECIFIC_ROLE: role 필터로 처리 */ }
        }

        // SAME_DEPT/SPECIFIC_ROLE이 아닌 경우에도 target_sub_prefix가 있으면 추가 필터
        if (!"SAME_DEPT".equals(scope) && !"ALL_PREFIX".equals(scope) && hasText(rule.getTargetSubPrefix())) {
            String pfx = rule.getTargetSubPrefix();
            stream = stream.filter(u -> u.getSubCode() != null && u.getSubCode().startsWith(pfx));
        }

        return stream.collect(Collectors.toList());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
