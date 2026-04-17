package com.coresolution.pe.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.service.EvalSubmitService;
import com.coresolution.pe.service.EvaluationFormService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class EvalSubmitPageController {

    private final EvaluationFormService evaluationFormService;

    @PostMapping("/formAction/{eval}/{target}/{ev}/{type}")
    public String submitEdit(
            Authentication auth,
            @PathVariable("eval") String evaluatorIdPath,
            @PathVariable("target") String targetId,
            @PathVariable("ev") String dataEv,
            @PathVariable("type") String dataType,
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") String year,
            HttpServletRequest request,
            RedirectAttributes ra) {

        String evaluatorId = auth.getName();
        if (!Objects.equals(evaluatorId, evaluatorIdPath)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 사용자");
        }

        long id = evaluationFormService.saveEdit(
                evaluatorId, targetId, year, dataType, dataEv, request.getParameterMap());

        ra.addFlashAttribute("savedId", id);
        return "redirect:/pe/FormEnd/" + evaluatorId + "/" + targetId;
    }
}