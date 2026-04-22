package com.coresolution.pe.security;

import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository; // <-- 이 임포트를 정확히 확인!
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CustomSecurityContextRepository implements SecurityContextRepository {

    private final HttpSessionSecurityContextRepository delegate = new HttpSessionSecurityContextRepository();

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        // 이 메서드가 deprecated된 loadContext를 대신합니다.
        return delegate.loadDeferredContext(request);
    }

    /**
     * 변경된 시그니처: HttpRequestResponseHolder를 받아야 합니다.
     */
    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        log.debug("CustomSecurityContextRepository: loadContext 호출됨.");
        SecurityContext context = delegate.loadContext(holder);

        if (context.getAuthentication() != null) {
            log.debug("CustomSecurityContextRepository: 인증 정보 로드 성공 - "
                    + context.getAuthentication().getName());
        } else {
            log.debug("CustomSecurityContextRepository: 세션에서 인증 정보 로드 실패 (또는 없음).");
        }
        return context;
    }

    /**
     * saveContext와 containsContext는 시그니처가 그대로 유지됩니다.
     */
    @Override
    public void saveContext(SecurityContext context,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.debug("CustomSecurityContextRepository: saveContext 호출됨.");
        delegate.saveContext(context, request, response);

        if (context.getAuthentication() != null) {
            log.debug("CustomSecurityContextRepository: 인증 정보 저장 성공 - "
                    + context.getAuthentication().getName());
        } else {
            log.debug("CustomSecurityContextRepository: 저장할 인증 정보 없음.");
        }
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        log.debug("CustomSecurityContextRepository: containsContext 호출됨.");
        return delegate.containsContext(request);
    }
}