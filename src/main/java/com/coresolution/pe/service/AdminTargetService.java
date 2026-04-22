package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.AdminCustomTarget;
import com.coresolution.pe.entity.AdminDefaultTarget;
import com.coresolution.pe.entity.DepartmentDto;
import com.coresolution.pe.entity.TargetRowDto;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.CustomTargetMapper;
import com.coresolution.pe.mapper.DefaultTargetMapper;
import com.coresolution.pe.mapper.LoginMapper;
import com.coresolution.pe.mapper.UserMapper;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTargetService {

    @Value("${app.current.eval-year}")
    private int currentEvalYear;
    private final UserMapper userMapper;
    private final LoginMapper loginMapper;
    private final DefaultTargetMapper defaultTargetMapper;
    private final CustomTargetMapper customTargetMapper;

    /** 부서별로 묶어서 DTO 반환 */
    public List<DepartmentDto> getDepartments(String year) {
        List<UserPE> all = loginMapper.getUserList(year);
        Map<String, List<UserPE>> map = all.stream()
                .collect(Collectors.groupingBy(UserPE::getSubName, LinkedHashMap::new, Collectors.toList()));
        return map.entrySet().stream()
                .map(e -> {
                    String subName = e.getKey();
                    List<UserPE> users = e.getValue();

                    // 같은 부서 사용자의 sub_code 중 첫 번째 값을 사용
                    String subCode = users.stream()
                            .map(UserPE::getSubCode)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);

                    DepartmentDto dto = new DepartmentDto(subName, users);
                    dto.setSubCode(subCode);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /** 경혁팀 전체 멤버 조회 */
    public List<UserPE> getGhTeamMembers(String year) {
        return userMapper.findByTeamCode("GH_TEAM", year);
    }

    /** 저장된 부서별 기본 대상 ID 목록 */
    public List<String> getDefaultDeptTargets(String year) {
        return defaultTargetMapper.findDeptTargetIds(year);
    }

    /** 저장된 팀별 기본 대상 ID 목록 */
    public List<String> getDefaultTeamTargets(String year) {
        return defaultTargetMapper.findTeamTargetIds(year);
    }

    /** 부서별 기본 대상 저장 */
    public void saveDefaultDeptTargets(String year, List<String> deptTargets) {
        defaultTargetMapper.deleteAllDept(year);
        for (String id : deptTargets) {
            defaultTargetMapper.insertDeptTarget(year, id);
        }
    }

    /** 팀별 기본 대상 저장 */
    public void saveDefaultTeamTargets(String year, List<String> teamTargets) {
        defaultTargetMapper.deleteAllTeam(year);
        for (String id : teamTargets) {
            defaultTargetMapper.insertTeamTarget(year, id);
        }
    }

    public int insertCustomOnly(String userId, String year,
            String targetId, String evalTypeCode, String dataEv, String dataType, String reason) {
        UserPE me = userMapper.findById(userId, year);
        UserPE tg = userMapper.findById(targetId, year);
        if (me == null || tg == null)
            throw new IllegalArgumentException("사용자 없음");
        if (!sameOrg(me, tg))
            throw new IllegalArgumentException("다른 기관은 추가할 수 없습니다.");
        return customTargetMapper.insertCustom(userId, year, targetId, evalTypeCode, dataEv, dataType, reason);
    }

    @Transactional
    public void removeCustomAddByIdx(int idx, String year, String targetId, String reason) {
        // idx -> userId
        UserPE user = loginMapper.findUserInfoByIdx(idx, currentEvalYear);
        String userId = user.getId();

        int updated = customTargetMapper.deactivateCustom(userId, year, targetId, reason);
        if (updated == 0) {
            // 이미 비활성화되었거나 행이 없는 경우: 굳이 실패로 보지 않아도 됨
        }
    }

    /** 커스텀 대상 비활성화 (userId 직접 — admin custom_new 화면용) */
    @Transactional
    public void removeCustomByUserId(String userId, String year, String targetId, String reason) {
        customTargetMapper.deactivateCustom(userId, year, targetId, reason);
    }

    /** 특정 평가자의 활성 커스텀 대상 목록 (상세) */
    public List<UserPE> getCustomTargetsList(String userId, String year) {
        return customTargetMapper.findCustomTargetsDetailed(userId, year);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean sameOrg(UserPE a, UserPE b) {
        return nz(a.getCName()).equals(nz(b.getCName()))
                && nz(a.getCName2()).equals(nz(b.getCName2()));
    }
}
