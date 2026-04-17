package com.coresolution.pe.controller.admin;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.coresolution.pe.entity.ReleaseWindowRow;
import com.coresolution.pe.service.ReleaseWindowService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/evalrelease")
@RequiredArgsConstructor
public class AdminReleaseController {

    private final ReleaseWindowService service;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    // 페이지 뷰
    @GetMapping
    public String page(Model model, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/pe/login";
        }
        model.addAttribute("currentYear", currentEvalYear);
        return "pe/admin/evalrelease";
    }

    // 목록 조회 (JSON)
    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> list(
            Authentication auth,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String ev,
            @RequestParam(required = false) String cName,
            @RequestParam(required = false) String subCode,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "0") int page) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("result", "unauthenticated", "redirect", "/pe/login"));
        }

        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;

        var rows = service.list(year, type, ev, cName, subCode, safeSize, offset);

        // 서비스에 count 메서드가 없다면 추가하세요 (mapper.countList 위임)
        int total = service.count(year, type, ev, cName, subCode);

        return ResponseEntity.ok(Map.of(
                "rows", rows,
                "total", total,
                "page", safePage,
                "size", safeSize));
    }

    // 신규/갱신 upsert
    @PostMapping(value = "/upsert", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upsert(
            Authentication auth,
            @Valid @RequestBody ReleaseWindowRow req) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("result", "unauthenticated", "redirect", "/pe/login"));
        }
        String op = auth.getName();
        service.upsert(req, op);
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // 단건 수정
    @PostMapping(value = "/update/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            Authentication auth,
            @PathVariable long id,
            @Valid @RequestBody ReleaseWindowRow req) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("result", "unauthenticated", "redirect", "/pe/login"));
        }
        String op = auth.getName();
        req.setId(id);
        int n = service.updateById(req, op);
        return ResponseEntity.ok(Map.of("result", n > 0 ? "ok" : "none"));
    }

    // 사용/중지 토글
    @PostMapping(value = "/toggle/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggle(
            Authentication auth,
            @PathVariable long id,
            @RequestParam boolean enabled) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("result", "unauthenticated", "redirect", "/pe/login"));
        }
        String op = auth.getName();
        int n = service.toggle(id, enabled, op);
        return ResponseEntity.ok(Map.of("result", n > 0 ? "ok" : "none"));
    }

    // 소프트 삭제
    @PostMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(
            Authentication auth,
            @PathVariable long id) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("result", "unauthenticated", "redirect", "/pe/login"));
        }
        String op = auth.getName();
        int n = service.softDelete(id, op);
        return ResponseEntity.ok(Map.of("result", n > 0 ? "ok" : "none"));
    }

    /**
     * 전년도 창구 설정 일괄 복사.
     * fromYear 의 모든 설정을 toYear 로 복사하고, 날짜를 offsetDays 만큼 이동.
     */
    @PostMapping(value = "/copy-from-year", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> copyFromYear(
            Authentication auth,
            @RequestParam int fromYear,
            @RequestParam int toYear,
            @RequestParam(defaultValue = "365") int offsetDays) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("result", "unauthenticated", "redirect", "/pe/login"));
        }
        try {
            int copied = service.copyFromYear(fromYear, toYear, offsetDays, auth.getName());
            return ResponseEntity.ok(Map.of("result", "ok", "copied", copied));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("result", "error", "message", e.getMessage()));
        }
    }
}