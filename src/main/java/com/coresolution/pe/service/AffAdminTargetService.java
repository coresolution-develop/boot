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
import com.coresolution.pe.mapper.AffCustomTargetMapper;
import com.coresolution.pe.mapper.AffDefaultTargetMapper;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.mapper.CustomTargetMapper;
import com.coresolution.pe.mapper.DefaultTargetMapper;
import com.coresolution.pe.mapper.LoginMapper;
import com.coresolution.pe.mapper.UserMapper;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffAdminTargetService {
    private final AffUserMapper userMapper;
    private final AffLoginMapper loginMapper;
    private final AffDefaultTargetMapper defaultTargetMapper;
    private final AffCustomTargetMapper customTargetMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    /** 부서별로 묶어서 DTO 반환 */
    public List<DepartmentDto> getDepartments(int year) {
        List<UserPE> all = loginMapper.getUserList(year);
        if (all == null || all.isEmpty()) {
            return List.of();
        }

        // null key 방지: subCode 기준으로 그룹핑 (null → 빈 문자열)
        Map<String, List<UserPE>> map = all.stream()
                .collect(Collectors.groupingBy(
                        u -> nz(u.getSubCode()), // ✅ null 이면 "" 로 치환
                        LinkedHashMap::new,
                        Collectors.toList()));

        return map.entrySet().stream()
                .map(e -> {
                    String subCodeKey = e.getKey(); // 실제 sub_code (없으면 "")
                    List<UserPE> users = e.getValue();

                    // 화면에 보여줄 부서명: 그룹 내에서 첫 번째로 발견되는 subName
                    String subName = users.stream()
                            .map(UserPE::getSubName)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse("(부서없음)");

                    DepartmentDto dto = new DepartmentDto(subName, users);

                    // DepartmentDto 에 subCode 세팅 (빈 문자열이면 null 처리)
                    dto.setSubCode(subCodeKey.isEmpty() ? null : subCodeKey);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public int insertCustomOnly(String userId, int year,
            String targetId, String evalTypeCode, String dataEv, String dataType, String reason) {

        UserPE me = userMapper.findById(userId, year);
        UserPE tg = userMapper.findById(targetId, year);

        if (me == null || tg == null) {
            throw new IllegalArgumentException("사용자 없음");
        }

        // 🔵 기관이 달라도 허용 (기존 sameOrg 제한 제거)
        // 필요하다면 로깅 정도만 남겨두기
        /*
         * if (!sameOrg(me, tg)) {
         * log.
         * warn("[AFF][CUSTOM] cross-org custom target: evaluator={}({}/{}) -> target={}({}/{})"
         * ,
         * me.getId(), nz(me.getCName()), nz(me.getCName2()),
         * tg.getId(), nz(tg.getCName()), nz(tg.getCName2()));
         * }
         */

        return customTargetMapper.insertCustom(userId, year, targetId, evalTypeCode, dataEv, dataType, reason);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean sameOrg(UserPE a, UserPE b) {
        return nz(a.getCName()).equals(nz(b.getCName()))
                && nz(a.getCName2()).equals(nz(b.getCName2()));
    }

    /** 커스텀 추가 대상만 evalTypeCode 별로 그룹핑 */
    public Map<String, List<UserPE>> getCustomTargetsGroupedByType(String userId, int year) {
        List<UserPE> list = customTargetMapper.findCustomTargetsDetailed(userId, year);
        Map<String, List<UserPE>> grouped = new LinkedHashMap<>();
        if (list == null) {
            return grouped;
        }

        for (UserPE u : list) {
            if (u == null)
                continue;
            String key = u.getEvalTypeCode();
            if (key == null || key.isBlank()) {
                key = "UNKNOWN";
            } else {
                key = key.trim();
            }
            grouped.computeIfAbsent(key, __ -> new ArrayList<>()).add(u);
        }
        return grouped;
    }

    @Transactional
    public void removeCustomAddByIdx(int idx, int year, String targetId, String reason) {
        // idx -> userId
        UserPE user = loginMapper.findUserInfoByIdx(idx, currentEvalYear);
        String userId = user.getId();

        int updated = customTargetMapper.deactivateCustom(userId, year, targetId, reason);
        if (updated == 0) {
            // 이미 비활성 or 행 없음 → 무시
        }
    }
}
