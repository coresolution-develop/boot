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

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffAdminTargetService {
    private final AffUserMapper userMapper;
    private final AffLoginMapper loginMapper;
    private final AffDefaultTargetMapper defaultTargetMapper;
    private final AffCustomTargetMapper customTargetMapper;

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
     * 부서별 역할 기반 평가 대상 자동 생성.
     */
    @Transactional
    public int generateTargets(String orgName, int year,
                               java.util.List<String> rules, String subDataType,
                               boolean clearFirst) {
        if (clearFirst) {
            loginMapper.deactivateTargetsByOrg(year, orgName);
        }

        java.util.List<UserPE> allUsers = loginMapper.getUsersWithRolesByOrg(String.valueOf(year), orgName);

        java.util.List<UserPE> ghTeam = allUsers.stream()
                .filter(u -> "GH_TEAM".equalsIgnoreCase(u.getTeamCode()))
                .collect(java.util.stream.Collectors.toList());

        java.util.List<UserPE> medicalAll = allUsers.stream()
                .filter(u -> u.getSubCode() != null && u.getSubCode().startsWith("A"))
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<String, java.util.List<UserPE>> byDept = allUsers.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        u -> u.getSubCode() != null ? u.getSubCode() : "__NO_DEPT__"));

        String yearStr = String.valueOf(year);
        int count = 0;

        for (java.util.List<UserPE> deptUsers : byDept.values()) {
            java.util.List<UserPE> heads = deptUsers.stream()
                    .filter(u -> hasRole(u.getRolesCsv(), "SUB_HEAD"))
                    .collect(java.util.stream.Collectors.toList());
            java.util.List<UserPE> members = deptUsers.stream()
                    .filter(u -> hasRole(u.getRolesCsv(), "SUB_MEMBER"))
                    .collect(java.util.stream.Collectors.toList());

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

        if (rules.contains("GH_TO_GH")) {
            for (UserPE ev : ghTeam) {
                for (UserPE tg : ghTeam) {
                    if (!ev.getId().equals(tg.getId())) {
                        customTargetMapper.upsertCustomAdd(ev.getId(), yearStr, tg.getId(),
                                "GH_TO_GH", "D", "AA", null);
                        count++;
                    }
                }
            }
        }

        if (rules.contains("GH_TO_MEDICAL")) {
            for (UserPE ev : ghTeam) {
                for (UserPE tg : medicalAll) {
                    if (!ev.getId().equals(tg.getId())) {
                        customTargetMapper.upsertCustomAdd(ev.getId(), yearStr, tg.getId(),
                                "GH_TO_MEDICAL", "C", "AA", null);
                        count++;
                    }
                }
            }
        }

        if (rules.contains("MEDICAL_TO_GH")) {
            for (UserPE ev : medicalAll) {
                for (UserPE tg : ghTeam) {
                    if (!ev.getId().equals(tg.getId())) {
                        customTargetMapper.upsertCustomAdd(ev.getId(), yearStr, tg.getId(),
                                "MEDICAL_TO_GH", "B", "AA", null);
                        count++;
                    }
                }
            }
        }

        if (rules.contains("MEDICAL_LEADER_TO_MEDICAL")) {
            java.util.Map<String, java.util.List<UserPE>> medByDept = medicalAll.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            u -> u.getSubCode() != null ? u.getSubCode() : "__NO_DEPT__"));
            for (java.util.List<UserPE> deptUsers : medByDept.values()) {
                java.util.List<UserPE> leaders = deptUsers.stream()
                        .filter(u -> hasRole(u.getRolesCsv(), "MEDICAL_LEADER"))
                        .collect(java.util.stream.Collectors.toList());
                for (UserPE leader : leaders) {
                    for (UserPE tg : deptUsers) {
                        if (!leader.getId().equals(tg.getId())) {
                            customTargetMapper.upsertCustomAdd(leader.getId(), yearStr, tg.getId(),
                                    "SUB_HEAD_TO_MEMBER", "A", "AB", null);
                            count++;
                        }
                    }
                }
            }
        }

        return count;
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
