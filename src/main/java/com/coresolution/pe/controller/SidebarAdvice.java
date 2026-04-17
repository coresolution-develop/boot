package com.coresolution.pe.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.coresolution.pe.service.YearService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class SidebarAdvice {

    private final YearService yearService;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    @ModelAttribute("sidebarCollapsed")
    public boolean sidebarCollapsed(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
            return false;
        for (Cookie c : cookies) {
            if ("sidebarCollapsed".equals(c.getName())) {
                return "true".equalsIgnoreCase(c.getValue());
            }
        }
        return false;
    }

    /** 모든 뷰에서 현재 평가 연도를 사용할 수 있도록 전역 주입 */
    @ModelAttribute("currentEvalYear")
    public int currentEvalYear() {
        return currentEvalYear;
    }

    /** 모든 뷰에서 선택 가능한 연도 목록을 사용할 수 있도록 전역 주입 */
    @ModelAttribute("availableYears")
    public List<String> availableYears() {
        return yearService.getYears();
    }
}