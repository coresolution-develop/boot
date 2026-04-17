package com.coresolution.pe.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 기관 관리자 세션 컨텍스트 유틸리티.
 *
 * <p>InstAdminSuccessHandler 가 로그인 성공 시 HTTP 세션에 저장한
 * institutionId / institutionName 값을 쉽게 꺼내 쓸 수 있도록 제공한다.</p>
 *
 * <p>이 값은 InstAdminPageController 가 서비스 호출 시
 * {@code org} 파라미터(= c_name 필터)로 사용한다.</p>
 */
public final class InstitutionAdminContext {

    private InstitutionAdminContext() {}

    // ── 세션 키 상수 ──────────────────────────────────────

    public static final String SESSION_KEY_ID   = "institutionId";
    public static final String SESSION_KEY_NAME = "institutionName";
    public static final String SESSION_KEY_ADMIN_NAME = "adminName";

    // ── 현재 요청 기준 조회 ───────────────────────────────

    /**
     * 세션에서 기관 ID 를 반환한다. 없으면 null.
     */
    public static Integer getInstitutionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object val = session.getAttribute(SESSION_KEY_ID);
        return (val instanceof Integer i) ? i : null;
    }

    /**
     * 세션에서 기관명(c_name 값)을 반환한다. 없으면 빈 문자열.
     */
    public static String getInstitutionName(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return "";
        Object val = session.getAttribute(SESSION_KEY_NAME);
        return (val instanceof String s) ? s : "";
    }

    /**
     * 세션에서 관리자 성명을 반환한다.
     */
    public static String getAdminName(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return "";
        Object val = session.getAttribute(SESSION_KEY_ADMIN_NAME);
        return (val instanceof String s) ? s : "";
    }

    // ── SecurityContext 기준 조회 ──────────────────────────

    /**
     * 현재 SecurityContext 의 Authentication 이 ROLE_INST_ADMIN 인지 확인한다.
     */
    public static boolean isInstAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INST_ADMIN".equals(a.getAuthority()));
    }

    /**
     * 현재 SecurityContext 의 로그인 ID(loginId) 반환.
     */
    public static String getCurrentLoginId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "";
    }
}
