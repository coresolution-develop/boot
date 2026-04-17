package com.coresolution.pe.security.filter;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.coresolution.pe.security.token.CustomAuthenticationToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    public CustomAuthenticationFilter(AuthenticationManager authenticationManager) {
        super.setAuthenticationManager(authenticationManager);

        // AJAX 요청이 아니라면 setFilterProcessesUrl 로 로그인 처리 URL 지정
        setFilterProcessesUrl("/loginAction");

        // 폼 필드 이름 매핑
        setUsernameParameter("id");
        setPasswordParameter("pwd");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
            HttpServletResponse response)
            throws AuthenticationException {
        // ● 여기서 세션을 강제 생성
        request.getSession(true);
        String loginType = request.getParameter("loginType");
        String credential;
        if ("byName".equals(loginType)) {
            credential = request.getParameter("name");
        } else {
            credential = request.getParameter(getPasswordParameter()); // 비밀번호는 setPasswordParameter에서 설정한 "pwd" 파라미터에서
                                                                       // 가져옴
        }
        CustomAuthenticationToken token = new CustomAuthenticationToken(
                obtainUsername(request),
                credential,
                loginType);

        System.out.println("CustomAuthenticationFilter - loginType: " + loginType + ", credential: " + credential);
        // --- 이 부분이 핵심 변경 사항입니다 ---
        // AuthenticationManager를 통해 인증을 시도하고 결과를 받습니다.
        Authentication authenticatedToken = this.getAuthenticationManager().authenticate(token);

        // 인증에 성공했다면, SecurityContextHolder에 인증 객체를 설정합니다.
        // UsernamePasswordAuthenticationFilter의 기본 구현은 이 작업을 하지만,
        // 커스텀 필터에서는 명시적으로 해주는 것이 좋습니다.
        SecurityContextHolder.getContext().setAuthentication(authenticatedToken);

        return authenticatedToken; // 인증된 토큰을 반환
    }

}
