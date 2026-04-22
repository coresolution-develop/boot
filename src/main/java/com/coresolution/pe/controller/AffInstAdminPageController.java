package com.coresolution.pe.controller;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.coresolution.pe.entity.OrgMemberProgressRow;
import com.coresolution.pe.entity.PendingPairRow;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.AffAdminProgressByOrgService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 계열사 기관 관리자(/aff/inst-admin/**) 전용 컨트롤러.
 *
 * <p>모든 데이터 조회는 세션의 institutionName(= c_name)으로
 * 기관 범위를 자동 제한합니다.</p>
 */
@Slf4j
@Controller
@RequestMapping("/aff/inst-admin")
@PreAuthorize("hasRole('INST_ADMIN')")
@RequiredArgsConstructor
public class AffInstAdminPageController {

    private final AffAdminProgressByOrgService progressService;
    private final AffUserMapper affUserMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    // ── 세션 헬퍼 ─────────────────────────────────────────

    private String institution(HttpServletRequest req) {
        String name = InstitutionAdminContext.getInstitutionName(req);
        if (name == null || name.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "기관 정보가 세션에 없습니다. 다시 로그인해주세요.");
        }
        return name;
    }

    private int resolveYear(Integer year) {
        return (year != null && year >= 2000 && year <= 2100) ? year : currentEvalYear;
    }

    // ── 대시보드 ──────────────────────────────────────────

    @GetMapping({"/", "/dashboard"})
    public String dashboard(HttpServletRequest req, Model model,
                            @RequestParam(required = false) Integer year) {
        int evalYear        = resolveYear(year);
        String orgName      = institution(req);

        // 진행률 통계
        Map<String, Object> progress = progressService.list(evalYear, orgName, "ALL", "", "progress_desc");

        @SuppressWarnings("unchecked")
        List<OrgMemberProgressRow> members = (List<OrgMemberProgressRow>) progress.get("rows");

        int totalUsers   = members.size();
        int completed    = (int) members.stream().filter(m -> m.getNeedPairs() > 0 && m.getDonePairs() >= m.getNeedPairs()).count();
        int notStarted   = (int) members.stream().filter(m -> m.getNeedPairs() > 0 && m.getDonePairs() == 0).count();
        int inProgress   = totalUsers - completed - notStarted;

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) progress.get("summary");

        model.addAttribute("year",             evalYear);
        model.addAttribute("institutionName",  orgName);
        model.addAttribute("totalUsers",       totalUsers);
        model.addAttribute("completed",        completed);
        model.addAttribute("inProgress",       Math.max(0, inProgress));
        model.addAttribute("notStarted",       notStarted);
        model.addAttribute("overallProgress",  summary.get("progress"));
        model.addAttribute("currentYear",      currentEvalYear);

        return "aff/inst-admin/dashboard";
    }

    // ── 진행률 현황 ───────────────────────────────────────

    @GetMapping("/progress")
    public String progress(HttpServletRequest req, Model model,
                           @RequestParam(required = false) Integer year,
                           @RequestParam(defaultValue = "ALL") String ev) {
        int evalYear   = resolveYear(year);
        String orgName = institution(req);

        model.addAttribute("year",            evalYear);
        model.addAttribute("ev",              ev);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("currentYear",     currentEvalYear);

        return "aff/inst-admin/progress";
    }

    // ── 진행률 API ─────────────────────────────────────────

    @GetMapping("/api/progress/members")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> members(
            HttpServletRequest req,
            @RequestParam int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "progress_desc") String sort) {

        String orgName = institution(req);
        Map<String, Object> data = progressService.list(year, orgName, ev, search, sort);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/api/progress/members/{targetId}/pending")
    @ResponseBody
    public ResponseEntity<List<PendingPairRow>> pending(
            @PathVariable String targetId,
            @RequestParam int year,
            @RequestParam(defaultValue = "ALL") String ev) {

        List<PendingPairRow> rows = progressService.pendingPairs(year, targetId, ev);
        return ResponseEntity.ok(rows);
    }
}
