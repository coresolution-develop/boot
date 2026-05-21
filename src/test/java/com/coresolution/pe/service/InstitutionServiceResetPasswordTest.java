package com.coresolution.pe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coresolution.pe.mapper.InstitutionAdminMapper;
import com.coresolution.pe.mapper.InstitutionMapper;

/**
 * 회귀 테스트 — TODO #1: 슈퍼 어드민 → 기관 관리자 비밀번호 재설정.
 * 정상 경로가 bcrypt 인코딩된 값을 매퍼에 전달하는지, 잘못된 adminId 시 예외가 발생하는지 검증.
 */
@ExtendWith(MockitoExtension.class)
class InstitutionServiceResetPasswordTest {

    @Mock private InstitutionMapper institutionMapper;
    @Mock private InstitutionAdminMapper adminMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private InstitutionService institutionService;

    @Test
    void resetAdminPassword_encodesAndDelegatesToMapper() {
        int adminId = 42;
        String raw = "newSecret1";
        when(passwordEncoder.encode(raw)).thenReturn("$2a$10$hashed");
        when(adminMapper.updatePassword(eq(adminId), anyString())).thenReturn(1);

        institutionService.resetAdminPassword(adminId, raw);

        ArgumentCaptor<String> pwdCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminMapper).updatePassword(eq(adminId), pwdCaptor.capture());
        assertThat(pwdCaptor.getValue()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void resetAdminPassword_throwsWhenNoRowAffected() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(adminMapper.updatePassword(eq(999), anyString())).thenReturn(0);

        assertThatThrownBy(() -> institutionService.resetAdminPassword(999, "newSecret1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adminId=999");
    }

    @Test
    void resetAdminPassword_acceptsSingleCharPassword() {
        // 1자리 비밀번호도 허용 (운영 정책 — 근무자 특성)
        when(passwordEncoder.encode("1")).thenReturn("$2a$10$singleHash");
        when(adminMapper.updatePassword(eq(1), anyString())).thenReturn(1);

        institutionService.resetAdminPassword(1, "1");

        verify(adminMapper).updatePassword(eq(1), anyString());
    }

    @Test
    void resetAdminPassword_rejectsEmptyPassword() {
        assertThatThrownBy(() -> institutionService.resetAdminPassword(1, ""))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(passwordEncoder, adminMapper);
    }

    @Test
    void resetAdminPassword_rejectsNullPassword() {
        assertThatThrownBy(() -> institutionService.resetAdminPassword(1, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(passwordEncoder, adminMapper);
    }
}
