package com.coresolution.pe.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.coresolution.pe.entity.ReleaseWindowRow;
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.ReleaseWindowService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기관 관리자 전용 평가 창구(오픈/마감) 설정 컨트롤러.
 *
 * <p>모든 데이터 조회/수정은 세션의 institutionName(= c_name) 범위로 자동 제한됩니다.
 * 기관 관리자는 자신의 기관 창구만 조회·설정할 수 있습니다.</p>
 */
@Slf4j
@Controller
@RequestMapping("/pe/inst-admin/evalrelease")
@PreAuthorize("hasRole('INST_ADMIN')")
@RequiredArgsConstructor
public class InstAdminReleaseController {

    private final ReleaseWindowService service;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    /** 세션에서 기관명을 꺼내는 헬퍼 */
    private String institution(HttpServletRequest req) {
        return InstitutionAdminContext.getInstitutionName(req);
    }

    // ── 페이지 뷰 ────────────────────────────────────────────────

    @GetMapping
    public String page(HttpServletRequest req, Model model) {
        model.addAttribute("currentYear",    currentEvalYear);
        model.addAttribute("institutionName", institution(req));
        return "pe/inst-admin/evalrelease";
    }

    // ── 목록 조회 (JSON) ─────────────────────────────────────────

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> list(
            HttpServletRequest req,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String ev,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "0")  int page) {

        String cName  = institution(req);
        int safeSize  = Math.max(1, Math.min(size, 100));
        int safePage  = Math.max(0, page);
        int offset    = safePage * safeSize;

        // cName 고정 — 자기 기관 데이터만 조회
        var rows  = service.list(year, type, ev, cName, null, safeSize, offset);
        int total = service.count(year, type, ev, cName, null);

        return ResponseEntity.ok(Map.of(
                "rows",  rows,
                "total", total,
                "page",  safePage,
                "size",  safeSize));
    }

    // ── 신규/갱신 upsert ─────────────────────────────────────────

    @PostMapping(value = "/upsert",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upsert(
            HttpServletRequest req,
            @Valid @RequestBody ReleaseWindowRow body) {

        // cName 강제 주입 — 클라이언트가 보낸 값 무시
        body.setCName(institution(req));

        try {
            service.upsert(body, InstitutionAdminContext.getCurrentLoginId());
            return ResponseEntity.ok(Map.of("result", "ok"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("result", "error", "message", e.getMessage()));
        }
    }

    // ── 단건 수정 ────────────────────────────────────────────────

    @PostMapping(value = "/update/{id}",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            HttpServletRequest req,
            @PathVariable long id,
            @Valid @RequestBody ReleaseWindowRow body) {

        body.setId(id);
        body.setCName(institution(req)); // cName 강제 주입

        try {
            int n = service.updateById(body, InstitutionAdminContext.getCurrentLoginId());
            return ResponseEntity.ok(Map.of("result", n > 0 ? "ok" : "none"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("result", "error", "message", e.getMessage()));
        }
    }

    // ── 활성/비활성 토글 ─────────────────────────────────────────

    @PostMapping(value = "/toggle/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggle(
            @PathVariable long id,
            @RequestParam boolean enabled) {

        int n = service.toggle(id, enabled, InstitutionAdminContext.getCurrentLoginId());
        return ResponseEntity.ok(Map.of("result", n > 0 ? "ok" : "none"));
    }

    // ── 소프트 삭제 ──────────────────────────────────────────────

    @PostMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable long id) {
        int n = service.softDelete(id, InstitutionAdminContext.getCurrentLoginId());
        return ResponseEntity.ok(Map.of("result", n > 0 ? "ok" : "none"));
    }
}
