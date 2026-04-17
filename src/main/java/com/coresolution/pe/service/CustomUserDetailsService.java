package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.LoginMapper;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final LoginMapper loginMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public CustomUserDetailsService(LoginMapper loginMapper) {
        this.loginMapper = loginMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserPE user = loginMapper.findById(username, currentEvalYear);
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }
        // 1) 권한
        List<GrantedAuthority> authorities = new ArrayList<>();
        if ("12365478".equals(user.getId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        if (authorities.isEmpty()) {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        // 2) 비밀번호: null/빈 문자열 방지 (byName 흐름 등에서 DB pwd가 null일 수 있음)
        String password = user.getPwd();
        if (password == null || password.isBlank()) {
            // 인증은 Provider에서 이미 끝냅니다. 여기는 세션에 넣을 컨테이너 역할이므로 더미면 충분.
            password = "N/A";
        }

        // 3) 상태 플래그 (필요 시 조정)
        boolean enabled = !"Y".equalsIgnoreCase(user.getDelYn());
        boolean accountNonExpired = true;
        boolean credentialsNonExpired = true;
        boolean accountNonLocked = true;

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getId()) // principal = 사번
                .password(password) // 절대 null/빈 문자열 금지
                .authorities(authorities) // 절대 null 금지
                .accountExpired(!accountNonExpired)
                .credentialsExpired(!credentialsNonExpired)
                .accountLocked(!accountNonLocked)
                .disabled(!enabled)
                .build();
    }
}
