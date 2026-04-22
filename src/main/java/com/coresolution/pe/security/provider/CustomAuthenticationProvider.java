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
import com.coresolution.pe.service.CustomUserDetailsService;
import com.coresolution.pe.service.PeService;
import lombok.extern.slf4j.Slf4j;

@Component

@Slf4j
public class CustomAuthenticationProvider
        implements AuthenticationProvider {

    private final PeService peService;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public CustomAuthenticationProvider(
            PeService peService,
            CustomUserDetailsService uds,
            PasswordEncoder passwordEncoder) {
        this.peService = peService;
        this.userDetailsService = uds;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        CustomAuthenticationToken token = (CustomAuthenticationToken) authentication;
        String id = token.getName();
        String rawCred = token.getCredentials().toString();
        String loginType = token.getLoginType();

        log.debug("CustomAuthenticationProvider - Authenticating id: " + id + ", loginType: " + loginType);

        if ("byName".equals(loginType)) {
            int result = peService.login(id, rawCred, loginType);
            switch (result) {
                case 0:
                    // 🔴 평가 대상 아님
                    throw new LockedException("NOT_TARGET");
                case 3:
                    throw new BadCredentialsException("이름이 일치하지 않습니다.");
                case 2:
                    throw new CredentialsExpiredException("비밀번호로 로그인 해주세요.");

                // ✅ 이 둘은 절대 성공 처리 금지!
                case 4: // 이름으로 로그인 '성공' but 비번 없음 (기존에 성공처리하던 코드 제거)
                case 5: // 비밀번호 미설정
                    throw new LockedException("PWD_NOT_SET"); // ← 실패(401) 경로로 보냄

                default:
                    throw new AuthenticationServiceException("예상치 못한 결과: " + result);
            }
        } else if ("byPwd".equals(loginType)) {
            UserPE user = peService.findUserInfoById(id, currentEvalYear);
            if (user == null) {
                throw new UsernameNotFoundException("사용자가 없습니다.");
            }

            // 🔴 비밀번호 로그인도 평가대상 아님 막기
            if ("Y".equalsIgnoreCase(user.getDelYn())) {
                throw new LockedException("NOT_TARGET");
            }

            String encoded = user.getPwd(); // BCrypt 해시
            if (encoded == null || encoded.isEmpty() || !passwordEncoder.matches(rawCred, encoded)) {
                throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
            }
            // 정상 비번 로그인
            UserDetails ud = userDetailsService.loadUserByUsername(id);
            return new UsernamePasswordAuthenticationToken(ud, ud.getPassword(), ud.getAuthorities());
        }

        throw new AuthenticationServiceException("Unknown loginType: " + loginType);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return CustomAuthenticationToken.class.isAssignableFrom(authentication);
    }
}