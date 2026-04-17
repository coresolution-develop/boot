package com.coresolution.pe.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.Institution;
import com.coresolution.pe.entity.InstitutionAdmin;
import com.coresolution.pe.mapper.InstitutionAdminMapper;
import com.coresolution.pe.mapper.InstitutionMapper;

import lombok.RequiredArgsConstructor;

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

    public Institution create(String code, String name) {
        Institution inst = new Institution();
        inst.setCode(code);
        inst.setName(name);
        inst.setActive(true);
        institutionMapper.insert(inst);
        return inst; // insert 후 id가 채워진 상태로 반환
    }

    public void update(int id, String code, String name, boolean isActive) {
        Institution inst = new Institution();
        inst.setId(id);
        inst.setCode(code);
        inst.setName(name);
        inst.setActive(isActive);
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
     * 비밀번호 재설정 (슈퍼 어드민이 강제 초기화할 때 사용)
     */
    public void resetAdminPassword(int id, String rawPassword) {
        adminMapper.updatePassword(id, passwordEncoder.encode(rawPassword));
    }

    public void deactivateAdmin(int id) {
        adminMapper.deactivate(id);
    }

    public void activateAdmin(int id) {
        adminMapper.activate(id);
    }
}
