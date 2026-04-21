package com.coresolution.pe.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 기관 관리자 로그인 페이지 렌더링 전용.
 * Security FilterChain 에서 permitAll 처리된 경로이므로
 * PreAuthorize 없이 단순 뷰 반환.
 */
@Controller
public class InstAdminLoginController {

    /** GET /pe/inst-login → 기관 관리자 로그인 페이지 */
    @GetMapping("/pe/inst-login")
    public String instLoginPage() {
        return "pe/inst-admin/login";
    }
}
