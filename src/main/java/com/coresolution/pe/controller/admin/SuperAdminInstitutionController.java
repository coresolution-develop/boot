package com.coresolution.pe.controller.admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.entity.Institution;
import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.service.InstitutionService;

import lombok.RequiredArgsConstructor;

/**
 * 슈퍼 어드민 전용 — 기관 등록·수정 및 기관 관리자 계정 관리 컨트롤러.
 * /admin/institutions/** — ROLE_ADMIN 만 접근 가능.
 */
@Controller
@RequestMapping("/admin/institutions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SuperAdminInstitutionController {

    private final InstitutionService institutionService;

    // ── 기관 목록 ─────────────────────────────────────────

    @GetMapping
    public String list(Model model) {
        List<Institution> institutions = institutionService.getAllInstitutions();
        model.addAttribute("institutions", institutions);
        return "pe/super-admin/institutions";
    }

    // ── 기관 생성 ─────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("institution", new Institution());
        model.addAttribute("mode", "new");
        return "pe/super-admin/institutionForm";
    }

    @PostMapping
    public String create(@RequestParam String code,
                         @RequestParam String name,
                         RedirectAttributes ra) {
        try {
            institutionService.create(code.trim(), name.trim());
            ra.addFlashAttribute("message", "기관이 등록되었습니다: " + name);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "기관 등록 실패: " + e.getMessage());
        }
        return "redirect:/admin/institutions";
    }

    // ── 기관 수정 ─────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable int id, Model model) {
        Institution inst = institutionService.getById(id);
        if (inst == null) return "redirect:/admin/institutions";
        model.addAttribute("institution", inst);
        model.addAttribute("mode", "edit");
        return "pe/super-admin/institutionForm";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable int id,
                         @RequestParam String code,
                         @RequestParam String name,
                         @RequestParam(defaultValue = "false") boolean isActive,
                         RedirectAttributes ra) {
        try {
            institutionService.update(id, code.trim(), name.trim(), isActive);
            ra.addFlashAttribute("message", "기관 정보가 수정되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "수정 실패: " + e.getMessage());
        }
        return "redirect:/admin/institutions";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable int id, RedirectAttributes ra) {
        institutionService.deactivate(id);
        ra.addFlashAttribute("message", "기관이 비활성화되었습니다.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable int id, RedirectAttributes ra) {
        institutionService.activate(id);
        ra.addFlashAttribute("message", "기관이 활성화되었습니다.");
        return "redirect:/admin/institutions";
    }

    // ── 기관 관리자 목록 ──────────────────────────────────

    @GetMapping("/{id}/admins")
    public String admins(@PathVariable int id, Model model) {
        Institution inst = institutionService.getById(id);
        if (inst == null) return "redirect:/admin/institutions";

        List<InstitutionAdmin> admins = institutionService.getAdminsByInstitution(id);
        model.addAttribute("institution", inst);
        model.addAttribute("admins", admins);
        return "pe/super-admin/institutionAdmins";
    }

    // ── 기관 관리자 생성 ──────────────────────────────────

    @PostMapping("/{id}/admins")
    public String createAdmin(@PathVariable int id,
                              @RequestParam String loginId,
                              @RequestParam String password,
                              @RequestParam String adminName,
                              RedirectAttributes ra) {
        try {
            institutionService.createAdmin(id, loginId.trim(), password, adminName.trim());
            ra.addFlashAttribute("message", "관리자 계정이 생성되었습니다: " + loginId);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "관리자 생성 실패: " + e.getMessage());
        }
        return "redirect:/admin/institutions/" + id + "/admins";
    }

    // ── 관리자 비밀번호 재설정 ─────────────────────────────

    @PostMapping("/{id}/admins/{adminId}/reset-pwd")
    public String resetAdminPassword(@PathVariable int id,
                                     @PathVariable int adminId,
                                     @RequestParam String newPassword,
                                     RedirectAttributes ra) {
        try {
            institutionService.resetAdminPassword(adminId, newPassword);
            ra.addFlashAttribute("message", "비밀번호가 재설정되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "비밀번호 재설정 실패: " + e.getMessage());
        }
        return "redirect:/admin/institutions/" + id + "/admins";
    }

    // ── 관리자 비활성화/활성화 ─────────────────────────────

    @PostMapping("/{id}/admins/{adminId}/deactivate")
    public String deactivateAdmin(@PathVariable int id,
                                  @PathVariable int adminId,
                                  RedirectAttributes ra) {
        institutionService.deactivateAdmin(adminId);
        ra.addFlashAttribute("message", "관리자 계정이 비활성화되었습니다.");
        return "redirect:/admin/institutions/" + id + "/admins";
    }

    @PostMapping("/{id}/admins/{adminId}/activate")
    public String activateAdmin(@PathVariable int id,
                                @PathVariable int adminId,
                                RedirectAttributes ra) {
        institutionService.activateAdmin(adminId);
        ra.addFlashAttribute("message", "관리자 계정이 활성화되었습니다.");
        return "redirect:/admin/institutions/" + id + "/admins";
    }
}
