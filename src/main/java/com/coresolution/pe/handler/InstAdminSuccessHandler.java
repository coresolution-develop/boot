package com.coresolution.pe.handler;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.coresolution.pe.security.CustomSecurityContextRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기관 관리자 로그인 성공 핸들러.
 * - 세션에 institutionId / institutionName 저장
 * - AJAX → JSON, 일반 폼 → 리다이렉트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstAdminSuccessHandler implements AuthenticationSuccessHandler {

    private static final String REDIRECT_URL = "/pe/inst-admin/dashboard";

    private final CustomSecurityContextRepository securityContextRepository;

    @Override
    @SuppressWarnings("unchecked")
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // 1) SecurityContext 세션 저장 (CustomSecurityContextRepository 방식 통일)
        var ctx = org.springframework.security.core.context.SecurityContextHolder.getContext();
        ctx.setAuthentication(authentication);
        securityContextRepository.saveContext(ctx, request, response);

        // 2) 기관 정보를 세션에 저장 (details Map 에서 추출)
        Object details = authentication.getDetails();
        HttpSession session = request.getSession(true);

        if (details instanceof Map<?, ?> detailsMap) {
            Object institutionId   = detailsMap.get("institutionId");
            Object institutionName = detailsMap.get("institutionName");
            Object adminName       = detailsMap.get("adminName");

            if (institutionId != null) {
                session.setAttribute("institutionId",   institutionId);
                session.setAttribute("institutionName", institutionName != null ? institutionName : "");
                session.setAttribute("adminName",       adminName != null ? adminName : "");
                log.info("[InstAdmin] 세션 저장: institutionId={}, institutionName={}",
                        institutionId, institutionName);
            }
        }

        // 3) 리다이렉트 (AJAX / 일반 폼 분기)
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":true,\"redirect\":\"" + REDIRECT_URL + "\"}");
        } else {
            response.sendRedirect(REDIRECT_URL);
        }
    }
}
