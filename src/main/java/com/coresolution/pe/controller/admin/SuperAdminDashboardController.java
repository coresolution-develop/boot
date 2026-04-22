package com.coresolution.pe.controller.admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.coresolution.pe.entity.Institution;
import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.service.InstitutionService;

import lombok.RequiredArgsConstructor;

/**
 * 슈퍼 어드민 대시보드 컨트롤러.
 * /admin/dashboard — ROLE_ADMIN 만 접근 가능.
 */
@Controller
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SuperAdminDashboardController {

    private final InstitutionService institutionService;

    @GetMapping
    public String dashboard(Model model) {
        List<Institution>      institutions = institutionService.getAllInstitutions();
        List<InstitutionAdmin> allAdmins    = institutionService.getAllAdmins();

        long totalInst    = institutions.size();
        long activeInst   = institutions.stream().filter(Institution::isActive).count();
        long inactiveInst = totalInst - activeInst;

        long totalAdmins  = allAdmins.size();
        long activeAdmins = allAdmins.stream().filter(InstitutionAdmin::isActive).count();

        // 기관별 관리자 수 (institutionId → count)
        Map<Integer, Long> adminCountByInst = allAdmins.stream()
                .collect(Collectors.groupingBy(InstitutionAdmin::getInstitutionId,
                                               Collectors.counting()));

        model.addAttribute("institutions",    institutions);
        model.addAttribute("totalInst",       totalInst);
        model.addAttribute("activeInst",      activeInst);
        model.addAttribute("inactiveInst",    inactiveInst);
        model.addAttribute("totalAdmins",     totalAdmins);
        model.addAttribute("activeAdmins",    activeAdmins);
        model.addAttribute("adminCountByInst", adminCountByInst);

        return "pe/super-admin/dashboard";
    }
}
