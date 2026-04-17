package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.EvalAnswer;
import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.mapper.EvalResultMapper;
import com.coresolution.pe.mapper.EvalSubmissionMapper;
import com.coresolution.pe.mapper.EvaluationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EvalSubmitService {

    private final EvaluationMapper evaluationMapper; // countByType(year, type)
    private final EvalSubmissionMapper submissionMapper;
    private final ObjectMapper om = new ObjectMapper();

    @Transactional(readOnly = true)
    public boolean isCompleted(String year, String evaluatorId, String targetId, String ev, String type) {
        int total = evaluationMapper.countByType(year, type);
        Integer answered = submissionMapper.findAnsweredCount(year, evaluatorId, targetId, type, ev);
        int answeredCount = answered == null ? 0 : answered;
        return total > 0 && answeredCount >= total;
    }

    @Transactional
    public void saveSubmission(String year, String evaluatorId, String targetId,
            String ev, String type,
            Map<String, String> radios, Map<String, String> essays) {

        // 1) 기존 활성본 소프트삭제 (del_yn='Y', is_active=0)
        submissionMapper.softDeleteActive(year, evaluatorId, targetId, type, ev, evaluatorId);

        // 2) 점수 계산
        int totalScore = 0;
        int radioCount = 0;
        if (radios != null) {
            for (String label : radios.values()) {
                int base = switch (label) {
                    case "매우우수" -> 5;
                    case "우수" -> 4;
                    case "보통" -> 3;
                    case "미흡" -> 2;
                    case "매우미흡" -> 1;
                    default -> 0;
                };
                if ("AA".equalsIgnoreCase(type))
                    base *= 2;
                totalScore += base;
                radioCount++;
            }
        }
        int answeredCount = (radios == null ? 0 : radios.size()) + (essays == null ? 0 : essays.size());
        double avgScore = radioCount > 0 ? (double) totalScore / radioCount : 0.0;

        // 3) JSON 생성
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("radios", radios == null ? Map.of() : radios);
        payload.put("essays", essays == null ? Map.of() : essays);
        String answersJson;
        try {
            answersJson = om.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("answers JSON serialize failed", e);
        }

        // 4) 다음 버전 번호
        int nextVer = submissionMapper.findMaxVersion(year, evaluatorId, targetId, type, ev) + 1;

        // 5) 신규 버전 INSERT
        EvalSubmissionRow row = EvalSubmissionRow.builder()
                .evalYear(Integer.parseInt(year)) // DB 컬럼이 INT
                .evaluatorId(evaluatorId)
                .targetId(targetId)
                .dataEv(ev)
                .dataType(type)
                .answersJson(answersJson)
                .answeredCount(answeredCount)
                .radioCount(radioCount)
                .totalScore(totalScore)
                .avgScore(avgScore)
                .version(nextVer)
                .build();

        submissionMapper.insertOne(row);
    }

    /**
     * 수정 저장: 기존 활성본을 del_yn='Y', is_active=0 처리 후
     * 새 버전( is_active=1, del_yn='N' )으로 INSERT
     */
    @Transactional
    public void saveNewVersion(String evaluatorId, String targetId, String year,
            String dataType, String dataEv,
            String answersJson,
            int answeredCount, int radioCount,
            int totalScore, double avgScore) {

        // 1) 기존 활성본 소프트삭제 (없으면 0건)
        submissionMapper.softDeleteActive(year, evaluatorId, targetId, dataType, dataEv, evaluatorId);

        // 2) 다음 버전 번호
        int nextVersion = submissionMapper.findMaxVersion(year, evaluatorId, targetId, dataType, dataEv) + 1;

        // 3) 새 버전 INSERT (활성)
        EvalSubmissionRow s = new EvalSubmissionRow();
        s.setEvalYear(Integer.parseInt(year)); // 컬럼은 INT이므로 int로 넣음
        s.setEvaluatorId(evaluatorId);
        s.setTargetId(targetId);
        s.setDataEv(dataEv);
        s.setDataType(dataType);
        s.setAnswersJson(answersJson); // 반드시 유효한 JSON 문자열
        s.setAnsweredCount(answeredCount);
        s.setRadioCount(radioCount);
        s.setTotalScore(totalScore);
        s.setAvgScore(avgScore);
        s.setVersion(nextVersion);
        s.setUpdatedBy(evaluatorId);

        try {
            submissionMapper.insertOne(s);
        } catch (DuplicateKeyException e) {
            // 동시성 경합 시 한번 더 비활성화 후 재시도
            submissionMapper.softDeleteActive(year, evaluatorId, targetId, dataType, dataEv, evaluatorId);
            nextVersion = submissionMapper.findMaxVersion(year, evaluatorId, targetId, dataType, dataEv) + 1;
            s.setVersion(nextVersion);
            submissionMapper.insertOne(s);
        }
    }
}