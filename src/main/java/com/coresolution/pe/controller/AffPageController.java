package com.coresolution.pe.controller;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.bind.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.entity.CombinedRow;
import com.coresolution.pe.entity.CommentReportDTO;
import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.FormPayload;
import com.coresolution.pe.entity.KpiSummaryRow;
import com.coresolution.pe.entity.MyKpiRow;
import com.coresolution.pe.entity.NoticeV2;
import com.coresolution.pe.entity.NoticeVo;
import com.coresolution.pe.entity.ObjectiveStats;
import com.coresolution.pe.entity.OrgProgressRow;
import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.PendingPairRow;
import com.coresolution.pe.entity.Progress;
import com.coresolution.pe.entity.ReleaseWindow;
import com.coresolution.pe.entity.ReleaseWindowRow;
import com.coresolution.pe.entity.SplitTargets;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffEvalResultMapper;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.service.AdminProgressByOrgService;
import com.coresolution.pe.service.AffAdminProgressByOrgService;
import com.coresolution.pe.service.AffAdminTargetService;
import com.coresolution.pe.service.AffEvalReportService;
import com.coresolution.pe.service.AffEvaluationFormService;
import com.coresolution.pe.service.AffEvaluationService;
import com.coresolution.pe.service.AffInfoService;
import com.coresolution.pe.service.AffKpiService;
import com.coresolution.pe.service.AffNoticeV2Service;
import com.coresolution.pe.service.AffReleaseGateService;
import com.coresolution.pe.service.AffNoticeV2Service.NoticeV2Req;
import com.coresolution.pe.service.AffReleaseWindowService;
import com.coresolution.pe.service.AffUserInfoPageService;
import com.coresolution.pe.service.AffUserService;
import com.coresolution.pe.service.PeAffService;
import com.coresolution.pe.service.PeService;
import com.coresolution.pe.service.ReleaseWindowService;
import com.coresolution.pe.service.YearService;
import com.coresolution.pe.service.UserInfoPageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/aff")
public class AffPageController {
    @Autowired
    AuthenticationManager authManager;
    @Autowired
    private PasswordEncoder passwordEncoder; // 추가
    @Autowired
    private PeAffService pe;
    @Autowired
    private AffNoticeV2Service noticeservice;
    @Autowired
    private AffEvaluationService affEvaluationService;
    @Autowired
    private AffUserService affUserService;
    @Autowired
    private AffInfoService infoService;
    @Autowired
    private AffUserMapper usermapper;
    @Autowired
    private AffEvaluationFormService evaluationFormService;
    @Autowired
    private AffUserInfoPageService userInfoPageService;
    @Autowired
    private AffAdminTargetService affAdminTargetService;
    @Autowired
    private AffReleaseWindowService releaseWindowService;
    @Autowired
    private AffAdminProgressByOrgService service;
    @Autowired
    private AffEvalReportService reportService;
    @Autowired
    private AffEvalResultMapper mapper;
    @Autowired
    private AffReleaseGateService releaseGateService;
    @Autowired
    private ObjectMapper objectMapper; // Spring Boot가 기본 빈으로 제공
    @Autowired
    private AffKpiService kpi;
    @Autowired
    private YearService yearService;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    private static final Logger log = LoggerFactory.getLogger(AffPageController.class);

    private static final DateTimeFormatter FMT_KO = DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h시 mm분",
            java.util.Locale.KOREAN);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // -------------------------------------------------------
    // 공통 가드: 열려있지 않으면 AccessDeniedException
    // -------------------------------------------------------
    /** 평가창구 열림여부 체크(평가자 기준) */
    private void ensureOpenOrThrow(String year, String type, String ev, String evaluatorId) {
        int yr = Integer.parseInt(year);
        UserPE me = pe.findById(evaluatorId, yr);
        String cName = (me != null ? me.getCName() : "");
        String subCode = (me != null ? me.getSubCode() : "");

        var w = releaseWindowService.findBestMatch(yr, type, ev, cName, subCode);
        boolean open = releaseWindowService.isOpen(w, KST);

        if (!open) {
            String reason = (w == null)
                    ? "현재 평가 창구가 설정되어 있지 않습니다."
                    : ("평가 기간이 아닙니다. 오픈: " + w.getOpenAt() + " / 마감: " + w.getCloseAt());
            throw new org.springframework.security.access.AccessDeniedException(reason);
        }
    }

    public boolean isOpen(ReleaseWindowRow w, ZoneId zone) {
        if (w == null)
            return false; // ← 없으면 닫힘
        if (!w.isEnabled())
            return false; // 비활성 닫힘
        var now = LocalDateTime.now(zone);
        return !now.isBefore(w.getOpenAt()) && !now.isAfter(w.getCloseAt());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static boolean notAuth(Authentication auth) {
        return (auth == null || !auth.isAuthenticated());
    }

    @RequestMapping("login")
    public String login(@CookieValue(value = "aff_savedId", defaultValue = "") String savedId,
            @CookieValue(value = "aff_savedLoginType", defaultValue = "byName") String savedLoginType,
            @RequestParam(value = "error", required = false) String error, Model model) throws Exception {
        System.out.println("로그인 페이지");
        // URL 디코딩 (인코딩해서 저장했을 경우)
        model.addAttribute("aff_savedId", URLDecoder.decode(savedId, "UTF-8"));
        model.addAttribute("aff_savedLoginType", savedLoginType);
        if (error != null) {
            model.addAttribute("loginError", true);
        }
        model.addAttribute("noticeV2List", noticeservice.getActiveNoticesaff(currentEvalYear));
        model.addAttribute("currentEvalYear", currentEvalYear);
        return "aff/login/login";
    }

    @RequestMapping(value = "admin/userList", method = RequestMethod.GET)
    public String adminUserList(
            Model model,
            @RequestParam(value = "year",  required = false, defaultValue = "${app.current.eval-year}") String year,
            @RequestParam(value = "q",     required = false) String q,
            @RequestParam(value = "dept",  required = false) String deptCode,
            @RequestParam(value = "pwd",   required = false) String pwd,
            @RequestParam(value = "org",   required = false) String org,
            @RequestParam(value = "delYn", required = false) String delYn,
            @RequestParam(value = "role",  required = false) String role,
            @RequestParam(value = "page",  required = false, defaultValue = "1")  int page,
            @RequestParam(value = "size",  required = false, defaultValue = "50") int size) {

        if (year == null || !year.matches("\\d{4}")) year = String.valueOf(currentEvalYear);
        if (page < 1) page = 1;
        if (size < 1 || size > 500) size = 50;
        int offset = (page - 1) * size;

        List<UserPE> userList = pe.getUserListpage(year, q, deptCode, pwd, org, delYn, role, offset, size);
        int totalCount        = pe.countUsers(year, q, deptCode, pwd, org, delYn, role);
        int totalPages        = (int) Math.ceil(totalCount / (double) size);
        if (page > Math.max(totalPages, 1)) {
            page   = Math.max(totalPages, 1);
            offset = (page - 1) * size;
            userList = pe.getUserListpage(year, q, deptCode, pwd, org, delYn, role, offset, size);
        }

        List<String>     organizations = pe.getOrganizations(year);
        List<Department> departments   = (org == null || org.isBlank())
                ? pe.getDepartments(year)
                : pe.getDepartmentsByOrg(year, org);

        model.addAttribute("userList",      userList);
        model.addAttribute("year",          year);
        model.addAttribute("q",             q);
        model.addAttribute("deptCode",      deptCode);
        model.addAttribute("pwd",           pwd);
        model.addAttribute("org",           org);
        model.addAttribute("delYn",         delYn);
        model.addAttribute("role",          role);
        model.addAttribute("departments",   departments);
        model.addAttribute("organizations", organizations);
        model.addAttribute("totalCount",    totalCount);
        model.addAttribute("totalPages",    totalPages);
        model.addAttribute("page",          page);
        model.addAttribute("size",          size);

        return "aff/admin/userList";
    }

    @GetMapping("admin/userInfo/{idx}")
    public String adminUserInfo(
            @PathVariable int idx,
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") int year,
            Model model) {

        // 0) idx로 직원 기본 정보 조회
        UserPE user = pe.findUserInfoByIdx(idx);
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        String employeeId = user.getId(); // 이 사람이 "평가자" 역할

        // 1) 마이페이지의 me와 같은 정보
        UserPE me = usermapper.findById(employeeId, year);
        model.addAttribute("userInfo", me);
        model.addAttribute("year", year);
        // 🔵 주식회사 조이 여부 확인
        String orgName = (me != null && me.getCName() != null) ? me.getCName().trim() : "";
        boolean isJoy = "주식회사 조이".equals(orgName);

        // 2) 최종 대상: 기본 + 커스텀 ADD - 커스텀 REMOVE
        List<UserPE> targets = infoService.getFinalTargets(employeeId, year);
        model.addAttribute("targets", targets); // 디버그용/필요시 사용

        // 3) 메타/진행률
        Map<String, PairMeta> metaMap = infoService.loadMetaMap(employeeId, year);
        Map<String, Progress> progressMap = infoService.buildProgressMap(employeeId, year, targets);

        model.addAttribute("metaMap", metaMap == null ? Map.of() : metaMap);
        model.addAttribute("progressMap", progressMap == null ? Map.of() : progressMap);

        // 4) 스코프별 버킷 (ORG / AGC / SUB + 부서장 평가 별도)
        List<UserPE> orgAll = new ArrayList<>();
        List<UserPE> agcAll = new ArrayList<>();
        List<UserPE> subAll = new ArrayList<>(); // 부서원/동료 평가
        List<UserPE> subHeadAll = new ArrayList<>(); // 부서장이 하는 평가

        for (UserPE u : targets) {
            String code = u.getEvalTypeCode();
            if (code == null || code.isBlank())
                continue;

            // 1) 기관 스코프(O*)
            if (code.startsWith("O")) {

                if (isJoy) {
                    // 조이: 기관 평가는 안 쓰고, OHEAD_TO_OHEAD만 부서장 평가로 사용
                    if ("OHEAD_TO_OHEAD".equals(code)) {
                        subHeadAll.add(u);
                    }
                } else {
                    orgAll.add(u);
                }
                continue;
            }

            // 2) 소속 스코프(A*)
            if (code.startsWith("A")) {
                agcAll.add(u);
                continue;
            }

            // 3) 부서 스코프(S*)
            if (code.startsWith("S")) {
                // 타겟이 부서장인 경우만 부서장 평가
                if ("SHEAD_TO_SHEAD".equals(code) || "SMEMBER_TO_SHEAD".equals(code)) {
                    subHeadAll.add(u);
                } else {
                    // SHEAD_TO_SMEMBER, SMEMBER_TO_SMEMBER 등은 일반 부서 평가
                    subAll.add(u);
                }
            }
        }

        model.addAttribute("orgAll", orgAll);
        model.addAttribute("agcAll", agcAll);
        model.addAttribute("subAll", subAll);
        model.addAttribute("subHeadAll", subHeadAll);
        model.addAttribute("scopeLabels", Map.of(
                "ORG", "기관 평가",
                "AGC", "소속 평가",
                "SUB", "부서 평가",
                "SUB_HEAD", "부서장 평가"));

        // 5) 커스텀 추가 대상 (admin_custom_targets 기준)
        Map<String, List<UserPE>> customGrouped = affAdminTargetService.getCustomTargetsGroupedByType(employeeId, year);

        model.addAttribute("customGrouped", customGrouped);
        model.addAttribute("typeLabels", affEvaluationService.getTypeLabels());

        return "aff/admin/userInfo";
    }

    @RequestMapping("admin/userDataUpload")
    public String userDataUpload(Model model,
            @RequestParam(value = "year", required = false, defaultValue = "${app.current.eval-year}") String year) {
        System.out.println("사용자 데이터 업로드 페이지");
        // 사용자 데이터 업로드 페이지로 이동
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            System.out.println("userDataUpload - 현재 인증된 사용자: " + authentication.getName());
            System.out.println("userDataUpload - 현재 사용자 권한: " + authentication.getAuthorities());
            // 이곳에 ROLE_ADMIN이 출력되어야 합니다!
        } else {
            System.out.println("userDataUpload - SecurityContextHolder에 인증 정보가 없거나 인증되지 않았습니다. (403 원인)");
        }
        model.addAttribute("year", year);
        return "aff/admin/userDataUpload";
    }

    @RequestMapping("admin/subManagement")
    public String subManagement(Model model,
            @RequestParam(value = "year", required = false, defaultValue = "${app.current.eval-year}") String year) {
        System.out.println("부서 관리 페이지");
        // 부서 관리 페이지로 이동
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            System.out.println("subManagement - 현재 인증된 사용자: " + authentication.getName());
            System.out.println("subManagement - 현재 사용자 권한: " + authentication.getAuthorities());
            // 이곳에 ROLE_ADMIN이 출력되어야 합니다!
        } else {
            System.out.println("subManagement - SecurityContextHolder에 인증 정보가 없거나 인증되지 않았습니다. (403 원인)");
        }
        List<SubManagement> subList = pe.getSubManagement(year);
        model.addAttribute("subList", subList);
        System.out.println("부서 목록: " + subList);
        model.addAttribute("year", year);

        return "aff/admin/subManagement";
    }

    @RequestMapping("pwd/{idx}")
    public String pwdSetting(@PathVariable("idx") Integer idx, Model model) {
        System.out.println("비밀번호 설정 페이지");
        // idx를 이용하여 사용자 정보를 조회하고 모델에 추가
        UserPE userInfo = pe.findUserInfoByIdx(idx);
        model.addAttribute("id", userInfo.getId());
        model.addAttribute("idx", userInfo.getIdx());
        model.addAttribute("name", userInfo.getName());
        System.out.println(
                "비밀번호 설정 페이지: idx=" + userInfo.getIdx() + ", id=" + userInfo.getId() + ", name=" + userInfo.getName());
        // 비밀번호 설정 페이지로 이동
        return "aff/login/pwd";
    }

    @GetMapping("pwdfind")
    public String pwdFind(Model model) {
        model.addAttribute("year", currentEvalYear);
        return "aff/login/findpwd";
    }

    @PostMapping("pwd/find-ajax")
    @ResponseBody
    public Map<String, Object> findAjax(@RequestParam String id, @RequestParam String ph, @RequestParam int year) {
        Map<String, Object> response = new HashMap<>();
        UserPE user = pe.findByUserIdWithNames(id, year);
        System.out.println("user = " + user);
        boolean exists = pe.existsByUserIdAndPhone(id, year, ph);

        if (!exists) {
            response.put("success", false);
            response.put("message", "사번과 휴대폰 번호가 일치하는 사용자가 없습니다.");
            return response;
        }

        // 여기서부터는 “존재하는 경우” 처리 (임시비밀번호 발급 등)
        response.put("success", true);
        response.put("message", "인증이 완료되었습니다.");
        System.out.println("response: " + response);
        return response;
    }

    @PostMapping(value = "/pwd/act/{id}", produces = "application/json")
    @ResponseBody
    public Map<String, Object> changePassword(
            @PathVariable("id") String userId,
            @RequestParam("pwd") String rawPassword,
            @RequestParam(value = "year", required = false) String year) {

        Map<String, Object> res = new HashMap<>();

        // 2) year 기본값 보정 (연도 컬럼을 쓰지 않으면 그냥 null/빈값으로 둬도 됨)
        if (year == null || year.isBlank()) {
            year = String.valueOf(java.time.Year.now(java.time.ZoneId.of("Asia/Seoul")).getValue());
        }

        String encoded = passwordEncoder.encode(rawPassword);
        System.out.println("비밀번호 변경 시도: userId=" + userId + ", year=" + year + ", encodedPwd=" + encoded);
        try {
            boolean ok = pe.changePasswordByUserIdAndYear(userId, year, encoded);
            if (ok) {
                res.put("result", "Y"); // ★ 프론트가 기대하는 값
                res.put("message", "비밀번호 변경 완료");
            } else {
                res.put("result", "N");
                res.put("message", "사용자를 찾을 수 없거나 변경 실패");
            }
        } catch (Exception e) {
            // 필요시 로깅
            res.put("result", "N");
            res.put("message", "서버 오류: " + e.getMessage());
        }

        return res;
    }

    @RequestMapping("admin/target")
    public String target(Model model,
            @RequestParam(value = "year", required = false, defaultValue = "${app.current.eval-year}") String year, Authentication auth) {
        System.out.println("평가 대상 메칭 페이지");
        // 목표 관리 페이지로 이동
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            System.out.println("target - 현재 인증된 사용자: " + authentication.getName());
            System.out.println("target - 현재 사용자 권한: " + authentication.getAuthorities());
            // 이곳에 ROLE_ADMIN이 출력되어야 합니다!
        } else {
            System.out.println("target - SecurityContextHolder에 인증 정보가 없거나 인증되지 않았습니다. (403 원인)");
        }
        // 인증·권한 로그 (ROLE_ADMIN 여부 확인)
        System.out.println("관리자: " + auth.getName() + ", 권한=" + auth.getAuthorities());

        model.addAttribute("year", year);
        return "aff/admin/target";
    }

    /** (버튼용) 기본 대상 테이블 초기화 및 재생성 (AFF) */
    @PostMapping("/admin/target/initDefaults")
    public String initDefaults(@RequestParam(value = "year", defaultValue = "${app.current.eval-year}") int year,
            RedirectAttributes ra) {
        int upserts = affEvaluationService.rebuildAdminDefaultTargets(year);
        ra.addFlashAttribute("message", "AFF 기본 대상이 초기화되었습니다. (" + upserts + "건)");
        return "redirect:/aff/admin/target?year=" + year;
    }

    @GetMapping("Info/{idx}")
    public String redirectOlduserInfo() {
        return "redirect:/aff/Info";
    }

    @GetMapping("pwdSet/{idx}")
    public String redirectOldPwdSet() {
        return "redirect:/aff/pwdSet";
    }

    /** (GET) 비밀번호 설정/변경 페이지 */

    @GetMapping("pwdSet")
    public String pwdSettingForMe(Authentication auth, Model model) {
        if (auth == null || !auth.isAuthenticated()) {
            // 비로그인 접근 방지
            return "redirect:/aff/login";
        }

        String userId = auth.getName();

        UserPE userInfo = pe.findByUserIdWithNames(userId, currentEvalYear);
        model.addAttribute("userInfo", userInfo);

        // 화면에 보여줄 값(읽기전용)
        model.addAttribute("id", userInfo.getId());
        model.addAttribute("idx", userInfo.getIdx()); // 굳이 안 써도 되지만 유지해도 무방
        model.addAttribute("name", userInfo.getName());

        return "aff/user/pwdset";
    }

    @PostMapping("user/pwdAction")
    public ResponseEntity<Map<String, Object>> pwdAction(
            Authentication auth,
            @RequestParam String pwd,
            @RequestParam String pwd2) {

        Map<String, Object> response = new HashMap<>();

        if (auth == null || !auth.isAuthenticated()) {
            response.put("result", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (pwd == null || pwd2 == null || pwd.isBlank() || pwd2.isBlank()) {
            response.put("result", "비밀번호를 입력해주세요.");
            return ResponseEntity.ok(response);
        }
        if (!pwd.equals(pwd2)) {
            response.put("result", "비밀번호가 일치하지 않습니다.");
            return ResponseEntity.ok(response);
        }

        String userId = auth.getName();
        UserPE user = pe.findByUserIdWithNames(userId, currentEvalYear);
        if (user == null) {
            response.put("result", "사용자를 찾을 수 없습니다.");
            return ResponseEntity.ok(response);
        }

        // “이미 설정됨” 여부와 무관하게 **항상 갱신**
        String encoded = passwordEncoder.encode(pwd);
        user.setPwd(encoded);
        int updated = pe.updateUserPassword(user);
        if (updated == 0) {
            response.put("result", "업데이트에 실패했습니다. 다시 시도해주세요.");
            return ResponseEntity.ok(response);
        }

        // (선택 1) 현재 세션 유지: 굳이 토큰 갈아끼울 필요 없음
        // (선택 2) 재인증/로그아웃을 원하면 아래처럼 처리
        // SecurityContextHolder.clearContext();

        response.put("result", "ok");
        response.put("redirectUrl", "/aff/Info"); // 너희 마이페이지 URL에 맞춰 수정
        return ResponseEntity.ok(response);
    }

    @RequestMapping("Info")
    public String affInfo(Model model, Authentication auth,
            @RequestParam(required = false) Integer year) {
        if (auth == null || !auth.isAuthenticated())
            return "redirect:/aff/login";

        final String userId = auth.getName();
        final int selectedYear = (year != null) ? year : currentEvalYear;
        final boolean isPastYear = selectedYear != currentEvalYear;

        // 1) 내 정보
        UserPE me = usermapper.findById(userId, selectedYear);
        model.addAttribute("userInfo", me);
        model.addAttribute("year", selectedYear);
        model.addAttribute("isPastYear", isPastYear);
        model.addAttribute("availableYears", yearService.getYears());

        // 2) 릴리즈 윈도우 조회 (현재 연도만, 전년도는 이미 닫힘)
        ReleaseWindow win = isPastYear ? null : releaseWindowService.findEarliestForUser(
                selectedYear,
                me.getCName(),
                me.getSubCode());
        boolean beforeOpen = !isPastYear && releaseWindowService.isBeforeOpen(win);
        long days = isPastYear ? 0 : releaseWindowService.daysUntilOpen(win);

        model.addAttribute("releaseWindow", win);
        model.addAttribute("isBeforeOpen", beforeOpen);
        model.addAttribute("daysUntilOpen", days);
        model.addAttribute("openAtStr", (win != null && win.getOpenAt() != null)
                ? win.getOpenAt().toString()
                : null);
        model.addAttribute("closeAtStr", (win != null && win.getCloseAt() != null)
                ? win.getCloseAt().toString()
                : null);
        // 최종 대상 목록
        List<UserPE> targets = infoService.getFinalTargets(userId, selectedYear);
        model.addAttribute("targets", targets);

        // 메타/진행률
        Map<String, PairMeta> metaMap = infoService.loadMetaMap(userId, selectedYear);
        Map<String, Progress> progressMap = infoService.buildProgressMap(userId, selectedYear, targets);
        model.addAttribute("metaMap", metaMap == null ? Map.of() : metaMap);
        model.addAttribute("progressMap", progressMap == null ? Map.of() : progressMap);

        // 4) 스코프별 + 부서장/부서원 분리
        List<UserPE> orgAll = new ArrayList<>();
        List<UserPE> agcAll = new ArrayList<>();
        List<UserPE> subHeadAll = new ArrayList<>();
        List<UserPE> subStaffAll = new ArrayList<>();

        for (UserPE u : targets) {
            String rawCode = u.getEvalTypeCode();
            if (rawCode == null || rawCode.isBlank()) {
                continue;
            }

            String code = rawCode.trim().toUpperCase(); // 안전하게

            if (code.startsWith("O")) {
                // 기관 평가
                orgAll.add(u);

            } else if (code.startsWith("A")) {
                // 소속 평가
                agcAll.add(u);

            } else if (code.startsWith("S")) {
                // ---------- S계열: 부서 범위 ----------
                // 패턴: (예상)
                // - SHEAD_TO_SMEMBER : 부서장 -> 부서원
                // - SMEMBER_TO_SHEAD : 부서원 -> 부서장
                // - SHEAD_TO_SHEAD : 부서장 -> 부서장

                int idx = code.indexOf("_TO_");
                if (idx > 0) {
                    String targetPart = code.substring(idx + 4); // "_TO_" 뒤

                    if ("SHEAD".equals(targetPart)) {
                        // 타겟이 부서장 → "부서장 평가" 섹션
                        subHeadAll.add(u);

                    } else if ("SMEMBER".equals(targetPart)) {
                        // 타겟이 부서원 → "부서원 평가" 섹션
                        subStaffAll.add(u);

                    } else {
                        // 혹시 다른 suffix가 생겼을 경우 fallback
                        if (isDeptHead(u)) {
                            subHeadAll.add(u);
                        } else {
                            subStaffAll.add(u);
                        }
                    }
                } else {
                    // "_TO_" 없는 예외 코드가 있다면 기존 로직 fallback
                    if (isDeptHead(u)) {
                        subHeadAll.add(u);
                    } else {
                        subStaffAll.add(u);
                    }
                }
            }
        }

        model.addAttribute("orgAll", orgAll);
        model.addAttribute("agcAll", agcAll);
        model.addAttribute("subHeadAll", subHeadAll);
        model.addAttribute("subStaffAll", subStaffAll);

        model.addAttribute("scopeLabels", Map.of(
                "ORG", "기관 평가",
                "AGC", "소속 평가",
                "SUB_HEAD", "부서장 평가",
                "SUB_STAFF", "부서원 평가"));

        return "aff/user/info";
    }

    // 🔧 실제 사내 룰에 맞게 조건만 수정하면 됨
    private boolean isDeptHead(UserPE u) {
        String pos = u.getPosition();
        if (pos == null)
            return false;
        // 예시: 직급에 '부서장', '팀장', '과장' 이 들어가면 부서장으로 본다
        return pos.contains("지점대표") || pos.contains("재무부장") || pos.contains("의전실장") || pos.contains("총괄운영실장")
                || pos.contains("시설팀방") || pos.contains("총괄팀장") || pos.contains("시설팀장") || pos.contains("서비스팀장")
                || pos.contains("기획팀장") || pos.contains("총무팀장") || pos.contains("실장");
    }

    @RequestMapping("admin/evaluation")
    public String eval(Model model,
            @RequestParam(value = "year", required = false, defaultValue = "${app.current.eval-year}") String year) {
        System.out.println("관리자 페이지");
        // 관리자 페이지로 이동
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            System.out.println("adminPage - 현재 인증된 사용자: " + authentication.getName());
            System.out.println("adminPage - 현재 사용자 권한: " + authentication.getAuthorities());
            // 이곳에 ROLE_ADMIN이 출력되어야 합니다!
        } else {
            System.out.println("adminPage - SecurityContextHolder에 인증 정보가 없거나 인증되지 않았습니다. (403 원인)");
        }
        List<Evaluation> eval = pe.getEvaluation(year);
        model.addAttribute("eval", eval);
        System.out.println("평가 목록: " + eval);
        return "aff/admin/evaluation";
    }

    // 예: AffEvalFormController#editForm or startForm 공통
    private static final List<String> GROUP_AC = List.of(
            "근무태도", "리더쉽", "조직관리", "업무처리", "소통 및 화합");
    private static final List<String> GROUP_AD = List.of(
            // ← AD용 실제 섹션명으로 교체
            "근무태도", "처리능력", "업무실적");
    private static final List<String> GROUP_AE = List.of(
            // ← AE용 실제 섹션명으로 교체
            "근무태도", "처리능력", "업무실적");

    private List<String> resolveGroupOrder(String dataType) {
        return switch (dataType) {
            case "AC" -> GROUP_AC;
            case "AD" -> GROUP_AD;
            case "AE" -> GROUP_AE;
            default -> GROUP_AC; // fallback
        };
    }

    @GetMapping("user/form/start")
    public String userForm(Model model, Authentication auth, @RequestParam("targetId") String targetId,
            @RequestParam("type") String dataType, @RequestParam("ev") String dataEv,
            @RequestParam(value = "year", required = false, defaultValue = "${app.current.eval-year}") int year) {
        System.out.println(" 페이지");
        // 사용자 정보 수정 페이지로 이동
        // 1) 로그인한 사용자 ID
        String evaluatorId = auth.getName();
        // ⬇⬇⬇ 헤더/레이아웃에서 쓰는 userInfo를 반드시 채워줍니다.
        UserPE userInfo = usermapper.findById(evaluatorId, year); // 서비스/매퍼 맞게 호출
        model.addAttribute("userInfo", userInfo);
        try {
            FormPayload payload = evaluationFormService.prepare(evaluatorId, targetId, year,
                    dataType,
                    dataEv);

            // 뷰에 바인딩
            model.addAttribute("year", year);
            model.addAttribute("dataType", dataType);
            model.addAttribute("dataEv", dataEv);
            model.addAttribute("evaluatorId", evaluatorId);
            model.addAttribute("seq", new java.util.concurrent.atomic.AtomicInteger(0));

            model.addAttribute("target", payload.getTarget()); // UserPE
            model.addAttribute("questions", payload.getQuestions()); // List<Evaluation>
            model.addAttribute("answerMap", payload.getAnswerMap()); // qIdx -> EvalResult
            model.addAttribute("answered", payload.getAnswered());
            model.addAttribute("total", payload.getTotal());
            model.addAttribute("completed", payload.isCompleted());
            model.addAttribute("avgScore", payload.getAvgScore()); // nullable(Double)
            model.addAttribute("grouped", payload.getGrouped());
            model.addAttribute("groupOrder", payload.getOrder());
            model.addAttribute("essay", payload.getEssay());
            return "aff/user/form"; // 타임리프 템플릿
        } catch (AccessDeniedException ex) {
            // 배정(meta)이 없거나 권한이 없을 때
            model.addAttribute("error", "접근 권한이 없습니다.");
            return "error/403";
        }
    }

    @GetMapping("user/form/edit")
    public String editForm(Model model,
            Authentication auth,
            @RequestParam("targetId") String targetId,
            @RequestParam("type") String dataType,
            @RequestParam("ev") String dataEv,
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") int year) {

        if (auth == null || !auth.isAuthenticated())
            return "redirect:/pe/login";
        final String evaluatorId = auth.getName();

        UserPE userInfo = usermapper.findById(evaluatorId, year);
        model.addAttribute("userInfo", userInfo);

        // ✅ prepareForEdit 가 questions + (grouped/order/essay/answerMap) 까지 만들어주도록
        FormPayload payload = evaluationFormService.prepareForEdit(
                evaluatorId, targetId, year, dataType, dataEv);

        // 공통
        model.addAttribute("year", year);
        model.addAttribute("dataType", dataType);
        model.addAttribute("dataEv", dataEv);
        model.addAttribute("evaluatorId", evaluatorId);

        model.addAttribute("target", payload.getTarget());
        model.addAttribute("questions", payload.getQuestions());
        model.addAttribute("answered", payload.getAnswered());
        model.addAttribute("totalScore", payload.getTotalScore());
        model.addAttribute("avgScore", payload.getAvgScore());

        // ✅ 뷰가 필요로 하는 핵심 4종
        model.addAttribute("grouped", payload.getGrouped()); // Map<String, List<Evaluation>>
        model.addAttribute("groupOrder", payload.getOrder()); // List<String>
        model.addAttribute("essay", payload.getEssay()); // List<Evaluation>
        model.addAttribute("answerMap", payload.getAnswerMap()); // Map<String,String> (r{idx}/t{idx})

        // (선택) JS 프리필/디버그용
        model.addAttribute("answersBundleJson", payload.getAnswersBundleJson());
        model.addAttribute("nameMap", payload.getNameMap());

        model.addAttribute("seq", new java.util.concurrent.atomic.AtomicInteger(0));
        return "aff/user/edit";
    }

    @PostMapping("admin/userInfo/{idx}/custom/remove")
    public String removeCustomTarget(
            @PathVariable int idx,
            @RequestParam("year") int year,
            @RequestParam("targetId") String targetId,
            @RequestParam(name = "reason", required = false, defaultValue = "관리자 수동 삭제") String reason,
            org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {

        // idx → userId 변환 + 커스텀 is_active=0 처리
        affAdminTargetService.removeCustomAddByIdx(idx, year, targetId, reason);

        ra.addFlashAttribute("message", "커스텀 평가 대상을 삭제했습니다.");
        return "redirect:/aff/admin/userInfo/" + idx + "?year=" + year;
    }

    @RequestMapping("admin/notice")
    public String notice(Model model) {
        System.out.println("관리자 페이지(공지사항)");
        // 관리자 페이지로 이동
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            System.out.println("admin - 현재 인증된 사용자: " + authentication.getName());
            System.out.println("admin - 현재 사용자 권한: " + authentication.getAuthorities());
            // 이곳에 ROLE_ADMIN이 출력되어야 합니다!
        } else {
            System.out.println("admin - SecurityContextHolder에 인증 정보가 없거나 인증되지 않았습니다. (403 원인)");
        }
        return "aff/admin/notice";
    }

    @GetMapping("admin/notices")
    @ResponseBody
    public List<NoticeV2> noticelist() {
        return noticeservice.list();
    }

    @GetMapping("admin/notices/{id}")
    @ResponseBody
    public NoticeV2 one(@PathVariable int id) {
        return noticeservice.get(id);
    }

    @PostMapping("admin/notices")
    @ResponseBody
    public ResponseEntity<?> noticecreate(@Valid @RequestBody NoticeV2Req req) {
        NoticeV2 created = noticeservice.create(req);
        return ResponseEntity.created(URI.create("/aff/admin/notices/" + created.getId())).body(created);
    }

    @PutMapping("admin/notices/{id}")
    @ResponseBody
    public NoticeV2 noticeupdate(@PathVariable int id, @Valid @RequestBody NoticeV2Req req) {
        return noticeservice.update(id, req);
    }

    @DeleteMapping("admin/notices/{id}")
    @ResponseBody
    public Map<String, Object> noticedelete(@PathVariable int id) {
        noticeservice.delete(id);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/progress/org/detail")
    public String view(@RequestParam int year,
            @RequestParam String org,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        model.addAttribute("year", year);
        model.addAttribute("org", org);
        model.addAttribute("ev", ev);
        model.addAttribute("search", search);
        return "aff/admin/progress-org-detail"; // 경로는 네 프로젝트 구조에 맞게
    }

    @GetMapping("/api/admin/progress/org/members")
    @ResponseBody
    public Map<String, Object> members(@RequestParam int year,
            @RequestParam String org,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "progress_desc") String sort) {
        return service.list(year, org, ev, search, sort);
    }

    @GetMapping("/api/admin/progress/org/members/{targetId}/pending")
    @ResponseBody
    public List<PendingPairRow> pending(@PathVariable String targetId,
            @RequestParam int year,
            @RequestParam(defaultValue = "ALL") String ev) {
        return service.pendingPairs(year, targetId, ev);
    }

    @GetMapping("/admin/progress/org")
    public String view(@RequestParam(defaultValue = "${app.current.eval-year}") int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        model.addAttribute("year", year);
        model.addAttribute("ev", ev);
        model.addAttribute("search", search);
        return "aff/admin/progress-org";
    }

    @GetMapping("/api/admin/progress/org")
    @ResponseBody
    public Map<String, Object> list(@RequestParam int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "progress_desc") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.getByOrg(year, ev, search, sort, page, size);
    }

    @GetMapping(value = "/api/admin/progress/org.csv", produces = "text/csv; charset=UTF-8")
    @ResponseBody
    public String csv(@RequestParam int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search) {
        var data = service.getByOrg(year, ev, search, "progress_desc", 1, 10000);
        @SuppressWarnings("unchecked")
        var list = (java.util.List<OrgProgressRow>) data.get("rows");
        String header = "orgName,totalPairs,completedPairs,pendingPairs,progress,updatedAt";
        String body = list.stream().map(r -> String.join(",",
                "\"" + r.getOrgName().replace("\"", "\"\"") + "\"",
                String.valueOf(r.getTotalPairs()),
                String.valueOf(r.getCompletedPairs()),
                String.valueOf(r.getPendingPairs()),
                String.valueOf(r.getProgress()),
                r.getUpdatedAt() == null ? "" : r.getUpdatedAt())).collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }

    public record EvOption(String value, String label) {

        public EvOption {
            value = Objects.requireNonNull(value, "value must not be null").trim().toUpperCase();

            if (label == null || label.isBlank()) {
                label = value;
            } else {
                label = label.trim();
            }
        }

        public static EvOption fromCode(String code, Map<String, String> pathMap) {
            if (code == null) {
                throw new IllegalArgumentException("code must not be null");
            }
            String normalized = code.trim().toUpperCase();
            String label = (pathMap == null)
                    ? normalized
                    : pathMap.getOrDefault(normalized, normalized);
            return new EvOption(normalized, label);
        }

        public boolean hasValue() {
            return value != null && !value.isBlank();
        }

        @Override
        public String toString() {
            return "EvOption[value=%s, label=%s]".formatted(value, label);
        }
    }

    private static final Map<String, String> EV_PATH = Map.of(
            "A", "부서장 > 부서장",
            "B", "부서장 > 부서원",
            "C", "부서원 > 부서장",
            "D", "부서원 > 부서원");

    @GetMapping("report")
    public String resultPage(Model model, Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String ev,
            HttpServletRequest request,
            @RequestParam(name = "show", required = false, defaultValue = "0") int show) {

        if (auth == null || !auth.isAuthenticated())
            return "redirect:/aff/login";
        String targetId = auth.getName();
        model.addAttribute("currentYear", currentEvalYear);
        // 사용자가 명시적으로 요청한 연도(Nullable)
        Integer requested = year;

        // 1) 기본 연도 결정 (파라미터 없을 때만)
        int y = (requested != null)
                ? requested
                : java.util.Optional.ofNullable(releaseGateService.latestOpenYear("user_result"))
                        .orElse(java.time.Year.now().getValue());

        // 2) 공개일 체크
        // boolean openNow = releaseGateService.isOpenNow("user_result", y);

        // 2-1) 사용자가 year를 명시했는데 미공개면 → 무조건 not-open 으로
        // if (requested != null && !openNow) {
        // // ev 보존(있으면), 이전 페이지로 돌아가기 위한 backUrl도 옵션으로 붙일 수 있음
        // String qs = new StringBuilder("?year=").append(requested)
        // .append(ev != null && !ev.isBlank()
        // ? "&ev=" + java.net.URLEncoder.encode(ev,
        // java.nio.charset.StandardCharsets.UTF_8)
        // : "")
        // .toString();
        // return "redirect:/aff/not-open" + qs;
        // }
        // 2-2) 사용자가 year 파라미터 없이 들어왔고,
        // 서버가 정한 기본 y가 미공개면 → 공개된 최신 연도로 '소프트' 안내(있으면),
        // 없으면 not-open
        // if (requested == null && !openNow) {
        // Integer fb = releaseGateService.latestOpenYear("user_result");
        // if (fb != null) {
        // String qs = new StringBuilder("?year=").append(fb)
        // .append(ev != null && !ev.isBlank()
        // ? "&ev=" + java.net.URLEncoder.encode(ev,
        // java.nio.charset.StandardCharsets.UTF_8)
        // : "")
        // .toString();
        // return "redirect:/aff/report" + qs;
        // }
        // return "redirect:/aff/not-open?year=" + y;
        // }

        model.addAttribute("year", y);
        // 내 정보
        UserPE me = pe.findByUserIdWithNames(targetId, y);
        model.addAttribute("empId", me.getId());
        model.addAttribute("orgName", me.getCName());
        model.addAttribute("deptName", me.getSubName());
        model.addAttribute("position", me.getPosition());
        model.addAttribute("empName", me.getName());

        // 존재하는 관계 옵션
        var evCodes = mapper.selectExistingDataEv(y, targetId);

        var optionsFromDb = evCodes.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase())
                .distinct()
                .map(code -> new EvOption(code, EV_PATH.getOrDefault(code, code)))
                .toList();

        // TOTAL 맨 앞에 추가
        List<EvOption> options = new ArrayList<>();
        options.addAll(optionsFromDb);

        model.addAttribute("availableEvOptions", options);

        // 선택값
        String evNorm = (ev == null || ev.isBlank()) ? null : ev.trim().toUpperCase();
        model.addAttribute("selectedEvValue", evNorm);
        model.addAttribute("selectedEvLabel", evNorm == null ? "" : EV_PATH.getOrDefault(evNorm, evNorm));

        // ── TOTAL이 아닌 경우: 기존 통계/VM
        if (evNorm != null) {
            // 🔧 수정: show == 1 일 때만 AI 생성 허용
            boolean withAI = (show == 1);
            // boolean withAI = false; // GET에서는 무조건 금지
            var agg = reportService.buildAggregate(
                    y, targetId, evNorm,
                    EV_PATH.getOrDefault(evNorm, evNorm),
                    me.getSubCode(),
                    withAI);

            model.addAttribute("vm", agg.vm());
            if (agg.report() != null)
                model.addAttribute("report", agg.report());
            if (agg.scoreReport() != null)
                model.addAttribute("scoreReport", agg.scoreReport());
            if (agg.stats() != null) {
                model.addAttribute("stats", agg.stats());
                // 🔧 공통으로 쓸 alias 하나 만들어 주기
                CommentReportDTO total = (agg.scoreReport() != null)
                        ? agg.scoreReport()
                        : agg.report();
                model.addAttribute("totalReport", total);
                // 프론트 payload
                ObjectiveStats s = agg.stats();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("labels", s.getLabels());
                payload.put("myArea100", s.getMyArea100());
                payload.put("myAreaDist", s.getMyAreaDist());
                payload.put("myOverall100", s.getMyOverall100());

                Map<String, Double> cohortArea100 = (s.getDeptEvArea100() != null && !s.getDeptEvArea100().isEmpty())
                        ? s.getDeptEvArea100()
                        : s.getEvArea100();
                payload.put("evArea100", cohortArea100);

                double cohortOverall100 = Optional.ofNullable(s.getCohortOverall100()).orElse(0.0);
                payload.put("cohortOverall100", cohortOverall100);

                payload.put("areaNarr", s.getAreaNarr());
                String selectedEvLabel = (String) model.getAttribute("selectedEvLabel");
                payload.put("cohortType", "평가구간");
                payload.put("cohortName", (selectedEvLabel != null ? selectedEvLabel : "") + " · " + me.getSubName());

                String objJson = "{}";
                try {
                    objJson = objectMapper.writeValueAsString(payload);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize payload", e);
                }
                model.addAttribute("objJson", objJson);
            }
        }

        // 버튼 누르고 돌아왔을 때만 보고서 섹션 노출
        model.addAttribute("showReport", show == 1);

        // CSRF
        CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
        if (csrf != null)
            model.addAttribute("_csrf", csrf);

        return "aff/user/report";
    }

    @PostMapping("report/summary")
    public String regenerateSummary(
            Authentication auth,
            @RequestParam int year,
            @RequestParam String ev,
            RedirectAttributes ra,
            HttpServletRequest request) {

        if (auth == null || !auth.isAuthenticated())
            return "redirect:/pe/login";
        final String targetId = auth.getName();
        final String evNorm = ev == null ? null : ev.trim().toUpperCase();
        // 사용자가 명시적으로 요청한 연도(Nullable)
        Integer requested = year;

        // 1) 기본 연도 결정 (파라미터 없을 때만)
        int y = (requested != null)
                ? requested
                : java.util.Optional.ofNullable(releaseGateService.latestOpenYear("user_result"))
                        .orElse(java.time.Year.now().getValue());

        UserPE me = pe.findByUserIdWithNames(targetId, year);
        if (me == null) {
            ra.addFlashAttribute("msg", "사용자 정보를 찾을 수 없습니다.");
            ra.addAttribute("year", y);
            ra.addAttribute("ev", ev);
            return "redirect:/aff/report";
        }
        // 공통: 리다이렉트 파라미터
        ra.addFlashAttribute("msg", "요약을 갱신했습니다.");
        ra.addAttribute("year", y);
        ra.addAttribute("ev", ev);
        ra.addAttribute("show", 1);

        return "redirect:/aff/report";
    }

    @GetMapping("/admin/summary")
    public String kpiSummary(Authentication auth,
            @RequestParam(required = false, defaultValue = "") String orgName,
            @RequestParam(required = false, defaultValue = "${app.current.eval-year}") int year,
            @RequestParam(required = false, defaultValue = "") String op,
            Model model) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/aff/login";
        }
        String org = (orgName == null ? "" : orgName.trim()); // '' = 전체
        // 셀렉트박스용 기관 목록
        model.addAttribute("orgList", kpi.fetchOrgList(year));

        // 선택값 바인딩(뷰에서 th:selected로 사용)
        model.addAttribute("selectedOrgName", org);
        model.addAttribute("selectedYear", year);

        // 표 제목에 쓸 표시명
        model.addAttribute("orgNameForTitle", org.isEmpty() ? "전체" : org);

        // 본문 데이터
        model.addAttribute("rows", kpi.fetchEvalSummary(org, year, op));

        // (기존 뷰에서 사용하므로 유지)
        model.addAttribute("year", year);
        model.addAttribute("orgName", org);
        model.addAttribute("currentYear", currentEvalYear);
        return "aff/admin/kpi/summary";
    }

    // kpi 직원
    @GetMapping("/admin/kpi/summary")
    public String kpiSummary(Authentication auth,
            @RequestParam String orgName, // ★ 그대로 유지(빈 문자열 허용)
            @RequestParam(required = false) Integer year, // ★ 그대로
            @RequestParam(required = false) String op,
            @RequestParam(required = false) String order,
            Model model) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/aff/login";
        }
        String org = (orgName == null ? "" : orgName.trim()); // '' = 전체

        // 1) 기본 연도: 파라미터 없으면
        int y;
        // 셀렉트박스용 기관 목록
        model.addAttribute("orgList", kpi.fetchOrgList(year));

        // 선택값 바인딩(뷰에서 th:selected로 사용)
        model.addAttribute("selectedOrgName", org);
        model.addAttribute("selectedYear", year);

        // 표 제목에 쓸 표시명
        model.addAttribute("orgNameForTitle", org.isEmpty() ? "전체" : org);

        // 본문 데이터
        model.addAttribute("selectedOrder", order);
        model.addAttribute("rows", kpi.fetchCombined(org, year, op, order));

        // (기존 뷰에서 사용하므로 유지)
        model.addAttribute("year", year);
        model.addAttribute("orgName", org);
        model.addAttribute("currentYear", currentEvalYear);

        return "aff/admin/kpi/summary2";
    }

    @GetMapping("/not-open")
    public String notOpen(@RequestParam(required = false, defaultValue = "${app.current.eval-year}") int year,
            @RequestParam(required = false) String ev, // 그대로 보존해서 돌아갈 때 사용 가능
            HttpServletRequest req,
            Model model) {

        model.addAttribute("year", year);
        model.addAttribute("ev", (ev == null || ev.isBlank()) ? null : ev);

        // 공개 예정일 (KST)
        var openAtOpt = releaseGateService.getOpenAtKst("user_result", year); // LocalDateTime (KST 기준)
        model.addAttribute("openAt", openAtOpt.orElse(null));
        // ✅ 한국어 표기: 2025년 11월 11일 오전 10시 58분
        model.addAttribute("openAtText",
                openAtOpt.map(dt -> dt.atZone(KST).format(FMT_KO)).orElse("미정"));

        // 카운트다운용 ISO는 그대로 유지
        model.addAttribute("openAtIso",
                openAtOpt.map(dt -> dt.atZone(KST).toOffsetDateTime().toString()).orElse(null));

        model.addAttribute("backUrl", safeBack(req, "/"));
        return "aff/not-open";
    }

    private String safeBack(HttpServletRequest req, String fallbackPath) {
        String ref = req.getHeader("Referer");
        if (ref == null || ref.isBlank())
            return fallbackPath;
        try {
            java.net.URI uri = java.net.URI.create(ref);

            // 외부 도메인 방지
            String host = uri.getHost();
            if (host != null && !host.equalsIgnoreCase(req.getServerName())) {
                return fallbackPath;
            }

            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String q = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            String target = path + q;

            // 자기 자신/현재 페이지로 돌아가면 루프 → 기본 경로
            if (target.startsWith("/aff/not-open"))
                return fallbackPath;

            // 결과 페이지로 다시 보내는 것도 무의미하니(또 막힘) 기본 경로 권장
            if (target.startsWith("/aff/user/report"))
                return fallbackPath;

            return target.isBlank() ? fallbackPath : target;
        } catch (Exception e) {
            return fallbackPath;
        }
    }

    @GetMapping("/admin/kpiGeneralUpload")
    public String kpiupload2(@RequestParam(defaultValue = "${app.current.eval-year}") int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        model.addAttribute("year", year);
        model.addAttribute("ev", ev);
        model.addAttribute("search", search);
        return "aff/admin/kpisubUpload";
    }

    // 직원 KPI 결과 저장
    @PostMapping("/admin/kpi/staff/save")
    @ResponseBody
    public Map<String, Object> saveStaffKpiResults2025(
            Authentication auth,
            @RequestParam int year,
            @RequestParam(required = false, defaultValue = "") String orgName,
            @RequestParam(required = false, defaultValue = "NEW") String mode) {

        if (auth == null || !auth.isAuthenticated()) {
            return Map.of("success", false, "message", "로그인이 필요합니다.");
        }
        String actor = auth.getName();
        int updated = kpi.saveStaffKpiResults2025(year, orgName, actor, mode);

        return Map.of(
                "success", true,
                "updated", updated,
                "message", year + "년 " + (orgName.isEmpty() ? "전체" : orgName)
                        + " 직원 " + updated + "건 KPI 결과 저장 완료");
    }

    // 직원 등급 산출
    @PostMapping("/admin/kpi/staff/grade")
    @ResponseBody
    public Map<String, Object> applyStaffGrades2025(Authentication auth,
            @RequestParam int year,
            @RequestParam(required = false, defaultValue = "") String orgName,
            @RequestParam(required = false, defaultValue = "NEW") String mode) {

        if (auth == null || !auth.isAuthenticated()) {
            return Map.of("success", false, "message", "로그인이 필요합니다.");
        }

        String actor = auth.getName();

        // 1) 점수 저장(업서트)
        int upserted = kpi.saveStaffKpiResults2025(year, orgName, actor, mode);

        // 2) 등급 산출(업데이트)
        int graded = kpi.applyStaffGrades2025(year, orgName);

        return Map.of(
                "success", true,
                "upserted", upserted,
                "graded", graded,
                "message", year + "년 " + (orgName.isEmpty() ? "전체" : orgName)
                        + " 직원 등급 산출 완료 (점수저장 " + upserted + "건, 등급반영 " + graded + "건)");
    }

    @RequestMapping("Logout")
    public String logout() {
        System.out.println("로그아웃 처리");
        SecurityContextHolder.clearContext(); // 스프링 시큐리티 컨텍스트 초기화
        return "redirect:/aff/login"; // 로그인 페이지로 리다이렉트
    }
}
