package com.coresolution.pe.controller.admin;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.beans.factory.annotation.Value;

import com.coresolution.pe.service.AdminTargetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin/targets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")

@Slf4j
public class AdminCustomTargetPageController {

    private final AdminTargetService adminTargetService;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) String year, Model model) {
        if (year == null || year.isEmpty()) year = String.valueOf(currentEvalYear);
        // 부서별로 묶인 사용자 목록 (select에 optgroup으로 렌더)
        var depts = adminTargetService.getDepartments(year); // List<DepartmentDto>

        log.debug("부서 목록: {}", depts);
        model.addAttribute("departments", depts);
        model.addAttribute("hasDepartments", depts != null && !depts.isEmpty());

        model.addAttribute("year", year);

        // select 옵션(간단 상수)
        // (예시) 평가 이벤트: 코드→라벨
        var dataEvs = new java.util.LinkedHashMap<String, String>();
        dataEvs.put("A", "진료팀장 → 진료부");
        dataEvs.put("B", "진료부 → 경혁팀");
        dataEvs.put("C", "경혁팀 → 진료부");
        dataEvs.put("D", "경혁팀 → 경혁팀");
        dataEvs.put("E", "부서장 → 부서원");
        dataEvs.put("F", "부서원 → 부서장");
        dataEvs.put("G", "부서원 → 부서원");
        model.addAttribute("dataEvs", dataEvs);
        // ✅ 평가 타입: 화면엔 “10문항”, 실제 값은 “AA”
        var dataTypes = new java.util.LinkedHashMap<String, String>();
        dataTypes.put("AA", "10문항");
        // 필요하면 추가
        dataTypes.put("AB", "20문항");
        model.addAttribute("dataTypes", dataTypes);

        // ✅ 평가 그룹(유형): 화면엔 “경혁팀평가”, 실제 값은 “GH”
        var typeCodes = new java.util.LinkedHashMap<String, String>();
        typeCodes.put("GH", "경혁팀평가");
        typeCodes.put("MEDICAL", "진료부평가");
        typeCodes.put("SUB_MEMBER_TO_HEAD", "부서원 → 부서장");
        typeCodes.put("SUB_HEAD_TO_MEMBER", "부서장 → 부서원");
        typeCodes.put("SUB_MEMBER_TO_MEMBER", "부서원 ↔ 부서원");
        model.addAttribute("typeCodes", typeCodes);
        // 화면에서 빈 상태 메시지 띄우기 좋게
        return "pe/admin/custom_new";
    }

    @PostMapping(path = "/custom", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public String upsertForm(
            @RequestParam String userId, // 평가자
            @RequestParam(required = false) String year,
            @RequestParam String targetId, // 평가 대상자
            @RequestParam String dataEv,
            @RequestParam String dataType,
            @RequestParam String evalTypeCode,
            @RequestParam(required = false) String reason,
            RedirectAttributes ra) {
        if (year == null || year.isEmpty()) year = String.valueOf(currentEvalYear);

        if (userId.equals(targetId)) {
            ra.addFlashAttribute("error", "평가자는 자기 자신을 평가 대상으로 지정할 수 없습니다.");
            return "redirect:/admin/targets/new?year=" + year + "&userId=" + userId;
        }

        adminTargetService.insertCustomOnly(userId, year, targetId, evalTypeCode, dataEv, dataType, reason);
        ra.addFlashAttribute("message", "커스텀 대상이 저장되었습니다.");

        return "redirect:/admin/targets/new?year=" + year + "&userId=" + userId;
    }
}
