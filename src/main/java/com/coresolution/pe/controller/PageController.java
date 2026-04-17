package com.coresolution.pe.controller;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import com.coresolution.pe.controller.PageController.EvOption;
import com.coresolution.pe.entity.CombinedRow;
import com.coresolution.pe.entity.CommentReportDTO;
import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.DepartmentDto;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.FormPayload;
import com.coresolution.pe.entity.KpiCohortAvg;
import com.coresolution.pe.entity.KpiScoreSaveReq;
import com.coresolution.pe.entity.KpiSummaryRow;
import com.coresolution.pe.entity.KyTotal2025Row;
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
import com.coresolution.pe.entity.StaffGradeRow;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.TargetBuckets;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.EvalResultMapper;
import com.coresolution.pe.service.AdminProgressByOrgService;
import com.coresolution.pe.service.AdminTargetService;
import com.coresolution.pe.service.BankService;
import com.coresolution.pe.service.EvalReportService;
import com.coresolution.pe.service.EvalSummaryService;
import com.coresolution.pe.service.EvalSummaryService.SummaryVM;
import com.coresolution.pe.service.EvaluationFormService;
import com.coresolution.pe.service.EvaluationService;
import com.coresolution.pe.service.KpiService;
import com.coresolution.pe.service.KpiService.Num;
import com.coresolution.pe.service.MyPageEvalService;
import com.coresolution.pe.service.NoticeV2Service;
import com.coresolution.pe.service.NoticeV2Service.NoticeV2Req;
import com.coresolution.pe.service.PeService;
import com.coresolution.pe.service.ReleaseGateService;
import com.coresolution.pe.service.ReleaseWindowService;
import com.coresolution.pe.service.YearService;
import com.coresolution.pe.service.UserInfoPageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/")
public class PageController {
    @Autowired
    AuthenticationManager authManager;
    @Autowired
    private PeService pe;
    @Autowired
    private EvaluationService evaluationService;
    @Autowired
    private AdminTargetService adminService;
    @Autowired
    private EvalResultMapper evalResultMapper;
    @Autowired
    private UserInfoPageService userInfoPageService;
    @Autowired
    private EvaluationFormService evaluationFormService;
    @Autowired
    private PasswordEncoder passwordEncoder; // 추가
    @Autowired
    private MyPageEvalService myPageEvalService; // ← 서비스 주입
    @Autowired
    private EvalReportService reportService;
    @Autowired
    private EvalResultMapper mapper;
    @Autowired
    private EvalSummaryService summaryService;
    @Autowired
    private KpiService kpi;
    @Autowired
    private ObjectMapper objectMapper; // Spring Boot가 기본 빈으로 제공
    @Autowired
    private NoticeV2Service noticeservice;
    @Autowired
    private ReleaseGateService releaseGateService;
    @Autowired
    private AdminProgressByOrgService service;
    @Autowired
    private ReleaseWindowService releaseWindowService;
    @Autowired
    private YearService yearService;
    @Value("${app.exhibition.mask-personal:false}")
    private boolean exhibitionMaskPersonal;
    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    private static final Logger log = LoggerFactory.getLogger(PageController.class);
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
    public String login(@CookieValue(value = "savedId", defaultValue = "") String savedId,
            @CookieValue(value = "savedLoginType", defaultValue = "byName") String savedLoginType,
            @RequestParam(value = "error", required = false) String error, Model model) throws Exception {
        System.out.println("로그인 페이지");
        // URL 디코딩 (인코딩해서 저장했을 경우)
        model.addAttribute("savedId", URLDecoder.decode(savedId, "UTF-8"));
        model.addAttribute("savedLoginType", savedLoginType);
        if (error != null) {
            model.addAttribute("loginError", true);
        }
        List<NoticeVo> noticeList = pe.notice();
        model.addAttribute("notice", noticeList);
        System.out.println("공지사항: " + noticeList);
        model.addAttribute("noticeV2List", noticeservice.getActiveNotices(currentEvalYear));
        model.addAttribute("currentEvalYear", currentEvalYear);
        return "pe/login/login";
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
        return "pe/login/pwd";
    }

    @GetMapping("pwdSet/{idx}")
    public String redirectOldPwdSet() {
        return "redirect:/pe/pwdSet";
    }

    @GetMapping("pwdfind")
    public String pwdFind() {
        System.out.println("비밀번호 찾기 페이지");
        return "pe/login/findpwd";
    }

    @PostMapping("pwd/find-ajax")
    @ResponseBody
    public Map<String, Object> findAjax(@RequestParam String id, @RequestParam String ph, @RequestParam String year) {
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

    /** (GET) 비밀번호 설정/변경 페이지 */

    @GetMapping("pwdSet")
    public String pwdSettingForMe(Authentication auth, Model model) {
        if (auth == null || !auth.isAuthenticated()) {
            // 비로그인 접근 방지
            return "redirect:/pe/login";
        }

        String userId = auth.getName();
        String year = String.valueOf(currentEvalYear);

        UserPE userInfo = pe.findByUserIdWithNames(userId, year);
        model.addAttribute("userInfo", userInfo);

        // 화면에 보여줄 값(읽기전용)
        model.addAttribute("id", userInfo.getId());
        model.addAttribute("idx", userInfo.getIdx()); // 굳이 안 써도 되지만 유지해도 무방
        model.addAttribute("name", userInfo.getName());

        return "pe/user/pwdset";
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
        String year = String.valueOf(currentEvalYear);
        UserPE user = pe.findByUserIdWithNames(userId, year);
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
        response.put("redirectUrl", "/Info"); // 너희 마이페이지 URL에 맞춰 수정
        return ResponseEntity.ok(response);
    }

    @GetMapping("Info/{idx}")
    public String redirectOlduserInfo() {
        return "redirect:/Info";
    }

    @RequestMapping("Info")
    public String userInfo(Model model, Authentication auth, HttpServletRequest request) {
        if (auth == null || !auth.isAuthenticated())
            return "redirect:/pe/login";

        String userId = auth.getName();
        String year = String.valueOf(currentEvalYear);
        int y = currentEvalYear;

        UserPE userInfo = pe.findByUserIdWithNames(userId, year);
        model.addAttribute("userInfo", userInfo);
        model.addAttribute("maskPersonalInfo", exhibitionMaskPersonal);

        // model.addAttribute("releaseWindow", win);
        // model.addAttribute("isBeforeOpen", beforeOpen);
        // model.addAttribute("isAfterClose", afterClose);
        // model.addAttribute("daysUntilOpen", days);
        // model.addAttribute("daysSinceClose",
        // releaseWindowService.daysSinceClose(win));

        // model.addAttribute("openAtStr", (win != null && win.getOpenAt() != null) ?
        // win.getOpenAt().toString() : null);
        // model.addAttribute("closeAtStr",
        // (win != null && win.getCloseAt() != null) ? win.getCloseAt().toString() :
        // null);

        Map<String, List<UserPE>> grouped = evaluationService.getFinalTargetsGroupedByType(userId, year);

        // === 경혁팀: 전부 통합 (이미 적용했던 로직 그대로) ===
        List<UserPE> ghAll = java.util.stream.Stream.of(
                grouped.getOrDefault("GH_TO_GH", java.util.Collections.emptyList()),
                grouped.getOrDefault("MEDICAL_TO_GH", java.util.Collections.emptyList()),
                grouped.getOrDefault("GH", java.util.Collections.emptyList()))
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toList());

        List<UserPE> membersAll = java.util.stream.Stream.of(
                grouped.getOrDefault("SUB_HEAD_TO_MEMBER", java.util.Collections.emptyList()),
                grouped.getOrDefault("SUB_MEMBER_TO_MEMBER", java.util.Collections.emptyList()))
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toList());

        List<UserPE> medicalAll = java.util.stream.Stream.of(
                grouped.getOrDefault("GH_TO_MEDICAL", java.util.Collections.emptyList()),
                grouped.getOrDefault("MEDICAL_TO_MEDICAL", java.util.Collections.emptyList()),
                grouped.getOrDefault("MEDICAL", java.util.Collections.emptyList()))
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toList());

        // 그 외 섹션(필요 시 유지)
        List<UserPE> subHeads = grouped.getOrDefault("SUB_MEMBER_TO_HEAD", java.util.Collections.emptyList());

        // 모델 바인딩
        model.addAttribute("ghAll", ghAll); // ✅ 경혁팀 통합
        model.addAttribute("membersAll", membersAll); // ✅ 부서원 통합
        model.addAttribute("medicalAll", medicalAll); // ✅ 진료부 통합
        model.addAttribute("subHeads", subHeads);
        model.addAttribute("year", year);
        // 필요하면 원본도 함께
        model.addAttribute("typeLabels", evaluationService.getTypeLabels());
        // 최종 대상 + 메타 + 버킷 한번에
        List<UserPE> targets = evaluationService.getFinalTargets(userId, year);
        model.addAttribute("targets", targets);

        Map<String, Map<String, PairMeta>> metaMap = evaluationService.loadMetaMapByType(userId, year);

        model.addAttribute("groupedTargets", grouped);
        model.addAttribute("metaByType", metaMap);

        TargetBuckets buckets = evaluationService.computeBuckets(userId, year);
        // 진행률 등 부가 정보가 있으면 그대로
        Map<String, Progress> progressMap = userInfoPageService.buildProgressMap(userId, year, buckets);
        progressMap.forEach((k, v) -> {
            System.out.println("[PROGRESS] key=" + k
                    + ", answered=" + v.getAnswered()
                    + ", total=" + v.getTotal()
                    + ", completed=" + v.isCompleted());
        });
        model.addAttribute("progressMap", progressMap);
        return "pe/user/info";
    }

    // 헬퍼 (컨트롤러 안 private 메서드로 두면 편해요)
    private static boolean hasRole(UserPE u, String role) {
        return u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }

    /** 경혁팀 여부 */
    private boolean isGhTeam(UserPE u) {
        // 팀코드로 식별 + 역할 플래그 보조
        return "GH_TEAM".equalsIgnoreCase(u.getTeamCode())
                || hasRole(u, "team_head")
                || hasRole(u, "team_member");
    }

    private static boolean isMedicalDept(UserPE u) {
        // 진료부 판별: sub_code 가 A로 시작 (A00/A01/A02...)
        String sc = u.getSubCode();
        if (sc != null && sc.startsWith("A"))
            return true;
        // 보조: subName 에 "진료부"가 들어가면 진료부로 간주
        String sn = u.getSubName();
        return sn != null && sn.contains("진료부");
    }

    @GetMapping("myPage/{idx}")
    public String redirectOldmypage() {
        return "redirect:/pe/myPage";
    }

    @GetMapping("myPage")
    public String mypage(Model model, Authentication auth,
            @RequestParam(required = false) Integer year) {

        if (auth == null || !auth.isAuthenticated())
            return "redirect:/pe/login";

        String userId = auth.getName();
        int selectedYear = (year != null) ? year : currentEvalYear;
        String yearStr = String.valueOf(selectedYear);
        boolean isPastYear = selectedYear != currentEvalYear;

        // 사용자 정보
        UserPE userInfo = pe.findByUserIdWithNames(userId, yearStr);
        model.addAttribute("userInfo", userInfo);

        // 나를 평가한 제출 목록 + 관계별 집계
        var list = myPageEvalService.receivedAll(yearStr, userId);
        var agg = myPageEvalService.byRelationAgg(yearStr, userId);

        model.addAttribute("year", selectedYear);
        model.addAttribute("isPastYear", isPastYear);
        model.addAttribute("availableYears", yearService.getYears());
        model.addAttribute("list", list);
        model.addAttribute("agg", agg);

        var evPath = new java.util.LinkedHashMap<String, String>();
        evPath.put("A", "진료팀장 > 진료부");
        evPath.put("B", "진료부 > 경혁팀");
        evPath.put("C", "경혁팀 > 진료부");
        evPath.put("D", "경혁팀 > 경혁팀");
        evPath.put("E", "부서장 > 부서원");
        evPath.put("F", "부서원 > 부서장");
        evPath.put("G", "부서원 > 부서원");
        model.addAttribute("evPath", evPath);

        return "pe/user/mypage";
    }

    private static final Map<String, String> EV_PATH = Map.ofEntries(
            Map.entry("A", "진료팀장 > 진료부"),
            Map.entry("B", "진료부 > 경혁팀"),
            Map.entry("C", "경혁팀 > 진료부"),
            Map.entry("D", "경혁팀 > 경혁팀"),
            Map.entry("E", "부서장 > 부서원"),
            Map.entry("F", "부서원 > 부서장"),
            Map.entry("G", "부서원 > 부서원"),
            Map.entry("TOTAL", "종합평가")
    // Map.entry("KY_KY", "경혁팀 > 경혁팀"),
    // Map.entry("KY_STAFF", "부서원 > 부서장"),
    // Map.entry("KY_CLINIC", "진료부 > 경혁팀")
    );

    // 셀렉트 옵션용 DTO(내부 static class 간단 선언)
    public record EvOption(String value, String label) {
    }

    @GetMapping("report")
    public String resultPage(Model model,
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String ev,
            HttpServletRequest request,
            @RequestParam(name = "show", required = false, defaultValue = "0") int show) {

        // 0. 로그인 체크
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }
        String targetId = auth.getName();

        // 1. 기본 연도 결정
        Integer requested = year; // 사용자가 명시적으로 선택한 연도(Nullable)
        int y = (requested != null)
                ? requested
                : java.util.Optional.ofNullable(releaseGateService.latestOpenYear("user_result"))
                        .orElse(java.time.Year.now().getValue());

        // 2. 공개일 체크
        boolean openNow = releaseGateService.isOpenNow("user_result", y);

        // 2-1) 사용자가 year를 명시했는데 미공개 → not-open 페이지로 이동
        if (requested != null && !openNow) {
            String qs = new StringBuilder("?year=").append(requested)
                    .append(ev != null && !ev.isBlank()
                            ? "&ev=" + java.net.URLEncoder.encode(ev, java.nio.charset.StandardCharsets.UTF_8)
                            : "")
                    .toString();
            return "redirect:/pe/not-open" + qs;
        }

        // 2-2) year 파라미터 없이 들어왔고, 기본 연도 y가 미공개인 경우
        if (requested == null && !openNow) {
            Integer fb = releaseGateService.latestOpenYear("user_result");
            if (fb != null) {
                String qs = new StringBuilder("?year=").append(fb)
                        .append(ev != null && !ev.isBlank()
                                ? "&ev=" + java.net.URLEncoder.encode(ev, java.nio.charset.StandardCharsets.UTF_8)
                                : "")
                        .toString();
                return "redirect:/report" + qs;
            }
            return "redirect:/pe/not-open?year=" + y;
        }

        // 3. 사용자 기본 정보
        model.addAttribute("year", y);

        UserPE me = pe.findByUserIdWithNames(targetId, String.valueOf(y));
        String displayEmpId = exhibitionMaskPersonal ? maskEmployeeId(me.getId()) : me.getId();
        String displayEmpName = exhibitionMaskPersonal ? maskName(me.getName()) : me.getName();
        model.addAttribute("empId", displayEmpId);
        model.addAttribute("orgName", me.getCName());
        model.addAttribute("deptName", me.getSubName());
        model.addAttribute("position", me.getPosition());
        model.addAttribute("empName", displayEmpName);

        // 3-1. 경혁팀 여부
        boolean isKy = isKyunghyeokTeam(me);
        model.addAttribute("isKyTeam", isKy);

        // 4. EV 옵션 목록 구성 (DB에 실제 존재하는 data_ev 기준)
        var rawEvCodes = mapper.selectExistingDataEv(y, targetId);
        // 1) 경혁팀이면 data_ev를 가상코드로 정상화
        List<String> normalizedCodes;
        if (isKy) {
            normalizedCodes = rawEvCodes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .distinct() // 이제는 가상코드 기준으로 중복 제거
                    .toList();
        } else {
            // 일반 직원은 원래 코드 그대로 사용
            normalizedCodes = rawEvCodes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .distinct()
                    .toList();
        }
        // 2) 화면 옵션으로 변환
        var optionsFromDb = normalizedCodes.stream()
                .map(code -> new EvOption(code, EV_PATH.getOrDefault(code, code)))
                .toList();

        // TOTAL 맨 앞에 추가
        List<EvOption> options = new ArrayList<>();
        options.add(new EvOption("TOTAL", EV_PATH.get("TOTAL")));
        options.addAll(optionsFromDb);

        model.addAttribute("availableEvOptions", options);
        // 5. 선택된 EV 값/라벨
        String evNorm = (ev == null || ev.isBlank()) ? null : ev.trim().toUpperCase();
        String evLabel = (evNorm == null ? "" : EV_PATH.getOrDefault(evNorm, evNorm));
        model.addAttribute("selectedEvValue", evNorm);
        model.addAttribute("selectedEvLabel", evLabel);

        // ─────────────────────────────────────
        // 6. TOTAL 분기
        // ─────────────────────────────────────
        if ("TOTAL".equals(evNorm)) {

            java.util.function.ToDoubleFunction<java.math.BigDecimal> BD = x -> (x == null ? 0d : x.doubleValue());
            java.util.function.BiFunction<Double, Double, Double> toPct = (val, max) -> {
                double v = (val == null ? 0d : val);
                if (max == null || max <= 0)
                    return 0d;
                double pct = Math.max(0, Math.min(100, v / max * 100d));
                return Math.round(pct * 10.0) / 10.0;
            };

            int userId = Integer.parseInt(targetId);

            // 6-1. 경혁팀 TOTAL (경혁팀 + 2025년 이상은 KY 특수 로직)
            if (isKy) {
                String orgName = me.getCName();
                String teamName = "경혁팀";

                java.util.function.Function<BigDecimal, BigDecimal> nz = v -> v != null ? v : BigDecimal.ZERO;

                // (1) 상단 카드/레이더용 요약값 (I~V 소계)
                KpiSummaryRow my = kpi.selectMyKpiForTeam(orgName, teamName, y, userId);

                // (2) 2025년 이상: 상세표(kpi_personal_2025) + 다면 20점 합산
                KyTotal2025Row detail = null;
                if (y >= 2025) {
                    // kpi_personal_2025 기준 상세값 (I~IV + V 기존 구조)
                    detail = kpi.selectKyTotalDetail2025(y, targetId);

                    // evaluation_submissions 기반 KY 20점 (경혁10 + 부서5 + 진료5) 가져오기
                    KpiSummaryRow multi = kpi.selectKyMultiDetailFromEs(orgName, teamName, y, userId);

                    if (detail != null && multi != null) {
                        Double vExp = round2(d(multi.getVExperience())); // 0~10 → 10점
                        Double vBoss = round2(d(multi.getVBossToStaff())); // 0~5
                        Double vStaff = round2(d(multi.getVStaffToStaff())); // 0~5

                        detail.setMultiGh10(vExp != null ? BigDecimal.valueOf(vExp) : null);
                        detail.setMultiDept5(vBoss != null ? BigDecimal.valueOf(vBoss) : null);
                        detail.setMultiClinic5(vStaff != null ? BigDecimal.valueOf(vStaff) : null);

                        BigDecimal gh = nz.apply(detail.getMultiGh10());
                        BigDecimal dept = nz.apply(detail.getMultiDept5());
                        BigDecimal clinic = nz.apply(detail.getMultiClinic5());
                        BigDecimal multiTotal = gh.add(dept).add(clinic);
                        detail.setMultiTotal(multiTotal);

                        // I(40) + II(15) + III(15) + IV(10) + V(다면 20)
                        BigDecimal iSub = nz.apply(my != null ? my.getISubtotal() : null);
                        BigDecimal iiSub = nz.apply(my != null ? my.getIiSubtotal() : null);
                        BigDecimal iiiSub = nz.apply(my != null ? my.getIiiSubtotal() : null);
                        BigDecimal ivSub = nz.apply(my != null ? my.getIvSubtotal() : null);

                        BigDecimal kpiTotal = iSub.add(iiSub).add(iiiSub).add(ivSub);
                        BigDecimal total = kpiTotal.add(multiTotal).setScale(1, RoundingMode.HALF_UP);
                        detail.setTotalScore(total);
                    }
                }

                // (3) 모델 바인딩
                model.addAttribute("kyRow", my); // 상단 카드
                model.addAttribute("row", detail != null ? detail : my); // 하단 상세표
                model.addAttribute("kpi", my);
                model.addAttribute("isTotal", true);
                model.addAttribute("isKyTeam", true);
                model.addAttribute("showReport", true);

                // (4) 팀 평균 / 레이더
                var teamRows = kpi.selectKpiForTeam(orgName, teamName, y);
                int n = (teamRows == null ? 0 : teamRows.size());

                double iSub = my != null ? BD.applyAsDouble(my.getISubtotal()) : 0d;
                double iiSub = my != null ? BD.applyAsDouble(my.getIiSubtotal()) : 0d;
                double iiiSub = my != null ? BD.applyAsDouble(my.getIiiSubtotal()) : 0d;
                double ivSub = my != null ? BD.applyAsDouble(my.getIvSubtotal()) : 0d;

                // (5) 경혁팀 등급 계산 (KPI + 다면 합산 후 NTILE(100))
                String kyGrade = null;
                if (teamRows != null && !teamRows.isEmpty()) {
                    Map<String, BigDecimal> totalByEmp = new HashMap<>();
                    List<BigDecimal> totals = new ArrayList<>();

                    for (KpiSummaryRow r : teamRows) {
                        BigDecimal i = nvl(r.getISubtotal());
                        BigDecimal ii = nvl(r.getIiSubtotal());
                        BigDecimal iii = nvl(r.getIiiSubtotal());
                        BigDecimal iv = nvl(r.getIvSubtotal());
                        BigDecimal v;

                        if (y >= 2025
                                && "경혁팀".equals(teamName)
                                && String.valueOf(r.getEmpNo()).equals(targetId)
                                && detail != null) {
                            v = nvl(detail.getMultiTotal()); // 로그인한 본인: V = 다면 합산 20점
                        } else {
                            v = nvl(r.getVSubtotal()); // 나머지 구성원: 기존 V 소계
                        }

                        BigDecimal total = i.add(ii).add(iii).add(iv).add(v);
                        totalByEmp.put(String.valueOf(r.getEmpNo()), total);
                        totals.add(total);
                    }

                    totals.sort(Comparator.reverseOrder());

                    BigDecimal myTotal = totalByEmp.get(targetId);
                    if (myTotal != null) {
                        int rank = 1;
                        for (BigDecimal t : totals) {
                            if (t.compareTo(myTotal) == 0)
                                break;
                            rank++;
                        }
                        int memberCount = totals.size();
                        int p100 = ((rank - 1) * 100) / memberCount + 1;
                        kyGrade = toEvalGrade(p100);
                    }
                }
                if (kyGrade != null) {
                    model.addAttribute("kyGrade", kyGrade);
                }

                // (6) 레이더용 V축: 2025 경혁팀 TOTAL이면 다면 총점(20점) 사용
                double vSub;
                if (y >= 2025 && detail != null) {
                    vSub = BD.applyAsDouble(detail.getMultiTotal());
                } else if (my != null) {
                    vSub = BD.applyAsDouble(my.getVSubtotal());
                } else {
                    vSub = 0d;
                }

                double iAvg = 0, iiAvg = 0, iiiAvg = 0, ivAvg = 0, vAvg = 0;
                if (n > 0 && teamRows != null) {
                    for (var r : teamRows) {
                        iAvg += BD.applyAsDouble(r.getISubtotal());
                        iiAvg += BD.applyAsDouble(r.getIiSubtotal());
                        iiiAvg += BD.applyAsDouble(r.getIiiSubtotal());
                        ivAvg += BD.applyAsDouble(r.getIvSubtotal());
                        vAvg += BD.applyAsDouble(r.getVSubtotal());
                    }
                    iAvg /= n;
                    iiAvg /= n;
                    iiiAvg /= n;
                    ivAvg /= n;
                    vAvg /= n;
                }

                Map<String, Object> radar = new LinkedHashMap<>();
                radar.put("iPct", toPct.apply(iSub, 40d));
                radar.put("iiPct", toPct.apply(iiSub, 15d));
                radar.put("iiiPct", toPct.apply(iiiSub, 15d));
                radar.put("ivPct", toPct.apply(ivSub, 10d));
                radar.put("vPct", toPct.apply(vSub, 20d));
                radar.put("cohortIPct", toPct.apply(iAvg, 40d));
                radar.put("cohortIIPct", toPct.apply(iiAvg, 15d));
                radar.put("cohortIIIPct", toPct.apply(iiiAvg, 15d));
                radar.put("cohortIVPct", toPct.apply(ivAvg, 10d));
                radar.put("cohortVPct", toPct.apply(vAvg, 20d));
                radar.put("cohortName", teamName);
                radar.put("orgName", orgName);

                try {
                    model.addAttribute("renderRadar", true);
                    model.addAttribute("radarJson", objectMapper.writeValueAsString(radar));
                } catch (Exception ignore) {
                }

                CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
                if (csrf != null) {
                    model.addAttribute("_csrf", csrf);
                }

                return "pe/user/report";
            }

            // 6-2. 일반 직원 TOTAL (기존 로직)
            MyKpiRow row = kpi.fetchMyKpi(Integer.parseInt(targetId), y);

            model.addAttribute("row", row);
            model.addAttribute("kpi", row);
            model.addAttribute("isTotal", true);
            model.addAttribute("showReport", true);

            CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
            if (csrf != null) {
                model.addAttribute("_csrf", csrf);
            }

            return "pe/user/report";
        }

        // ─────────────────────────────────────
        // 7. TOTAL이 아닌 경우: 기존 통계 + VM
        // ─────────────────────────────────────
        if (evNorm != null) {

            boolean withAI = (show == 1);

            // 가상 코드(KY_*)를 DB 실제 코드(A~G)로 변환
            String evForQuery = evNorm;
            // if (isKy) {
            // switch (evNorm) {
            // case "KY_KY" -> evForQuery = "D"; // 경혁팀 > 경혁팀
            // case "KY_STAFF" -> evForQuery = "F"; // 부서원 > 경혁팀 (부서원>부서장 코드 재사용)
            // case "KY_CLINIC" -> evForQuery = "B"; // 진료부 > 경혁팀
            // default -> {
            // /* A~G, 기타는 그대로 */ }
            // }
            // }

            var agg = reportService.buildAggregate(
                    y,
                    targetId,
                    evForQuery, // DB 조회용 코드
                    evLabel, // 화면 표시용 라벨 (경혁팀 > 경혁팀, 부서원 > 경혁팀 등)
                    me.getSubCode(),
                    withAI);

            model.addAttribute("vm", agg.vm());
            if (agg.report() != null) {
                model.addAttribute("report", agg.report());
            }
            if (agg.scoreReport() != null) {
                model.addAttribute("scoreReport", agg.scoreReport());
            }
            if (agg.stats() != null) {
                model.addAttribute("stats", agg.stats());

                CommentReportDTO total = (agg.scoreReport() != null)
                        ? agg.scoreReport()
                        : agg.report();
                model.addAttribute("totalReport", total);

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
                payload.put("cohortName",
                        (selectedEvLabel != null ? selectedEvLabel : "") + " · " + me.getSubName());

                String objJson = "{}";
                try {
                    objJson = objectMapper.writeValueAsString(payload);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize payload", e);
                }
                model.addAttribute("objJson", objJson);
            }
        }

        // 8. 버튼 눌렀을 때만 리포트 영역 노출
        model.addAttribute("showReport", show == 1);

        // 9. CSRF
        CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
        if (csrf != null) {
            model.addAttribute("_csrf", csrf);
        }

        return "pe/user/report";
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String toEvalGrade(int p100) {
        if (p100 <= 4)
            return "A+";
        if (p100 <= 11)
            return "A";
        if (p100 <= 23)
            return "B+";
        if (p100 <= 40)
            return "B";
        if (p100 <= 60)
            return "C+";
        if (p100 <= 77)
            return "C";
        if (p100 <= 89)
            return "D+";
        if (p100 <= 96)
            return "D";
        return "E";
    }

    @PostMapping("/admin/kpi/saveScoreBulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveKpiScoreBulk(
            @RequestBody List<KpiScoreSaveReq> requestList,
            Authentication authentication) {

        // 로그인 정보에서 username 정도만 쓰고, 없으면 "system"
        String updatedBy = (authentication != null ? authentication.getName() : "system");

        int successCount = kpi.saveKpiTotalScoreBulk(requestList, updatedBy);

        Map<String, Object> body = new HashMap<>();
        body.put("result", "OK");
        body.put("successCount", successCount);
        body.put("totalCount", requestList.size());
        return ResponseEntity.ok(body);
    }

    @PostMapping("report/summary")
    public String regenerateSummary(
            Authentication auth,
            @RequestParam Integer year,
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

        // 2) 공개일 체크
        boolean openNow = releaseGateService.isOpenNow("user_result", y);

        // 2-1) 사용자가 year를 명시했는데 미공개면 → 무조건 not-open 으로
        if (requested != null && !openNow) {
            // ev 보존(있으면), 이전 페이지로 돌아가기 위한 backUrl도 옵션으로 붙일 수 있음
            String qs = new StringBuilder("?year=").append(requested)
                    .append(ev != null && !ev.isBlank()
                            ? "&ev=" + java.net.URLEncoder.encode(ev, java.nio.charset.StandardCharsets.UTF_8)
                            : "")
                    .toString();
            return "redirect:/pe/not-open" + qs;
        }
        // 2-2) 사용자가 year 파라미터 없이 들어왔고,
        // 서버가 정한 기본 y가 미공개면 → 공개된 최신 연도로 '소프트' 안내(있으면),
        // 없으면 not-open
        if (requested == null && !openNow) {
            Integer fb = releaseGateService.latestOpenYear("user_result");
            if (fb != null) {
                String qs = new StringBuilder("?year=").append(fb)
                        .append(ev != null && !ev.isBlank()
                                ? "&ev=" + java.net.URLEncoder.encode(ev, java.nio.charset.StandardCharsets.UTF_8)
                                : "")
                        .toString();
                return "redirect:/report" + qs;
            }
            return "redirect:/pe/not-open?year=" + y;
        }

        // TOTAL이 아니면: 기존 로직 그대로(AI 생성 허용)
        UserPE me = pe.findByUserIdWithNames(targetId, String.valueOf(y));
        // 공통: 리다이렉트 파라미터
        ra.addFlashAttribute("msg", "요약을 갱신했습니다.");
        ra.addAttribute("year", y);
        ra.addAttribute("ev", ev);
        ra.addAttribute("show", 1);

        // TOTAL이면: 마이KPI + 비교축 → 레이더 페이로드 + 종합 요약 생성
        if ("TOTAL".equals(evNorm)) {

            final int userIdInt = Integer.parseInt(targetId);
            final boolean kyTeam = isKyunghyeokTeam(me);
            final String orgName = me.getCName();
            final String relationLabel = kyTeam ? "경혁팀 종합평가" : "종합평가";

            // 공통: 리다이렉트 기본 파라미터
            ra.addAttribute("year", y);
            ra.addAttribute("ev", "TOTAL");
            ra.addAttribute("show", 1);

            // -----------------------------
            // 1) 2025년 경혁팀: 새 5축 레이더 + Ky 전용 AI 요약
            // -----------------------------
            if (kyTeam && y >= 2025) {
                final String teamName = "경혁팀";

                // 1-1) 내 5축 원점수(40/15/15/10/20) - 총괄표 쿼리 재사용
                KpiSummaryRow myKy = kpi.selectMyKpiForTeam(orgName, teamName, y, userIdInt);
                if (myKy == null) {
                    ra.addFlashAttribute("msg", "종합평가(경혁팀) 데이터를 찾을 수 없습니다.");
                    return "redirect:/report";
                }

                java.util.function.Function<java.math.BigDecimal, Double> toDouble = bd -> (bd == null ? 0d
                        : bd.doubleValue());

                double i40 = toDouble.apply(myKy.getISubtotal()); // 재무 40
                double ii15 = toDouble.apply(myKy.getIiSubtotal()); // 고객 15
                double iii15 = toDouble.apply(myKy.getIiiSubtotal()); // 프로세스 15
                double iv10 = toDouble.apply(myKy.getIvSubtotal()); // 학습 10
                double v20 = toDouble.apply(myKy.getVSubtotal()); // 다면 20

                // 1-2) 팀 평균(경혁팀 전체) – 총괄표에서 가져온 행 List 재사용
                var teamRows = kpi.selectKpiForTeam(orgName, teamName, y);
                int n = (teamRows == null ? 0 : teamRows.size());

                double i40avg = 0, ii15avg = 0, iii15avg = 0, iv10avg = 0, v20avg = 0;
                if (n > 0) {
                    for (var r : teamRows) {
                        i40avg += toDouble.apply(r.getISubtotal());
                        ii15avg += toDouble.apply(r.getIiSubtotal());
                        iii15avg += toDouble.apply(r.getIiiSubtotal());
                        iv10avg += toDouble.apply(r.getIvSubtotal());
                        v20avg += toDouble.apply(r.getVSubtotal());
                    }
                    i40avg /= n;
                    ii15avg /= n;
                    iii15avg /= n;
                    iv10avg /= n;
                    v20avg /= n;
                }

                java.util.function.DoubleBinaryOperator toPct = (val,
                        denom) -> Math.round((denom <= 0 ? 0d : (val / denom * 100d)) * 10d) / 10d;

                // 1-3) 레이더용 페이로드(5축 %)
                Map<String, Object> kyPayload = new java.util.LinkedHashMap<>();
                kyPayload.put("iPct", toPct.applyAsDouble(i40, 40d));
                kyPayload.put("iiPct", toPct.applyAsDouble(ii15, 15d));
                kyPayload.put("iiiPct", toPct.applyAsDouble(iii15, 15d));
                kyPayload.put("ivPct", toPct.applyAsDouble(iv10, 10d));
                kyPayload.put("vPct", toPct.applyAsDouble(v20, 20d));
                kyPayload.put("cohortIPct", toPct.applyAsDouble(i40avg, 40d));
                kyPayload.put("cohortIIPct", toPct.applyAsDouble(ii15avg, 15d));
                kyPayload.put("cohortIIIPct", toPct.applyAsDouble(iii15avg, 15d));
                kyPayload.put("cohortIVPct", toPct.applyAsDouble(iv10avg, 10d));
                kyPayload.put("cohortVPct", toPct.applyAsDouble(v20avg, 20d));
                kyPayload.put("cohortName", teamName);
                kyPayload.put("orgName", orgName);

                Map<String, Double> avg100ByAxis = new LinkedHashMap<>();
                avg100ByAxis.put("I 재무성과", toPct.applyAsDouble(i40avg, 40d));
                avg100ByAxis.put("II 고객서비스", toPct.applyAsDouble(ii15avg, 15d));
                avg100ByAxis.put("III 프로세스혁신", toPct.applyAsDouble(iii15avg, 15d));
                avg100ByAxis.put("IV 학습성장", toPct.applyAsDouble(iv10avg, 10d));
                avg100ByAxis.put("V 다면평가", toPct.applyAsDouble(v20avg, 20d));

                String radarJson = "{}";
                try {
                    radarJson = objectMapper.writeValueAsString(kyPayload);
                } catch (Exception ignore) {
                }

                ra.addFlashAttribute("renderRadar", true);
                ra.addFlashAttribute("radarJson", radarJson);
                ra.addFlashAttribute("relationLabel", "경혁팀 종합평가");

                // 1-4) AI 요약용 100점 스케일 + 총점
                Map<String, Double> kpi100 = new LinkedHashMap<>();
                kpi100.put("I 재무성과(40)→100", toPct.applyAsDouble(i40, 40d));
                kpi100.put("II 고객서비스(15)→100", toPct.applyAsDouble(ii15, 15d));
                kpi100.put("III 프로세스혁신(15)→100", toPct.applyAsDouble(iii15, 15d));
                kpi100.put("IV 학습성장(10)→100", toPct.applyAsDouble(iv10, 10d));
                kpi100.put("V 다면평가(20)→100", toPct.applyAsDouble(v20, 20d));

                double myKyTotal100 = i40 + ii15 + iii15 + iv10 + v20; // 내 총점(100)
                double sameKyTotal100 = i40avg + ii15avg + iii15avg + iv10avg + v20avg; // 팀 평균 총점(100)

                kpi100.put("_TOTAL_my", myKyTotal100);
                kpi100.put("_TOTAL_same", sameKyTotal100);

                // 팀 기준 비교키 사용
                String compareKeyForService = me.getTeamCode(); // 경혁팀 전체 비교

                CommentReportDTO totalReport = reportService.buildKyTotalSummary(
                        y,
                        targetId,
                        relationLabel, // "경혁팀 종합평가"
                        compareKeyForService, // 팀코드
                        i40, ii15, iii15, iv10, v20, // 5축 원점수(합=100)
                        kpi100,
                        avg100ByAxis,
                        "1".equals(request.getParameter("run")));

                ra.addFlashAttribute("totalReport", totalReport);
                return "redirect:/report";
            }

            // -----------------------------
            // 2) 그 외(비-경혁팀 or 2024이하): 기존 4축 로직
            // -----------------------------
            MyKpiRow row = kpi.fetchMyKpi(userIdInt, y);
            if (row == null) {
                ra.addFlashAttribute("msg", "종합평가 데이터를 찾을 수 없습니다.");
                return "redirect:/report";
            }

            /*
             * =========================
             * KPI: 원본 배점(20/15/15) → 표시/계산 배점(10/10/10) 환산
             * =========================
             */
            double i20 = row.getKpiICalc() == null ? 0d : row.getKpiICalc().doubleValue(); // 기존 I (20점 만점)
            double ii15 = row.getKpiIICalc() == null ? 0d : row.getKpiIICalc().doubleValue(); // 기존 II (15점 만점)
            double iii15 = row.getKpiIIICalc() == null ? 0d : row.getKpiIIICalc().doubleValue(); // 기존 III (15점 만점)

            // ✅ 10/10/10으로 환산
            double i10 = (i20 / 20d) * 10d;
            double ii10 = (ii15 / 15d) * 10d;
            double iii10 = (iii15 / 15d) * 10d;

            /*
             * =========================
             * 다면평가: 70점 만점(가중합) 유지
             * =========================
             */
            double v70 = (row.getEvalSum70() != null)
                    ? row.getEvalSum70().doubleValue()
                    : (row.getEvalSum20() != null ? row.getEvalSum20().doubleValue() * 3.5 : 0d);

            /*
             * =========================
             * 레이더/AI 입력용: 100점 스케일 값 산출
             * (KPI는 10점 만점 기준으로 100 환산)
             * =========================
             */
            double promo100 = (i10 / 10d) * 100d;
            double vol100 = (ii10 / 10d) * 100d;
            double edu100 = (iii10 / 10d) * 100d;
            double multi100 = (v70 <= 0) ? 0d : (v70 / 70d) * 100d;

            java.util.function.DoubleUnaryOperator clampPct1 = x -> Math.round(Math.max(0, Math.min(100, x)) * 10d)
                    / 10d;

            String orgName2 = me.getCName();

            // 조직 평균(4축, 100스케일) - ✅ 이 쿼리도 같은 기준(10/10/10 + 70)으로 맞춰져 있어야 함
            KpiCohortAvg avg = kpi.selectOrgRadarAvg(y, orgName2);
            double cohortPromo100 = (avg != null && avg.promoAvg100() != null) ? avg.promoAvg100() : 0d;
            double cohortVol100 = (avg != null && avg.volAvg100() != null) ? avg.volAvg100() : 0d;
            double cohortEdu100 = (avg != null && avg.eduAvg100() != null) ? avg.eduAvg100() : 0d;
            double cohortMulti100 = (avg != null && avg.multiAvg100() != null) ? avg.multiAvg100() : 0d;

            // 레이더 payload
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("promoPct", clampPct1.applyAsDouble(promo100));
            payload.put("volunteerPct", clampPct1.applyAsDouble(vol100));
            payload.put("educationPct", clampPct1.applyAsDouble(edu100));
            payload.put("multiPct", clampPct1.applyAsDouble(multi100));
            payload.put("extraPct", 85d);

            payload.put("cohortPromoPct", clampPct1.applyAsDouble(cohortPromo100));
            payload.put("cohortVolunteerPct", clampPct1.applyAsDouble(cohortVol100));
            payload.put("cohortEducationPct", clampPct1.applyAsDouble(cohortEdu100));
            payload.put("cohortMultiPct", clampPct1.applyAsDouble(cohortMulti100));
            payload.put("orgName", orgName2);
            payload.put("cohortExtraPct", 85d);

            Map<String, Object> raw = new java.util.LinkedHashMap<>();
            raw.put("I10", i10);
            raw.put("II10", ii10);
            raw.put("III10", iii10);
            raw.put("V70", v70);
            payload.put("raw", raw);

            String radarJson = "{}";
            try {
                radarJson = objectMapper.writeValueAsString(payload);
            } catch (Exception ignore) {
            }

            ra.addFlashAttribute("renderRadar", true);
            ra.addFlashAttribute("radarJson", radarJson);

            /*
             * =========================
             * ✅ 총점(100) = KPI(10+10+10) + 다면평가(70)
             * =========================
             */
            double myTotal100 = Math.round((i10 + ii10 + iii10 + v70) * 10.0) / 10.0;

            // 동일구간 평균 총점도 동일 기준으로
            double cohortI10 = cohortPromo100 / 10.0; // 100 → 10
            double cohortII10 = cohortVol100 / 10.0; // 100 → 10
            double cohortIII10 = cohortEdu100 / 10.0; // 100 → 10
            double cohortV70 = cohortMulti100 * 0.70; // 100 → 70
            double sameTotal100 = Math.round((cohortI10 + cohortII10 + cohortIII10 + cohortV70) * 10.0) / 10.0;

            // 화면에서 row.totalCalc100 대신 쓰도록 넘김 (총점 표시 교체용)
            ra.addFlashAttribute("totalCalc100", myTotal100);

            Map<String, Double> kpi100 = new LinkedHashMap<>();
            kpi100.put("홍보공헌(100)", promo100);
            kpi100.put("자원봉사(100)", vol100);
            kpi100.put("교육이수(100)", edu100);
            kpi100.put("다면평가(100)", multi100);
            kpi100.put("임의데이터(100)", 85d);
            kpi100.put("_TOTAL_my", myTotal100);
            kpi100.put("_TOTAL_same", sameTotal100);

            String compareKeyForService = kyTeam ? me.getTeamCode() : me.getSubCode();
            StaffGradeRow g = kpi.selectMyStaffGrade(y, targetId);
            ra.addFlashAttribute("myGrade", g); // 또는 ra.addFlashAttribute(...)
            CommentReportDTO totalReport = reportService.buildTotalSummary(
                    y,
                    targetId,
                    relationLabel,
                    compareKeyForService,
                    promo100, vol100, edu100, multi100,
                    kpi100,
                    /* withAI= */ "1".equals(request.getParameter("run")));

            ra.addFlashAttribute("totalReport", totalReport);
            return "redirect:/report";
        }
        reportService.buildAggregate(
                y, targetId, evNorm,
                EV_PATH.getOrDefault(evNorm, evNorm),
                me.getSubCode(),
                /* withAI */ true);

        return "redirect:/report";
    }

    private static Double d(Object v) {
        if (v == null)
            return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "-".equals(s))
            return null;
        s = s.replace(",", "")
                .replace("%", "")
                .replace("▲", "")
                .replace("▼", "")
                .replaceAll("[^0-9.\\-]", "");
        try {
            return Double.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double round2(Double x) {
        return x == null ? null : Math.round(x * 100.0) / 100.0;
    }

    private static BigDecimal bd(Double x) {
        return x == null ? null : BigDecimal.valueOf(x);
    }

    private static double toDouble(BigDecimal v) {
        return (v == null) ? 0d : v.doubleValue();
    }

    private static BigDecimal nz(BigDecimal v) {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    // 다면평가 총괄표
    @GetMapping("admin/reportsummary")
    public String summaryPage(Model model, Authentication auth,
            @RequestParam(defaultValue = "${app.current.eval-year}") Integer year,
            @RequestParam(required = false, name = "orgName") String orgName) {
        if (auth == null || !auth.isAuthenticated())
            return "redirect:/pe/login";

        int selectedYear = (year != null) ? year : LocalDate.now().getYear();
        // 드롭다운 옵션 로딩

        // EV 라벨(예: A~G → “진료팀장 > 진료부” 등)
        Map<String, String> evPath = Map.of(
                "A", "진료팀장 > 진료부",
                "B", "진료부 > 경혁팀",
                "C", "경혁팀 > 진료부",
                "D", "경혁팀 > 경혁팀",
                "E", "부서장 > 부서원",
                "F", "부서원 > 부서장",
                "G", "부서원 > 부서원");

        var vm = summaryService.buildSummary(selectedYear, orgName, evPath);

        // 기관 선택지
        var orgList = mapper.selectAllOrgNames(selectedYear);

        model.addAttribute("vm", vm);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("selectedOrgName", orgName);
        model.addAttribute("orgList", orgList);
        model.addAttribute("currentYear", currentEvalYear);

        return "pe/admin/reportsummary";
    }

    // kpi 직원
    @GetMapping("/admin/kpi/summary")
    public String kpiSummary(Authentication auth,
            @RequestParam String orgName, // ★ 그대로 유지(빈 문자열 허용)
            @RequestParam(required = false) Integer year, // ★ 그대로
            @RequestParam(required = false) String op,
            Model model) {

        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/pe/login";
        }
        String org = (orgName == null ? "" : orgName.trim()); // '' = 전체

        // 1) 기본 연도: 파라미터 없으면
        int y;
        if (year == null) {
            // (A) 게이트에서 ‘최근 공개 연도’가 있으면 그걸 기본값으로
            Integer openYear = releaseGateService.latestOpenYear("user_result");
            if (openYear != null) {
                y = openYear; // 예: 2024가 공개 상태면 2024로 기본 진입
            } else {
                y = java.time.Year.now().getValue(); // 공개된 게 하나도 없으면 현재연도(또는 정책상 2025)
            }
        } else {
            y = year;
        }

        // 2) 선택된 연도가 공개 전이면 → 공개된 최신 연도로 자동 폴백(소프트 리디렉트)
        boolean openNow = releaseGateService.isOpenNow("user_result", y);
        if (!openNow) {
            Integer fallback = releaseGateService.latestOpenYear("user_result");
            if (fallback != null && fallback != y) {
                return "redirect:/pe/user/report?year=" + fallback; // 예: 2025 미공개 → 2024로 안내
            }
            // 공개된 연도가 하나도 없다면 기존 정책대로 차단/안내 처리
            return "redirect:/pe/not-open?year=" + y;
        }
        // 셀렉트박스용 기관 목록
        model.addAttribute("orgList", kpi.fetchOrgList(y));

        // 선택값 바인딩(뷰에서 th:selected로 사용)
        model.addAttribute("selectedOrgName", org);
        model.addAttribute("selectedYear", y);

        // 표 제목에 쓸 표시명
        model.addAttribute("orgNameForTitle", org.isEmpty() ? "전체" : org);

        // 본문 데이터
        model.addAttribute("rows", kpi.fetchCombined(org, y, op));

        // (기존 뷰에서 사용하므로 유지)
        model.addAttribute("year", y);
        model.addAttribute("orgName", org);
        model.addAttribute("currentYear", currentEvalYear);

        return "pe/admin/kpi/summary";
    }

    // kpi 진료부
    @GetMapping("/admin/kpi/summary3")
    public String kpiSummaryMedical(Authentication auth,
            @RequestParam String orgName, // ★ 그대로 유지(빈 문자열 허용)
            @RequestParam(required = false) Integer year, // ★ 그대로
            Model model) {
        List<CombinedRow> rows = kpi.selectCombinedByOrgYear2025Medical(orgName, year);
        // org 리스트 (드롭다운)
        List<String> orgList = kpi.fetchOrgList(year);
        model.addAttribute("orgNameForTitle", orgName);
        model.addAttribute("selectedOrgName", orgName); // ← 폼에서 선택 유지
        model.addAttribute("orgList", orgList);
        model.addAttribute("rows", rows);
        model.addAttribute("selectedYear", year);
        model.addAttribute("currentYear", currentEvalYear);
        return "pe/admin/kpi/summary3";
    }

    // kpi 경혁
    @GetMapping("/admin/kpi/summary2")
    public String kpiSummary2(
            @RequestParam String orgName,
            @RequestParam(defaultValue = "경혁팀") String teamName, // 기본값
            @RequestParam Integer year,
            Model model) {

        List<KpiSummaryRow> rows = kpi.selectKpiForTeam(orgName, teamName, year);
        // List<KpiSummaryRow> rows = kpi.buildRows(raw);

        // org 리스트 (드롭다운)
        List<String> orgList = kpi.fetchOrgList(year);
        model.addAttribute("rows", rows);
        model.addAttribute("selectedYear", year);
        model.addAttribute("orgNameForTitle", orgName);
        model.addAttribute("orgList", orgList);
        model.addAttribute("selectedOrgName", orgName); // ← 폼에서 선택 유지
        model.addAttribute("currentYear", currentEvalYear);
        return "pe/admin/kpi/summary2";
    }

    // My KPI (직원 본인)
    @GetMapping("/my/kpi")
    public String myKpi(
            @RequestParam(required = false) Integer year,
            Model model,
            HttpServletRequest request) {

        // 1) 연도 기본값
        int y = (year == null ? Year.now().getValue() : year);

        // 2) 로그인 사용자 ID 가져오기 (둘 중 하나 사용)
        // 2-1) Spring Security 사용자
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userIdStr = null;
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            userIdStr = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        }
        // 2-2) 세션에 id 저장해둔 경우
        if (userIdStr == null) {
            Object sid = request.getSession().getAttribute("id");
            if (sid != null)
                userIdStr = String.valueOf(sid);
        }
        if (userIdStr == null)
            throw new IllegalStateException("로그인 사용자를 확인할 수 없습니다.");
        int userId = Integer.parseInt(userIdStr);

        // 3) 본인 KPI 1행 로딩
        MyKpiRow row = kpi.fetchMyKpi(userId, y);

        // 4) 화면 바인딩
        model.addAttribute("row", row);
        model.addAttribute("kpi", row);
        model.addAttribute("selectedYear", y);

        // 상단 셀렉트박스용: 내 소속 기관/연도
        model.addAttribute("orgName", row != null ? row.getOrgName() : "-");

        return "pe/user/mypage-kpi";
    }

    @GetMapping("user/form/start")
    public String userForm(Model model, Authentication auth, @RequestParam("targetId") String targetId,
            @RequestParam("type") String dataType, @RequestParam("ev") String dataEv,
            @RequestParam(value = "year", required = false, defaultValue = "${app.current.eval-year}") String year,
            org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        System.out.println(" 페이지");

        year = String.valueOf(currentEvalYear);
        // 사용자 정보 수정 페이지로 이동
        // 1) 로그인한 사용자 ID
        String evaluatorId = auth.getName();
        // ⬇⬇⬇ 헤더/레이아웃에서 쓰는 userInfo를 반드시 채워줍니다.
        UserPE userInfo = pe.findByUserIdWithNames(evaluatorId, year); // 서비스/매퍼 맞게 호출
        model.addAttribute("userInfo", userInfo);
        model.addAttribute("maskPersonalInfo", exhibitionMaskPersonal);

        final int y = currentEvalYear;
        // ✅ dataEv 없이 “엄격 차단”
        var gate = releaseWindowService.checkStrictForUser(y, userInfo.getCName(), userInfo.getSubCode());

        if (gate.status == ReleaseWindowService.GateStatus.BEFORE_OPEN) {
            // 오픈 전이면 접근 차단(원하면 기존처럼 모달만 띄우고 페이지는 보여주는 방식도 가능)
            return "redirect:/pe/gate-closed?year=" + year + "&reason=before_open";
        }
        if (gate.status == ReleaseWindowService.GateStatus.AFTER_CLOSE) {
            // 마감 후 차단
            return "redirect:/pe/gate-closed?year=" + year + "&reason=after_close";
        }

        // 안내용(옵션)
        model.addAttribute("openAtStr", gate.openAt != null ? gate.openAt.toString() : null);
        model.addAttribute("closeAtStr", gate.closeAt != null ? gate.closeAt.toString() : null);
        try {
            FormPayload payload = evaluationFormService.prepare(evaluatorId, targetId, year,
                    dataType,
                    dataEv);

            // 뷰에 바인딩
            model.addAttribute("year", year);
            model.addAttribute("dataType", dataType);
            model.addAttribute("dataEv", dataEv);
            model.addAttribute("evaluatorId", evaluatorId);

            model.addAttribute("target", payload.getTarget()); // UserPE
            model.addAttribute("questions", payload.getQuestions()); // List<Evaluation>
            model.addAttribute("answerMap", payload.getAnswerMap()); // qIdx -> EvalResult
            model.addAttribute("answered", payload.getAnswered());
            model.addAttribute("total", payload.getTotal());
            model.addAttribute("completed", payload.isCompleted());
            model.addAttribute("avgScore", payload.getAvgScore()); // nullable(Double)

            return "pe/user/form"; // 타임리프 템플릿
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
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") String year) {

        String evaluatorId = auth.getName();

        // 공통 헤더용 사용자
        UserPE userInfo = pe.findByUserIdWithNames(evaluatorId, year);
        model.addAttribute("userInfo", userInfo);
        model.addAttribute("maskPersonalInfo", exhibitionMaskPersonal);
        // try {
        //     ensureOpenOrThrow(year, dataType, dataEv, auth.getName());
        // } catch (org.springframework.security.access.AccessDeniedException ex) {
        //     return "redirect:/Info?blocked=1&message=" + enc(ex.getMessage());
        // }
        // 페이로드 구성(질문 + 제출 + 답변JSON)
        FormPayload payload = evaluationFormService.prepareForEdit(evaluatorId, targetId, year, dataType, dataEv);

        final int y = Integer.parseInt(year);

        // ✅ dataEv/dataType 포함한 “엄격 + 예외 허용” 체크로 변경
        var gate = releaseWindowService.checkStrictWithExceptionForUser(
                y, userInfo.getCName(), userInfo.getSubCode(), dataType, dataEv);

        if (gate.status == ReleaseWindowService.GateStatus.BEFORE_OPEN) {
            return "redirect:/pe/gate-closed?year=" + year + "&reason=before_open";
        }
        if (gate.status == ReleaseWindowService.GateStatus.AFTER_CLOSE) {
            return "redirect:/pe/gate-closed?year=" + year + "&reason=after_close";
        }

        model.addAttribute("year", year);
        model.addAttribute("dataType", dataType);
        model.addAttribute("dataEv", dataEv);
        model.addAttribute("evaluatorId", evaluatorId);

        model.addAttribute("target", payload.getTarget());
        model.addAttribute("questions", payload.getQuestions());

        model.addAttribute("answered", payload.getAnswered());
        model.addAttribute("totalScore", payload.getTotalScore());
        model.addAttribute("avgScore", payload.getAvgScore());

        // ★ 뷰에서 JS로 주입
        model.addAttribute("answersBundleJson", payload.getAnswersBundleJson());
        model.addAttribute("seq", new java.util.concurrent.atomic.AtomicInteger(0));

        // ★ 입력 name 매핑(예: 섬33, 배36, t43 …) ← 이게 빠져있어서 NPE/EL1012 발생
        model.addAttribute("nameMap", payload.getNameMap());

        // ★ 입력 name 매핑(예: 섬33, 배36, t43 …)
        System.out.println("nameMap: " + payload.getNameMap());
        System.out.println("answersBundleJson: " + payload.getAnswersBundleJson());
        return "pe/user/edit";
    }

    /** (버튼용) 기본 대상 테이블 초기화 및 재생성 **/
    @PostMapping("admin/target/initDefaults")
    public String initDefaults(@RequestParam(value = "year", defaultValue = "${app.current.eval-year}") String year,
            RedirectAttributes ra) {
        // 1) 기본 대상 테이블 재구성 (부서/경혁/진료부 등 "기본"만)
        int upserts = evaluationService.rebuildAdminDefaultTargets(year);

        ra.addFlashAttribute("message", "기본 대상이 초기화되었습니다. (" + upserts + "건)");
        return "redirect:/pe/admin/target?year=" + year;
    }

    @GetMapping("admin/userInfo/{idx}")
    public String adminUserInfo(
            @PathVariable int idx,
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") String year,
            Model model) {

        // 직원 기본 정보
        UserPE user = pe.findUserInfoByIdx(idx);
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        String employeeId = user.getId();

        UserPE userInfo = pe.findByUserIdWithNames(employeeId, year);
        if (exhibitionMaskPersonal && userInfo != null) {
            userInfo = toMaskedUser(userInfo);
        }
        model.addAttribute("userInfo", userInfo);
        model.addAttribute("year", year);
        model.addAttribute("maskPersonalInfo", exhibitionMaskPersonal);

        // ✅ 직원 마이페이지와 동일한 "최종 대상" 집계
        Map<String, List<UserPE>> grouped = evaluationService.getFinalTargetsGroupedByType(employeeId, year);

        List<UserPE> ghAll = java.util.stream.Stream.of(
                grouped.getOrDefault("GH_TO_GH", java.util.Collections.emptyList()),
                grouped.getOrDefault("MEDICAL_TO_GH", java.util.Collections.emptyList()),
                grouped.getOrDefault("GH", java.util.Collections.emptyList()) // 레거시 대비
        )
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toList()); // ✅ 중복 제거 없음

        List<UserPE> membersAll = java.util.stream.Stream.of(
                grouped.getOrDefault("SUB_HEAD_TO_MEMBER", java.util.Collections.emptyList()),
                grouped.getOrDefault("SUB_MEMBER_TO_MEMBER", java.util.Collections.emptyList()))
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toList()); // ✅ 중복 제거 없음

        List<UserPE> medicalAll = java.util.stream.Stream.of(
                grouped.getOrDefault("GH_TO_MEDICAL", java.util.Collections.emptyList()),
                grouped.getOrDefault("MEDICAL_TO_MEDICAL", java.util.Collections.emptyList()),
                grouped.getOrDefault("MEDICAL", java.util.Collections.emptyList()) // 레거시 대비
        )
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toList()); // ✅ 중복 제거 없음

        List<UserPE> subHeads = grouped.getOrDefault("SUB_MEMBER_TO_HEAD", java.util.Collections.emptyList());

        // 화면에 바인딩 (직원화면과 동일)
        model.addAttribute("ghAll", ghAll);
        model.addAttribute("membersAll", membersAll);
        model.addAttribute("medicalAll", medicalAll);
        model.addAttribute("subHeads", subHeads);
        model.addAttribute("typeLabels", evaluationService.getTypeLabels());

        // 🔧 커스텀 관리(삭제용) 섹션은 기존대로 유지
        SplitTargets split = evaluationService.splitTargets(employeeId, year);
        model.addAttribute("customGrouped", split.getCustomGrouped()); // 커스텀 추가만

        return "pe/admin/userInfo";
    }

    private UserPE toMaskedUser(UserPE src) {
        UserPE masked = new UserPE();
        masked.setIdx(src.getIdx());
        masked.setId(maskEmployeeId(src.getId()));
        masked.setName(maskName(src.getName()));
        masked.setSubName(src.getSubName());
        masked.setTeamName(src.getTeamName());
        masked.setPosition(src.getPosition());
        masked.setPhone(maskPhone(src.getPhone()));
        return masked;
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        if (name.length() <= 1) {
            return "*";
        }
        if (name.length() == 2) {
            return name.substring(0, 1) + "*";
        }
        return name.substring(0, 1) + "*".repeat(name.length() - 2) + name.substring(name.length() - 1);
    }

    private String maskEmployeeId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        if (id.length() <= 2) {
            return "**";
        }
        return id.substring(0, 2) + "*".repeat(id.length() - 2);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    @PostMapping("admin/userInfo/{idx}/custom/remove")
    public String removeCustom(
            @PathVariable int idx,
            @RequestParam(defaultValue = "${app.current.eval-year}") String year,
            @RequestParam String targetId,
            @RequestParam(required = false, defaultValue = "관리자 수동 삭제") String reason,
            RedirectAttributes ra) {
        adminService.removeCustomAddByIdx(idx, year, targetId, reason);
        ra.addFlashAttribute("message", "커스텀 평가 대상이 삭제(비활성화)되었습니다.");
        return "redirect:/admin/userInfo/" + idx + "?year=" + year;
    }

    /** ③ 부서/팀 기본 대상 저장 핸들러 **/
    @PostMapping("/saveDefault")
    public String saveDefaults(
            @RequestParam String employeeId,
            @RequestParam List<String> deptTargets,
            @RequestParam List<String> teamTargets,
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") String year) {
        evaluationService.saveDefaultDeptTargets(year, deptTargets);
        evaluationService.saveDefaultTeamTargets(year, teamTargets);
        return "redirect:/pe/admin/target/" + employeeId + "?year=" + year;
    }

    @RequestMapping("admin/admin")
    public String admin(Model model) {
        System.out.println("관리자 페이지");
        // 관리자 페이지로 이동
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            System.out.println("admin - 현재 인증된 사용자: " + authentication.getName());
            System.out.println("admin - 현재 사용자 권한: " + authentication.getAuthorities());
            // 이곳에 ROLE_ADMIN이 출력되어야 합니다!
        } else {
            System.out.println("admin - SecurityContextHolder에 인증 정보가 없거나 인증되지 않았습니다. (403 원인)");
        }
        return "pe/admin/admin";
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
        return "pe/admin/notice";
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
    public ResponseEntity<?> noticecreate(@Valid @RequestBody NoticeV2Service.NoticeV2Req req) {
        NoticeV2 created = noticeservice.create(req);
        return ResponseEntity.created(URI.create("/admin/notices/" + created.getId())).body(created);
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

    // @RequestMapping("admin/userList")
    // public String adminPage(Model model,
    // @RequestParam(value = "year", required = false, defaultValue = "${app.current.eval-year}") String
    // year) {
    // System.out.println("관리자 페이지");
    // // 관리자 페이지로 이동
    // Authentication authentication =
    // SecurityContextHolder.getContext().getAuthentication();

    // if (authentication != null && authentication.isAuthenticated()) {
    // System.out.println("adminPage - 현재 인증된 사용자: " + authentication.getName());
    // System.out.println("adminPage - 현재 사용자 권한: " +
    // authentication.getAuthorities());
    // // 이곳에 ROLE_ADMIN이 출력되어야 합니다!
    // } else {
    // System.out.println("adminPage - SecurityContextHolder에 인증 정보가 없거나 인증되지 않았습니다.
    // (403 원인)");
    // }

    // List<NoticeVo> noticeList = pe.notice();
    // model.addAttribute("notice", noticeList);
    // System.out.println("공지사항: " + noticeList);

    // List<UserPE> userList = pe.getUserList(year);
    // model.addAttribute("userList", userList);
    // System.out.println("사용자 목록: " + userList);
    // model.addAttribute("year", year);
    // return "pe/admin/userList";
    // }
    @RequestMapping(value = "admin/userList", method = RequestMethod.GET)
    public String adminUserList(
            Model model,
            @RequestParam(value = "year",   required = false, defaultValue = "${app.current.eval-year}") String year,
            @RequestParam(value = "q",      required = false) String q,
            @RequestParam(value = "dept",   required = false) String deptCode,
            @RequestParam(value = "pwd",    required = false) String pwd,    // "set" | "unset" | null
            @RequestParam(value = "org",    required = false) String org,
            @RequestParam(value = "delYn",  required = false) String delYn,  // "Y" | "N" | null(전체)
            @RequestParam(value = "role",   required = false) String role,   // 역할 코드 | null(전체)
            @RequestParam(value = "page",   required = false, defaultValue = "1")  int page,
            @RequestParam(value = "size",   required = false, defaultValue = "50") int size) {

        // 가드
        if (year == null || !year.matches("\\d{4}")) year = String.valueOf(currentEvalYear);
        if (page < 1) page = 1;
        if (size < 1 || size > 500) size = 50;
        int offset = (page - 1) * size;

        // 공지
        model.addAttribute("notice", pe.notice());

        // 목록 & 카운트
        List<UserPE> userList = pe.getUserListpage(year, q, deptCode, pwd, org, delYn, role, offset, size);
        int totalCount        = pe.countUsers(year, q, deptCode, pwd, org, delYn, role);
        int totalPages        = (int) Math.ceil(totalCount / (double) size);
        if (page > Math.max(totalPages, 1)) {
            page   = Math.max(totalPages, 1);
            offset = (page - 1) * size;
            userList = pe.getUserListpage(year, q, deptCode, pwd, org, delYn, role, offset, size);
        }

        // 셀렉트용 데이터
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

        return "pe/admin/userList";
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

        return "pe/admin/subManagement";
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
        return "pe/admin/userDataUpload";
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

        // // 1) 부서별 DTO
        // List<DepartmentDto> departments = evaluationService.getDepartments(year);
        // (가벼운) 부서/직원 선택용만 내려주기
        // List<DepartmentDto> departmentsLite =
        // evaluationService.getDepartmentsLite(year);
        // 2) 전체 직원
        List<UserPE> userList = pe.getUserList(year);
        // 3) 부서 기본 대상 맵: evaluatorId → List<UserPE>
        // Map<String, List<UserPE>> defaultTargetsMap = new HashMap<>();
        // for (UserPE u : userList) {
        // defaultTargetsMap.put(
        // u.getId(),
        // evaluationService.getDefaultTargetsFromDb(u.getId(), year));
        // }

        // 4) 경혁+진료부 대상 맵
        // Map<String, List<UserPE>> ghJinMap = new HashMap<>();
        // for (UserPE u : userList) {
        // ghJinMap.put(
        // u.getId(),
        // evaluationService.getGhJinryuTargets(u.getId(), year));
        // }

        // model.addAttribute("departments", departments);
        // model.addAttribute("defaultTargetsMap", defaultTargetsMap);
        // model.addAttribute("ghJinMap", ghJinMap);

        model.addAttribute("year", year);
        return "pe/admin/target";
    }

    /** 부서별 기본 대상 저장 */
    @PostMapping("admin/target/save")
    public String saveDept(
            @RequestParam(value = "year") String year,
            @RequestParam(value = "deptTargets", required = false) List<String> deptTargets) {
        adminService.saveDefaultDeptTargets(year,
                deptTargets != null ? deptTargets : Collections.emptyList());
        return "redirect:/pe/admin/target?year=" + year;
    }

    /** 팀별 기본 대상 저장 */
    @PostMapping("admin/target/saveTeam")
    public String saveTeam(
            @RequestParam(value = "year") String year,
            @RequestParam(value = "teamTargets", required = false) List<String> teamTargets) {
        adminService.saveDefaultTeamTargets(year,
                teamTargets != null ? teamTargets : Collections.emptyList());
        return "redirect:/pe/admin/target?year=" + year;
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
        return "pe/admin/evaluation";
    }

    @RequestMapping("evdata/{inst}")
    public String status(Model model, @PathVariable("inst") String inst,
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
        return "pe/admin/evdata";
    }

    @RequestMapping("release")
    public String release(Model model,
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
        model.addAttribute("year", year);
        System.out.println("평가 목록: " + year);

        return "pe/admin/release";
    }

    // 현재 설정 조회
    @GetMapping("/admin/release/api/gate")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getGate(@RequestParam int year) {
        var g = releaseGateService.getOrNull("user_result", year);
        boolean openNow = releaseGateService.isOpenNow("user_result", year);
        return Map.of(
                "exists", g != null,
                "openNow", openNow,
                "data", g);
    }

    // 저장/갱신
    @PostMapping("/admin/release/api/gate")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> saveGate(
            @RequestParam int year,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime openAt,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime closeAt,
            @RequestParam(defaultValue = "true") boolean enabled) {
        releaseGateService.saveGate("user_result", year, openAt, closeAt, enabled);
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
        return "pe/admin/progress-org-detail"; // 경로는 네 프로젝트 구조에 맞게
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

    private static boolean eq(String a, String b) {
        return a != null && b != null && a.trim().equals(b.trim());
    }

    /** 경혁팀 소속 여부 판정 */
    private boolean isKyunghyeokTeam(UserPE me) {
        if (me == null)
            return false;
        String code = me.getTeamCode();
        return code != null && code.trim().equalsIgnoreCase("GH_TEAM"); // 필요시 "GHTEAM"도 OR 추가
    }

    @GetMapping("/pe/not-open")
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
        return "pe/not-open";
    }

    @GetMapping("/pe/gate-closed")
    public String notOpen2(@RequestParam(required = false, defaultValue = "${app.current.eval-year}") int year,
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
        return "pe/gate-closed";
    }

    @GetMapping("/admin/progress/org")
    public String view(@RequestParam(defaultValue = "${app.current.eval-year}") int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        model.addAttribute("year", year);
        model.addAttribute("ev", ev);
        model.addAttribute("search", search);
        return "pe/admin/progress-org";
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

    public final class Num {
        private Num() {
        }

        public static Double d(Object v) {
            if (v == null)
                return null;
            String s = String.valueOf(v).trim();
            if (s.isEmpty() || s.equals("-"))
                return null;
            // "132,988", "90.43%", "▲3.83%", "▼5.44%", "1.25" 등 처리
            s = s.replace(",", "").replace("%", "")
                    .replace("▲", "").replace("▼", "")
                    .replaceAll("[^0-9.\\-]", ""); // 혹시 남는 문자 제거
            try {
                return Double.valueOf(s);
            } catch (Exception e) {
                return null;
            }
        }

        public static Integer i(Object v) {
            Double d = d(v);
            return d == null ? null : d.intValue();
        }

        public static Double sum(Double... xs) {
            double t = 0;
            boolean any = false;
            for (Double x : xs) {
                if (x != null) {
                    t += x;
                    any = true;
                }
            }
            return any ? t : null;
        }

        public static Double round2(Double x) {
            return x == null ? null : Math.round(x * 100.0) / 100.0;
        }
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
            if (target.startsWith("/pe/not-open"))
                return fallbackPath;

            // 결과 페이지로 다시 보내는 것도 무의미하니(또 막힘) 기본 경로 권장
            if (target.startsWith("/pe/user/report"))
                return fallbackPath;

            return target.isBlank() ? fallbackPath : target;
        } catch (Exception e) {
            return fallbackPath;
        }
    }

    @GetMapping("/admin/kpiupload")
    public String kpiupload(@RequestParam(defaultValue = "${app.current.eval-year}") int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        model.addAttribute("year", year);
        model.addAttribute("ev", ev);
        model.addAttribute("search", search);
        return "pe/admin/kpiUpload";
    }

    @GetMapping("/admin/kpiGeneralUpload")
    public String kpiupload2(@RequestParam(defaultValue = "${app.current.eval-year}") int year,
            @RequestParam(defaultValue = "ALL") String ev,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        model.addAttribute("year", year);
        model.addAttribute("ev", ev);
        model.addAttribute("search", search);
        return "pe/admin/kpisubUpload";
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

    // 진료부 KPI 결과 저장
    @PostMapping("/admin/kpi/clinic/save")
    @ResponseBody
    public Map<String, Object> saveClinicKpiResults2025(
            Authentication auth,
            @RequestParam int year,
            @RequestParam(required = false, defaultValue = "") String orgName) {

        if (auth == null || !auth.isAuthenticated()) {
            return Map.of("success", false, "message", "로그인이 필요합니다.");
        }
        String actor = auth.getName();
        int updated = kpi.saveClinicKpiResults2025(year, orgName, actor);

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
        return "redirect:/pe/login"; // 로그인 페이지로 리다이렉트
    }

    @RequestMapping("main")
    public String main() {
        return "pe/main/main";
    }

    @RequestMapping("404")
    public String notFound() {
        return "pe/error/404";
    }

    @RequestMapping("500")
    public String internalServerError() {
        return "pe/error/500";
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null)
            return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role) || a.getAuthority().equals("ROLE_" + role));
    }
}
