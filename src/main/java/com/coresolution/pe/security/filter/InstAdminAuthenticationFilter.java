package com.coresolution.pe.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 기관 관리자 전용 인증 필터.
 * POST /pe/inst-loginAction 을 처리하며,
 * loginType 개념 없이 항상 ID/PW 방식으로 인증한다.
 */
public class InstAdminAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    public InstAdminAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
        // 처리 URL 고정
        setFilterProcessesUrl("/pe/inst-loginAction");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response)
            throws AuthenticationException {
        // 세션 강제 생성 (CustomAuthenticationFilter 패턴 동일)
        request.getSession(true);

        String loginId = obtainUsername(request);   // "username" 파라미터
        String password = obtainPassword(request);  // "password" 파라미터

        if (loginId == null) loginId = "";
        if (password == null) password = "";

        loginId = loginId.trim();

        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(loginId, password);
        setDetails(request, token);

        return this.getAuthenticationManager().authenticate(token);
    }
}
