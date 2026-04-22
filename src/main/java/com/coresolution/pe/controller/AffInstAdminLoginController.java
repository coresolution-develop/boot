package com.coresolution.pe.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 계열사 기관 관리자 로그인 페이지 렌더링 전용.
 */
@Controller
public class AffInstAdminLoginController {

    /** GET /aff/inst-login → 계열사 기관 관리자 로그인 페이지 */
    @GetMapping("/aff/inst-login")
    public String instLoginPage() {
        return "aff/inst-admin/login";
    }
}
