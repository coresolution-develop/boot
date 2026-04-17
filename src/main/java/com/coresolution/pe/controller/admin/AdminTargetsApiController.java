package com.coresolution.pe.controller.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.pe.entity.CustomInsertReq;
import com.coresolution.pe.entity.TargetRowDto;
import com.coresolution.pe.service.AdminTargetService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/targets")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminTargetsApiController {

    private final AdminTargetService adminTargetService;

    @org.springframework.beans.factory.annotation.Value("${app.current.eval-year}")
    private int currentEvalYear;

    @PostMapping("/custom/{userId}/new")
    public Map<String, Object> insertCustom(@PathVariable String userId,
            @RequestParam(required = false) String year,
            @RequestBody CustomInsertReq req) {
        if (year == null || year.isEmpty()) year = String.valueOf(currentEvalYear);
        int n = adminTargetService.insertCustomOnly(
                userId, year,
                req.getTargetId(), req.getEvalTypeCode(), req.getDataEv(), req.getDataType(), req.getReason());
        return Map.of("ok", n == 1, "inserted", n);
    }

    // ---- 예외 핸들러 (jakarta로 통일) ----
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleConstraint(jakarta.validation.ConstraintViolationException e) {
        return Map.of("ok", false, "error", "validation", "details", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalid(MethodArgumentNotValidException e) {
        var fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> Map.of("field", fe.getField(), "msg", fe.getDefaultMessage()))
                .toList();
        return Map.of("ok", false, "error", "invalid_request", "details", fieldErrors);
    }

}