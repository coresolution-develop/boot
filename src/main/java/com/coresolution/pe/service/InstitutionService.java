package com.coresolution.pe.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.Institution;
import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.mapper.InstitutionAdminMapper;
import com.coresolution.pe.mapper.InstitutionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionMapper institutionMapper;
    private final InstitutionAdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    // ── 기관 조회 ─────────────────────────────────────────

    public List<Institution> getAllInstitutions() {
        return institutionMapper.findAll();
    }

    public List<Institution> getActiveInstitutions() {
        return institutionMapper.findAllActive();
    }

    public Institution getById(int id) {
        return institutionMapper.findById(id);
    }

    public Institution getByName(String name) {
        return institutionMapper.findByName(name);
    }

    // ── 기관 생성/수정 ────────────────────────────────────

    private static final java.util.Set<String> ALLOWED_KINDS = java.util.Set.of("PE", "AFF");

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) return "PE";
        String upper = kind.trim().toUpperCase();
        if (!ALLOWED_KINDS.contains(upper)) {
            throw new IllegalArgumentException("기관 종류는 PE 또는 AFF 여야 합니다: " + kind);
        }
        return upper;
    }

    private static String normalizeAgcCode(String agcCode) {
        if (agcCode == null) return null;
        String trimmed = agcCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Institution create(String code, String name, String kind, String agcCode) {
        Institution inst = new Institution();
        inst.setCode(code);
        inst.setName(name);
        inst.setActive(true);
        inst.setKind(normalizeKind(kind));
        inst.setAgcCode(normalizeAgcCode(agcCode));
        institutionMapper.insert(inst);
        return inst; // insert 후 id가 채워진 상태로 반환
    }

    public void update(int id, String code, String name, boolean isActive, String kind, String agcCode) {
        Institution inst = new Institution();
        inst.setId(id);
        inst.setCode(code);
        inst.setName(name);
        inst.setActive(isActive);
        inst.setKind(normalizeKind(kind));
        inst.setAgcCode(normalizeAgcCode(agcCode));
        institutionMapper.update(inst);
    }

    public void deactivate(int id) {
        institutionMapper.deactivate(id);
    }

    public void activate(int id) {
        institutionMapper.activate(id);
    }

    // ── 기관 관리자 조회 ──────────────────────────────────

    public List<InstitutionAdmin> getAllAdmins() {
        return adminMapper.findAll();
    }

    public List<InstitutionAdmin> getAdminsByInstitution(int institutionId) {
        return adminMapper.findByInstitutionId(institutionId);
    }

    public InstitutionAdmin getAdminById(int id) {
        return adminMapper.findById(id);
    }

    public InstitutionAdmin getAdminByLoginId(String loginId) {
        return adminMapper.findByLoginId(loginId);
    }

    // ── 기관 관리자 생성/수정 ─────────────────────────────

    /**
     * 기관 관리자 계정 생성
     *
     * @param institutionId 소속 기관 ID
     * @param loginId       로그인 ID
     * @param rawPassword   평문 비밀번호 (BCrypt 인코딩 처리)
     * @param name          성명
     */
    public InstitutionAdmin createAdmin(int institutionId, String loginId,
                                        String rawPassword, String name) {
        InstitutionAdmin admin = new InstitutionAdmin();
        admin.setInstitutionId(institutionId);
        admin.setLoginId(loginId);
        admin.setPwd(passwordEncoder.encode(rawPassword));
        admin.setName(name);
        admin.setActive(true);
        adminMapper.insert(admin);
        return admin;
    }

    public void updateAdmin(int id, String name, boolean isActive) {
        InstitutionAdmin admin = new InstitutionAdmin();
        admin.setId(id);
        admin.setName(name);
        admin.setActive(isActive);
        adminMapper.update(admin);
    }

    /**
     * 비밀번호 재설정 (슈퍼 어드민이 강제 초기화할 때 사용).
     * 영향받은 행이 0이면 IllegalStateException — 잘못된 adminId 또는 삭제된 계정.
     */
    public void resetAdminPassword(int id, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("비밀번호를 입력해주세요.");
        }
        int affected = adminMapper.updatePassword(id, passwordEncoder.encode(rawPassword));
        if (affected == 0) {
            throw new IllegalStateException(
                "해당 ID의 관리자 계정을 찾을 수 없습니다 (adminId=" + id + ")");
        }
        log.info("[InstitutionService] 기관 관리자 비밀번호 재설정 완료 adminId={}, affected={}", id, affected);
    }

    /**
     * 로그인 ID 변경 (슈퍼 어드민 전용).
     * 중복 시 mapper 단에서 {@link org.springframework.dao.DuplicateKeyException} 발생.
     * 영향받은 행이 0이면 IllegalStateException.
     */
    public void updateAdminLoginId(int id, String newLoginId) {
        if (newLoginId == null) {
            throw new IllegalArgumentException("로그인 ID를 입력해주세요.");
        }
        String trimmed = newLoginId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("로그인 ID를 입력해주세요.");
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("로그인 ID는 100자 이하여야 합니다.");
        }
        InstitutionAdmin before = adminMapper.findById(id);
        if (before == null) {
            throw new IllegalStateException("해당 ID의 관리자 계정을 찾을 수 없습니다 (adminId=" + id + ")");
        }
        if (trimmed.equals(before.getLoginId())) {
            log.info("[InstitutionService] 기관 관리자 로그인 ID 변경 — 변경 없음 (동일 값) adminId={}", id);
            return;
        }
        int affected = adminMapper.updateLoginId(id, trimmed);
        if (affected == 0) {
            throw new IllegalStateException("해당 ID의 관리자 계정을 찾을 수 없습니다 (adminId=" + id + ")");
        }
        log.info("[InstitutionService] 기관 관리자 로그인 ID 변경 완료 adminId={}, oldLoginId={}, newLoginId={}",
                id, before.getLoginId(), trimmed);
    }

    public void deactivateAdmin(int id) {
        adminMapper.deactivate(id);
    }

    public void activateAdmin(int id) {
        adminMapper.activate(id);
    }
}
