package com.coresolution.pe.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.EndLetter;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.OrgMemberProgressRow;
import com.coresolution.pe.entity.PendingPairRow;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffEndLetterMapper;
import com.coresolution.pe.mapper.AffEvaluationMapper;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.AffAdminProgressByOrgService;
import com.coresolution.pe.service.AffAdminTargetService;

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
    private final AffLoginMapper affLoginMapper;
    private final AffAdminTargetService affTargetService;
    private final AffEvaluationMapper affEvaluationMapper;
    private final AffEndLetterMapper affEndLetterMapper;

    @Value("${app.upload.bg-dir:uploads/bg}")
    private String bgUploadDir;

    private static final List<String> VALID_ROLES = Arrays.asList(
            "team_head", "team_member", "sub_head",
            "one_person_sub", "medical_leader", "sub_member");

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
        List<OrgMemberProgressRow> members = progress.get("rows") instanceof List<?> l
                ? (List<OrgMemberProgressRow>) l
                : List.of();

        int totalUsers = members.size();
        int completed  = (int) members.stream()
                .filter(m -> m.getNeedPairs() > 0 && m.getDonePairs() >= m.getNeedPairs()).count();
        int notStarted = (int) members.stream()
                .filter(m -> m.getNeedPairs() > 0 && m.getDonePairs() == 0).count();
        int inProgress = Math.max(0, totalUsers - completed - notStarted);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = progress.get("summary") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of("progress", 0.0);
        Object overallProgress = summary.getOrDefault("progress", 0.0);

        model.addAttribute("year",             evalYear);
        model.addAttribute("institutionName",  orgName);
        model.addAttribute("totalUsers",       totalUsers);
        model.addAttribute("completed",        completed);
        model.addAttribute("inProgress",       inProgress);
        model.addAttribute("notStarted",       notStarted);
        model.addAttribute("overallProgress",  overallProgress);
        model.addAttribute("currentYear",      currentEvalYear);

        return "aff/inst-admin/dashboard";
    }

    // ── 진행률 현황 ───────────────────────────────────────

    @GetMapping("/progress")
    public String progress(HttpServletRequest req, Model model,
                           @RequestParam(required = false) Integer year,
                           @RequestParam(defaultValue = "ALL") String ev,
                           @RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "default") String sort) {
        int evalYear   = resolveYear(year);
        String orgName = institution(req);

        Map<String, Object> data = progressService.list(evalYear, orgName, ev, q, sort);

        @SuppressWarnings("unchecked")
        List<OrgMemberProgressRow> members = data.get("rows") instanceof List<?> l
                ? (List<OrgMemberProgressRow>) l : List.of();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = data.get("summary") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of("needPairs", 0, "donePairs", 0, "progress", 0.0);

        int totalMembers   = members.size();
        int doneAll        = (int) members.stream()
                .filter(mb -> mb.getNeedPairs() > 0 && mb.getPendingPairs() == 0).count();
        int notStarted     = (int) members.stream()
                .filter(mb -> mb.getNeedPairs() > 0 && mb.getDonePairs() == 0).count();
        int inProgress     = Math.max(0, totalMembers - doneAll - notStarted);
        int totalPairs     = ((Number) summary.getOrDefault("needPairs", 0)).intValue();
        int completedPairs = ((Number) summary.getOrDefault("donePairs", 0)).intValue();

        model.addAttribute("year",            evalYear);
        model.addAttribute("ev",              ev);
        model.addAttribute("q",               q);
        model.addAttribute("sort",            sort);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("members",         members);
        model.addAttribute("totalMembers",    totalMembers);
        model.addAttribute("doneAll",         doneAll);
        model.addAttribute("inProgress",      inProgress);
        model.addAttribute("notStarted",      notStarted);
        model.addAttribute("totalPairs",      totalPairs);
        model.addAttribute("completedPairs",  completedPairs);
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

    // ── 직원명부 ──────────────────────────────────────────

    @GetMapping("/userList")
    public String userList(HttpServletRequest req, Model model,
                           @RequestParam(required = false) String year,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) String dept,
                           @RequestParam(required = false) String pwd,
                           @RequestParam(required = false) String delYn,
                           @RequestParam(required = false) String role,
                           @RequestParam(defaultValue = "1")  int page,
                           @RequestParam(defaultValue = "50") int size) {

        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);
        if (page < 1) page = 1;
        if (size < 1 || size > 500) size = 50;
        int offset = (page - 1) * size;

        List<UserPE> userList  = affLoginMapper.getUserListpage(year, q, dept, pwd, orgName, delYn, role, offset, size);
        int totalCount         = affLoginMapper.countUsers(year, q, dept, pwd, orgName, delYn, role);
        int totalPages         = (int) Math.ceil(totalCount / (double) size);

        if (page > Math.max(totalPages, 1)) {
            page     = Math.max(totalPages, 1);
            offset   = (page - 1) * size;
            userList = affLoginMapper.getUserListpage(year, q, dept, pwd, orgName, delYn, role, offset, size);
        }

        List<Department> departments = affLoginMapper.getDepartmentsByOrg(year, orgName);

        model.addAttribute("userList",        userList);
        model.addAttribute("year",            year);
        model.addAttribute("q",               q);
        model.addAttribute("deptCode",        dept);
        model.addAttribute("pwd",             pwd);
        model.addAttribute("delYn",           delYn);
        model.addAttribute("role",            role);
        model.addAttribute("departments",     departments);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("totalCount",      totalCount);
        model.addAttribute("totalPages",      totalPages);
        model.addAttribute("page",            page);
        model.addAttribute("size",            size);
        model.addAttribute("currentYear",     currentEvalYear);

        return "aff/inst-admin/userList";
    }

    // ── 직원 데이터 업로드 ────────────────────────────────

    @GetMapping("/userDataUpload")
    public String userDataUpload(HttpServletRequest req, Model model,
                                 @RequestParam(required = false) String year) {
        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("currentYear",     currentEvalYear);
        return "aff/inst-admin/userDataUpload";
    }

    // ── 직원 상세 ─────────────────────────────────────────

    @GetMapping("/userDetail/{idx}")
    public String userDetail(HttpServletRequest req, Model model,
                             @PathVariable int idx,
                             @RequestParam(required = false) String year) {
        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);

        UserPE user = affLoginMapper.findUserByIdxAndOrg(idx, year, orgName);
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "해당 직원 정보에 접근할 수 없습니다.");
        }

        List<Department> departments = affLoginMapper.getDepartmentsByOrg(year, orgName);

        model.addAttribute("user",            user);
        model.addAttribute("year",            year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("departments",     departments);
        model.addAttribute("validRoles",      VALID_ROLES);
        model.addAttribute("currentYear",     currentEvalYear);
        return "aff/inst-admin/userDetail";
    }

    /** 평가제외 여부 토글 */
    @PostMapping("/userDetail/{idx}/delYn")
    public String updateDelYn(HttpServletRequest req,
                              @PathVariable int idx,
                              @RequestParam String year,
                              @RequestParam String delYn,
                              RedirectAttributes ra) {
        String orgName = institution(req);

        UserPE user = affLoginMapper.findUserByIdxAndOrg(idx, year, orgName);
        if (user == null) {
            ra.addFlashAttribute("error", "해당 직원 정보에 접근할 수 없습니다.");
            return "redirect:/aff/inst-admin/userList?year=" + year;
        }
        if (!"Y".equals(delYn) && !"N".equals(delYn)) {
            ra.addFlashAttribute("error", "잘못된 요청입니다.");
            return "redirect:/aff/inst-admin/userDetail/" + idx + "?year=" + year;
        }

        affLoginMapper.updateDelYn(idx, year, delYn);
        ra.addFlashAttribute("success",
                "Y".equals(delYn) ? "평가 제외로 변경되었습니다." : "평가 포함으로 변경되었습니다.");
        return "redirect:/aff/inst-admin/userDetail/" + idx + "?year=" + year;
    }

    /** 비밀번호 초기화 */
    @PostMapping("/userDetail/{idx}/resetPwd")
    public String resetPassword(HttpServletRequest req,
                                @PathVariable int idx,
                                @RequestParam String year,
                                RedirectAttributes ra) {
        String orgName = institution(req);

        UserPE user = affLoginMapper.findUserByIdxAndOrg(idx, year, orgName);
        if (user == null) {
            ra.addFlashAttribute("error", "해당 직원 정보에 접근할 수 없습니다.");
            return "redirect:/aff/inst-admin/userList?year=" + year;
        }

        affLoginMapper.resetPasswordByIdx(idx, year);
        ra.addFlashAttribute("success", "비밀번호가 초기화되었습니다. 직원이 이름으로 재로그인 후 비밀번호를 설정해야 합니다.");
        return "redirect:/aff/inst-admin/userDetail/" + idx + "?year=" + year;
    }

    // ── 부서 설정 ──────────────────────────────────────────

    @GetMapping("/subManagement")
    public String subManagement(HttpServletRequest req, Model model,
                                @RequestParam(required = false) String year) {
        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);

        List<SubManagement> subList = affLoginMapper.getDepartmentsByOrg(year, orgName)
                .stream()
                .map(d -> {
                    SubManagement sm = new SubManagement();
                    sm.setSubCode(d.getSubCode());
                    sm.setSubName(d.getSubName());
                    return sm;
                })
                .toList();

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("subList",         subList);
        model.addAttribute("currentYear",     currentEvalYear);
        return "aff/inst-admin/subManagement";
    }

    // ── 평가 대상 설정 ─────────────────────────────────────

    @GetMapping("/targets")
    public String targets(HttpServletRequest req, Model model,
                          @RequestParam(required = false) String year) {
        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);

        List<UserPE> evaluators = affTargetService.getEvaluatorSummary(orgName, Integer.parseInt(year));
        int totalPairs = affTargetService.countTargets(orgName, Integer.parseInt(year));

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("evaluators",      evaluators);
        model.addAttribute("totalPairs",      totalPairs);
        model.addAttribute("currentYear",     currentEvalYear);
        return "aff/inst-admin/targets";
    }

    @PostMapping("/targets/generate")
    public String generateTargets(HttpServletRequest req,
                                  @RequestParam String year,
                                  @RequestParam(required = false) List<String> rules,
                                  @RequestParam(defaultValue = "AA") String subDataType,
                                  @RequestParam(defaultValue = "false") boolean clearFirst,
                                  RedirectAttributes ra) {
        String orgName = institution(req);
        if (rules == null || rules.isEmpty()) {
            ra.addFlashAttribute("error", "적용할 규칙을 하나 이상 선택하세요.");
            return "redirect:/aff/inst-admin/targets?year=" + year;
        }
        int count = affTargetService.generateTargets(orgName, Integer.parseInt(year),
                rules, subDataType, clearFirst);
        ra.addFlashAttribute("success", count + "개의 평가 대상 쌍이 생성되었습니다.");
        return "redirect:/aff/inst-admin/targets?year=" + year;
    }

    @PostMapping("/targets/clear")
    public String clearTargets(HttpServletRequest req,
                               @RequestParam String year,
                               RedirectAttributes ra) {
        String orgName = institution(req);
        int deleted = affTargetService.clearTargets(orgName, Integer.parseInt(year));
        ra.addFlashAttribute("success", deleted + "개의 평가 대상 설정이 초기화되었습니다.");
        return "redirect:/aff/inst-admin/targets?year=" + year;
    }

    // ── 평가 설정 ──────────────────────────────────────────

    @GetMapping("/evaluation")
    public String evaluation(HttpServletRequest req, Model model,
                             @RequestParam(required = false) String year) {
        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);

        List<Evaluation> aaList = affEvaluationMapper.findByYearAndType(year, "AA");
        List<Evaluation> abList = affEvaluationMapper.findByYearAndType(year, "AB");

        List<String> groupOrder = java.util.Arrays.asList(
                "섬김", "배움", "키움", "나눔", "목표관리", "주관식");

        java.util.Map<String, List<Evaluation>> aaGrouped = new java.util.LinkedHashMap<>();
        java.util.Map<String, List<Evaluation>> abGrouped = new java.util.LinkedHashMap<>();
        groupOrder.forEach(g -> {
            aaGrouped.put(g, new java.util.ArrayList<>());
            abGrouped.put(g, new java.util.ArrayList<>());
        });
        aaList.forEach(e -> aaGrouped.computeIfAbsent(e.getD3(), k -> new java.util.ArrayList<>()).add(e));
        abList.forEach(e -> abGrouped.computeIfAbsent(e.getD3(), k -> new java.util.ArrayList<>()).add(e));

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("aaList",          aaList);
        model.addAttribute("abList",          abList);
        model.addAttribute("aaGrouped",       aaGrouped);
        model.addAttribute("abGrouped",       abGrouped);
        model.addAttribute("aaCount",         aaList.size());
        model.addAttribute("abCount",         abList.size());
        model.addAttribute("currentYear",     currentEvalYear);
        return "aff/inst-admin/evaluation";
    }

    // ── 평가완료 편지 설정 ─────────────────────────────────

    @GetMapping("/end-letter")
    public String endLetterPage(HttpServletRequest req, Model model,
                                @RequestParam(required = false) String year) {
        if (year == null || year.isBlank()) year = String.valueOf(currentEvalYear);
        String orgName = institution(req);

        EndLetter letter = null;
        try {
            letter = affEndLetterMapper.findByYearAndInstitution(Integer.parseInt(year), orgName);
        } catch (Exception e) {
            log.warn("[aff end-letter] DB 조회 실패: {}", e.getMessage());
            model.addAttribute("error",
                    "end_letter 테이블이 아직 생성되지 않았습니다. 관리자에게 문의하세요.");
        }

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", orgName);
        model.addAttribute("letter",          letter);
        model.addAttribute("currentYear",     currentEvalYear);
        return "aff/inst-admin/end-letter";
    }

    @PostMapping("/end-letter/save")
    public String endLetterSave(HttpServletRequest req,
                                @RequestParam String year,
                                @RequestParam(required = false) String content,
                                @RequestParam(required = false) String bgImageUrl,
                                RedirectAttributes ra) {
        String orgName = institution(req);

        EndLetter letter = new EndLetter();
        letter.setEvalYear(Integer.parseInt(year));
        letter.setInstitutionName(orgName);
        letter.setContent(content != null ? content.trim() : "");
        letter.setBgImageUrl(bgImageUrl != null && !bgImageUrl.isBlank() ? bgImageUrl.trim() : null);
        try {
            affEndLetterMapper.upsert(letter);
            ra.addFlashAttribute("success", "평가완료 메시지가 저장되었습니다.");
        } catch (Exception e) {
            log.error("[aff end-letter] 저장 실패: {}", e.getMessage());
            ra.addFlashAttribute("error", "저장에 실패했습니다.");
        }
        return "redirect:/aff/inst-admin/end-letter?year=" + year;
    }

    @PostMapping("/end-letter/upload-bg")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadBg(
            @RequestParam("file") MultipartFile file) {

        String mime = file.getContentType();
        if (mime == null || !mime.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "이미지 파일만 업로드할 수 있습니다."));
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "파일 크기는 10MB 이하만 허용됩니다."));
        }

        try {
            Path dir = Paths.get(bgUploadDir);
            Files.createDirectories(dir);
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) {
                ext = orig.substring(orig.lastIndexOf('.'));
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Path dest = dir.resolve(filename);
            file.transferTo(dest.toFile());
            return ResponseEntity.ok(Map.of("ok", true, "url", "/uploads/bg/" + filename));
        } catch (IOException e) {
            log.error("[aff end-letter] 배경 이미지 업로드 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("ok", false, "error", "업로드 중 오류가 발생했습니다."));
        }
    }

    /** 역할 변경 */
    @PostMapping("/userDetail/{idx}/roles")
    @Transactional
    public String updateRoles(HttpServletRequest req,
                              @PathVariable int idx,
                              @RequestParam String year,
                              @RequestParam(required = false) List<String> roles,
                              RedirectAttributes ra) {
        String orgName = institution(req);

        UserPE user = affLoginMapper.findUserByIdxAndOrg(idx, year, orgName);
        if (user == null) {
            ra.addFlashAttribute("error", "해당 직원 정보에 접근할 수 없습니다.");
            return "redirect:/aff/inst-admin/userList?year=" + year;
        }

        List<String> filtered = roles == null ? List.of() :
                roles.stream()
                     .map(String::toLowerCase)
                     .filter(VALID_ROLES::contains)
                     .distinct()
                     .toList();

        affLoginMapper.deleteRolesByUserId(user.getId(), year);
        for (String role : filtered) {
            affLoginMapper.insertRoleForUser(user.getId(), role, year);
        }
        ra.addFlashAttribute("success", "역할이 저장되었습니다.");
        return "redirect:/aff/inst-admin/userDetail/" + idx + "?year=" + year;
    }
}
