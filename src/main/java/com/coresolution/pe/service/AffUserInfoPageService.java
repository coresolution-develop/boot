package com.coresolution.pe.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffEvalSubmissionMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffUserInfoPageService {

    private final AffEvalSubmissionMapper affSubmissionMapper;

    /** targetId 기준 받은 평가 목록 맵 */
    @Transactional(readOnly = true)
    public Map<String, List<EvaluationSubmission>> loadReceivedMap(int year, List<UserPE> targets) {
        Map<String, List<EvaluationSubmission>> map = new LinkedHashMap<>();
        if (targets == null || targets.isEmpty())
            return map;

        for (UserPE t : targets) {
            String targetId = t.getId();
            List<EvaluationSubmission> list = affSubmissionMapper.selectReceivedAll(
                    year, targetId);
            map.put(targetId, list == null ? List.of() : list);
        }
        return map;
    }

    /** targetId 기준 받은 평가 건수 맵 */
    @Transactional(readOnly = true)
    public Map<String, Integer> loadReceivedCountMap(int year, List<UserPE> targets) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (targets == null || targets.isEmpty())
            return map;

        for (UserPE t : targets) {
            String targetId = t.getId();
            int cnt = affSubmissionMapper.countReceivedList(year, targetId);
            map.put(targetId, cnt);
        }
        return map;
    }
}