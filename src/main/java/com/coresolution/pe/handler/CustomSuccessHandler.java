package com.coresolution.pe.handler;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomSuccessHandler implements AuthenticationSuccessHandler {

        @Override
        public void onAuthenticationSuccess(
                        HttpServletRequest request, // ← HttpServletRequest
                        HttpServletResponse response, // ← HttpServletResponse
                        Authentication authentication)
                        throws IOException, ServletException {

                // 로그인 성공 후 리다이렉트
                response.sendRedirect("/pe/home");
        }
}