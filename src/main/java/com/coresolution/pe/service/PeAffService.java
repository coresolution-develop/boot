package com.coresolution.pe.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coresolution.pe.controller.AffExcelContoller;
import com.coresolution.pe.entity.Department;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.NoticeVo;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffAdminMapper;
import com.coresolution.pe.mapper.AffExcelMapper;
import com.coresolution.pe.mapper.AffLoginMapper;

@Service
public class PeAffService {

    private final AffLoginMapper loginMapper;
    private final AffExcelMapper excelMapper;
    private final AffAdminMapper adminmapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public PeAffService(AffLoginMapper loginMapper, AffExcelMapper excelMapper, AffAdminMapper adminmapper) {
        this.loginMapper = loginMapper;
        this.excelMapper = excelMapper;
        this.adminmapper = adminmapper;
    }

    /** 로그인 성공 시 사용할 idx 조회 */
    public Integer findIdx(String id) {
        return loginMapper.findIdxById(id, currentEvalYear);
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

    public List<SubManagement> getSubManagement(String year) {
        return loginMapper.getSubManagement(year);
    }

    public String getSubcode(String subName, int year) {
        return loginMapper.getSubcode(subName, year);
    }

    public UserPE findUserById(String id, int year) {
        // 사번으로 유저 정보 조회 (연도별 테이블)
        UserPE user = loginMapper.findById(id, year);
        if (user == null) {
            System.out.println("사용자를 찾을 수 없습니다: " + id);
            return null;
        }
        System.out.println("사용자 정보 조회: " + user + " from ");
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

    public int countByCodeAndYear(String sub_code, int year) {
        return excelMapper.countByCodeAndYear(sub_code, year);
    }

    public void subupdate(SubManagement s) {
        excelMapper.subupdate(s);
    }

    public void subinsert(SubManagement s) {
        excelMapper.subinsert(s);
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
        System.out.println("AUTO_INCREMENT가 초기화되었습니다.");
    }

    public UserPE findUserInfoByIdx(int idx) {
        return loginMapper.findUserInfoByIdx(idx, currentEvalYear);
    }

    // 사용중 (year정보 없음)
    public int updateUserPassword(UserPE user) {
        int result = loginMapper.updateUserPassword(user);
        System.out.println("비밀번호 업데이트: " + user.getId() + ", 새로운 비밀번호: " + user.getPwd());
        return result;
    }

    public List<Evaluation> getEvaluation(String year) {
        return adminmapper.getEvaluation(year);
    }

    public UserPE findByUserIdWithNames(String userId, int year) {
        // 사번으로 유저 정보 조회 (연도별 테이블) + 이름 필드 추가
        UserPE user = loginMapper.findByIdWithNames(userId, year);
        if (user == null) {
            System.out.println("사용자를 찾을 수 없습니다: " + userId);
            return null;
        }
        System.out.println("findByUserIdWithNames 사용자 정보 조회: " + user + " from year: " + year);
        return user;
    }

    public boolean existsByUserIdAndPhone(String id, int year, String phone) {
        int cnt = loginMapper.findByUserIdWithPhone(id, year, phone);
        return cnt > 0;
    }

    public boolean changePasswordByUserIdAndYear(String userId, String year, String encoded) {
        int updated = loginMapper.changePasswordByUserIdAndYear(userId, year, encoded);
        return updated > 0;
    }

    public UserPE findById(String id, int year) {
        return loginMapper.findById(id, year);
    }
}
