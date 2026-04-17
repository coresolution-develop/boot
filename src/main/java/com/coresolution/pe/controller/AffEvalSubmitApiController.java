package com.coresolution.pe.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.pe.entity.EditPayload;
import com.coresolution.pe.service.AffEvalSubmitService;
import com.coresolution.pe.service.EvalSubmitService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/aff/api")
@RequiredArgsConstructor
public class AffEvalSubmitApiController {

    private final AffEvalSubmitService submitService;

    // 신규 제출(폼 페이지) — x-www-form-urlencoded
    @PostMapping(value = "/formAction/{eval}/{target}/{ev}/{type}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> submit(
            @PathVariable String eval,
            @PathVariable String target,
            @PathVariable String ev,
            @PathVariable String type,
            @RequestParam(defaultValue = "${app.current.eval-year}") int year,
            @RequestParam MultiValueMap<String, String> params) {

        Map<String, String> radios = new LinkedHashMap<>();
        Map<String, String> essays = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            if ("score".equals(k) || "year".equals(k))
                return;
            String val = (v == null || v.isEmpty()) ? "" : v.get(0);
            if (k.startsWith("t"))
                essays.put(k, val);
            else
                radios.put(k, val);
        });

        submitService.saveSubmission(year, eval, target, ev, type, radios, essays);

        return Map.of("result", "2");
    }

    // ✅ (신규) JSON 방식 전송 (edit 페이지에서 사용)
    @PostMapping(value = "/editAction/{eval}/{target}/{ev}/{type}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> submitEditJson(
            @PathVariable("eval") String evaluatorId,
            @PathVariable("target") String targetId,
            @PathVariable("ev") String dataEv,
            @PathVariable("type") String dataType,
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") int year,
            @RequestBody EditPayload payload) {

        // 필요 시 '수정은 항상 허용'하려면 isCompleted 체크를 빼세요.
        // if (submitService.isCompleted(...)) { ... }

        Map<String, String> radios = Optional.ofNullable(payload.getRadios()).orElseGet(HashMap::new);
        Map<String, String> essays = Optional.ofNullable(payload.getEssays()).orElseGet(HashMap::new);

        submitService.saveSubmission(year, evaluatorId, targetId, dataEv, dataType, radios, essays);

        Map<String, Object> res = new HashMap<>();
        res.put("result", "2");
        return res;
    }
}