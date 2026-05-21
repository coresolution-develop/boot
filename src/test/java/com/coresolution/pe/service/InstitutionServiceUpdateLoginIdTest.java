package com.coresolution.pe.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.mapper.InstitutionAdminMapper;
import com.coresolution.pe.mapper.InstitutionMapper;

/**
 * 회귀 테스트 — TODO #2: 슈퍼 어드민 → 기관 관리자 loginId 변경.
 */
@ExtendWith(MockitoExtension.class)
class InstitutionServiceUpdateLoginIdTest {

    @Mock private InstitutionMapper institutionMapper;
    @Mock private InstitutionAdminMapper adminMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private InstitutionService institutionService;

    private InstitutionAdmin existing(int id, String loginId) {
        InstitutionAdmin a = new InstitutionAdmin();
        a.setId(id);
        a.setLoginId(loginId);
        return a;
    }

    @Test
    void updateAdminLoginId_happyPath_callsMapperWithTrimmedValue() {
        when(adminMapper.findById(7)).thenReturn(existing(7, "old_admin"));
        when(adminMapper.updateLoginId(eq(7), eq("new_admin"))).thenReturn(1);

        institutionService.updateAdminLoginId(7, "  new_admin  ");

        verify(adminMapper).updateLoginId(7, "new_admin");
    }

    @Test
    void updateAdminLoginId_sameValue_skipsUpdate() {
        when(adminMapper.findById(7)).thenReturn(existing(7, "same_id"));

        institutionService.updateAdminLoginId(7, "same_id");

        verify(adminMapper, never()).updateLoginId(anyInt(), anyString());
    }

    @Test
    void updateAdminLoginId_throwsWhenAdminNotFound() {
        when(adminMapper.findById(999)).thenReturn(null);

        assertThatThrownBy(() -> institutionService.updateAdminLoginId(999, "any"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adminId=999");

        verify(adminMapper, never()).updateLoginId(anyInt(), anyString());
    }

    @Test
    void updateAdminLoginId_propagatesDuplicateKeyException() {
        when(adminMapper.findById(7)).thenReturn(existing(7, "old"));
        when(adminMapper.updateLoginId(eq(7), eq("dup")))
                .thenThrow(new DuplicateKeyException("uq_inst_admin_login_id"));

        assertThatThrownBy(() -> institutionService.updateAdminLoginId(7, "dup"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void updateAdminLoginId_rejectsBlank() {
        assertThatThrownBy(() -> institutionService.updateAdminLoginId(1, "   "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(adminMapper, never()).findById(anyInt());
    }

    @Test
    void updateAdminLoginId_rejectsNull() {
        assertThatThrownBy(() -> institutionService.updateAdminLoginId(1, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(adminMapper, never()).findById(anyInt());
    }

    @Test
    void updateAdminLoginId_rejectsOver100Chars() {
        String tooLong = "a".repeat(101);
        assertThatThrownBy(() -> institutionService.updateAdminLoginId(1, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
