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
import com.coresolution.pe.service.AffAdminTargetService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/aff/admin/targets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AffAdminCustomTargetPageController {

    private final AffAdminTargetService adminTargetService;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Integer year, Model model) {
        if (year == null) year = currentEvalYear;
        // 부서별로 묶인 사용자 목록 (select에 optgroup으로 렌더)
        var depts = adminTargetService.getDepartments(year); // List<DepartmentDto>

        model.addAttribute("departments", depts);
        model.addAttribute("hasDepartments", depts != null && !depts.isEmpty());
        model.addAttribute("year", year);

        // 🔹 A/B/C/D 공통 의미: 계열사 전체에서 동일하게 사용
        // A: 부서장 ↔ 부서장
        // B: 부서장 → 부서원
        // C: 부서원 → 부서장
        // D: 부서원 ↔ 부서원
        var dataEvs = new java.util.LinkedHashMap<String, String>();
        dataEvs.put("A", "부서장 ↔ 부서장");
        dataEvs.put("B", "부서장 → 부서원");
        dataEvs.put("C", "부서원 → 부서장");
        dataEvs.put("D", "부서원 ↔ 부서원");
        model.addAttribute("dataEvs", dataEvs);

        // 🔹 폼코드: AC/AD/AE를 직접 고르게
        // - AC : 상하/부서장 평가형 (부서장↔부서장, 부서원→부서장 등)
        // - AD : 병원/사랑모아/정성모아/자야클린용 구성원 평가형
        // - AE : 계열사형(핵심인재/조이/자야/장례문화원 등) 구성원 평가형
        var dataTypes = new java.util.LinkedHashMap<String, String>();
        dataTypes.put("AC", "AC (상하/부서장 평가형)");
        dataTypes.put("AD", "AD (병원/사랑모아/정성모아/자야클린 구성원형)");
        dataTypes.put("AE", "AE (계열사 구성원형 - 핵심인재/조이 등)");
        model.addAttribute("dataTypes", dataTypes);

        // 🔹 eval_type_code (우리 계열사 공통 코드)
        // - SUB: 부서 단위
        // - AGC: 소속 단위
        // - ORG: 기관 단위
        var typeCodes = new java.util.LinkedHashMap<String, String>();

        // SUB(부서) 스코프
        typeCodes.put("SHEAD_TO_SMEMBER", "부서장 → 부서원 (부서)");
        typeCodes.put("SMEMBER_TO_SHEAD", "부서원 → 부서장 (부서)");
        typeCodes.put("SMEMBER_TO_SMEMBER", "부서원 ↔ 부서원 (부서)");
        typeCodes.put("SHEAD_TO_SHEAD", "부서장 ↔ 부서장 (부서)");

        // AGC(소속) 스코프
        typeCodes.put("AHEAD_TO_AMEMBER", "소속장 → 소속 구성원 (소속)");
        typeCodes.put("AMEMBER_TO_AHEAD", "소속 구성원 → 소속장 (소속)");
        typeCodes.put("AMEMBER_TO_AMEMBER", "소속 구성원 ↔ 소속 구성원 (소속)");
        typeCodes.put("AHEAD_TO_AHEAD", "소속장 ↔ 소속장 (소속)");

        // ORG(기관) 스코프
        typeCodes.put("OHEAD_TO_OMEMBER", "기관장 → 기관 구성원 (기관)");
        typeCodes.put("OMEMBER_TO_OHEAD", "기관 구성원 → 기관장 (기관)");
        typeCodes.put("OMEMBER_TO_OMEMBER", "기관 구성원 ↔ 기관 구성원 (기관)");
        typeCodes.put("OHEAD_TO_OHEAD", "기관장 ↔ 기관장 (기관)");

        model.addAttribute("typeCodes", typeCodes);

        return "aff/admin/custom_new";
    }

    @PostMapping(path = "/custom", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public String upsertForm(
            @RequestParam String userId, // 평가자
            @RequestParam(required = false) Integer year,
            @RequestParam String targetId, // 평가 대상자
            @RequestParam String dataEv,
            @RequestParam String dataType,
            @RequestParam String evalTypeCode,
            @RequestParam(required = false) String reason,
            RedirectAttributes ra) {
        if (year == null) year = currentEvalYear;

        if (userId.equals(targetId)) {
            ra.addFlashAttribute("error", "평가자는 자기 자신을 평가 대상으로 지정할 수 없습니다.");
            return "redirect:/aff/admin/targets/new?year=" + year + "&userId=" + userId;
        }

        adminTargetService.insertCustomOnly(userId, year, targetId,
                evalTypeCode, dataEv, dataType, reason);
        ra.addFlashAttribute("message", "커스텀 대상이 저장되었습니다.");

        return "redirect:/aff/admin/targets/new?year=" + year + "&userId=" + userId;
    }
}
