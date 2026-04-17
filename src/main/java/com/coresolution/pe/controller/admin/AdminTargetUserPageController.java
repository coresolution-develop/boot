package com.coresolution.pe.controller.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.coresolution.pe.service.FormService;
import com.coresolution.pe.service.YearService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTargetUserPageController {

    private final YearService yearService; // 연도 셀렉트에 바인딩할 목록 제공 (예: ["2024","2025"])
    private final FormService formService; // 평가 양식 목록 제공

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    /**
     * 직원별 평가 대상 설정 페이지 (마스터-디테일)
     * GET /pe/admin/target/user?year=2026&userId=114010
     */
    @GetMapping("/admin/target/user")
    public String targetUserPage(
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String userId,
            Model model) {
        if (year == null || year.isEmpty()) year = String.valueOf(currentEvalYear);
        model.addAttribute("year", year);
        model.addAttribute("years", yearService.getYears());
        model.addAttribute("userId", userId == null ? "" : userId);
        model.addAttribute("forms", formService.listForms(year)); // (id,name)
        return "pe/admin/target-user";
    }
}
