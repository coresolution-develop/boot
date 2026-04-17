package com.coresolution.pe.controller.admin;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.coresolution.pe.entity.EvalMappingRule;
import com.coresolution.pe.service.EvalMappingRuleService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/mapping-rule")
@RequiredArgsConstructor
public class AdminMappingRuleController {

    private final EvalMappingRuleService service;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    // ── 페이지 ───────────────────────────────────────────────────────────

    @GetMapping
    public String page(Model model, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return "redirect:/pe/login";
        model.addAttribute("currentYear", currentEvalYear);
        return "pe/admin/mapping-rule";
    }

    // ── 목록 조회 ────────────────────────────────────────────────────────

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> list(Authentication auth,
                                   @RequestParam(defaultValue = "0") int year) {
        if (auth == null || !auth.isAuthenticated())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "unauthenticated"));
        int y = (year == 0) ? currentEvalYear : year;
        return ResponseEntity.ok(Map.of("rules", service.listByYear(y)));
    }

    // ── 저장 (신규/수정) ──────────────────────────────────────────────────

    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> save(Authentication auth,
                                   @RequestBody EvalMappingRule rule) {
        if (auth == null || !auth.isAuthenticated())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "unauthenticated"));
        try {
            EvalMappingRule saved = service.save(rule, auth.getName());
            return ResponseEntity.ok(Map.of("result", "ok", "id", saved.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("result", "error", "message", e.getMessage()));
        }
    }

    // ── 삭제 ─────────────────────────────────────────────────────────────

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> delete(Authentication auth, @PathVariable Long id) {
        if (auth == null || !auth.isAuthenticated())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "unauthenticated"));
        service.delete(id);
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // ── 전년도 복사 ──────────────────────────────────────────────────────

    @PostMapping(value = "/copy-year", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> copyYear(Authentication auth,
                                       @RequestParam int fromYear,
                                       @RequestParam int toYear) {
        if (auth == null || !auth.isAuthenticated())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "unauthenticated"));
        try {
            int copied = service.copyFromYear(fromYear, toYear, auth.getName());
            return ResponseEntity.ok(Map.of("result", "ok", "copied", copied));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("result", "error", "message", e.getMessage()));
        }
    }

    // ── 기본 규칙 시딩 ───────────────────────────────────────────────────

    @PostMapping(value = "/seed-defaults", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> seedDefaults(Authentication auth,
                                           @RequestParam(defaultValue = "0") int year) {
        if (auth == null || !auth.isAuthenticated())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "unauthenticated"));
        int y = (year == 0) ? currentEvalYear : year;
        int seeded = service.seedDefaultRules(y, auth.getName());
        String msg = seeded > 0 ? seeded + "개 기본 규칙이 생성되었습니다." : "이미 규칙이 존재합니다. 삭제 후 다시 시도하세요.";
        return ResponseEntity.ok(Map.of("result", "ok", "seeded", seeded, "message", msg));
    }

    // ── 대상 재생성 ──────────────────────────────────────────────────────

    @PostMapping(value = "/apply", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> apply(Authentication auth,
                                    @RequestParam(defaultValue = "0") int year) {
        if (auth == null || !auth.isAuthenticated())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "unauthenticated"));
        try {
            int y = (year == 0) ? currentEvalYear : year;
            int total = service.applyRules(String.valueOf(y));
            return ResponseEntity.ok(Map.of("result", "ok", "total", total,
                    "message", total + "건의 평가 대상이 생성되었습니다."));
        } catch (Exception e) {
            log.error("[MappingRule] applyRules 오류", e);
            return ResponseEntity.internalServerError().body(Map.of("result", "error", "message", e.getMessage()));
        }
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AdminMappingRuleController.class);
}
