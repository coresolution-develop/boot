package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.AdminCustomTarget;
import com.coresolution.pe.entity.AdminDefaultTarget;
import com.coresolution.pe.entity.DepartmentDto;
import com.coresolution.pe.entity.TargetRowDto;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffCustomTargetMapper;
import com.coresolution.pe.mapper.AffDefaultTargetMapper;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.mapper.InstitutionMapper;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffAdminTargetService {
    private final AffUserMapper userMapper;
    private final AffLoginMapper loginMapper;
    private final AffDefaultTargetMapper defaultTargetMapper;
    private final AffCustomTargetMapper customTargetMapper;
    private final InstitutionMapper institutionMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    /** 부서별로 묶어서 DTO 반환 */
    public List<DepartmentDto> getDepartments(int year) {
        List<UserPE> all = loginMapper.getUserList(year);
        if (all == null || all.isEmpty()) {
            return List.of();
        }

        // null key 방지: subCode 기준으로 그룹핑 (null → 빈 문자열)
        Map<String, List<UserPE>> map = all.stream()
                .collect(Collectors.groupingBy(
                        u -> nz(u.getSubCode()), // ✅ null 이면 "" 로 치환
                        LinkedHashMap::new,
                        Collectors.toList()));

        return map.entrySet().stream()
                .map(e -> {
                    String subCodeKey = e.getKey(); // 실제 sub_code (없으면 "")
                    List<UserPE> users = e.getValue();

                    // 화면에 보여줄 부서명: 그룹 내에서 첫 번째로 발견되는 subName
                    String subName = users.stream()
                            .map(UserPE::getSubName)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse("(부서없음)");

                    DepartmentDto dto = new DepartmentDto(subName, users);

                    // DepartmentDto 에 subCode 세팅 (빈 문자열이면 null 처리)
                    dto.setSubCode(subCodeKey.isEmpty() ? null : subCodeKey);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public int insertCustomOnly(String userId, int year,
            String targetId, String evalTypeCode, String dataEv, String dataType, String reason) {

        UserPE me = userMapper.findById(userId, year);
        UserPE tg = userMapper.findById(targetId, year);

        if (me == null || tg == null) {
            throw new IllegalArgumentException("사용자 없음");
        }

        // 🔵 기관이 달라도 허용 (기존 sameOrg 제한 제거)
        // 필요하다면 로깅 정도만 남겨두기
        /*
         * if (!sameOrg(me, tg)) {
         * log.
         * warn("[AFF][CUSTOM] cross-org custom target: evaluator={}({}/{}) -> target={}({}/{})"
         * ,
         * me.getId(), nz(me.getCName()), nz(me.getCName2()),
         * tg.getId(), nz(tg.getCName()), nz(tg.getCName2()));
         * }
         */

        return customTargetMapper.insertCustom(userId, year, targetId, evalTypeCode, dataEv, dataType, reason);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean sameOrg(UserPE a, UserPE b) {
        return nz(a.getCName()).equals(nz(b.getCName()))
                && nz(a.getCName2()).equals(nz(b.getCName2()));
    }

    /** 커스텀 추가 대상만 evalTypeCode 별로 그룹핑 */
    public Map<String, List<UserPE>> getCustomTargetsGroupedByType(String userId, int year) {
        List<UserPE> list = customTargetMapper.findCustomTargetsDetailed(userId, year);
        Map<String, List<UserPE>> grouped = new LinkedHashMap<>();
        if (list == null) {
            return grouped;
        }

        for (UserPE u : list) {
            if (u == null)
                continue;
            String key = u.getEvalTypeCode();
            if (key == null || key.isBlank()) {
                key = "UNKNOWN";
            } else {
                key = key.trim();
            }
            grouped.computeIfAbsent(key, __ -> new ArrayList<>()).add(u);
        }
        return grouped;
    }

    @Transactional
    /** 기관 소속 평가자별 현재 활성 대상 수 목록 */
    public List<UserPE> getEvaluatorSummary(String orgName, int year) {
        return customTargetMapper.getEvaluatorsWithTargetCount(String.valueOf(year), orgName);
    }

    /** 기관 전체 활성 평가 쌍 수 */
    public int countTargets(String orgName, int year) {
        return customTargetMapper.countTargetsByOrg(String.valueOf(year), orgName);
    }

    /** 기관의 모든 평가 대상 비활성화 */
    @Transactional
    public int clearTargets(String orgName, int year) {
        return loginMapper.deactivateTargetsByOrg(year, orgName);
    }

    /**
     * AFF 평가 대상 자동 생성 — 운영 실태 기반 6개 핵심 규칙.
     *
     * <ul>
     *   <li>부서(S) — 같은 sub_code 내
     *     <ul>
     *       <li>{@code SHEAD_TO_SMEMBER} — 수직평가 (본진)
     *       <li>{@code SMEMBER_TO_SHEAD} — 역방향
     *       <li>{@code SMEMBER_TO_SMEMBER} — 동료평가
     *     </ul>
     *   <li>소속(A) — 같은 AGC 그룹 (cross-ORG)
     *     <ul>
     *       <li>{@code AHEAD_TO_AMEMBER} — 그룹 대표 → 그룹 직원
     *       <li>{@code AMEMBER_TO_AHEAD} — 그룹 직원 → 그룹 대표
     *     </ul>
     *   <li>기관(O) — 단일 ORG
     *     <ul>
     *       <li>{@code OMEMBER_TO_OHEAD} — 직원 → 기관장
     *     </ul>
     * </ul>
     *
     * 역할 매핑(users.role 또는 user_roles):
     * {@code AFF_ORG_HEAD}, {@code AFF_AGC_HEAD}, {@code AFF_SUB_HEAD}, {@code SUB_MEMBER}.
     * cross-ORG 특수 케이스(예: 영양과 영양팀장)는 커스텀 평가 메뉴에서 수동 추가.
     */
    @Transactional
    public int generateTargets(String orgName, int year,
                               java.util.List<String> rules, String subDataType,
                               boolean clearFirst) {
        if (clearFirst) {
            loginMapper.deactivateTargetsByOrg(year, orgName);
        }

        String yearStr = String.valueOf(year);

        // 자기 ORG 직원 (S* / O* 규칙용)
        java.util.List<UserPE> orgUsers =
                loginMapper.getUsersWithRolesByOrg(yearStr, orgName);

        // 같은 AGC 그룹의 모든 ORG 직원 (A* 규칙용)
        java.util.List<UserPE> agcUsers = resolveAgcUsers(orgName, yearStr);

        java.util.List<UserPE> orgHeads = filterByRole(orgUsers, "AFF_ORG_HEAD");
        java.util.List<UserPE> agcHeads = filterByRole(agcUsers, "AFF_AGC_HEAD");
        java.util.List<UserPE> agcMembers = filterByRole(agcUsers, "SUB_MEMBER");

        java.util.Map<String, java.util.List<UserPE>> byDept = orgUsers.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        u -> u.getSubCode() != null ? u.getSubCode() : "__NO_DEPT__"));

        int count = 0;

        // ── 부서(S) — 같은 부서 내 ────────────────────────────
        for (java.util.List<UserPE> deptUsers : byDept.values()) {
            java.util.List<UserPE> deptHeads = filterByRole(deptUsers, "AFF_SUB_HEAD");
            java.util.List<UserPE> deptMembers = filterByRole(deptUsers, "SUB_MEMBER");

            if (rules.contains("SHEAD_TO_SMEMBER")) {
                count += pair(deptHeads, deptMembers, yearStr, "SHEAD_TO_SMEMBER", "a", subDataType);
            }
            if (rules.contains("SMEMBER_TO_SHEAD")) {
                count += pair(deptMembers, deptHeads, yearStr, "SMEMBER_TO_SHEAD", "b", subDataType);
            }
            if (rules.contains("SMEMBER_TO_SMEMBER")) {
                count += pair(deptMembers, deptMembers, yearStr, "SMEMBER_TO_SMEMBER", "c", subDataType);
            }
        }

        // ── 소속(A) — 같은 AGC 그룹 cross-ORG ─────────────────
        if (rules.contains("AHEAD_TO_AMEMBER")) {
            count += pair(agcHeads, agcMembers, yearStr, "AHEAD_TO_AMEMBER", "e", "AA");
        }
        if (rules.contains("AMEMBER_TO_AHEAD")) {
            count += pair(agcMembers, agcHeads, yearStr, "AMEMBER_TO_AHEAD", "f", "AA");
        }

        // ── 기관(O) — 단일 ORG ───────────────────────────────
        if (rules.contains("OMEMBER_TO_OHEAD")) {
            java.util.List<UserPE> orgMembers = filterByRole(orgUsers, "SUB_MEMBER");
            count += pair(orgMembers, orgHeads, yearStr, "OMEMBER_TO_OHEAD", "j", "AA");
        }

        return count;
    }

    /**
     * 같은 AGC 그룹의 모든 ORG 직원 조회. agc_code 없으면 자기 ORG 만 반환.
     */
    private java.util.List<UserPE> resolveAgcUsers(String orgName, String yearStr) {
        com.coresolution.pe.entity.Institution self = institutionMapper.findByName(orgName);
        if (self == null || self.getAgcCode() == null || self.getAgcCode().isBlank()) {
            return loginMapper.getUsersWithRolesByOrg(yearStr, orgName);
        }
        java.util.List<com.coresolution.pe.entity.Institution> sameAgc =
                institutionMapper.findByAgcCode(self.getAgcCode());
        if (sameAgc.isEmpty()) {
            return loginMapper.getUsersWithRolesByOrg(yearStr, orgName);
        }
        java.util.List<String> orgNames = sameAgc.stream()
                .map(com.coresolution.pe.entity.Institution::getName)
                .collect(java.util.stream.Collectors.toList());
        return loginMapper.getUsersWithRolesByOrgList(yearStr, orgNames);
    }

    private java.util.List<UserPE> filterByRole(java.util.List<UserPE> users, String role) {
        return users.stream()
                .filter(u -> hasRole(u.getRolesCsv(), role))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 두 리스트의 카르테시안 곱으로 (evaluator → target) 쌍을 upsert.
     * 자기 자신은 제외. 영향 행 수 반환.
     */
    private int pair(java.util.List<UserPE> evaluators, java.util.List<UserPE> targets,
                     String yearStr, String evalTypeCode, String dataEv, String dataType) {
        int n = 0;
        for (UserPE ev : evaluators) {
            for (UserPE tg : targets) {
                if (ev.getId().equals(tg.getId())) continue;
                customTargetMapper.upsertCustomAdd(
                        ev.getId(), yearStr, tg.getId(),
                        evalTypeCode, dataEv, dataType, null);
                n++;
            }
        }
        return n;
    }

    private boolean hasRole(String rolesCsv, String role) {
        if (rolesCsv == null || rolesCsv.isBlank()) return false;
        for (String r : rolesCsv.split(",")) {
            if (r.trim().equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    public void removeCustomAddByIdx(int idx, int year, String targetId, String reason) {
        // idx -> userId
        UserPE user = loginMapper.findUserInfoByIdx(idx, currentEvalYear);
        String userId = user.getId();

        int updated = customTargetMapper.deactivateCustom(userId, year, targetId, reason);
        if (updated == 0) {
            // 이미 비활성 or 행 없음 → 무시
        }
    }

    /** 커스텀 대상 비활성화 (userId 직접 지정 — admin custom_new 화면용) */
    @Transactional
    public void removeCustomByUserId(String userId, int year, String targetId, String reason) {
        customTargetMapper.deactivateCustom(userId, year, targetId, reason);
    }

    /** 특정 평가자의 활성 커스텀 대상 목록 반환 */
    public List<UserPE> getCustomTargetsList(String userId, int year) {
        return customTargetMapper.findCustomTargetsDetailed(userId, year);
    }
}
