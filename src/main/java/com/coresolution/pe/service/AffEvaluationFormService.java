package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.FormPayload;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffEvalSubmissionMapper;
import com.coresolution.pe.mapper.AffEvaluationMapper;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffEvaluationFormService {

    private static final Logger log = LoggerFactory.getLogger(AffEvaluationFormService.class);

    private final AffLoginMapper loginMapper;
    private final AffEvaluationMapper evaluationMapper;
    private final AffEvalSubmissionMapper submissionMapper;

    private final ObjectMapper om = new ObjectMapper();

    // ===== 점수 맵 =====
    private static final Map<String, Integer> SCORE = Map.of(
            "매우우수", 5, "우수", 4, "보통", 3, "미흡", 2, "매우미흡", 1);

    // ===== 공개 폼 시작(신규) =====
    @Transactional(readOnly = true)
    public FormPayload prepare(String evaluatorId,
            String targetId,
            int year,
            String dataType, // AC/AD/AE
            String dataEv) {

        UserPE target = loginMapper.findByIdWithNames(targetId, year);
        if (target == null)
            throw new AccessDeniedException("대상자를 찾을 수 없습니다.");

        // 문항 + 카테고리 정규화
        List<Evaluation> questions = evaluationMapper.selectByType(year, dataType);
        questions.forEach(q -> q.setD3(normalize(q.getD3())));

        // 주관식/객관식 분리 + 그룹핑
        List<Evaluation> essay = new ArrayList<>();
        Map<String, List<Evaluation>> grouped = new LinkedHashMap<>();
        for (Evaluation q : questions) {
            if ("주관식".equals(q.getD3()))
                essay.add(q);
            else
                grouped.computeIfAbsent(q.getD3(), k -> new ArrayList<>()).add(q);
        }

        // 표시 순서
        List<String> order = resolveGroupOrder(dataType);
        if (order == null || order.isEmpty())
            order = new ArrayList<>(grouped.keySet());
        else
            for (String g : grouped.keySet())
                if (!order.contains(g))
                    order.add(g);

        // 최신 제출(활성 → 최신Strict → 느슨)
        EvaluationSubmission foundEntity = submissionMapper.findActiveStrict(year, evaluatorId, targetId, dataType,
                dataEv);
        if (foundEntity == null) {
            foundEntity = submissionMapper.findLatestStrict(year, evaluatorId, targetId, dataType, dataEv);
            if (foundEntity == null) {
                foundEntity = submissionMapper.findLatestLoose(year, evaluatorId, targetId);
            }
        }

        // 필요 시 Row로 변환
        EvalSubmissionRow foundRow = toRow(foundEntity);

        // JSON → 맵
        Map<String, String> answerMap = readAnswers(foundEntity, questions);

        // 메타 수치
        int answeredCount = Optional.ofNullable(foundEntity).map(EvaluationSubmission::getAnsweredCount).orElse(0);
        Integer totalScore = Optional.ofNullable(foundEntity).map(EvaluationSubmission::getTotalScore).orElse(null);
        Double avgScore = Optional.ofNullable(foundEntity).map(EvaluationSubmission::getAvgScore).orElse(null);

        int total = evaluationMapper.countByType(year, dataType);
        boolean completed = (total > 0) && (answeredCount >= total);

        // nameMap & bundle(프리필)
        Map<Integer, String> nameMap = buildNameMap(questions);
        String answersBundleJson = buildAnswersBundleJson(questions, nameMap, answerMap);

        log.debug("[AFF] questions={}, groupedKeys={}, essay={}, order={}",
                questions == null ? null : questions.size(),
                grouped.keySet(), essay.size(), order);

        return FormPayload.builder()
                .target(target)
                .questions(questions)
                .grouped(grouped)
                .order(order)
                .essay(essay)
                .answerMap(answerMap)
                .answered(answeredCount)
                .total(total)
                .completed(completed)
                .totalScore(totalScore)
                .avgScore(avgScore)
                .answersBundleJson(answersBundleJson)
                .nameMap(nameMap)
                .build();
    }

    // ===== 수정 폼(기존 제출 강하게 탐색) =====
    @Transactional(readOnly = true)
    public FormPayload prepareForEdit(String evaluatorId,
            String targetId,
            int year,
            String dataType,
            String dataEv) {

        UserPE target = loginMapper.findByIdWithNames(targetId, year);
        if (target == null)
            throw new AccessDeniedException("대상자를 찾을 수 없습니다.");

        List<Evaluation> questions = evaluationMapper.selectByType(year, dataType);
        questions.forEach(q -> q.setD3(normalize(q.getD3())));

        // 활성→엄격 최신→느슨
        EvaluationSubmission foundEntity = submissionMapper.findActiveStrict(year, evaluatorId, targetId, dataType,
                dataEv);
        if (foundEntity == null) {
            foundEntity = submissionMapper.findLatestStrict(year, evaluatorId, targetId, dataType, dataEv);
            if (foundEntity == null) {
                foundEntity = submissionMapper.findLatestLoose(year, evaluatorId, targetId);
            }
        }

        // 필요 시 Row로 변환
        EvalSubmissionRow foundRow = toRow(foundEntity);

        // JSON → 맵
        Map<String, String> answerMap = readAnswers(foundEntity, questions);

        // 메타 수치
        int answeredCount = Optional.ofNullable(foundEntity).map(EvaluationSubmission::getAnsweredCount).orElse(0);
        Integer totalScore = Optional.ofNullable(foundEntity).map(EvaluationSubmission::getTotalScore).orElse(null);
        Double avgScore = Optional.ofNullable(foundEntity).map(EvaluationSubmission::getAvgScore).orElse(null);

        int total = evaluationMapper.countByType(year, dataType);
        boolean completed = (total > 0) && (answeredCount >= total);

        // 그룹 구성
        List<Evaluation> essay = questions.stream().filter(q -> "주관식".equals(q.getD3())).collect(Collectors.toList());
        List<Evaluation> objective = questions.stream().filter(q -> !"주관식".equals(q.getD3()))
                .collect(Collectors.toList());

        List<String> order = resolveGroupOrder(dataType);
        if (order == null || order.isEmpty()) {
            order = objective.stream().map(Evaluation::getD3).distinct().collect(Collectors.toList());
        } else {
            // order에 정의되지 않은 카테고리는 끝에 추가
            for (String g : objective.stream().map(Evaluation::getD3).distinct().toList()) {
                if (!order.contains(g))
                    order.add(g);
            }
        }

        Map<String, List<Evaluation>> grouped = new LinkedHashMap<>();
        for (String g : order) {
            List<Evaluation> list = objective.stream().filter(q -> g.equals(q.getD3())).collect(Collectors.toList());
            if (!list.isEmpty())
                grouped.put(g, list);
        }

        Map<Integer, String> nameMap = buildNameMap(questions);
        String answersBundleJson = buildAnswersBundleJson(questions, nameMap, answerMap);

        return FormPayload.builder()
                .target(target)
                .questions(questions)
                .grouped(grouped)
                .order(order)
                .essay(essay)
                .answerMap(answerMap)
                .answered(answeredCount)
                .total(total)
                .completed(completed)
                .totalScore(totalScore)
                .avgScore(avgScore)
                .answersBundleJson(answersBundleJson)
                .nameMap(nameMap)
                .build();
    }

    // ===== 저장(버저닝) =====
    @Transactional
    public long saveEdit(String evaluatorId,
            String targetId,
            int year,
            String dataType,
            String dataEv,
            Map<String, String[]> parameterMap) {

        // 1) 폼 파싱
        Map<String, String> radios = new LinkedHashMap<>();
        Map<String, String> essays = new LinkedHashMap<>();
        parameterMap.forEach((k, vArr) -> {
            if (k == null)
                return;
            String v = (vArr != null && vArr.length > 0) ? vArr[0] : "";
            if (k.startsWith("r"))
                radios.put(k, v.trim());
            else if (k.startsWith("t"))
                essays.put(k, v);
        });

        // 2) 카운트/점수
        int radioCountDefined = evaluationMapper.countRadioByType(year, dataType);
        int radiosAnswered = (int) radios.values().stream().filter(s -> s != null && !s.isBlank()).count();
        int essaysAnswered = (int) essays.values().stream().filter(s -> s != null && !s.isBlank()).count();
        int answeredCount = radiosAnswered + essaysAnswered;

        // 평균 점수(1~5 구간 유지)
        double avg = radiosAnswered == 0 ? 0.0
                : radios.values().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .mapToInt(this::scoreOf)
                        .average()
                        .orElse(0.0);

        // 기존 방식대로 “기본 총점” 계산
        int baseTotalScore = (int) Math.round(avg * radioCountDefined);

        // ✅ AC 타입만 총점을 2배로
        int totalScore = "AC".equals(dataType) ? baseTotalScore * 2 : baseTotalScore;

        // 3) JSON
        ObjectNode radiosNode = om.createObjectNode();
        radios.forEach(radiosNode::put);
        ObjectNode essaysNode = om.createObjectNode();
        essays.forEach(essaysNode::put);
        ObjectNode bundle = om.createObjectNode();
        bundle.set("radios", radiosNode);
        bundle.set("essays", essaysNode);
        String answersJson = bundle.toString();

        // 4) 활성본 soft-delete
        submissionMapper.softDeleteActive(year, evaluatorId, targetId, dataType, dataEv, evaluatorId);

        // 5) 버전 계산
        int maxVer = submissionMapper.findMaxVersion(year, evaluatorId, targetId, dataType, dataEv);
        int nextVer = maxVer + 1;

        // 6) INSERT
        EvalSubmissionRow row = new EvalSubmissionRow();
        row.setEvalYear(year);
        row.setEvaluatorId(evaluatorId);
        row.setTargetId(targetId);
        row.setDataEv(dataEv);
        row.setDataType(dataType);
        row.setAnswersJson(answersJson);
        row.setAnsweredCount(answeredCount);
        row.setRadioCount(radioCountDefined);
        row.setTotalScore(totalScore);
        row.setAvgScore(round3(avg));
        row.setVersion(nextVer);
        row.setUpdatedBy(evaluatorId);

        int n = submissionMapper.insertOne(row);
        log.debug("Aff saveEdit inserted: {}, nextVer={}", n, nextVer);

        return nextVer;
    }

    // ===== 유틸 =====

    /** 엔티티 문항 idx 기준 nameMap (라디오=r{idx}, 주관식=t{idx}) */
    private Map<Integer, String> buildNameMap(List<Evaluation> questions) {
        return questions.stream().collect(Collectors.toMap(
                Evaluation::getIdx,
                q -> "주관식".equals(q.getD3()) ? ("t" + q.getIdx()) : ("r" + q.getIdx()),
                (a, b) -> a,
                LinkedHashMap::new));
    }

    /** 기존 제출 JSON을 프리필 번들(JSON 문자열)로 변환 */
    private String buildAnswersBundleJson(List<Evaluation> questions,
            Map<Integer, String> nameMap,
            Map<String, String> answerMap) {
        ObjectNode radiosNode = om.createObjectNode();
        ObjectNode essaysNode = om.createObjectNode();
        for (Evaluation q : questions) {
            String nm = nameMap.get(q.getIdx());
            String v = answerMap.getOrDefault(nm, "");
            if ("주관식".equals(q.getD3()))
                essaysNode.put(nm, v);
            else
                radiosNode.put(nm, v);
        }
        ObjectNode bundle = om.createObjectNode();
        bundle.set("radios", radiosNode);
        bundle.set("essays", essaysNode);
        return bundle.toString();
    }

    /** 제출 행에서 radios/essays를 하나의 맵으로 읽어옴 */
    private Map<String, String> readAnswers(EvaluationSubmission found, List<Evaluation> questions) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (found == null)
            return merged;
        String json = found.getAnswersJson();
        if (json == null || json.isBlank())
            return merged;

        try {
            JsonNode root = om.readTree(json);
            JsonNode r = root.get("radios");
            if (r != null && r.isObject())
                r.fields().forEachRemaining(e -> merged.put(e.getKey(), e.getValue().asText("")));
            JsonNode e = root.get("essays");
            if (e != null && e.isObject())
                e.fields().forEachRemaining(x -> merged.put(x.getKey(), x.getValue().asText("")));
        } catch (Exception ex) {
            log.warn("answers_json 파싱 실패", ex);
        }

        // ★ 레거시 키(근12/처22/업27 등) → r{idx}/t{idx} 표준화
        return canonicalizeAnswerKeys(merged, questions);
    }

    private static String normalize(String s) {
        if (s == null)
            return "";
        return s.replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace('\u3000', ' ')
                .trim();
    }

    private int scoreOf(String label) {
        if (label == null)
            return 0;
        return SCORE.getOrDefault(label.trim(), 0);
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // 타입별 기본 그룹 순서 (필요 시 실제 섹션명으로 교체)
    private static final List<String> GROUP_AC = List.of("근무태도", "리더쉽", "조직관리", "업무처리", "소통 및 화합");
    private static final List<String> GROUP_AD = List.of("근무태도", "처리능력", "업무실적");
    private static final List<String> GROUP_AE = List.of("근무태도", "처리능력", "업무실적");

    private List<String> resolveGroupOrder(String dataType) {
        return switch (dataType) {
            case "AC" -> new ArrayList<>(GROUP_AC);
            case "AD" -> new ArrayList<>(GROUP_AD);
            case "AE" -> new ArrayList<>(GROUP_AE);
            default -> new ArrayList<>(GROUP_AC);
        };
    }

    private EvalSubmissionRow toRow(EvaluationSubmission s) {
        if (s == null)
            return null;
        return EvalSubmissionRow.builder()
                .id(s.getId())
                .evalYear(s.getEvalYear())
                .evaluatorId(s.getEvaluatorId())
                .targetId(s.getTargetId())
                .dataEv(s.getDataEv())
                .dataType(s.getDataType())
                .answersJson(s.getAnswersJson())
                .answeredCount(s.getAnsweredCount())
                .radioCount(0) // 테이블에 없으므로 계산/주입 시 세팅
                .totalScore(s.getTotalScore() == null ? 0 : s.getTotalScore())
                .avgScore(s.getAvgScore() == null ? 0.0 : s.getAvgScore())
                .version(s.getVersion() == null ? 0 : s.getVersion())
                .updatedBy(null) // 필요 시 채움
                .build();
    }

    // key에서 끝의 숫자(idx)만 뽑기. 없으면 -1
    private static int tailIndexNumber(String key) {
        if (key == null)
            return -1;
        int n = key.length();
        int i = n - 1;
        while (i >= 0 && Character.isDigit(key.charAt(i)))
            i--;
        if (i == n - 1)
            return -1; // 끝에 숫자 없음
        try {
            return Integer.parseInt(key.substring(i + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // 레거시 키(근12/업27/처22 …)나 r12/t32를 표준(r{idx}/t{idx})로 맞춰주기
    private static Map<String, String> canonicalizeAnswerKeys(Map<String, String> raw, List<Evaluation> questions) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty())
            return out;

        // 1) 먼저 r*/t*는 그대로 반영
        raw.forEach((k, v) -> {
            if (k != null && (k.startsWith("r") || k.startsWith("t")))
                out.put(k, v);
        });

        // 2) 끝자리에 숫자(idx)가 있는 레거시 키는 r{idx}/t{idx}로 매핑
        // 주관식 여부는 질문 목록으로 판단
        Map<Integer, Boolean> isEssayByIdx = questions.stream()
                .collect(Collectors.toMap(Evaluation::getIdx, q -> "주관식".equals(q.getD3())));

        raw.forEach((k, v) -> {
            if (k == null || k.startsWith("r") || k.startsWith("t"))
                return; // 이미 표준 처리됨
            int idx = tailIndexNumber(k);
            if (idx <= 0)
                return;
            boolean essay = isEssayByIdx.getOrDefault(idx, false);
            String canonical = (essay ? "t" : "r") + idx;
            // 이미 표준 키가 있다면 그대로 두고, 없을 때만 채움(표준>레거시 우선)
            out.putIfAbsent(canonical, v);
        });

        return out;
    }
}