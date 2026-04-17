package com.coresolution.pe.handler;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException, ServletException {
                // AJAX 요청인지 확인 (옵션)
                String previousPage = request.getHeader("Referer"); // 이전 페이지 URL 가져오기
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(
                                "<script>" +
                                                "alert('권한이 없습니다.');" +
                                                (previousPage != null ? "window.location.href = '" + previousPage + "';"
                                                                : "window.history.back();")
                                                +
                                                "</script>");
        }
}
