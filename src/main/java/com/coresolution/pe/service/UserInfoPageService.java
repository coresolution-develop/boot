package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.PairMeta;
import com.coresolution.pe.entity.Progress;
import com.coresolution.pe.entity.TargetBuckets;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.DefaultTargetMapper;
import com.coresolution.pe.mapper.EvalResultMapper;
import com.coresolution.pe.mapper.EvalSubmissionMapper;
import com.coresolution.pe.mapper.EvaluationMapper;
import com.coresolution.pe.mapper.LoginMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserInfoPageService {

    private final LoginMapper pe; // pe.findByUserIdWithNames(...)
    private final EvaluationService evaluationService; // getFinalTargets(...)
    private final DefaultTargetMapper defaultTargetMapper;
    private final EvaluationMapper evaluationMapper; // countByType(...)
    private final EvalResultMapper evalResultMapper; // countAnswered/avgScore...
    private final EvalSubmissionMapper submissionMapper; // ← 새로 주입 (JSON 테이블)

    @Transactional(readOnly = true)
    public UserPE loadUserInfo(String userId, String year) {
        return pe.findByIdWithNames(userId, year);
    }

    @Transactional(readOnly = true)
    public TargetBuckets computeBuckets(String userId, String year) {
        List<UserPE> targets = evaluationService.getFinalTargets(userId, year);

        Comparator<UserPE> byDeptThenName = Comparator
                .comparing((UserPE u) -> u.getSubName(), Comparator.nullsLast(String::compareTo))
                .thenComparing(UserPE::getName, Comparator.nullsLast(String::compareTo));

        List<UserPE> sorted = new ArrayList<>(targets);
        sorted.sort(byDeptThenName);

        List<UserPE> ghTeam = new ArrayList<>();
        List<UserPE> medicalDepts = new ArrayList<>();
        List<UserPE> subHeads = new ArrayList<>();
        List<UserPE> subMembers = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (UserPE u : sorted) {
            if (!seen.add(u.getId()))
                continue;

            boolean inGh = hasRole(u, "team_head") || hasRole(u, "team_member");
            boolean inMedical = isMedicalDept(u);
            boolean inSubHead = hasRole(u, "sub_head");
            boolean inSubMem = hasRole(u, "sub_member");

            if (inGh) {
                ghTeam.add(u);
                continue;
            }
            if (inMedical) {
                medicalDepts.add(u);
                continue;
            }
            if (inSubHead) {
                subHeads.add(u);
                continue;
            }
            if (inSubMem) {
                subMembers.add(u);
                continue;
            }
        }
        return new TargetBuckets(ghTeam, medicalDepts, subHeads, subMembers);
    }

    @Transactional(readOnly = true)
    public Map<String, PairMeta> loadMetaMap(String userId, String year) {
        return defaultTargetMapper.findMetaForUserTyped(userId, year).stream()
                .collect(Collectors.toMap(PairMeta::getTargetId, m -> m));
    }

    @Transactional(readOnly = true)
    public Map<String, Progress> buildProgressMap(String userId, String year, TargetBuckets buckets) {
        int totalAA = evaluationMapper.countByType(year, "AA");
        int totalAB = evaluationMapper.countByType(year, "AB");

        Map<String, PairMeta> metaMap = loadMetaMap(userId, year);
        Map<String, Progress> progressMap = new HashMap<>();

        for (String targetId : buckets.allTargetIds()) {
            PairMeta meta = metaMap.get(targetId); // null 가능

            // ① 우선순위: 활성+정합 → 정합 → 느슨(최신)
            EvaluationSubmission s = null;
            if (meta != null) {
                s = submissionMapper.findActiveStrict(year, userId, targetId, meta.getDataType(), meta.getDataEv());
                if (s == null)
                    s = submissionMapper.findLatestStrict(year, userId, targetId, meta.getDataType(), meta.getDataEv());
            }
            if (s == null)
                s = submissionMapper.findLatestLoose(year, userId, targetId);

            // ② 총문항 수 계산 기준(type): meta 우선, 없으면 제출의 값 사용
            String typeForCount = meta != null && meta.getDataType() != null
                    ? meta.getDataType()
                    : (s != null ? s.getDataType() : "AA");

            int total = "AA".equalsIgnoreCase(typeForCount) ? totalAA : totalAB;

            Progress p = new Progress();
            p.setTotal(total);

            if (s != null) {
                p.setAnswered(s.getAnsweredCount() != null ? s.getAnsweredCount() : 0);
                p.setTotalScore(s.getTotalScore());
                p.setAvg(s.getAvgScore());
                p.setCompleted(total > 0 && p.getAnswered() >= total);
                // ★ 링크 파라미터 대체
                p.setDataType(s.getDataType());
                p.setDataEv(s.getDataEv());
            } else {
                // 제출 없음: 최소한 링크 파라미터는 meta로 제공
                p.setAnswered(0);
                p.setCompleted(false);
                if (meta != null) {
                    p.setDataType(meta.getDataType());
                    p.setDataEv(meta.getDataEv());
                } else {
                    // 완전 무배정/무제출인 극단 케이스
                    p.setDataType("AA");
                    p.setDataEv("C");
                }
            }
            progressMap.put(targetId, p);
        }
        return progressMap;
    }

    // ── helpers ───────────────────────────────────────────────
    private boolean hasRole(UserPE u, String role) {
        return u.getRoles() != null && u.getRoles().contains(role);
    }

    private boolean isMedicalDept(UserPE u) {
        String sub = u.getSubCode();
        return sub != null && sub.startsWith("A"); // A00/A01/A02...
    }
}