package com.coresolution.pe.service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.mapper.InstitutionAdminMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 기관 관리자(institution_admins 테이블)용 UserDetailsService.
 * CustomUserDetailsService(직원)과 완전히 분리된 별도 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstAdminUserDetailsService implements UserDetailsService {

    private final InstitutionAdminMapper adminMapper;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        InstitutionAdmin admin = adminMapper.findByLoginId(loginId);

        if (admin == null) {
            log.warn("[InstAdmin] 관리자 계정 없음: {}", loginId);
            throw new UsernameNotFoundException("기관 관리자 계정을 찾을 수 없습니다: " + loginId);
        }
        if (!admin.isActive()) {
            log.warn("[InstAdmin] 비활성 계정 로그인 시도: {}", loginId);
            throw new UsernameNotFoundException("비활성화된 기관 관리자 계정입니다: " + loginId);
        }

        log.info("[InstAdmin] 인증 로드: loginId={}, institution={}", loginId, admin.getInstitutionName());

        return User.builder()
                .username(loginId)
                .password(admin.getPwd())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INST_ADMIN")))
                .accountLocked(false)
                .disabled(false)
                .build();
    }
}
