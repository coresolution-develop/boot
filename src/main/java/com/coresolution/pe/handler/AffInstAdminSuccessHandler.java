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
 * 계열사 기관 관리자 로그인 성공 핸들러.
 * 세션에 institutionId / institutionName 저장 후 /aff/inst-admin/dashboard 로 이동.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AffInstAdminSuccessHandler implements AuthenticationSuccessHandler {

    private static final String REDIRECT_URL = "/aff/inst-admin/dashboard";

    private final CustomSecurityContextRepository securityContextRepository;

    @Override
    @SuppressWarnings("unchecked")
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        var ctx = org.springframework.security.core.context.SecurityContextHolder.getContext();
        ctx.setAuthentication(authentication);
        securityContextRepository.saveContext(ctx, request, response);

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
                log.info("[AffInstAdmin] 세션 저장: institutionId={}, institutionName={}",
                        institutionId, institutionName);
            }
        }

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
