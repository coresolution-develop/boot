package com.coresolution.pe.security.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.coresolution.pe.entity.Institution;
import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.mapper.InstitutionAdminMapper;
import com.coresolution.pe.mapper.InstitutionMapper;
import com.coresolution.pe.service.InstAdminUserDetailsService;

import java.util.List;

/**
 * 회귀 테스트 — TODO #7: 로그인 진입점(PE/AFF) ↔ 기관 종류 일치 게이트.
 */
@ExtendWith(MockitoExtension.class)
class InstAdminAuthenticationProviderKindGateTest {

    @Mock private InstAdminUserDetailsService userDetailsService;
    @Mock private InstitutionAdminMapper adminMapper;
    @Mock private InstitutionMapper institutionMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private InstAdminAuthenticationProvider provider;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private InstitutionAdmin admin(int institutionId) {
        InstitutionAdmin a = new InstitutionAdmin();
        a.setId(1);
        a.setLoginId("alice");
        a.setPwd("$2a$10$hashed");
        a.setActive(true);
        a.setInstitutionId(institutionId);
        return a;
    }

    private Institution institution(int id, String kind) {
        Institution i = new Institution();
        i.setId(id);
        i.setKind(kind);
        return i;
    }

    private UserDetails userDetails() {
        return User.builder()
                .username("alice")
                .password("$2a$10$hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INST_ADMIN")))
                .build();
    }

    @Test
    void peLoginUrlWithPeInstitution_succeeds() {
        bindRequest("/pe/inst-loginAction");
        when(adminMapper.findByLoginId("alice")).thenReturn(admin(10));
        when(passwordEncoder.matches("raw", "$2a$10$hashed")).thenReturn(true);
        when(institutionMapper.findById(10)).thenReturn(institution(10, "PE"));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());

        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("alice", "raw"));

        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void affLoginUrlWithAffInstitution_succeeds() {
        bindRequest("/aff/inst-loginAction");
        when(adminMapper.findByLoginId("alice")).thenReturn(admin(20));
        when(passwordEncoder.matches("raw", "$2a$10$hashed")).thenReturn(true);
        when(institutionMapper.findById(20)).thenReturn(institution(20, "AFF"));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());

        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("alice", "raw"));

        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void peLoginUrlWithAffInstitution_throws() {
        bindRequest("/pe/inst-loginAction");
        when(adminMapper.findByLoginId("alice")).thenReturn(admin(20));
        when(passwordEncoder.matches("raw", "$2a$10$hashed")).thenReturn(true);
        when(institutionMapper.findById(20)).thenReturn(institution(20, "AFF"));

        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("alice", "raw")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("AFF 전용");
    }

    @Test
    void affLoginUrlWithPeInstitution_throws() {
        bindRequest("/aff/inst-loginAction");
        when(adminMapper.findByLoginId("alice")).thenReturn(admin(10));
        when(passwordEncoder.matches("raw", "$2a$10$hashed")).thenReturn(true);
        when(institutionMapper.findById(10)).thenReturn(institution(10, "PE"));

        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("alice", "raw")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("PE 전용");
    }

    @Test
    void missingInstitution_defaultsToPe() {
        // institution row 누락 시 안전한 기본값(PE)으로 해석 → PE URL 만 통과
        bindRequest("/pe/inst-loginAction");
        when(adminMapper.findByLoginId("alice")).thenReturn(admin(99));
        when(passwordEncoder.matches("raw", "$2a$10$hashed")).thenReturn(true);
        when(institutionMapper.findById(99)).thenReturn(null);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());

        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("alice", "raw"));

        assertThat(result.isAuthenticated()).isTrue();
    }
}
