package com.coresolution.pe.handler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.service.PeService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final SecurityContextRepository securityContextRepository;
    private final PeService peService;
    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public CustomAuthenticationSuccessHandler(
            SecurityContextRepository securityContextRepository, // 또는 @Qualifier("customSecurityContextRepository")
            PeService peService) {
        this.securityContextRepository = securityContextRepository;
        this.peService = peService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        final String ctx = request.getContextPath();
        final String cookiePath = (ctx == null || ctx.isEmpty()) ? "/" : ctx;

        // ── 1) 입력값 쿠키 저장
        String idsaved = request.getParameter("id");
        String loginType = request.getParameter("loginType");
        if (idsaved != null && !idsaved.isBlank()) {
            Cookie idCookie = new Cookie("savedId",
                    URLEncoder.encode(idsaved, StandardCharsets.UTF_8));
            idCookie.setPath(cookiePath);
            idCookie.setMaxAge(60 * 60 * 24 * 30);
            response.addCookie(idCookie);
        }
        Cookie typeCookie = new Cookie("savedLoginType",
                (loginType == null || loginType.isBlank()) ? "byName" : loginType);
        typeCookie.setPath(cookiePath);
        typeCookie.setMaxAge(60 * 60 * 24 * 30);
        response.addCookie(typeCookie);

        // ── 2) 현재 사용자 사번
        String userId = (authentication.getPrincipal() instanceof User u)
                ? u.getUsername()
                : authentication.getName();

        // ── 3) 비밀번호 미설정 여부 확인 (DB)
        UserPE user = peService.findUserInfoById(userId, currentEvalYear);
        boolean mustSetPwd = (user == null) || user.getPwd() == null || user.getPwd().isBlank();
        // 🔴 여기서 del_yn = 'Y' 체크
        if (user != null && "Y".equalsIgnoreCase(user.getDelYn())) {
            boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
            String msg = currentEvalYear + "년도 직원근무평가 대상직원이 아닙니다.";

            // 로그인 세션/컨텍스트 정리
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            if (isAjax) {
                // login.js 가 status=401 에서 JSON을 읽도록 맞춰줌
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"ok\":false," +
                                "\"result\":\"0\"," +
                                "\"message\":\"" + msg + "\"}");
            } else {
                // 일반 폼 submit 대비: 파라미터로 에러코드 전달
                String enc = URLEncoder.encode(msg, StandardCharsets.UTF_8);
                String loginUrl = ctx + "/login?error=0&msg=" + enc;
                redirectStrategy.sendRedirect(request, response, loginUrl);
            }
            return;
        }

        // (옵션) Provider에서 details 플래그가 심어졌다면 함께 고려
        Object details = authentication.getDetails();
        if (!mustSetPwd && details instanceof Map<?, ?> map) {
            Object flag = map.get("mustSetPwd");
            if (flag instanceof Boolean b && b)
                mustSetPwd = true;
        }

        // ── 4) 반드시 SecurityContext 저장(세션 생성 보장)
        request.getSession(true);
        securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

        // ── 5) 목적지 결정
        String target;
        if (mustSetPwd) {
            String idxStr = (user != null && user.getIdx() > 0)
                    ? String.valueOf(user.getIdx())
                    : "me";
            target = ctx + "/pwd/" + idxStr;
        } else if (hasRole(authentication.getAuthorities(), "ROLE_ADMIN")) {
            target = ctx + "/admin/userList";
        } else {
            Integer idx = peService.findIdx(userId); // DB 보정용
            String idxStr = (idx != null && idx > 0) ? String.valueOf(idx) : "me";
            target = ctx + "/Info/" + idxStr;
        }

        // ── 6) AJAX면 JSON, 아니면 redirect
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":true,\"redirect\":\"" + target + "\"}");
            return;
        }
        redirectStrategy.sendRedirect(request, response, target);
    }

    private boolean hasRole(Collection<? extends GrantedAuthority> authorities, String role) {
        return authorities.stream().anyMatch(a -> role.equals(a.getAuthority()));
    }
}
