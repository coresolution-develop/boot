package com.coresolution.pe.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.entity.DepartmentDto;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.AffAdminTargetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AFF 기관 관리자 전용 커스텀 평가 대상 관리.
 * <p>
 * PE 측 {@code InstAdminCustomTargetController} 와 동일 패턴.
 * AFF 도메인 typeCode (S* / A* / O*) 12개를 노출하며, 평가자는 자기 기관 직원,
 * 대상자는 자기 기관 직원에서 선택 (cross-AGC 가 필요한 경우 별도 확장).
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/aff/inst-admin/custom-targets")
@PreAuthorize("hasRole('INST_ADMIN')")
@RequiredArgsConstructor
public class AffInstAdminCustomTargetController {

    private final AffAdminTargetService affTargetService;
    private final AffLoginMapper affLoginMapper;
    private final AffUserMapper affUserMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    private String institution(HttpServletRequest req) {
        String name = InstitutionAdminContext.getInstitutionName(req);
        if (name == null || name.isBlank()) {
            throw new AccessDeniedException("기관 정보가 세션에 없습니다. 다시 로그인해주세요.");
        }
        return name;
    }

    // ── 메인 페이지 ──────────────────────────────────────

    @GetMapping
    public String page(HttpServletRequest req,
                       @RequestParam(required = false) String year,
                       @RequestParam(required = false) String userId,
                       Model model) {
        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);

        // 자기 기관 직원 (부서별 그룹핑)
        List<UserPE> orgUsers = affLoginMapper.getUserListpage(
                year, null, null, null, orgName, null, null, 0, Integer.MAX_VALUE);
        List<DepartmentDto> departments = groupByDept(orgUsers);

        // 평가자 검증 + 기존 커스텀 대상 조회
        List<UserPE> existingTargets = List.of();
        UserPE selectedUser = null;
        if (userId != null && !userId.isBlank()) {
            selectedUser = orgUsers.stream()
                    .filter(u -> userId.equals(u.getId()))
                    .findFirst()
                    .orElse(null);
            if (selectedUser != null) {
                existingTargets = affTargetService.getCustomTargetsList(userId, Integer.parseInt(year));
            }
        }

        model.addAttribute("year", year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("departments", departments);
        model.addAttribute("hasDepartments", !departments.isEmpty());
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("existingTargets", existingTargets);

        // AFF 도메인 평가 유형
        model.addAttribute("dataTypes", buildDataTypes());
        model.addAttribute("typeCodes", buildAffTypeCodes());
        return "aff/inst-admin/custom-targets";
    }

    // ── 추가 ─────────────────────────────────────────────

    @PostMapping(path = "/add", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public String add(HttpServletRequest req,
                      @RequestParam("year") String year,
                      @RequestParam("userId") String userId,
                      @RequestParam("targetId") String targetId,
                      @RequestParam("evalTypeCode") String evalTypeCode,
                      @RequestParam(value = "dataType", defaultValue = "AA") String dataType,
                      @RequestParam(value = "reason", required = false) String reason,
                      RedirectAttributes ra) {
        String orgName = institution(req);
        int y = Integer.parseInt(year);

        if (userId.equals(targetId)) {
            ra.addFlashAttribute("error", "평가자는 자기 자신을 평가 대상으로 지정할 수 없습니다.");
            return redirect(year, userId);
        }
        if (!isSameOrgEmployee(userId, y, orgName) || !isSameOrgEmployee(targetId, y, orgName)) {
            ra.addFlashAttribute("error", "본인 기관 직원만 평가 대상에 추가할 수 있습니다.");
            return redirect(year, userId);
        }

        String dataEv = inferDataEv(evalTypeCode);
        try {
            affTargetService.insertCustomOnly(userId, y, targetId, evalTypeCode, dataEv, dataType, reason);
            ra.addFlashAttribute("message", "커스텀 평가 대상이 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("[AffInstAdmin] 커스텀 평가 추가 실패 userId={}, targetId={}", userId, targetId, e);
            ra.addFlashAttribute("error", "추가 실패: " + e.getMessage());
        }
        return redirect(year, userId);
    }

    // ── 삭제 ─────────────────────────────────────────────

    @PostMapping(path = "/remove", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public String remove(HttpServletRequest req,
                         @RequestParam("year") String year,
                         @RequestParam("userId") String userId,
                         @RequestParam("targetId") String targetId,
                         @RequestParam(value = "reason", required = false, defaultValue = "기관 관리자 수동 삭제") String reason,
                         RedirectAttributes ra) {
        String orgName = institution(req);
        int y = Integer.parseInt(year);
        if (!isSameOrgEmployee(userId, y, orgName)) {
            ra.addFlashAttribute("error", "본인 기관 직원만 처리할 수 있습니다.");
            return redirect(year, userId);
        }
        affTargetService.removeCustomByUserId(userId, y, targetId, reason);
        ra.addFlashAttribute("message", "커스텀 평가 대상을 삭제했습니다.");
        return redirect(year, userId);
    }

    // ── 헬퍼 ─────────────────────────────────────────────

    private boolean isSameOrgEmployee(String userId, int year, String orgName) {
        UserPE u = affUserMapper.findById(userId, year);
        return u != null && orgName.equals(u.getCName());
    }

    private String redirect(String year, String userId) {
        StringBuilder sb = new StringBuilder("redirect:/aff/inst-admin/custom-targets?year=").append(year);
        if (userId != null && !userId.isBlank()) {
            sb.append("&userId=").append(userId);
        }
        return sb.toString();
    }

    private List<DepartmentDto> groupByDept(List<UserPE> users) {
        if (users == null || users.isEmpty()) return List.of();
        Map<String, List<UserPE>> grouped = users.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getSubName() == null ? "(부서 미지정)" : u.getSubName(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> {
                    DepartmentDto dto = new DepartmentDto(e.getKey(), e.getValue());
                    e.getValue().stream()
                            .map(UserPE::getSubCode)
                            .filter(s -> s != null)
                            .findFirst()
                            .ifPresent(dto::setSubCode);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /** evalTypeCode 패턴으로 dataEv 자동 매핑 (자동 생성과 일관). */
    private String inferDataEv(String code) {
        if (code == null) return "a";
        return switch (code) {
            case "SHEAD_TO_SMEMBER"  -> "a";
            case "SMEMBER_TO_SHEAD"  -> "b";
            case "SMEMBER_TO_SMEMBER" -> "c";
            case "SHEAD_TO_SHEAD"    -> "d";
            case "AHEAD_TO_AMEMBER"  -> "e";
            case "AMEMBER_TO_AHEAD"  -> "f";
            case "AMEMBER_TO_AMEMBER" -> "g";
            case "AHEAD_TO_AHEAD"    -> "h";
            case "OHEAD_TO_OMEMBER"  -> "i";
            case "OMEMBER_TO_OHEAD"  -> "j";
            case "OMEMBER_TO_OMEMBER" -> "k";
            case "OHEAD_TO_OHEAD"    -> "l";
            default -> "a";
        };
    }

    private Map<String, String> buildDataTypes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("AA", "10문항");
        m.put("AB", "20문항");
        return m;
    }

    /** AFF 도메인 12개 typeCode. */
    private Map<String, String> buildAffTypeCodes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SHEAD_TO_SMEMBER",  "부서장 → 부서원 (부서)");
        m.put("SMEMBER_TO_SHEAD",  "부서원 → 부서장 (부서)");
        m.put("SMEMBER_TO_SMEMBER", "부서원 ↔ 부서원 (부서)");
        m.put("SHEAD_TO_SHEAD",    "부서장 ↔ 부서장 (부서)");
        m.put("AHEAD_TO_AMEMBER",  "그룹 대표 → 구성원 (소속)");
        m.put("AMEMBER_TO_AHEAD",  "구성원 → 그룹 대표 (소속)");
        m.put("AMEMBER_TO_AMEMBER", "구성원 ↔ 구성원 (소속)");
        m.put("AHEAD_TO_AHEAD",    "그룹 대표 ↔ 그룹 대표 (소속)");
        m.put("OHEAD_TO_OMEMBER",  "기관장 → 구성원 (기관)");
        m.put("OMEMBER_TO_OHEAD",  "구성원 → 기관장 (기관)");
        m.put("OMEMBER_TO_OMEMBER", "구성원 ↔ 구성원 (기관)");
        m.put("OHEAD_TO_OHEAD",    "기관장 ↔ 기관장 (기관)");
        return m;
    }
}
