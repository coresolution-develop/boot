package com.coresolution.pe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import com.coresolution.pe.entity.PendingPairRow;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffEndLetterMapper;
import com.coresolution.pe.mapper.AffEvaluationMapper;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.AffAdminProgressByOrgService;
import com.coresolution.pe.service.AffAdminTargetService;

/**
 * 회귀 테스트 — TODO 보안 항목: AFF inst-admin pendingPairs IDOR 가드.
 * PE 선례(d9580fa)와 동일한 기관 스코프 검증 동작 보장.
 */
@ExtendWith(MockitoExtension.class)
class AffInstAdminPendingPairsGuardTest {

    @Mock private AffAdminProgressByOrgService progressService;
    @Mock private AffUserMapper affUserMapper;
    @Mock private AffLoginMapper affLoginMapper;
    @Mock private AffAdminTargetService affTargetService;
    @Mock private AffEvaluationMapper affEvaluationMapper;
    @Mock private AffEndLetterMapper affEndLetterMapper;

    @Mock private HttpServletRequest request;
    @Mock private HttpSession session;

    @InjectMocks private AffInstAdminPageController controller;

    private static final int YEAR = 2026;
    private static final String ORG_A = "기관A";
    private static final String ORG_B = "기관B";
    private static final String TARGET_ID = "10001";

    private UserPE user(String cName) {
        UserPE u = new UserPE();
        u.setId(TARGET_ID);
        u.setCName(cName);
        return u;
    }

    private void sessionWithInstitution(String institutionName) {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(InstitutionAdminContext.SESSION_KEY_NAME)).thenReturn(institutionName);
    }

    @Test
    void pending_sameInstitution_returnsRows() {
        sessionWithInstitution(ORG_A);
        when(affLoginMapper.findById(TARGET_ID, YEAR)).thenReturn(user(ORG_A));
        List<PendingPairRow> rows = List.of(new PendingPairRow());
        when(progressService.pendingPairs(YEAR, TARGET_ID, "ALL")).thenReturn(rows);

        ResponseEntity<List<PendingPairRow>> resp =
                controller.pending(request, TARGET_ID, YEAR, "ALL");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void pending_otherInstitution_returns403_andDoesNotQueryPairs() {
        sessionWithInstitution(ORG_A);
        when(affLoginMapper.findById(TARGET_ID, YEAR)).thenReturn(user(ORG_B));

        ResponseEntity<List<PendingPairRow>> resp =
                controller.pending(request, TARGET_ID, YEAR, "ALL");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(progressService, never()).pendingPairs(YEAR, TARGET_ID, "ALL");
    }

    @Test
    void pending_unknownTarget_returns403() {
        sessionWithInstitution(ORG_A);
        when(affLoginMapper.findById(TARGET_ID, YEAR)).thenReturn(null);

        ResponseEntity<List<PendingPairRow>> resp =
                controller.pending(request, TARGET_ID, YEAR, "ALL");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(progressService, never()).pendingPairs(YEAR, TARGET_ID, "ALL");
    }

    @Test
    void pending_missingSession_isRejectedByInstitutionHelper() {
        when(request.getSession(false)).thenReturn(null);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> controller.pending(request, TARGET_ID, YEAR, "ALL"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
