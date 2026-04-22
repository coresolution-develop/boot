package com.coresolution.pe.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 계열사 기관 관리자 전용 인증 필터.
 * POST /aff/inst-loginAction 을 처리한다.
 */
public class AffInstAdminAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    public AffInstAdminAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
        setFilterProcessesUrl("/aff/inst-loginAction");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response)
            throws AuthenticationException {
        request.getSession(true);

        String loginId  = obtainUsername(request);
        String password = obtainPassword(request);

        if (loginId  == null) loginId  = "";
        if (password == null) password = "";
        loginId = loginId.trim();

        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(loginId, password);
        setDetails(request, token);

        return this.getAuthenticationManager().authenticate(token);
    }
}
