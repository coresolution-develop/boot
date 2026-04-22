package com.coresolution.pe.security.provider;

import java.util.Map;

import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;

import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.security.token.CustomAuthenticationToken;
import com.coresolution.pe.service.AffUserService;
import com.coresolution.pe.service.CustomAffUserDetailsService;
import lombok.extern.slf4j.Slf4j;

@Component

@Slf4j
public class AffAuthenticationProvider implements AuthenticationProvider {

    private final AffUserService peService; // ← aff 전용 서비스/매퍼
    private final CustomAffUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public AffAuthenticationProvider(AffUserService peService, PasswordEncoder passwordEncoder,
            CustomAffUserDetailsService uds) {
        this.peService = peService;
        this.userDetailsService = uds;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        CustomAuthenticationToken token = (CustomAuthenticationToken) authentication;
        String id = token.getName();
        String rawCred = token.getCredentials().toString();
        String loginType = token.getLoginType();

        log.debug("CustomAuthenticationProvider - Authenticating id: " + id + ", loginType: " + loginType);

        if ("byName".equals(loginType)) {
            int result = peService.login(id, rawCred, loginType);
            switch (result) {
                case 3:
                    throw new BadCredentialsException("이름이 일치하지 않습니다.");
                case 2:
                    throw new CredentialsExpiredException("비밀번호로 로그인 해주세요.");

                // ✅ 이 둘은 절대 성공 처리 금지!
                case 4: // 이름으로 로그인 '성공' but 비번 없음 (기존에 성공처리하던 코드 제거)
                case 5: // 비밀번호 미설정
                    throw new LockedException("PWD_NOT_SET"); // ← 실패(401) 경로로 보냄

                case 0:
                    throw new UsernameNotFoundException("2025년도 직원근무평가 대상직원이 아닙니다.");
                default:
                    throw new AuthenticationServiceException("예상치 못한 결과: " + result);
            }
        } else if ("byPwd".equals(loginType)) {
            // 사번 + 비밀번호 로그인
            UserPE user = peService.findUserInfoById(id, currentEvalYear);
            if (user == null) {
                throw new UsernameNotFoundException("사용자가 없습니다.");
            }
            String encoded = user.getPwd(); // DB 에 저장된 BCrypt 해시
            if (encoded == null || encoded.isEmpty() || !passwordEncoder.matches(rawCred, encoded)) {
                throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
            }

            // 인증 성공 → UserDetailsService 로 권한까지 가져와서 3‑arg 토큰 생성
            UserDetails ud = userDetailsService.loadUserByUsername(id);
            log.debug("byPwd 로그인 성공 - UserDetails 권한: " + ud.getAuthorities());
            return new UsernamePasswordAuthenticationToken(
                    ud, // principal
                    ud.getPassword(), // credentials (인증 후에는 보통 null 또는 "N/A" 처리되지만, 여기서는 ud.getPassword() 그대로 사용)
                    ud.getAuthorities() // authorities
            );
        }

        throw new AuthenticationServiceException("Unknown loginType: " + loginType);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return CustomAuthenticationToken.class.isAssignableFrom(authentication);
    }
}