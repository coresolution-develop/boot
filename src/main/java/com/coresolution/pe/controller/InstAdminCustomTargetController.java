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
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.AdminTargetService;
import com.coresolution.pe.service.PeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PE 기관 관리자 전용 커스텀 평가 대상 관리.
 * <p>
 * 슈퍼 어드민의 {@code AdminCustomTargetPageController} 와 동일 기능을 inst-admin 스코프로 제공.
 * 모든 평가자·대상은 세션의 기관(c_name) 직원으로 제한된다.
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/pe/inst-admin/custom-targets")
@PreAuthorize("hasRole('INST_ADMIN')")
@RequiredArgsConstructor
public class InstAdminCustomTargetController {

    private final AdminTargetService adminTargetService;
    private final PeService pe;

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

        // 자기 기관 직원만 부서별 그룹핑
        List<UserPE> orgUsers = pe.getUserListpage(
                year, null, null, null, orgName, null, null, 0, Integer.MAX_VALUE);
        List<DepartmentDto> departments = groupByDept(orgUsers);

        // 선택된 평가자가 자기 기관 직원인지 확인
        List<UserPE> existingTargets = List.of();
        UserPE selectedUser = null;
        if (userId != null && !userId.isBlank()) {
            selectedUser = orgUsers.stream()
                    .filter(u -> userId.equals(u.getId()))
                    .findFirst()
                    .orElse(null);
            if (selectedUser != null) {
                existingTargets = adminTargetService.getCustomTargetsList(userId, year);
            }
        }

        model.addAttribute("year", year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("departments", departments);
        model.addAttribute("hasDepartments", !departments.isEmpty());
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("existingTargets", existingTargets);

        // PE 도메인 평가 유형
        model.addAttribute("dataEvs", buildDataEvs());
        model.addAttribute("dataTypes", buildDataTypes());
        model.addAttribute("typeCodes", buildTypeCodes());
        return "pe/inst-admin/custom-targets";
    }

    // ── 추가 ─────────────────────────────────────────────

    @PostMapping(path = "/add", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public String add(HttpServletRequest req,
                      @RequestParam("year") String year,
                      @RequestParam("userId") String userId,
                      @RequestParam("targetId") String targetId,
                      @RequestParam("dataEv") String dataEv,
                      @RequestParam("dataType") String dataType,
                      @RequestParam("evalTypeCode") String evalTypeCode,
                      @RequestParam(value = "reason", required = false) String reason,
                      RedirectAttributes ra) {
        String orgName = institution(req);

        if (userId.equals(targetId)) {
            ra.addFlashAttribute("error", "평가자는 자기 자신을 평가 대상으로 지정할 수 없습니다.");
            return redirect(year, userId);
        }
        // 평가자·대상 둘 다 자기 기관 직원인지 검증 (IDOR 방지)
        if (!isSameOrgEmployee(userId, year, orgName) || !isSameOrgEmployee(targetId, year, orgName)) {
            ra.addFlashAttribute("error", "본인 기관 직원만 평가 대상에 추가할 수 있습니다.");
            return redirect(year, userId);
        }

        try {
            adminTargetService.insertCustomOnly(userId, year, targetId, evalTypeCode, dataEv, dataType, reason);
            ra.addFlashAttribute("message", "커스텀 평가 대상이 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("[InstAdmin] 커스텀 평가 추가 실패 userId={}, targetId={}", userId, targetId, e);
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
        if (!isSameOrgEmployee(userId, year, orgName)) {
            ra.addFlashAttribute("error", "본인 기관 직원만 처리할 수 있습니다.");
            return redirect(year, userId);
        }
        adminTargetService.removeCustomByUserId(userId, year, targetId, reason);
        ra.addFlashAttribute("message", "커스텀 평가 대상을 삭제했습니다.");
        return redirect(year, userId);
    }

    // ── 헬퍼 ─────────────────────────────────────────────

    private boolean isSameOrgEmployee(String userId, String year, String orgName) {
        int y;
        try { y = Integer.parseInt(year); }
        catch (NumberFormatException nfe) { return false; }
        UserPE u = pe.findUserById(userId, y);
        return u != null && orgName.equals(u.getCName());
    }

    private String redirect(String year, String userId) {
        StringBuilder sb = new StringBuilder("redirect:/pe/inst-admin/custom-targets?year=").append(year);
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

    private Map<String, String> buildDataEvs() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("A", "진료팀장 → 진료부");
        m.put("B", "진료부 → 경혁팀");
        m.put("C", "경혁팀 → 진료부");
        m.put("D", "경혁팀 → 경혁팀");
        m.put("E", "부서장 → 부서원");
        m.put("F", "부서원 → 부서장");
        m.put("G", "부서원 → 부서원");
        return m;
    }

    private Map<String, String> buildDataTypes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("AA", "10문항");
        m.put("AB", "20문항");
        return m;
    }

    private Map<String, String> buildTypeCodes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("GH", "경혁팀 평가");
        m.put("MEDICAL", "진료부 평가");
        m.put("SUB_HEAD_TO_MEMBER", "부서장 → 부서원");
        m.put("SUB_MEMBER_TO_HEAD", "부서원 → 부서장");
        m.put("SUB_MEMBER_TO_MEMBER", "부서원 ↔ 부서원");
        return m;
    }
}
