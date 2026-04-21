package com.coresolution.pe.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.EndLetter;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.OrgMemberProgressRow;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AdminProgressByOrgMapper;
import com.coresolution.pe.mapper.EndLetterMapper;
import com.coresolution.pe.mapper.EvaluationMapper;
import com.coresolution.pe.mapper.LoginMapper;
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.InstAdminTargetService;
import com.coresolution.pe.service.InstitutionService;
import com.coresolution.pe.service.PeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기관 관리자(/pe/inst-admin/**) 전용 컨트롤러.
 *
 * <p>모든 데이터 조회는 세션의 institutionName(= c_name)을 org 파라미터로
 * 강제 주입하여 기관 범위를 자동 제한한다.</p>
 */
@Slf4j
@Controller
@RequestMapping("/pe/inst-admin")
@PreAuthorize("hasRole('INST_ADMIN')")
@RequiredArgsConstructor
public class InstAdminPageController {

    private final PeService pe;
    private final InstitutionService institutionService;
    private final LoginMapper loginMapper;
    private final InstAdminTargetService targetService;
    private final EvaluationMapper evaluationMapper;
    private final EndLetterMapper endLetterMapper;
    private final AdminProgressByOrgMapper progressMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    // ── 세션에서 기관명 추출 헬퍼 ──────────────────────────

    private String resolveInstitutionName(HttpServletRequest request) {
        String name = InstitutionAdminContext.getInstitutionName(request);
        if (name == null || name.isBlank()) {
            log.warn("[InstAdmin] 세션에 institutionName 없음 — loginId={}",
                     InstitutionAdminContext.getCurrentLoginId());
            throw new org.springframework.security.access.AccessDeniedException(
                    "기관 정보가 세션에 없습니다. 다시 로그인해주세요.");
        }
        return name;
    }

    private String resolveYear(String year) {
        if (year == null || !year.matches("\\d{4}")) return String.valueOf(currentEvalYear);
        return year;
    }

    // ── 대시보드 ──────────────────────────────────────────

    @GetMapping({"/", "/dashboard"})
    public String dashboard(HttpServletRequest request, Model model,
                            @RequestParam(required = false) String year) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);

        // 기관 통계
        int totalUsers  = pe.countUsers(year, null, null, null, institutionName, null, null);
        int activeUsers = pe.countUsers(year, null, null, null, institutionName, "N", null);
        int pwdUnset    = pe.countUsers(year, null, null, "unset", institutionName, "N", null);
        List<Department> depts = loginMapper.getDepartmentsByInstitution(year, institutionName);

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("totalUsers",      totalUsers);
        model.addAttribute("activeUsers",     activeUsers);
        model.addAttribute("pwdUnset",        pwdUnset);
        model.addAttribute("deptCount",       depts.size());

        return "pe/inst-admin/dashboard";
    }

    // ── 직원명부 ──────────────────────────────────────────

    @GetMapping("/userList")
    public String userList(HttpServletRequest request, Model model,
                           @RequestParam(required = false) String year,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) String dept,
                           @RequestParam(required = false) String pwd,
                           @RequestParam(required = false) String delYn,
                           @RequestParam(required = false) String role,
                           @RequestParam(defaultValue = "1")  int page,
                           @RequestParam(defaultValue = "50") int size) {

        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);
        if (page < 1) page = 1;
        if (size < 1 || size > 500) size = 50;
        int offset = (page - 1) * size;

        // 기관명을 org 로 강제 고정
        List<UserPE> userList  = pe.getUserListpage(year, q, dept, pwd, institutionName, delYn, role, offset, size);
        int totalCount         = pe.countUsers(year, q, dept, pwd, institutionName, delYn, role);
        int totalPages         = (int) Math.ceil(totalCount / (double) size);

        if (page > Math.max(totalPages, 1)) {
            page     = Math.max(totalPages, 1);
            offset   = (page - 1) * size;
            userList = pe.getUserListpage(year, q, dept, pwd, institutionName, delYn, role, offset, size);
        }

        // 부서 목록 — 기관 범위로 제한
        List<Department> departments = loginMapper.getDepartmentsByInstitution(year, institutionName);

        model.addAttribute("userList",        userList);
        model.addAttribute("year",            year);
        model.addAttribute("q",               q);
        model.addAttribute("deptCode",        dept);
        model.addAttribute("pwd",             pwd);
        model.addAttribute("delYn",           delYn);
        model.addAttribute("role",            role);
        model.addAttribute("departments",     departments);
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("totalCount",      totalCount);
        model.addAttribute("totalPages",      totalPages);
        model.addAttribute("page",            page);
        model.addAttribute("size",            size);

        return "pe/inst-admin/userList";
    }

    // ── 직원 데이터 업로드 ────────────────────────────────

    @GetMapping("/userDataUpload")
    public String userDataUpload(HttpServletRequest request, Model model,
                                 @RequestParam(required = false) String year) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", institutionName);
        return "pe/inst-admin/userDataUpload";
    }

    // ── 부서 설정 ─────────────────────────────────────────

    @GetMapping("/subManagement")
    public String subManagement(HttpServletRequest request, Model model,
                                @RequestParam(required = false) String year) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);

        Integer institutionId = InstitutionAdminContext.getInstitutionId(request);
        List<SubManagement> subList =
                loginMapper.getSubManagementByInstitution(year, institutionId, institutionName);

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("subList",         subList);
        return "pe/inst-admin/subManagement";
    }

    // ── 평가 설정 (문제은행) ──────────────────────────────

    @GetMapping("/evaluation")
    public String evaluation(HttpServletRequest request, Model model,
                             @RequestParam(required = false) String year) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);
        List<Evaluation> aaList = evaluationMapper.findByYearAndType(year, "AA");
        List<Evaluation> abList = evaluationMapper.findByYearAndType(year, "AB");

        // 그룹 순서 고정
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
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("aaList",          aaList);
        model.addAttribute("abList",          abList);
        model.addAttribute("aaGrouped",       aaGrouped);
        model.addAttribute("abGrouped",       abGrouped);
        model.addAttribute("aaCount",         aaList.size());
        model.addAttribute("abCount",         abList.size());
        return "pe/inst-admin/evaluation";
    }

    // ── 직원 상세 ─────────────────────────────────────────

    private static final List<String> VALID_ROLES =
            Arrays.asList("team_head", "team_member", "sub_head",
                          "one_person_sub", "medical_leader", "sub_member");

    @GetMapping("/userDetail/{idx}")
    public String userDetail(HttpServletRequest request, Model model,
                             @PathVariable int idx,
                             @RequestParam(required = false) String year) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);

        UserPE user = pe.findUserByIdxAndOrg(idx, year, institutionName);
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "해당 직원 정보에 접근할 수 없습니다.");
        }

        List<Department> departments = loginMapper.getDepartmentsByInstitution(year, institutionName);

        model.addAttribute("user",            user);
        model.addAttribute("year",            year);
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("departments",     departments);
        model.addAttribute("validRoles",      VALID_ROLES);
        return "pe/inst-admin/userDetail";
    }

    /** 평가제외 여부 토글 */
    @PostMapping("/userDetail/{idx}/delYn")
    public String updateDelYn(HttpServletRequest request,
                              @PathVariable int idx,
                              @RequestParam String year,
                              @RequestParam String delYn,
                              RedirectAttributes ra) {
        String institutionName = resolveInstitutionName(request);

        UserPE user = pe.findUserByIdxAndOrg(idx, year, institutionName);
        if (user == null) {
            ra.addFlashAttribute("error", "해당 직원 정보에 접근할 수 없습니다.");
            return "redirect:/pe/inst-admin/userList?year=" + year;
        }
        if (!"Y".equals(delYn) && !"N".equals(delYn)) {
            ra.addFlashAttribute("error", "잘못된 요청입니다.");
            return "redirect:/pe/inst-admin/userDetail/" + idx + "?year=" + year;
        }

        pe.updateDelYn(idx, year, delYn);
        ra.addFlashAttribute("success",
                "Y".equals(delYn) ? "평가 제외로 변경되었습니다." : "평가 포함으로 변경되었습니다.");
        return "redirect:/pe/inst-admin/userDetail/" + idx + "?year=" + year;
    }

    /** 비밀번호 초기화 */
    @PostMapping("/userDetail/{idx}/resetPwd")
    public String resetPassword(HttpServletRequest request,
                                @PathVariable int idx,
                                @RequestParam String year,
                                RedirectAttributes ra) {
        String institutionName = resolveInstitutionName(request);

        UserPE user = pe.findUserByIdxAndOrg(idx, year, institutionName);
        if (user == null) {
            ra.addFlashAttribute("error", "해당 직원 정보에 접근할 수 없습니다.");
            return "redirect:/pe/inst-admin/userList?year=" + year;
        }

        pe.resetPasswordByIdx(idx, year);
        ra.addFlashAttribute("success", "비밀번호가 초기화되었습니다. 직원이 이름으로 재로그인 후 비밀번호를 설정해야 합니다.");
        return "redirect:/pe/inst-admin/userDetail/" + idx + "?year=" + year;
    }

    /** 역할 변경 */
    @PostMapping("/userDetail/{idx}/roles")
    public String updateRoles(HttpServletRequest request,
                              @PathVariable int idx,
                              @RequestParam String year,
                              @RequestParam(required = false) List<String> roles,
                              RedirectAttributes ra) {
        String institutionName = resolveInstitutionName(request);

        UserPE user = pe.findUserByIdxAndOrg(idx, year, institutionName);
        if (user == null) {
            ra.addFlashAttribute("error", "해당 직원 정보에 접근할 수 없습니다.");
            return "redirect:/pe/inst-admin/userList?year=" + year;
        }

        // 허용된 역할만 필터링
        List<String> filtered = roles == null ? List.of() :
                roles.stream()
                     .map(String::toLowerCase)
                     .filter(VALID_ROLES::contains)
                     .distinct()
                     .toList();

        pe.updateRoles(user.getId(), year, filtered);
        ra.addFlashAttribute("success", "역할이 저장되었습니다.");
        return "redirect:/pe/inst-admin/userDetail/" + idx + "?year=" + year;
    }

    // ── 평가 대상 설정 ─────────────────────────────────────────────

    /** 평가 대상 설정 페이지 */
    @GetMapping("/targets")
    public String targets(HttpServletRequest request, Model model,
                          @RequestParam(required = false) String year) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);

        List<UserPE> evaluators = targetService.getEvaluatorSummary(institutionName, Integer.parseInt(year));
        int totalPairs = targetService.countTargets(institutionName, Integer.parseInt(year));

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("evaluators",      evaluators);
        model.addAttribute("totalPairs",      totalPairs);
        return "pe/inst-admin/targets";
    }

    /** 평가 대상 자동 생성 */
    @PostMapping("/targets/generate")
    public String generateTargets(HttpServletRequest request,
                                  @RequestParam String year,
                                  @RequestParam(required = false) List<String> rules,
                                  @RequestParam(defaultValue = "AA") String subDataType,
                                  @RequestParam(defaultValue = "false") boolean clearFirst,
                                  RedirectAttributes ra) {
        String institutionName = resolveInstitutionName(request);
        if (rules == null || rules.isEmpty()) {
            ra.addFlashAttribute("error", "적용할 규칙을 하나 이상 선택하세요.");
            return "redirect:/pe/inst-admin/targets?year=" + year;
        }
        int count = targetService.generateTargets(institutionName, Integer.parseInt(year),
                rules, subDataType, clearFirst);
        ra.addFlashAttribute("success", count + "개의 평가 대상 쌍이 생성되었습니다.");
        return "redirect:/pe/inst-admin/targets?year=" + year;
    }

    /** 평가 대상 전체 삭제 */
    @PostMapping("/targets/clear")
    public String clearTargets(HttpServletRequest request,
                               @RequestParam String year,
                               RedirectAttributes ra) {
        String institutionName = resolveInstitutionName(request);
        int deleted = targetService.clearTargets(institutionName, Integer.parseInt(year));
        ra.addFlashAttribute("success", deleted + "개의 평가 대상 설정이 삭제되었습니다.");
        return "redirect:/pe/inst-admin/targets?year=" + year;
    }

    // ── 평가 진행률 현황 ──────────────────────────────────────────

    @GetMapping("/progress")
    public String progress(HttpServletRequest request, Model model,
                           @RequestParam(required = false) String year,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "default") String sort) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);
        String usersTable = "personnel_evaluation.users_" + year;

        List<OrgMemberProgressRow> members = progressMapper.selectOrgMembers(
                Integer.parseInt(year), institutionName, "ALL",
                q, sort, usersTable);

        // 요약 통계
        int totalMembers   = members.size();
        int doneAll        = (int) members.stream().filter(m -> m.getNeedPairs() > 0 && m.getPendingPairs() == 0).count();
        int inProgress     = (int) members.stream().filter(m -> m.getDonePairs() > 0 && m.getPendingPairs() > 0).count();
        int notStarted     = (int) members.stream().filter(m -> m.getDonePairs() == 0).count();
        int totalPairs     = members.stream().mapToInt(OrgMemberProgressRow::getNeedPairs).sum();
        int completedPairs = members.stream().mapToInt(OrgMemberProgressRow::getDonePairs).sum();

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("members",         members);
        model.addAttribute("q",               q);
        model.addAttribute("sort",            sort);
        model.addAttribute("totalMembers",    totalMembers);
        model.addAttribute("doneAll",         doneAll);
        model.addAttribute("inProgress",      inProgress);
        model.addAttribute("notStarted",      notStarted);
        model.addAttribute("totalPairs",      totalPairs);
        model.addAttribute("completedPairs",  completedPairs);
        return "pe/inst-admin/progress";
    }

    // ── 평가완료 편지 설정 ────────────────────────────────────────

    /** 편지 편집 페이지 */
    @GetMapping("/end-letter")
    public String endLetterPage(HttpServletRequest request, Model model,
                                @RequestParam(required = false) String year) {
        year = resolveYear(year);
        String institutionName = resolveInstitutionName(request);

        EndLetter letter = null;
        try {
            letter = endLetterMapper.findByYearAndInstitution(
                    Integer.parseInt(year), institutionName);
        } catch (Exception e) {
            log.warn("[end-letter] DB 조회 실패 (테이블 미생성?): {}", e.getMessage());
            model.addAttribute("error",
                    "end_letter 테이블이 아직 생성되지 않았습니다. " +
                    "아래 SQL을 DB에서 실행해주세요: " +
                    "CREATE TABLE IF NOT EXISTS personnel_evaluation.end_letter " +
                    "(id INT AUTO_INCREMENT PRIMARY KEY, eval_year INT NOT NULL, " +
                    "institution_name VARCHAR(200) NOT NULL, content TEXT, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uq_year_inst (eval_year, institution_name));");
        }

        model.addAttribute("year",            year);
        model.addAttribute("institutionName", institutionName);
        model.addAttribute("letter",          letter);
        return "pe/inst-admin/end-letter";
    }

    /** 편지 저장 */
    @PostMapping("/end-letter/save")
    public String endLetterSave(HttpServletRequest request,
                                @RequestParam String year,
                                @RequestParam(required = false) String content,
                                RedirectAttributes ra) {
        String institutionName = resolveInstitutionName(request);

        EndLetter letter = new EndLetter();
        letter.setEvalYear(Integer.parseInt(year));
        letter.setInstitutionName(institutionName);
        letter.setContent(content != null ? content.trim() : "");
        try {
            endLetterMapper.upsert(letter);
            ra.addFlashAttribute("success", "평가완료 메시지가 저장되었습니다.");
        } catch (Exception e) {
            log.error("[end-letter] 저장 실패: {}", e.getMessage());
            ra.addFlashAttribute("error", "저장에 실패했습니다. end_letter 테이블이 생성되었는지 확인하세요.");
        }
        return "redirect:/pe/inst-admin/end-letter?year=" + year;
    }
}
