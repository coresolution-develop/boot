package com.coresolution.pe.security.provider;

import java.util.Map;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.mapper.InstitutionAdminMapper;
import com.coresolution.pe.service.InstAdminUserDetailsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기관 관리자 전용 AuthenticationProvider.
 * institution_admins 테이블 기반 BCrypt 인증 수행.
 * 인증 성공 시 details Map 에 institutionId / institutionName 을 담아 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstAdminAuthenticationProvider implements AuthenticationProvider {

    private final InstAdminUserDetailsService userDetailsService;
    private final InstitutionAdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String loginId  = authentication.getName();
        String rawPwd   = (String) authentication.getCredentials();

        // 1) 계정 조회 (비활성 포함 차단은 UserDetailsService 에서 처리)
        InstitutionAdmin admin = adminMapper.findByLoginId(loginId);
        if (admin == null) {
            throw new UsernameNotFoundException("기관 관리자 계정을 찾을 수 없습니다: " + loginId);
        }
        if (!admin.isActive()) {
            throw new UsernameNotFoundException("비활성화된 기관 관리자 계정입니다.");
        }

        // 2) 비밀번호 검증
        if (!passwordEncoder.matches(rawPwd, admin.getPwd())) {
            log.warn("[InstAdmin] 비밀번호 불일치: loginId={}", loginId);
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        // 3) UserDetails 로드 (권한 세팅)
        var userDetails = userDetailsService.loadUserByUsername(loginId);

        // 4) 인증 토큰 생성 — details 에 기관 정보 저장 (SuccessHandler 에서 세션에 기록)
        var token = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        token.setDetails(Map.of(
                "institutionId",   admin.getInstitutionId(),
                "institutionName", admin.getInstitutionName() != null
                                       ? admin.getInstitutionName() : "",
                "adminName",       admin.getName()
        ));

        log.info("[InstAdmin] 인증 성공: loginId={}, institution={}, institutionId={}",
                loginId, admin.getInstitutionName(), admin.getInstitutionId());
        return token;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
