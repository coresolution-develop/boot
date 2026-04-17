package com.coresolution.pe.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.AnswerPayload;
import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.MyPageSubmissionDTO;
import com.coresolution.pe.mapper.EvalSubmissionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyPageEvalService {
    private final EvalSubmissionMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    public record Page<T>(List<T> content, int total, int page, int size) {
    }

    private static int keyOrder(String k) {
        // 끝의 숫자 추출 → 없으면 아주 큰 값으로
        var m = java.util.regex.Pattern.compile("(\\d+)$").matcher(k == null ? "" : k);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private static List<Map.Entry<String, String>> toSortedList(Map<String, String> map) {
        if (map == null || map.isEmpty())
            return java.util.List.of();
        return map.entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> keyOrder(e.getKey())))
                .toList();
    }

    private MyPageSubmissionDTO toDTO(EvaluationSubmission e) {
        AnswerPayload payload = new AnswerPayload(); // 기본값
        try {
            if (e.getAnswersJson() != null && !e.getAnswersJson().isBlank()) {
                payload = om.readValue(e.getAnswersJson(), AnswerPayload.class);
            }
        } catch (Exception ex) {
            // 필요하면 로깅
            payload = new AnswerPayload();
        }
        // 1) 평가의견(essays): 우선 t43 키 사용, 없으면 첫 번째 값
        String essay = null;
        if (payload.getEssays() != null && !payload.getEssays().isEmpty()) {
            essay = payload.getEssays().get("t43");
            if (essay == null) {
                // 첫 번째 값으로 대체
                var it = payload.getEssays().values().iterator();
                essay = it.hasNext() ? it.next() : null;
            }
        }

        // 2) 라디오 값 정렬 후 20칸으로 패딩
        java.util.List<String> radioVals20 = new java.util.ArrayList<>(java.util.Collections.nCopies(20, null));
        if (payload.getRadios() != null && !payload.getRadios().isEmpty()) {
            var ordered = payload.getRadios().entrySet().stream()
                    .sorted(java.util.Comparator.comparing(e1 -> keyOrder(e1.getKey())))
                    .map(Map.Entry::getValue) // 값만
                    .toList();
            for (int i = 0; i < Math.min(20, ordered.size()); i++) {
                radioVals20.set(i, ordered.get(i));
            }
        }
        return MyPageSubmissionDTO.builder()
                .id(e.getId())
                .evalYear(e.getEvalYear())
                .evaluatorId(e.getEvaluatorId())
                .targetId(e.getTargetId())
                .dataEv(e.getDataEv())
                .dataType(e.getDataType())
                .answeredCount(e.getAnsweredCount())
                .totalScore(e.getTotalScore())
                // ★ avgScore는 이제 안 씀 (필요없다 했으므로 제거)
                .version(e.getVersion())
                .isActive(e.getIsActive() != null && e.getIsActive() == 1)
                .delYn(e.getDelYn())
                .updatedAt(e.getUpdatedAt())
                .answers(payload)
                .rawAnswersJson(e.getAnswersJson()) // (옵션) 원본 JSON 보기용
                .radiosList(toSortedList(payload.getRadios()))
                .essaysList(toSortedList(payload.getEssays()))
                .evaluatorName(e.getEvaluatorName())
                .evaluatorDept(e.getEvaluatorDept())
                .evaluatorPos(e.getEvaluatorPos())
                .essayText(essay) // ★
                .radioVals20(radioVals20) // ★ (비어도 20칸 존재)
                .build();
    }

    /** 전체 목록 */
    public List<MyPageSubmissionDTO> receivedAll(String year, String targetId) {
        return mapper.selectReceivedAll(year, targetId).stream().map(this::toDTO).toList();
    }

    public MyPageSubmissionDTO detail(Long id) {
        EvaluationSubmission row = mapper.selectById(id);
        return row == null ? null : toDTO(row);
    }

    public List<Map<String, Object>> byRelationAgg(String year, String targetId) {
        return mapper.aggregateByRelation(year, targetId);
    }
}
