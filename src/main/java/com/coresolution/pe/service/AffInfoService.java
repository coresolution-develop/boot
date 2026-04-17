package com.coresolution.pe.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.Progress;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffCustomTargetMapper;
import com.coresolution.pe.mapper.AffDefaultTargetMapper;
import com.coresolution.pe.mapper.AffEvalSubmissionMapper;
import com.coresolution.pe.mapper.AffEvaluationMapper;
import com.coresolution.pe.mapper.AffUserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffInfoService {
    private final AffDefaultTargetMapper defMap;
    private final AffCustomTargetMapper customMap;
    private final AffUserMapper userMapper;
    private final AffEvalSubmissionMapper affSubmissionMapper;
    private final AffEvaluationMapper affEvaluationMapper; // ← 총문항 수 조회용(AD/… 타입)
    private final PaperTypeResolver paperTypeResolver;

    private static final Logger log = LoggerFactory.getLogger(AffInfoService.class);

    /** 최종 대상(중복 제거: 기본 → 커스텀 ADD 덮어쓰기 → 커스텀 REMOVE 제거) */
    @Transactional(readOnly = true)
    public List<UserPE> getFinalTargets(String userId, int year) {
        // 1) 기본
        List<UserPE> defaults = defMap.findDefaultTargetsDetailed(userId, year);
        // 2) 커스텀 ADD
        List<UserPE> adds = customMap.findCustomTargetsDetailed(userId, year);
        // 3) 커스텀 REMOVE
        List<String> removes = customMap.findActiveRemoves(userId, year);

        // id 기준 병합 (커스텀 ADD가 최종 우선)
        var map = new java.util.LinkedHashMap<String, UserPE>();
        defaults.forEach(u -> map.put(u.getId(), u));
        adds.forEach(u -> map.put(u.getId(), u));
        removes.forEach(map::remove);

        return new java.util.ArrayList<>(map.values());
    }

    /** Info용: 타입별 그룹핑(라벨 Key 순서 보장) */
    @Transactional(readOnly = true)
    public java.util.Map<String, java.util.List<UserPE>> getFinalTargetsGroupedByType(String userId, int year) {

        // 기본/커스텀 타입 소스 취합 (커스텀 우선)
        var typeById = new java.util.LinkedHashMap<String, String>();
        defMap.findTargetsWithType(userId, year)
                .forEach(r -> typeById.put(r.getTargetId(), safeType(r.getEvalTypeCode())));
        customMap.findActiveAddsWithType(userId, year)
                .forEach(r -> typeById.put(r.getTargetId(), safeType(r.getEvalTypeCode())));
        customMap.findActiveRemoves(userId, year).forEach(typeById::remove);

        // User 정보 붙여 그룹핑
        var grouped = new java.util.LinkedHashMap<String, java.util.List<UserPE>>();
        getTypeLabels().keySet().forEach(k -> grouped.put(k, new java.util.ArrayList<>()));

        typeById.forEach((tid, type) -> {
            UserPE u = userMapper.findById(tid, year);
            if (u == null)
                return;
            grouped.computeIfAbsent(type, __ -> new java.util.ArrayList<>()).add(u);
        });
        return grouped;
    }

    /** 메타맵: 기본 우선 후 커스텀 덮어쓰기(필요 시 순서 반대로 바꿔도 됨) */
    @Transactional(readOnly = true)
    public Map<String, PairMeta> loadMetaMap(String userId, int year) {
        var meta = new java.util.LinkedHashMap<String, PairMeta>();
        defMap.findDefaultMeta(userId, year).forEach(pm -> meta.put(pm.getTargetId(), pm));
        customMap.findCustomMeta(userId, year).forEach(pm -> meta.put(pm.getTargetId(), pm));
        return meta;
    }

    /** evaluator(=userId)가 각 target을 평가한 진행상태/점수 맵 */
    @Transactional(readOnly = true)
    public Map<String, Progress> buildProgressMap(String userId, int year, List<UserPE> targets) {
        Map<String, Progress> map = new LinkedHashMap<>();
        if (targets == null || targets.isEmpty())
            return map;

        for (UserPE t : targets) {
            final String targetId = t.getId();

            // 1) 기본 ev (룰)
            final String defaultEv = resolveEvalTypeForTarget(userId, t); // A/B/C/D

            // 2) 기본 paper (룰)
            final String cName = (t.getCName() != null && !t.getCName().isBlank())
                    ? t.getCName()
                    : t.getCName2();
            final String defaultPaper = paperTypeResolver.resolveByCName(cName, defaultEv); // AC/AD/AE

            // 3) 제출본 조회 (기존 그대로)
            EvaluationSubmission s = affSubmissionMapper.findActiveStrict(year, userId, targetId, defaultPaper,
                    defaultEv);
            if (s == null)
                s = affSubmissionMapper.findLatestStrict(year, userId, targetId, defaultPaper, defaultEv);
            if (s == null)
                s = affSubmissionMapper.findLatestLoose(year, userId, targetId);

            // 4) 실제 사용할 ev/type 확정
            String usedEv = defaultEv;
            String usedType = defaultPaper;

            if (s != null) {
                if (s.getDataEv() != null && !s.getDataEv().isBlank()) {
                    usedEv = s.getDataEv();
                }
                if (s.getDataType() != null && !s.getDataType().isBlank()) {
                    usedType = s.getDataType();
                }
            }

            // 5) 최종 타입 기준으로 문항 수 재계산
            final int total = affEvaluationMapper.countByType(year, usedType);

            // 6) Progress 채우기
            Progress p = new Progress();
            p.setTotal(total);
            p.setDataType(usedType); // ✅ DB 기준
            p.setDataEv(usedEv); // ✅ DB 기준

            if (s != null) {
                int answered = Optional.ofNullable(s.getAnsweredCount()).orElse(0);
                p.setAnswered(answered);
                p.setTotalScore(s.getTotalScore());
                p.setAvg(s.getAvgScore());
                p.setCompleted(total > 0 && answered >= total);
            } else {
                p.setAnswered(0);
                p.setCompleted(false);
            }
            log.debug(
                    "[AFF-progress] evaluator={}, target={}, usedEv={}, usedType={}, total={}, answered={}, completed={}",
                    userId, targetId,
                    usedEv, usedType,
                    total,
                    (s != null ? s.getAnsweredCount() : 0),
                    (s != null && total > 0 && s.getAnsweredCount() >= total));
            map.put(targetId, p);
        }
        return map;
    }

    /**
     * 평가유형(A/B/C/D) 추출 규칙:
     * - UserPE.evalTypeCode 끝글자가 A/B/C/D면 그 값을 사용
     * - 없으면 기본값 "A"
     */
    private String resolveEvalTypeForTarget(String evaluatorId, UserPE t) {
        String code = t.getEvalTypeCode();
        if (code != null && !code.isBlank()) {
            char last = code.charAt(code.length() - 1);
            if (last == 'A' || last == 'B' || last == 'C' || last == 'D') {
                return String.valueOf(last);
            }
        }
        return "A";
    }

    /** 라벨 사전 — 축약 코드 패턴 */
    public java.util.LinkedHashMap<String, String> getTypeLabels() {
        var m = new java.util.LinkedHashMap<String, String>();
        // 필요 라벨만 노출(예시)
        m.put("SHEAD_TO_SMEMBER", "부서장→부서원(부서)");
        m.put("SMEMBER_TO_SHEAD", "부서원→부서장(부서)");
        m.put("SHEAD_TO_SHEAD", "부서장↔부서장(부서)");
        m.put("SMEMBER_TO_SMEMBER", "부서원↔부서원(부서)");
        m.put("AHEAD_TO_AMEMBER", "소속장→구성원(소속)");
        m.put("AMEMBER_TO_AHEAD", "구성원→소속장(소속)");
        m.put("AHEAD_TO_AHEAD", "소속장↔소속장(소속)");
        m.put("OMEMBER_TO_OHEAD", "구성원→기관장(기관)");
        m.put("OHEAD_TO_OMEMBER", "기관장→구성원(기관)");
        m.put("OHEAD_TO_OHEAD", "기관장↔기관장(기관)");
        return m;
    }

    private static String safeType(String t) {
        return (t == null || t.isBlank()) ? "SMEMBER_TO_SMEMBER" : t.trim();
    }
}