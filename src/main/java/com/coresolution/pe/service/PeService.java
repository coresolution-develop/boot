package com.coresolution.pe.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.NoticeVo;
import com.coresolution.pe.entity.ReleaseGate;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.TargetBuckets;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AdminMapper;
import com.coresolution.pe.mapper.ExcelMapper;
import com.coresolution.pe.mapper.LoginMapper;
import com.coresolution.pe.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;

@Service

@Slf4j
public class PeService {

    private final LoginMapper loginMapper;
    private final ExcelMapper excelMapper;
    private final AdminMapper adminmapper;
    private final UserMapper userMapper;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public PeService(LoginMapper loginMapper, ExcelMapper excelMapper, AdminMapper adminmapper, UserMapper userMapper) {
        this.loginMapper = loginMapper;
        this.excelMapper = excelMapper;
        this.adminmapper = adminmapper;
        this.userMapper = userMapper;
    }

    public List<NoticeVo> notice() {
        return loginMapper.findAll();
    }

    public int login(String id, String credential, String loginType) {
        log.debug("PeService.login: id=" + id + ", credential=" + credential
                + ", loginType=" + loginType);

        if (!"byName".equals(loginType)) {
            throw new IllegalArgumentException("byPwd는 CustomAuthenticationProvider에서 처리합니다.");
        }

        // 🔴 1) 먼저 해당 사번이 '삭제된 직원'인지 확인
        UserPE user = userMapper.findByIdForLogin(id, String.valueOf(currentEvalYear));
        if (user != null && "Y".equalsIgnoreCase(user.getDelYn())) {
            return 0; // 평가 대상자가 아님
        }

        // 🔵 2) 기존 사번+이름 / 비밀번호 설정 여부 로직
        int cnt = loginMapper.countByIdAndName(id, credential, currentEvalYear);
        if (cnt == 0)
            return 3; // 이름 불일치

        int pwdCnt = loginMapper.countPwdById(id, currentEvalYear);
        return pwdCnt == 0 ? 5 : 2;
    }

    /** 로그인 성공 시 사용할 idx 조회 */
    public Integer findIdx(String id) {
        return loginMapper.findIdxById(id, currentEvalYear);
    }

    public UserPE findById(String id, int year) {
        return loginMapper.findById(id, year);
    }

    public UserPE findUserInfoByIdx(int idx) {
        return loginMapper.findUserInfoByIdx(idx, currentEvalYear);
    }

    // 사용중 (year정보 없음)
    public int updateUserPassword(UserPE user) {
        int result = loginMapper.updateUserPassword(user);
        log.debug("비밀번호 업데이트: " + user.getId() + ", 새로운 비밀번호: " + user.getPwd());
        return result;
    }

    public UserPE findUserInfoById(String id, int year) {
        // 사번으로 유저 정보 조회
        UserPE user = loginMapper.findById(id, year);
        if (user == null) {
            log.debug("사용자를 찾을 수 없습니다: " + id);
            return null;
        }
        log.debug("사용자 정보 조회: " + user);
        return user;
    }

    public List<UserPE> getUserList(String year) {
        return loginMapper.getUserList(year);
    }

    public String getSubcode(String subName, int year) {
        return loginMapper.getSubcode(subName, year);
    }

    public UserPE findUserById(String id, int year) {
        // 사번으로 유저 정보 조회 (연도별 테이블)
        UserPE user = loginMapper.findById(id, year);
        if (user == null) {
            log.debug("사용자를 찾을 수 없습니다: " + id);
            return null;
        }
        log.debug("사용자 정보 조회: " + user + " from ");
        return user;
    }

    public void getUserExcelUpdate(UserPE u) {
        excelMapper.getUserExcelUpdate(u);
    }

    public void getUserExcelUpload(UserPE u) {
        excelMapper.getUserExcelUpload(u);
    }

    public void getRoleDelete(int year) {
        excelMapper.getRoleDelete(year);
    }

    public void getRoleExcelUpload(String user_id, String name, int year) {
        excelMapper.getRoleExcelUpload(user_id, name, year);
    }

    public List<SubManagement> getPendingDepartments(int year) {
        return excelMapper.getPendingDepartments(year);
    }

    public void subDelete(int year) {
        excelMapper.subDelete(year);
    }

    public void getSubExcelUpload(SubManagement sub) {
        excelMapper.getSubExcelUpload(sub);
    }

    public int countByCodeAndYear(String sub_code, int year) {
        return excelMapper.countByCodeAndYear(sub_code, year);
    }

    public void subupdate(SubManagement s) {
        excelMapper.subupdate(s);
    }

    public void subinsert(SubManagement s) {
        excelMapper.subinsert(s);
    }

    public List<SubManagement> getSubManagement(String year) {
        return loginMapper.getSubManagement(year);
    }

    public void deleteRolesByYearExcept(int year, String adminId) {
        loginMapper.deleteRolesByYearExcept(year, adminId);
    }

    public void deleteUsersByYearExcept(int year, String adminId) {
        loginMapper.deleteUsersByYearExcept(year, adminId);
    }

    public void deleteByYearExcept(int year, String adminSubCode) {
        loginMapper.deleteByYearExcept(year, adminSubCode);
    }

    public void resetAutoIncrement() {
        loginMapper.resetAutoIncrement();
        log.debug("AUTO_INCREMENT가 초기화되었습니다.");
    }

    public UserPE findByUserId(String userId, String year) {
        // 사번으로 유저 정보 조회 (연도별 테이블)
        UserPE user = loginMapper.findByIdandyear(userId, year);
        if (user == null) {
            log.debug("사용자를 찾을 수 없습니다: " + userId);
            return null;
        }
        log.debug("findByUserId 사용자 정보 조회: " + user + " from year: " + year);
        return user;
    }

    public UserPE findByUserIdWithNames(String userId, String year) {
        // 사번으로 유저 정보 조회 (연도별 테이블) + 이름 필드 추가
        UserPE user = loginMapper.findByIdWithNames(userId, year);
        if (user == null) {
            log.debug("사용자를 찾을 수 없습니다: " + userId);
            return null;
        }
        log.debug("findByUserIdWithNames 사용자 정보 조회: " + user + " from year: " + year);
        return user;
    }

    public List<Evaluation> getEvaluation(String year) {
        return adminmapper.getEvaluation(year);
    }

    public int findByUserIdWithPhone(String id, String ph, String year) {
        return loginMapper.findByUserIdWithPhone(id, ph, year);
    }

    public boolean changePasswordByUserIdAndYear(String userId, String year, String encoded) {
        int updated = loginMapper.changePasswordByUserIdAndYear(userId, year, encoded);
        return updated > 0;
    }

    public List<UserPE> getUserListpage(String year, String q, String dept, String pwd,
            String org, String delYn, String role, int offset, int size) {
        return loginMapper.getUserListpage(year, q, dept, pwd, org, delYn, role, offset, size);
    }

    public int countUsers(String year, String q, String dept, String pwd,
            String org, String delYn, String role) {
        return loginMapper.countUsers(year, q, dept, pwd, org, delYn, role);
    }

    public List<Department> getDepartments(String year) {
        return loginMapper.getDepartments(year);
    }

    public List<String> getOrganizations(String year) {
        return loginMapper.getOrganizations(year);
    }

    public List<Department> getDepartmentsByOrg(String year, String org) {
        return loginMapper.getDepartmentsByOrg(year, org);
    }

    public boolean existsByUserIdAndPhone(String id, String year, String ph) {
        int cnt = loginMapper.findByUserIdWithPhone(id, year, ph);
        return cnt > 0;
    }

    // ── 기관 관리자 전용 ──────────────────────────────────────────────────

    /** idx + institutionName 으로 직원 상세 조회 (기관 범위 검증 포함) */
    public UserPE findUserByIdxAndOrg(int idx, String year, String institutionName) {
        return loginMapper.findUserByIdxAndOrg(idx, year, institutionName);
    }

    /** 평가제외 여부 변경 */
    public boolean updateDelYn(int idx, String year, String delYn) {
        return loginMapper.updateDelYn(idx, year, delYn) > 0;
    }

    /** 비밀번호 초기화 (NULL 설정) */
    public boolean resetPasswordByIdx(int idx, String year) {
        return loginMapper.resetPasswordByIdx(idx, year) > 0;
    }

    /** 역할 전체 교체 (삭제 후 재삽입) */
    @Transactional
    public void updateRoles(String userId, String year, List<String> roles) {
        loginMapper.deleteRolesByUserId(userId, year);
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) {
                    loginMapper.insertRoleForUser(userId, role.toLowerCase(), year);
                }
            }
        }
    }

    /** 기관 범위 직원 + 역할 초기화 (기관 관리자 전용) */
    @Transactional
    public void resetByInstitution(String year, String institutionName) {
        loginMapper.deleteRolesByYearAndOrg(year, institutionName);
        loginMapper.deleteUsersByYearAndOrg(year, institutionName);
    }

}
