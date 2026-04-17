package com.coresolution.pe.service;

import java.security.Policy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.AdminCustomTarget;
import com.coresolution.pe.entity.AdminDefaultTarget;
import com.coresolution.pe.entity.DepartmentDto;
import com.coresolution.pe.entity.EvalMeta;
import com.coresolution.pe.entity.EvalPolicy;
import com.coresolution.pe.entity.EvalResult;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.SplitTargets;
import com.coresolution.pe.entity.TargetBuckets;
import com.coresolution.pe.entity.TargetRow;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.CustomTargetMapper;
import com.coresolution.pe.mapper.DefaultTargetMapper;
import com.coresolution.pe.mapper.FinalTargetMapper;
import com.coresolution.pe.mapper.LoginMapper;
import com.coresolution.pe.mapper.PolicyMapper;
import com.coresolution.pe.mapper.UserMapper;
import com.coresolution.pe.mapper.UserRolesMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {
    @Autowired
    private LoginMapper loginMapper; // users_2025 조회
    @Autowired
    private UserMapper userMapper; // users_2025 조회
    @Autowired
    private UserRolesMapper rolesMapper; // user_roles_2025 조회
    @Autowired
    private CustomTargetMapper customMapper; // custom_target_2025 조회
    @Autowired
    private DefaultTargetMapper defMap; // default_targets_2025 조회
    @Autowired
    private FinalTargetMapper finalTargetMapper; // final_targets_2025 조회
    @Autowired
    private PolicyMapper policyMapper; // eval_policies_2025 조회

    public List<UserPE> getEvaluationTargets(String userId, String year) {
        // 1) 내 역할 조회
        List<String> myRoles = rolesMapper.findRolesByUser(userId, year);

        // 2) 공통 대상: 진료부 전체, 경혁팀 전체
        List<UserPE> allJinryubu = userMapper.findBySubCodes(Arrays.asList("A00", "A01", "A02"), year);
        List<UserPE> allGhTeam = userMapper.findByTeamCode("GH_TEAM", year);

        // 3) 내 부서원
        String mySub = userMapper.findSubCodeByUser(userId, year);
        List<UserPE> myDeptMembers = userMapper.findBySubCode(mySub, year);

        // 4) 1인부서 직원
        List<String> onePersonIds = rolesMapper.findUserIdsByRole("one_person_sub", year);
        List<UserPE> onePersonStaff = userMapper.findByIds(onePersonIds, year);

        // 결과 리스트에 중복 없이 담기
        Set<String> added = new HashSet<>();
        List<UserPE> targets = new ArrayList<>();

        BiConsumer<List<UserPE>, Boolean> addAllIf = (list, includeSelf) -> {
            for (UserPE u : list) {
                if (!includeSelf && u.getId().equals(userId))
                    continue;
                if (added.add(u.getId())) {
                    targets.add(u);
                }
            }
        };

        boolean isTeamHead = myRoles.contains("team_head");
        boolean isTeamMember = myRoles.contains("team_member");
        boolean isSubHead = myRoles.contains("sub_head");
        boolean isOnePerson = myRoles.contains("one_person_sub");

        // ── 경혁팀/부서장(=team_head || sub_head) ──
        if (isTeamHead || isSubHead) {
            addAllIf.accept(allJinryubu, false);
            addAllIf.accept(allGhTeam, false);
            addAllIf.accept(myDeptMembers, false);
            // 경혁팀장/부서장만 1인부서도 평가
            if (isTeamHead) {
                addAllIf.accept(onePersonStaff, false);
            }
        }
        // ── 경혁팀원(TEAM_MEMBER) ──
        else if (isTeamMember) {
            addAllIf.accept(allJinryubu, false);
            addAllIf.accept(allGhTeam, false);
            // 내 부서장 + 내 부서원
            List<UserPE> myDeptHead = rolesMapper
                    .findUserIdsBySubAndRoles(
                            mySub,
                            String.valueOf(year),
                            Collections.singletonList("sub_head"))
                    .stream()
                    .map(id -> userMapper.findById(id, String.valueOf(year)))
                    .collect(Collectors.toList());
            addAllIf.accept(myDeptHead, false);
            addAllIf.accept(myDeptMembers, false);
        }
        // ── 경혁팀/1인부서 ──
        else if (isOnePerson) {
            addAllIf.accept(allJinryubu, false);
            addAllIf.accept(allGhTeam, false);
        }

        return targets;
    }

    public List<UserPE> getGhTeamMembers(String year) {
        return userMapper.findByTeamCode("GH_TEAM", year);
    }

    // 부서별로 묶어서 Department 리스트로 반환
    public List<DepartmentDto> getDepartments(String year) {
        List<UserPE> all = loginMapper.getUserList(year);
        // 부서명(sub_name)으로 그룹핑
        Map<String, List<UserPE>> map = all.stream()
                .collect(Collectors.groupingBy(UserPE::getSubName));
        // DTO로 변환
        return map.entrySet().stream()
                .map(e -> new DepartmentDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** 1) 기본 대상 계산 */
    public List<UserPE> getDefaultTargets(String userId, String year) {
        String mySub = userMapper.findSubCodeByUser(userId, year);
        if (mySub == null || mySub.isBlank())
            return List.of();
        return userMapper.findBySubCode(mySub, year).stream()
                .filter(t -> !userId.equals(t.getId())) // 본인 제외
                .collect(java.util.stream.Collectors.toList());
    }

    /** 2) 커스텀 기록 조회 */
    public List<AdminCustomTarget> getCustoms(String userId, String year) {
        return customMapper.findByUserAndYear(userId, year);
    }

    /** 팀별 기본평가 대상만 따로 분리한다면... */
    public List<String> getDefaultTargetsIdsForTeam(String userId, String year) {
        // 예: 경혁팀장 / 팀원 전용 로직
        return getEvaluationTargets(userId, year)
                .stream()
                .filter(u -> "GH_TEAM".equals(u.getTeamCode()))
                .map(UserPE::getId)
                .collect(Collectors.toList());
    }

    /** 부서별 기본 설정을 저장 */
    public void saveDefaultDeptTargets(String year, List<String> deptTargetIds) {
        // 예: default_targets 테이블 전체 delete 후 insert
        defMap.deleteAllForDept(year);
        for (String id : deptTargetIds) {
            defMap.insertDeptTarget(year, id);
        }
    }

    /** 팀별 기본 설정을 저장 */
    public void saveDefaultTeamTargets(String year, List<String> teamTargetIds) {
        defMap.deleteAllForTeam(year);
        for (String id : teamTargetIds) {
            defMap.insertTeamTarget(year, id);
        }
    }

    // public List<UserPE> getDefaultTargets(String id, String year) {
    // // 0) 본인 정보 조회 (기관명 가져오기 위해)
    // UserPE me = userMapper.findById(id, year);
    // String myCompany = me.getC_name();
    // // 1) 내 부서 코드
    // String mySub = userMapper.findSubCodeByUser(id, year);
    // // 2) 내 부서원 전체 중 같은 기관명인 직원만, 그리고 본인(id) 제외
    // List<UserPE> members = userMapper.findBySubCode(mySub, year).stream()
    // .filter(u -> myCompany.equals(u.getC_name()))
    // .filter(u -> !u.getId().equals(id))
    // .collect(Collectors.toList());
    // // 3) 내 부서의 부서장만 중 같은 기관명인 직원만, 그리고 본인 제외
    // List<UserPE> heads = rolesMapper
    // .findUserIdsBySubAndRoles(mySub, year, List.of("sub_head"))
    // .stream()
    // .map(userId -> userMapper.findById(userId, year))
    // .filter(u -> myCompany.equals(u.getC_name()))
    // .filter(u -> !u.getId().equals(id))
    // .collect(Collectors.toList());

    // // 4) 합집합 (중복 제거)
    // Set<String> added = new HashSet<>();
    // List<UserPE> result = new ArrayList<>();
    // Stream.concat(heads.stream(), members.stream())
    // .forEach(u -> {
    // if (added.add(u.getId())) {
    // result.add(u);
    // }
    // });

    // return result;
    // }
    // 기존 규칙계산 버전은 이름 바꾸거나 남겨두고,
    // 화면에서 “기본/커스텀 분리 출력”이 필요할 때 이걸 쓰세요.
    public List<UserPE> getDefaultTargetsFromDb(String userId, String year) {
        return defMap.findDefaultTargetsDetailed(userId, year);
    }

    /**
     * 경혁팀+진료부+1인부서 상호평가 대상 조회
     */
    public List<UserPE> getGhJinryuTargets(String userId, String year) {

        UserPE me = userMapper.findById(userId, year); // ★ 평가자 전체(기관 포함)
        String mySub = userMapper.findSubCodeByUser(userId, year);
        String myTeamCode = userMapper.findTeamCodeByUser(userId, year);
        List<String> myRolesRaw = rolesMapper.findRolesByUser(userId, year);
        Set<String> myRoles = safeList(myRolesRaw).stream()
                .filter(Objects::nonNull).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        boolean isGhTeam = "GH_TEAM".equals(myTeamCode);
        boolean isOnePerson = myRoles.contains("one_person_sub");
        boolean isTeamHead = myRoles.contains("team_head");
        boolean isMedicalLead = myRoles.contains("medical_leader"); // ★ 추가
        boolean isJinryu = mySub != null && mySub.startsWith("A");

        String cName = me.getCName();
        String cName2 = me.getCName2();
        // ★ 기관 정책 로드
        String orgCode = nvl(userMapper.findOrgCodeByUser(userId, year)); // c_name 등
        EvalPolicy policy = policyMapper.findPolicy(year, orgCode);

        // 기본값: 기관 정책 없으면 기존 동작 유지 (ALL_MEDICAL)
        String scope = (policy != null && policy.getLeaderScope() != null)
                ? policy.getLeaderScope()
                : "ALL_MEDICAL";
        List<String> customSubs = (policy != null) ? parseCsv(policy.getLeaderSublist()) : List.of();

        Set<String> added = new HashSet<>();
        List<UserPE> targets = new ArrayList<>();

        // (1) GH 팀원/팀장: 진료부 전체 + GH 팀
        if (isGhTeam) {
            userMapper.findBySubCodesInOrg(List.of("A00", "A01", "A02"), year, cName, cName2)
                    .forEach(u -> addIfNew(u, targets, added, userId));
            userMapper.findByTeamCodeInOrg("GH_TEAM", year, cName, cName2)
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }

        // (2) 진료부(팀장·팀원)
        if (isJinryu) {
            // ⬇️ 팀장 OR 진료팀장 둘 다 진입
            if (isTeamHead || isMedicalLead) {
                if (isMedicalLead) {
                    // 기관 정책 기반(ALL_MEDICAL / OWN_SUB / CUSTOM)
                    List<String> subsToEval;
                    String effectiveScope = (scope != null ? scope : "ALL_MEDICAL");
                    switch (effectiveScope) {
                        case "OWN_SUB" -> subsToEval = List.of(mySub);
                        case "CUSTOM" -> subsToEval = (customSubs == null || customSubs.isEmpty())
                                ? List.of(mySub)
                                : customSubs;
                        default -> subsToEval = List.of("A00", "A01", "A02");
                    }
                    userMapper.findBySubCodesInOrg(subsToEval, year, cName, cName2)
                            .forEach(u -> addIfNew(u, targets, added, userId));
                } else {
                    // 일반 부서장: 본인 부서원 전원
                    userMapper.findBySubCodeInOrg(mySub, year, cName, cName2)
                            .forEach(u -> addIfNew(u, targets, added, userId));
                }
            }
            // 진료부 ↔ GH 상호평가는 동일
            userMapper.findByTeamCodeInOrg("GH_TEAM", year, cName, cName2)
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }

        // (3) 1인부서 ↔ GH 팀장
        if (isOnePerson && !isGhTeam) {
            rolesMapper.findUserIdsByRoleInOrg("team_head", year, cName, cName2).stream()
                    .map(id -> userMapper.findById(id, year))
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }
        if (isTeamHead && !isOnePerson) {
            rolesMapper.findUserIdsByRoleInOrg("one_person_sub", year, cName, cName2).stream()
                    .map(id -> userMapper.findById(id, year))
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }

        return targets;
    }

    /** 1년치 모든 기본 대상 초기화 후 재저장 **/
    @Transactional
    public void saveAllDefaultTargets(String year) {
        // ① 기존 데이터 싹 지우고
        defMap.deleteAllForYear(year);

        // ② 모든 직원 조회
        List<UserPE> all = loginMapper.getUserList(year);

        // ③ 직원별 기본 대상을 계산해서 insert
        for (UserPE u : all) {
            List<UserPE> defaults = getDefaultTargets(u.getId(), year);
            for (UserPE t : defaults) {
                defMap.insert(year, u.getId(), t.getId());
            }
        }
    }

    /** (사용자별) 커스텀 대상 삭제가 필요할 때는 이거 사용 */
    @Transactional
    public int clearCustomTargets(String userId, String year) {
        customMapper.deleteAllForUser(userId, year);
        return 1;
    }

    // /** ADD / REMOVE 리스트를 INSERT (upsert) **/
    // @Transactional
    // public void saveCustomTargets(
    // String userId,
    // String year,
    // List<String> addTargets,
    // List<String> removeTargets) {

    // if (addTargets != null) {
    // for (String targetId : addTargets) {
    // customMapper.upsert(year, userId, targetId,
    // AdminCustomTarget.Action.ADD.name());
    // }
    // }
    // if (removeTargets != null) {
    // for (String targetId : removeTargets) {
    // customMapper.upsert(year, userId, targetId,
    // AdminCustomTarget.Action.REMOVE.name());
    // }
    // }
    // }

    // (가) 커스텀 ADD 목록

    // 기존 규칙계산 버전은 이름 바꾸거나 남겨두고,
    // 화면에서 “기본/커스텀 분리 출력”이 필요할 때 이걸 쓰세요.
    // public List<UserPE> getCustomAdds(String userId, String year) {
    // return customMapper.findCustomAdds(userId, year).stream()
    // .map(id -> userMapper.findById(id, year))
    // .collect(Collectors.toList());
    // }
    public List<UserPE> getCustomAdds(String userId, String year) {
        return customMapper.findCustomAddsDetailed(userId, year);
    }

    // (나) 커스텀 REMOVE 목록
    public List<UserPE> getCustomRemoves(String userId, String year) {
        return customMapper.findCustomRemoves(userId, year).stream()
                .map(id -> userMapper.findById(id, year))
                .collect(Collectors.toList());
    }

    // // (다) 기본 + GH·진료부 대상 합산 후 ADD, REMOVE 적용
    // public List<UserPE> getFinalTargets(String userId, String year) {
    // // 1) 부서장+부서원 기본 대상
    // List<UserPE> defaults = getDefaultTargets(userId, year);
    // // 2) 경혁+진료부(+1인부서) 대상
    // List<UserPE> ghJin = getGhJinryuTargets(userId, year);
    // // 3) 커스텀
    // List<UserPE> adds = getCustomAdds(userId, year);
    // List<UserPE> removes = getCustomRemoves(userId, year);

    // Map<String, UserPE> map = new LinkedHashMap<>();
    // Stream.concat(defaults.stream(), ghJin.stream()).forEach(u ->
    // map.put(u.getId(), u));
    // adds.forEach(u -> map.put(u.getId(), u));
    // removes.forEach(u -> map.remove(u.getId()));

    // List<UserPE> result = new ArrayList<>(map.values());

    // // ★ 여기서 후처리로 roles / (필요 시) 부서명·팀명까지 채움
    // for (UserPE u : result) {
    // // 1) 역할 채우기
    // u.setRoles(rolesMapper.findRolesByUser(u.getId(), year));

    // // 2) 계산된 대상(defaults/ghJin)은 subName/teamName이 비어 있을 수 있으니 보강
    // if (u.getSubName() == null || u.getTeamName() == null) {
    // UserPE filled = loginMapper.findByIdWithNames(u.getId(), year);
    // if (filled != null) {
    // u.setSubName(filled.getSubName());
    // u.setTeamName(filled.getTeamName());
    // }
    // }
    // }

    // return result;
    // }

    // public void saveDefaultAsCustomAdds(String userId, String year, List<UserPE>
    // defaultTargets) {
    // for (UserPE t : defaultTargets) {
    // // 중복 방지는 DB의 UNIQUE 제약이 이미 있으므로, 예외를 무시하거나 존재 여부 체크
    // try {
    // customMapper.insertCustomTarget(userId, t.getId(), year, "ADD");
    // } catch (Exception e) {
    // // 이미 있으면 무시
    // }
    // }
    // }

    /** DB에 저장된 최종 대상 그대로 */
    public List<UserPE> getDbFinalTargets(String userId, String year) {
        return finalTargetMapper.findTargetsWithNames(userId, year);
    }

    /** 계산 결과를 DB(admin_default_targets)에 덮어쓰기 */
    public void saveFinalTargetsToDb(String userId, String year, List<String> targetIds) {
        defMap.deleteByUserAndYear(userId, year);
        if (targetIds != null && !targetIds.isEmpty()) {
            defMap.insertAll(userId, year, targetIds);
        }
    }

    /** [초기화 버튼] 전 직원 계산 → DB에 일괄 저장 */
    @Transactional
    public int initDefaultsForAll(String year) {
        List<UserPE> users = loginMapper.getUserList(year); // 활성 사용자 조회 쿼리 필요
        int totalInserted = 0;
        for (UserPE u : users) {
            String userId = u.getId();
            List<UserPE> calc = getFinalTargets(userId, year); // 규칙+커스텀 계산
            List<String> ids = calc.stream().map(UserPE::getId).toList();
            defMap.deleteByUserAndYear(userId, year);
            if (!ids.isEmpty()) {
                totalInserted += defMap.insertAll(userId, year, ids);
            }
        }
        return totalInserted;
    }

    /** 기본 대상 = (동일부서 규칙) + (경혁/진료/1인부서 규칙) [모두 '같은 기관'만] */
    public List<UserPE> computeBaseDefaultTargets(String userId, String year) {
        UserPE me = userMapper.findById(userId, year);
        if (me == null)
            return List.of();

        List<UserPE> sameSub = computeSameSubTargets(userId, year, me); // E/F/G
        List<UserPE> ghJin = getGhJinryuTargetsInOrg(userId, year, me); // A/B/C/D + 기타
        // del_yn = 'Y' 인 애들 제거 (방어코드)
        sameSub.removeIf(u -> "Y".equalsIgnoreCase(u.getDelYn()));
        ghJin.removeIf(u -> "Y".equalsIgnoreCase(u.getDelYn()));
        // ID 기준 Dedup
        Map<String, UserPE> map = new LinkedHashMap<>();
        sameSub.stream().filter(u -> !userId.equals(u.getId())).forEach(u -> map.put(u.getId(), u));
        ghJin.stream().filter(u -> !userId.equals(u.getId())).forEach(u -> map.put(u.getId(), u));

        return new ArrayList<>(map.values());

    }

    /** 동일 부서 규칙 (같은 기관만) */
    private List<UserPE> computeSameSubTargets(String userId, String year, UserPE me) {
        String mySub = nz(userMapper.findSubCodeByUser(userId, year));
        if (mySub.isEmpty())
            return List.of();

        // 같은 기관 + 같은 부서
        List<UserPE> members = userMapper.findBySubCodeInOrg(mySub, year, me.getCName(), me.getCName2());
        Set<String> roles = lower(rolesMapper.findRolesByUser(userId, year));

        boolean isHead = roles.contains("sub_head"); // 부서장
        boolean isMedLead = roles.contains("medical_leader");// 진료팀장도 동일부서면 ‘부서장’으로 취급

        List<UserPE> out = new ArrayList<>();
        if (isHead || isMedLead) {
            // 부서장/진료팀장 → 부서원 전원(E)
            for (UserPE u : members) {
                if (!u.getId().equals(userId) && sameOrg(u, me))
                    out.add(u);
            }
        } else {
            // 부서원 → 부서장(F) + 부서원↔부서원(G) 대상(부서원 전체) 일단 모음
            for (UserPE u : members) {
                if (!u.getId().equals(userId) && sameOrg(u, me))
                    out.add(u);
            }
        }
        return out;
    }

    /** GH/진료/1인부서 규칙 (같은 기관만) — 기존 getGhJinryuTargets를 기관제한 버전으로 */
    public List<UserPE> getGhJinryuTargetsInOrg(String userId, String year, UserPE me) {
        String mySub = userMapper.findSubCodeByUser(userId, year);
        String myTeamCode = userMapper.findTeamCodeByUser(userId, year);
        Set<String> myRoles = lower(rolesMapper.findRolesByUser(userId, year));

        boolean isGhTeam = "GH_TEAM".equalsIgnoreCase(nz(myTeamCode));
        boolean isOnePerson = myRoles.contains("one_person_sub");
        boolean isTeamHead = myRoles.contains("team_head");
        boolean isMedicalLead = myRoles.contains("medical_leader");
        boolean isJinryu = mySub != null && mySub.startsWith("A");

        // 기관 정책
        String orgCode = nvl(userMapper.findOrgCodeByUser(userId, year));
        EvalPolicy policy = policyMapper.findPolicy(year, orgCode);
        String scope = (policy != null && policy.getLeaderScope() != null) ? policy.getLeaderScope() : "ALL_MEDICAL";
        List<String> customSubs = (policy != null) ? parseCsv(policy.getLeaderSublist()) : List.of();

        Set<String> added = new HashSet<>();
        List<UserPE> targets = new ArrayList<>();

        // (1) GH → 진료부 전체 + GH
        if (isGhTeam) {
            userMapper.findBySubCodesInOrg(List.of("A00", "A01", "A02"), year, me.getCName(), me.getCName2())
                    .forEach(u -> addIfNew(u, targets, added, userId));
            userMapper.findByTeamCodeInOrg("GH_TEAM", year, me.getCName(), me.getCName2())
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }

        // (2) 진료부(팀장/팀원)
        if (isJinryu) {
            if (isTeamHead || isMedicalLead) {
                if (isMedicalLead) {
                    List<String> subsToEval;
                    switch (scope) {
                        case "OWN_SUB" -> subsToEval = List.of(mySub);
                        case "CUSTOM" ->
                            subsToEval = (customSubs == null || customSubs.isEmpty()) ? List.of(mySub) : customSubs;
                        default -> subsToEval = List.of("A00", "A01", "A02");
                    }
                    userMapper.findBySubCodesInOrg(subsToEval, year, me.getCName(), me.getCName2())
                            .forEach(u -> addIfNew(u, targets, added, userId));
                } else {
                    userMapper.findBySubCodeInOrg(mySub, year, me.getCName(), me.getCName2())
                            .forEach(u -> addIfNew(u, targets, added, userId));
                }
            }
            // 진료부 ↔ GH
            userMapper.findByTeamCodeInOrg("GH_TEAM", year, me.getCName(), me.getCName2())
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }

        // (3) 1인부서 ↔ GH 팀장
        if (isOnePerson && !isGhTeam) {
            rolesMapper.findUserIdsByRoleInOrg("team_head", year, me.getCName(), me.getCName2()).stream()
                    .map(id -> userMapper.findById(id, year))
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }
        if (isTeamHead && !isOnePerson) {
            rolesMapper.findUserIdsByRoleInOrg("one_person_sub", year, me.getCName(), me.getCName2()).stream()
                    .map(id -> userMapper.findById(id, year))
                    .forEach(u -> addIfNew(u, targets, added, userId));
        }
        return targets;
    }

    public int clearAllCustomTargets(String year) {
        return customMapper.deleteAllByYear(year);
    }

    // 전체 루프는 트랜잭션 밖에서 (아주 중요)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int rebuildAdminDefaultTargets(String year) {
        List<UserPE> allUsers = loginMapper.getUserList(year);
        log.info("[INIT] year={}, users={}", year, allUsers == null ? 0 : allUsers.size());
        if (allUsers == null || allUsers.isEmpty())
            return 0;

        UserPE first = allUsers.get(0);
        log.info("[INIT] firstUser id={}, cName={}, cName2={}, subCode={}",
                first.getId(), first.getCName(), first.getCName2(), first.getSubCode());

        int total = 0;
        for (UserPE evaluator : allUsers) {
            total += rebuildForOne(evaluator.getId(), year);
        }
        log.info("[INIT] finished. upserts={}", total);
        return total;
    }

    // 한 명 처리 = 별도 트랜잭션, 빠르게 커밋
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public int rebuildForOne(String userId, String year) {
        // 1) 먼저 지우고
        defMap.deleteByUserAndYear(userId, year);

        // 2) 새로 계산해서 넣기
        List<UserPE> base = computeBaseDefaultTargets(userId, year);
        log.debug("rebuildForOne userId={}, computedTargets={}", userId, base.size());

        int cnt = 0;
        for (UserPE target : base) {
            EvalMeta meta = resolveEvalMeta(userId, target, year);
            AdminDefaultTarget row = new AdminDefaultTarget();
            row.setEvalYear(Integer.parseInt(year));
            row.setUserId(userId);
            row.setTargetId(target.getId());
            row.setEvalTypeCode(meta.getEvalTypeCode());
            row.setDataEv(meta.getDataEv());
            row.setDataType(meta.getDataType());
            row.setFormId(meta.getFormId());
            cnt += defMap.upsert(row);
        }
        return cnt;
    }

    // ==== 메타 계산: ★ 여기서 규칙 확정 저장 ====

    /**
     * 평가자(userId) → 대상(target) 메타 산출
     * - 진료팀장 → 진료부: A + SUB_HEAD_TO_MEMBER
     * - 같은 부서에서 부서장 → 부서원: E + SUB_HEAD_TO_MEMBER
     * - 같은 부서에서 부서원 → 부서장: F + SUB_MEMBER_TO_HEAD
     * - 같은 부서에서 부서원 ↔ 부서원: G + SUB_MEMBER_TO_MEMBER
     * - 경혁 → 진료: C
     * - 진료 → 경혁: B
     * - 경혁 → 경혁: D
     */
    private EvalMeta resolveEvalMeta(String evaluatorId, UserPE target, String year) {

        Set<String> evalRoles = lower(rolesMapper.findRolesByUser(evaluatorId, year));
        Set<String> targetRoles = lower(rolesMapper.findRolesByUser(target.getId(), year));

        String evalSub = nvl(userMapper.findSubCodeByUser(evaluatorId, year));
        String targetSub = nvl(userMapper.findSubCodeByUser(target.getId(), year));
        String evalTeam = nvl(userMapper.findTeamCodeByUser(evaluatorId, year));
        String targetTeam = nvl(userMapper.findTeamCodeByUser(target.getId(), year));

        boolean sameSub = !evalSub.isEmpty() && evalSub.equals(targetSub);
        boolean evalGh = "GH_TEAM".equalsIgnoreCase(evalTeam);
        boolean targetJin = targetSub.startsWith("A") || targetRoles.contains("medical_leader");

        EvalMeta m = new EvalMeta();
        log.debug(
                "META pair: evalId={}, tgtId={}, evalSub='{}', tgtSub='{}', sameSub={}, evalTeam='{}', evalGh={}, evalRoles={}, tgtRoles={}",
                evaluatorId, target.getId(), evalSub, targetSub, sameSub, evalTeam, evalGh, evalRoles, targetRoles);
        // 1) 같은 부서 우선
        if (sameSub) {
            if (evalRoles.contains("sub_head") || evalRoles.contains("medical_leader")) {
                // 부서장/진료팀장 → 부서원 : E
                m.setDataEv("E");
                m.setDataType("AB");
                m.setEvalTypeCode("SUB_HEAD_TO_MEMBER");
                return m;
            } else if (targetRoles.contains("sub_head") || targetRoles.contains("medical_leader")) {
                // 부서원 → 부서장 : F
                m.setDataEv("F");
                m.setDataType("AA");
                m.setEvalTypeCode("SUB_MEMBER_TO_HEAD");
                return m;
            } else {
                // 부서원 ↔ 부서원 : G
                m.setDataEv("G");
                m.setDataType("AB");
                m.setEvalTypeCode("SUB_MEMBER_TO_MEMBER");
                return m;
            }
        }

        // 2) GH 규칙
        if (evalGh) {
            m.setDataType("AA");
            if (targetJin) {
                // GH → 진료부 : C
                m.setDataEv("C");
                m.setEvalTypeCode("GH_TO_MEDICAL");
            } else {
                // GH → GH/기타 : D
                m.setDataEv("D");
                m.setEvalTypeCode("GH_TO_GH");
            }
            return m;
        }

        // 3) 진료팀장의 타 부서 진료부에 대한 평가 : A
        if (evalRoles.contains("medical_leader") && targetJin) {
            m.setDataEv("A"); // 사진 기준: 진료팀장 → 진료부 : A
            m.setDataType("AB");
            m.setEvalTypeCode("SUB_HEAD_TO_MEMBER");
            return m;
        }

        // 4) 안전망
        if (targetJin) {
            m.setDataEv("C");
            m.setDataType("AA");
            m.setEvalTypeCode("MEDICAL"); // 거의 오지 않음
        } else if ("GH_TEAM".equalsIgnoreCase(targetTeam)) {
            m.setDataEv("D");
            m.setDataType("AA");
            m.setEvalTypeCode("GH");
        } else {
            m.setDataEv("G");
            m.setDataType("AB");
            m.setEvalTypeCode("DEFAULT");
        }
        return m;
    }

    // 신규
    /** 기본 + 커스텀을 합쳐 최종 대상(중복 포함) */
    public List<UserPE> getFinalTargets(String userId, String year) {
        List<UserPE> defaults = defMap.findDefaultTargets(userId, year);
        List<UserPE> adds = customMapper.findCustomTargets(userId, year);
        List<String> removes = customMapper.findCustomRemoveIds(userId, year);

        // 1) 기본 + 커스텀을 그대로 이어 붙임 (중복 허용)
        List<UserPE> all = new ArrayList<>();
        all.addAll(defaults); // 기본 대상
        all.addAll(adds); // 커스텀 ADD 대상

        // 2) REMOVE 목록에 있는 id 들은 전부 제거
        if (removes != null && !removes.isEmpty()) {
            // removes 리스트가 크면 Set으로 바꾸는게 더 효율적
            Set<String> removedIds = new HashSet<>(removes);
            all.removeIf(u -> removedIds.contains(u.getId()));
        }

        return all;
    }

    // 관리자 페이지에서 분해해서 보여줄 때 사용 (버킷별)
    public Map<String, List<UserPE>> groupByEvalType(List<UserPE> users) {
        Map<String, List<UserPE>> g = new LinkedHashMap<>();
        typeLabels().keySet().forEach(k -> g.put(k, new ArrayList<>()));
        for (UserPE u : users) {
            String k = u.getEvalTypeCode();
            if (k != null)
                g.computeIfAbsent(k, __ -> new ArrayList<>()).add(u);
        }
        return g;
    }

    /** 화면 메타맵: targetId -> PairMeta(출처/유형/사유 등) */
    public Map<String, PairMeta> loadMetaMap(String userId, String year) {
        Map<String, PairMeta> meta = new HashMap<>();
        for (PairMeta m : defMap.findDefaultMeta(userId, year)) {
            m.setSource("DEFAULT");
            meta.put(m.getTargetId(), m);
        }
        for (PairMeta m : customMapper.findCustomMeta(userId, year)) {
            // 커스텀 우선 덮어쓰기 (원치 않으면 putIfAbsent)
            m.setSource("CUSTOM");
            meta.put(m.getTargetId(), m);
        }
        return meta;
    }

    /** 화면 메타맵: (targetId, evalTypeCode) -> PairMeta */
    public Map<String, Map<String, PairMeta>> loadMetaMapByType(String userId, String year) {
        Map<String, Map<String, PairMeta>> meta = new HashMap<>();

        for (PairMeta m : defMap.findDefaultMeta(userId, year)) {
            m.setSource("DEFAULT");
            meta
                    .computeIfAbsent(m.getTargetId(), __ -> new HashMap<>())
                    .put(m.getEvalTypeCode(), m);
        }

        for (PairMeta m : customMapper.findCustomMeta(userId, year)) {
            m.setSource("CUSTOM");
            meta
                    .computeIfAbsent(m.getTargetId(), __ -> new HashMap<>())
                    .put(m.getEvalTypeCode(), m); // 커스텀 우선
        }

        return meta;
    }

    /** 버킷 분류: eval_type_code가 있으면 우선 반영, 없으면 기존 규칙(hasRole/isMedicalDept 등) */
    public TargetBuckets computeBuckets(String userId, String year) {
        List<UserPE> targets = getFinalTargets(userId, year);
        Map<String, PairMeta> meta = loadMetaMap(userId, year);

        List<UserPE> ghTeam = new ArrayList<>();
        List<UserPE> medical = new ArrayList<>();
        List<UserPE> subHeads = new ArrayList<>();
        List<UserPE> subMembers = new ArrayList<>();

        for (UserPE u : targets) {
            PairMeta pm = meta != null ? meta.get(u.getId()) : null;
            String type = (pm != null) ? pm.getEvalTypeCode() : null;

            // 1순위: 커스텀에서 지정한 eval_type_code
            if ("TEAM_HEAD".equals(type) || "TEAM_MEMBER".equals(type)) {
                ghTeam.add(u);
                continue;
            }
            if ("MEDICAL".equals(type)) {
                medical.add(u);
                continue;
            }
            if ("SUB_HEAD".equals(type)) {
                subHeads.add(u);
                continue;
            }
            if ("SUB_MEMBER".equals(type)) {
                subMembers.add(u);
                continue;
            }

            // 어디에도 안 맞으면 부서원으로
            subMembers.add(u);
        }

        return new TargetBuckets(ghTeam, medical, subHeads, subMembers);
    }

    /** 코드 순서/라벨(뷰에서 사용) */
    public LinkedHashMap<String, String> typeLabels() {
        var m = new LinkedHashMap<String, String>();
        m.put("GH", "경혁팀");
        m.put("MEDICAL", "진료부");
        m.put("SUB_HEAD_TO_MEMBER", "부서장→부서원");
        m.put("SUB_MEMBER_TO_MEMBER", "부서원↔부서원");
        m.put("SUB_MEMBER_TO_HEAD", "부서원→부서장");
        return m;
    }

    /** 기본 vs 커스텀(추가) 분리 */
    @Transactional(readOnly = true)
    public SplitTargets splitTargets(String userId, String year) {
        var defaults = getDefaultTargets(userId, year);
        var adds = getCustomAdds(userId, year);

        var dIds = defaults.stream().map(UserPE::getId).collect(java.util.stream.Collectors.toSet());
        var aIds = adds.stream().map(UserPE::getId).collect(java.util.stream.Collectors.toSet());

        var defaultOnly = defaults.stream().filter(u -> !aIds.contains(u.getId())).toList();
        var customOnly = adds.stream().filter(u -> !dIds.contains(u.getId())).toList();
        var overlap = defaults.stream().filter(u -> aIds.contains(u.getId())).toList(); // 참고용

        var dto = new SplitTargets();
        dto.setDefaultOnly(defaultOnly);
        dto.setCustomOnly(customOnly);
        dto.setOverlap(overlap);
        dto.setDefaultGrouped(groupByEvalType(defaultOnly));
        dto.setCustomGrouped(groupByEvalType(customOnly));
        return dto;
    }

    public Map<String, List<UserPE>> getFinalTargetsGroupedByType(String evaluatorId, String year) {

        List<TargetRow> defaults = defMap.findTargetsWithType(evaluatorId, year);
        List<TargetRow> customAdds = customMapper.findActiveAddsWithType(evaluatorId, year);
        Set<String> customRemove = new HashSet<>(customMapper.findActiveRemoves(evaluatorId, year));

        Map<String, List<UserPE>> grouped = new LinkedHashMap<>();

        // 한 줄(TargetRow)을 타입 버킷에 넣는 공통 함수
        BiConsumer<TargetRow, Boolean> addRow = (row, fromDefault) -> {
            if (row == null)
                return;
            String tid = row.getTargetId();
            if (tid == null || tid.isBlank())
                return;

            // 삭제 대상이면 건너뜀
            if (customRemove.contains(tid))
                return;

            // 원시 타입 코드 (GH_TO_GH, SUB_HEAD_TO_MEMBER 등)
            String typeRaw = (row.getEvalTypeCode() != null && !row.getEvalTypeCode().isBlank())
                    ? row.getEvalTypeCode()
                    : "UNKNOWN";

            // 기본(자동매핑)은 normalize 적용, 커스텀은 있는 그대로
            String typeKey = fromDefault ? normalizeAutoType(typeRaw) : typeRaw;

            // 사용자 기본 정보
            UserPE base = userMapper.findByIdWithNames(tid.trim(), year);
            if (base == null)
                return;

            // 🔹 관계마다 다른 인스턴스 생성
            UserPE u = new UserPE();
            // 기본 정보 복사
            u.setId(base.getId());
            u.setName(base.getName());
            u.setPosition(base.getPosition());
            u.setCName(base.getCName());
            u.setSubCode(base.getSubCode());
            u.setSubName(base.getSubName());
            u.setTeamCode(base.getTeamCode());
            u.setTeamName(base.getTeamName());
            // ... 필요한 필드 더 복사

            // 🔹 이 관계에 대한 EV/TYPE 심기
            u.setEvalTypeCode(typeRaw); // GH_TO_GH / SUB_HEAD_TO_MEMBER ...
            u.setDataEv(row.getDataEv()); // D 또는 E
            u.setDataType(row.getDataType()); // AA 또는 AB

            grouped.computeIfAbsent(typeKey, k -> new ArrayList<>()).add(u);
        };

        // 기본(자동 매핑) 쪽
        for (TargetRow r : defaults) {
            addRow.accept(r, true);
        }

        // 커스텀 ADD 쪽
        for (TargetRow r : customAdds) {
            if (r == null || r.getTargetId() == null || r.getTargetId().isBlank())
                continue;
            if (r.getTargetId().equals(evaluatorId)) {
                System.out.printf("[WARN] customAdd targetId == evaluatorId: %s%n", r);
                // 필요하면 continue;
            }
            addRow.accept(r, false);
        }

        return grouped;
    }

    /** 자동 매핑(defaults)에서만 적용하는 정규화 규칙 */
    private String normalizeAutoType(String code) {
        if (code == null || code.isBlank())
            return "UNKNOWN";
        return switch (code) {
            // 레거시 'GH'는 "진료부 → 경영혁신"으로 해석
            case "GH" -> "MEDICAL_TO_GH";
            default -> code; // 나머지는 그대로
        };
    }

    /** 라벨 맵 (뷰에 함께 전달) */
    public Map<String, String> getTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("GH", "경혁팀 평가");
        labels.put("MEDICAL", "진료부 평가");
        labels.put("SUB_HEAD_TO_MEMBER", "부서원 평가");
        labels.put("SUB_MEMBER_TO_MEMBER", "부서원 평가");
        labels.put("SUB_MEMBER_TO_HEAD", "부서장 평가");
        return labels;
    }

    // public OrgPair findOrgPairByUser(String userId, String year) {
    // return userMapper.findOrgPairByUser(userId, year);
    // }

    public List<DepartmentDto> getDepartmentsLite(String year) {
        // 필요시 구현 (자동 매핑과 직접 연관 없음)
        throw new UnsupportedOperationException("getDepartmentsLite는 이 단계에선 생략");
    }

    // ───────────── 유틸 ─────────────
    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private static String nvl(String s) {
        return (s == null) ? "" : s;
    }

    private static List<String> safeList(List<String> src) {
        return (src == null) ? List.of() : src;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean sameOrg(UserPE a, UserPE b) {
        return Objects.equals(nz(a.getCName()), nz(b.getCName()))
                && Objects.equals(nz(a.getCName2()), nz(b.getCName2()));
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private static Set<String> lower(List<String> roles) {
        return roles == null ? Set.of()
                : roles.stream().filter(Objects::nonNull)
                        .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank())
            return List.of();
        return Arrays.stream(csv.split("\\s*,\\s*")).filter(s -> !s.isEmpty()).toList();
    }

    private void addIfNew(UserPE u, List<UserPE> list, Set<String> seen, String selfId) {
        if (u == null)
            return;
        if (u.getId() == null)
            return;
        if (u.getId().equals(selfId))
            return; // 자기 자신 제외
        if (!seen.add(u.getId()))
            return; // 이미 추가된 대상 제외
        list.add(u);
    }
}
