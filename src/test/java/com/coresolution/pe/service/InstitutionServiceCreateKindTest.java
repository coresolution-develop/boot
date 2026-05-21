package com.coresolution.pe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coresolution.pe.entity.Institution;
import com.coresolution.pe.mapper.InstitutionAdminMapper;
import com.coresolution.pe.mapper.InstitutionMapper;

/**
 * 회귀 테스트 — TODO #7: institutions.kind (PE/AFF 배타적).
 */
@ExtendWith(MockitoExtension.class)
class InstitutionServiceCreateKindTest {

    @Mock private InstitutionMapper institutionMapper;
    @Mock private InstitutionAdminMapper adminMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private InstitutionService institutionService;

    @Test
    void create_normalizesPeKindToUpper() {
        institutionService.create("CODE1", "기관1", "pe", null);

        ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionMapper).insert(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("PE");
    }

    @Test
    void create_acceptsAff() {
        institutionService.create("CODE2", "기관2", "AFF", "JEONGSUNG_MOA");

        ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionMapper).insert(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("AFF");
        assertThat(captor.getValue().getAgcCode()).isEqualTo("JEONGSUNG_MOA");
    }

    @Test
    void create_blankAgcCodeBecomesNull() {
        institutionService.create("CODE_BLANK", "기관_blank", "AFF", "   ");

        ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionMapper).insert(captor.capture());
        assertThat(captor.getValue().getAgcCode()).isNull();
    }

    @Test
    void create_defaultsToPeWhenNullOrBlank() {
        institutionService.create("CODE3", "기관3", null, null);

        ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionMapper).insert(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("PE");
    }

    @Test
    void create_rejectsInvalidKind() {
        assertThatThrownBy(() -> institutionService.create("CODE4", "기관4", "BOTH", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PE 또는 AFF");

        verify(institutionMapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void update_passesKindToMapper() {
        institutionService.update(5, "CODE5", "기관5", true, "AFF", "JEONGSUNG_MOA");

        ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionMapper).update(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo("AFF");
        assertThat(captor.getValue().getId()).isEqualTo(5);
    }
}
