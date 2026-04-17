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
import com.coresolution.pe.mapper.AffLoginMapper;

@Service
public class CustomAffUserDetailsService implements UserDetailsService {

    private final AffLoginMapper loginMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public CustomAffUserDetailsService(AffLoginMapper loginMapper) {
        this.loginMapper = loginMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserPE user = loginMapper.findById(username, currentEvalYear);
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }

        // 권한
        List<GrantedAuthority> authorities = new ArrayList<>();
        if ("12365478".equals(user.getId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        // 🔒 비번 null/빈 문자열 방어
        String passwordNonNull = (user.getPwd() != null && !user.getPwd().isBlank())
                ? user.getPwd()
                : "N/A"; // 더미값

        return new org.springframework.security.core.userdetails.User(
                user.getId(),
                passwordNonNull,
                authorities);
    }
}
