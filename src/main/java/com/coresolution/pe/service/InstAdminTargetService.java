package com.coresolution.pe.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.CustomTargetMapper;
import com.coresolution.pe.mapper.LoginMapper;

import lombok.RequiredArgsConstructor;

/**
 * 기관 관리자 전용 — 평가 대상 자동 생성/조회/삭제 서비스.
 *
 * <p>지원 규칙(rules):</p>
 * <ul>
 *   <li>SUB_MEMBER_TO_HEAD  : 부서원 → 부서장</li>
 *   <li>SUB_HEAD_TO_MEMBER  : 부서장 → 부서원</li>
 *   <li>SUB_MEMBER_TO_MEMBER: 부서원 ↔ 부서원</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InstAdminTargetService {

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    private final LoginMapper loginMapper;
    private final CustomTargetMapper customTargetMapper;

    // ── 조회 ──────────────────────────────────────────────────────

    /** 기관 소속 평가자별 현재 활성 대상 수 목록 */
    public List<UserPE> getEvaluatorSummary(String orgName, int year) {
        return customTargetMapper.getEvaluatorsWithTargetCount(String.valueOf(year), orgName);
    }

    /** 기관 전체 활성 평가 쌍 수 */
    public int countTargets(String orgName, int year) {
        return customTargetMapper.countTargetsByOrg(String.valueOf(year), orgName);
    }

    // ── 자동 생성 ─────────────────────────────────────────────────

    /**
     * 부서별 역할 기반 평가 대상 자동 생성.
     *
     * <p>지원 규칙:</p>
     * <ul>
     *   <li>SUB_MEMBER_TO_HEAD       : 부서원 → 부서장 (ev=F, type=AA)</li>
     *   <li>SUB_HEAD_TO_MEMBER       : 부서장 → 부서원 (ev=E, type=AB)</li>
     *   <li>SUB_MEMBER_TO_MEMBER     : 부서원 ↔ 부서원 (ev=G, type=AB)</li>
     *   <li>GH_TO_GH                 : 경혁팀 → 경혁팀 (ev=D, type=AA)</li>
     *   <li>GH_TO_MEDICAL            : 경혁팀 → 진료부 (ev=C, type=AA)</li>
     *   <li>MEDICAL_TO_GH            : 진료부 → 경혁팀 (ev=B, type=AA)</li>
     *   <li>MEDICAL_LEADER_TO_MEDICAL: 진료팀장 → 진료부원(같은부서) (ev=A, type=AB)</li>
     * </ul>
     *
     * @param orgName    기관명 (c_name)
     * @param year       평가 연도
     * @param rules      적용할 규칙 목록
     * @param subDataType 부서 평가(SUB_*) 양식 코드 (예: "AA") — 경혁팀/의료진 규칙은 고정값 사용
     * @param clearFirst  생성 전 기존 대상 전체 삭제 여부
     * @return 생성된 쌍 수
     */
    @Transactional
    public int generateTargets(String orgName, int year,
                               List<String> rules, String subDataType,
                               boolean clearFirst) {
        if (clearFirst) {
            loginMapper.deleteTargetsByOrg(year, orgName);
        }

        List<UserPE> allUsers = loginMapper.getUsersWithRolesByOrg(String.valueOf(year), orgName);

        // ── 경혁팀 / 진료부 전체 목록 ─────────────────────────────────
        List<UserPE> ghTeam = allUsers.stream()
                .filter(u -> "GH_TEAM".equalsIgnoreCase(u.getTeamCode()))
                .collect(Collectors.toList());

        // 진료부: subCode가 "A"로 시작하는 직원
        List<UserPE> medicalAll = allUsers.stream()
                .filter(u -> u.getSubCode() != null && u.getSubCode().startsWith("A"))
                .collect(Collectors.toList());

        // 부서 코드별로 그룹핑
        Map<String, List<UserPE>> byDept = allUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getSubCode() != null ? u.getSubCode() : "__NO_DEPT__"));

        String yearStr = String.valueOf(year);
        int count = 0;

        // ══ 부서 평가 규칙 ══════════════════════════════════════════
        for (List<UserPE> deptUsers : byDept.values()) {
            List<UserPE> heads = deptUsers.stream()
                    .filter(u -> hasRole(u.getRolesCsv(), "SUB_HEAD"))
                    .collect(Collectors.toList());
            List<UserPE> members = deptUsers.stream()
                    .filter(u -> hasRole(u.getRolesCsv(), "SUB_MEMBER"))
                    .collect(Collectors.toList());

            // 부서원 → 부서장 (ev=F)
            if (rules.contains("SUB_MEMBER_TO_HEAD")) {
                for (UserPE member : members) {
                    for (UserPE head : heads) {
                        if (!member.getId().equals(head.getId())) {
                            customTargetMapper.upsertCustomAdd(
                                    member.getId(), yearStr, head.getId(),
                                    "SUB_MEMBER_TO_HEAD", "F", subDataType, null);
                            count++;
                        }
                    }
                }
            }

            // 부서장 → 부서원 (ev=E)
            if (rules.contains("SUB_HEAD_TO_MEMBER")) {
                for (UserPE head : heads) {
                    for (UserPE member : members) {
                        if (!head.getId().equals(member.getId())) {
                            customTargetMapper.upsertCustomAdd(
                                    head.getId(), yearStr, member.getId(),
                                    "SUB_HEAD_TO_MEMBER", "E", subDataType, null);
                            count++;
                        }
                    }
                }
            }

            // 부서원 ↔ 부서원 (ev=G)
            if (rules.contains("SUB_MEMBER_TO_MEMBER")) {
                for (UserPE m1 : members) {
                    for (UserPE m2 : members) {
                        if (!m1.getId().equals(m2.getId())) {
                            customTargetMapper.upsertCustomAdd(
                                    m1.getId(), yearStr, m2.getId(),
                                    "SUB_MEMBER_TO_MEMBER", "G", subDataType, null);
                            count++;
                        }
                    }
                }
            }
        }

        // ══ 경혁팀 평가 규칙 ═══════════════════════════════════════
        // 경혁팀 → 경혁팀 (ev=D, type=AA)
        if (rules.contains("GH_TO_GH")) {
            for (UserPE ev : ghTeam) {
                for (UserPE tg : ghTeam) {
                    if (!ev.getId().equals(tg.getId())) {
                        customTargetMapper.upsertCustomAdd(
                                ev.getId(), yearStr, tg.getId(),
                                "GH_TO_GH", "D", "AA", null);
                        count++;
                    }
                }
            }
        }

        // 경혁팀 → 진료부 (ev=C, type=AA)
        if (rules.contains("GH_TO_MEDICAL")) {
            for (UserPE ev : ghTeam) {
                for (UserPE tg : medicalAll) {
                    if (!ev.getId().equals(tg.getId())) {
                        customTargetMapper.upsertCustomAdd(
                                ev.getId(), yearStr, tg.getId(),
                                "GH_TO_MEDICAL", "C", "AA", null);
                        count++;
                    }
                }
            }
        }

        // 진료부 → 경혁팀 (ev=B, type=AA)
        if (rules.contains("MEDICAL_TO_GH")) {
            for (UserPE ev : medicalAll) {
                for (UserPE tg : ghTeam) {
                    if (!ev.getId().equals(tg.getId())) {
                        customTargetMapper.upsertCustomAdd(
                                ev.getId(), yearStr, tg.getId(),
                                "MEDICAL_TO_GH", "B", "AA", null);
                        count++;
                    }
                }
            }
        }

        // ══ 의료진(진료부) 평가 규칙 ═══════════════════════════════
        // 진료팀장 → 진료부원(같은 부서) (ev=A, type=AB)
        if (rules.contains("MEDICAL_LEADER_TO_MEDICAL")) {
            // 진료부 내 부서별 그룹핑
            Map<String, List<UserPE>> medByDept = medicalAll.stream()
                    .collect(Collectors.groupingBy(
                            u -> u.getSubCode() != null ? u.getSubCode() : "__NO_DEPT__"));
            for (List<UserPE> deptUsers : medByDept.values()) {
                List<UserPE> leaders = deptUsers.stream()
                        .filter(u -> hasRole(u.getRolesCsv(), "MEDICAL_LEADER"))
                        .collect(Collectors.toList());
                for (UserPE leader : leaders) {
                    for (UserPE tg : deptUsers) {
                        if (!leader.getId().equals(tg.getId())) {
                            customTargetMapper.upsertCustomAdd(
                                    leader.getId(), yearStr, tg.getId(),
                                    "SUB_HEAD_TO_MEMBER", "A", "AB", null);
                            count++;
                        }
                    }
                }
            }
        }

        return count;
    }

    // ── 삭제 ──────────────────────────────────────────────────────

    /** 기관의 모든 평가 대상 삭제 */
    @Transactional
    public int clearTargets(String orgName, int year) {
        return loginMapper.deleteTargetsByOrg(year, orgName);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────

    /**
     * rolesCsv(예: "SUB_HEAD,SUB_MEMBER" 또는 "sub_head,sub_member")에
     * 특정 역할이 포함됐는지 대소문자 무관하게 확인한다.
     */
    private boolean hasRole(String rolesCsv, String role) {
        if (rolesCsv == null || rolesCsv.isBlank()) return false;
        for (String r : rolesCsv.split(",")) {
            if (r.trim().equalsIgnoreCase(role)) return true;
        }
        return false;
    }
}
