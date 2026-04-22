package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.EvalResult;
import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.FormPayload;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.DefaultTargetMapper;
import com.coresolution.pe.mapper.EvalResultMapper;
import com.coresolution.pe.mapper.EvalSubmissionMapper;
import com.coresolution.pe.mapper.EvaluationMapper;
import com.coresolution.pe.mapper.LoginMapper;
import com.coresolution.pe.mapper.UserMapper;
import com.coresolution.pe.support.AnswerBundle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.core.model.Model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EvaluationFormService {
    private final DefaultTargetMapper defaultTargetMapper;
    private final EvaluationMapper evaluationMapper;
    private final EvalResultMapper evalResultMapper;
    private final UserMapper userMapper; // ← loginMapper 대신 UserMapper 권장
    private final LoginMapper loginMapper; // 사용자 정보 조회용
    private final EvalSubmissionMapper submissionMapper;
    private final ObjectMapper om = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(EvaluationFormService.class);

    private static final Map<String, Integer> SCORE = Map.of(
            "매우우수", 5, "우수", 4, "보통", 3, "미흡", 2, "매우미흡", 1);

    private static String prefixByGroup(String d3) {
        return switch (d3) {
            case "섬김" -> "섬";
            case "배움" -> "배";
            case "키움" -> "키";
            case "나눔" -> "나";
            case "목표관리" -> "목";
            case "주관식" -> "t";
            default -> "q";
        };
    }

    /**
     * 폼 진입에 필요한 데이터 준비.
     * - 대상자 정보
     * - 문항 리스트
     * - 이전 제출값 프리필(answers_json → answerMap)
     * - 진행/평균/완료 여부
     */
    public FormPayload prepare(String evaluatorId,
            String targetId,
            String year,
            String dataType,
            String dataEv) throws AccessDeniedException {

        // 0) (선택) 접근 권한/메타 체크가 필요하면 여기서 검사하고 AccessDeniedException 던지기
        // ex) defaultTargetMapper.findMetaForUserTyped(...) 확인 등

        // 1) 대상자 정보
        UserPE target = loginMapper.findByIdWithNames(targetId, year);
        if (target == null) {
            throw new AccessDeniedException("대상자를 찾을 수 없습니다.");
        }

        // 2) 문항 리스트
        List<Evaluation> questions = evaluationMapper.findByYearAndType(year, dataType);

        // 3) 기존 제출(활성 1건) → 프리필 맵
        Map<String, String> answerMap = new LinkedHashMap<>();
        EvalSubmissionRow sub = submissionMapper.findActiveSubmission(
                year, evaluatorId, targetId, dataType, dataEv);

        Integer answeredCount = 0;
        Double avgScore = null;

        if (sub != null) {
            answeredCount = Optional.ofNullable(sub.getAnsweredCount()).orElse(0);
            avgScore = sub.getAvgScore();

            String json = sub.getAnswersJson();
            if (json != null && !json.isBlank()) {
                try {
                    JsonNode root = om.readTree(json);
                    // radios: { "q33":"우수", ... } 식
                    JsonNode r = root.get("radios");
                    if (r != null && r.isObject()) {
                        r.fields().forEachRemaining(e -> answerMap.put(e.getKey(), e.getValue().asText("")));
                    }
                    // essays: { "t43":"자유기술", ... } 식
                    JsonNode e = root.get("essays");
                    if (e != null && e.isObject()) {
                        e.fields().forEachRemaining(x -> answerMap.put(x.getKey(), x.getValue().asText("")));
                    }
                } catch (Exception parseEx) {
                    // 파싱 오류는 프리필 생략 (로그만 남기는 걸 권장)
                }
            }
        }

        // 4) 총 문항수/완료 여부
        int total = evaluationMapper.countByType(year, dataType);
        boolean completed = (total > 0) && (answeredCount >= total);

        // 4-1) 뷰 편의용: 주관식/객관식 분리 + 그룹핑
        List<Evaluation> essay = new ArrayList<>();
        Map<String, List<Evaluation>> grouped = new LinkedHashMap<>();
        if (questions != null) {
            for (Evaluation q : questions) {
                if ("주관식".equals(q.getD3())) {
                    essay.add(q);
                } else {
                    grouped.computeIfAbsent(q.getD3(), k -> new ArrayList<>()).add(q);
                }
            }
        }
        // 표시 순서: 고정 순서 우선, 그 외 그룹은 뒤에 추가
        List<String> knownOrder = List.of("섬김", "배움", "키움", "나눔", "목표관리");
        List<String> order = new ArrayList<>(knownOrder);
        for (String g : grouped.keySet()) {
            if (!order.contains(g)) order.add(g);
        }

        // 5) 컨트롤러에서 model.addAttribute로 바인딩할 DTO 리턴
        return FormPayload.builder()
                .target(target)
                .questions(questions)
                .grouped(grouped)
                .order(order)
                .essay(essay)
                .answerMap(answerMap) // radios + essays를 한 맵에 담아 반환 (이름: q??/t??)
                .answered(answeredCount)
                .total(total)
                .completed(completed)
                .avgScore(avgScore)
                .build();
    }

    /** 편집 페이지용: 질문 + 기존제출 + 정규화답변 + 타겟정보 */
    @Transactional(readOnly = true)
    public FormPayload prepareForEdit(String evaluatorId, String targetId, String year,
            String dataType, String dataEv) {
        log.info("[prepareForEdit] in: year={} type='{}' ev='{}' eval={} target={}",
                year, dataType, dataEv, evaluatorId, targetId);
        // 1) 질문목록
        List<Evaluation> questions = evaluationMapper.findByYearAndType(year, dataType);

        // 2) 제출본 (활성 → 정합 → 느슨)
        EvaluationSubmission sub = submissionMapper.findActiveStrict(year, evaluatorId, targetId, dataType, dataEv);
        if (sub == null) {
            log.info("[prepareForEdit] activeStrict = null → try latestStrict");
            sub = submissionMapper.findLatestStrict(year, evaluatorId, targetId, dataType, dataEv);
        }
        if (sub == null) {
            log.info("[prepareForEdit] latestStrict = null → try latestLoose");
            sub = submissionMapper.findLatestLoose(year, evaluatorId, targetId);
        }
        log.info("[prepareForEdit] submission found? {}", (sub != null));
        if (sub != null) {
            String aj = sub.getAnswersJson();
            log.info("[prepareForEdit] answersJson len={}, isActive={}, ver={}, updatedAt={}",
                    (aj == null ? 0 : aj.length()), sub.getIsActive(), sub.getVersion(), sub.getUpdatedAt());
        }
        // 3) 정규화된 답변
        AnswerBundle bundle = AnswerBundle.fromJsonOrLegacy(sub != null ? sub.getAnswersJson() : null);
        log.info("[prepareForEdit] bundle radios={}, essays={}",
                (bundle.getRadios() == null ? 0 : bundle.getRadios().size()),
                (bundle.getEssays() == null ? 0 : bundle.getEssays().size()));

        // ★ 3-1) 입력 name 매핑 (기존키가 있으면 재사용, 없으면 d3로 접두사 추론)
        Map<Integer, String> nameMap = new LinkedHashMap<>();
        Set<String> allKeys = new HashSet<>();
        if (bundle.getRadios() != null)
            allKeys.addAll(bundle.getRadios().keySet());
        if (bundle.getEssays() != null)
            allKeys.addAll(bundle.getEssays().keySet());

        // 기존 저장 포맷에서 쓰던 가능한 접두사들
        List<String> knownPrefixes = List.of("섬", "배", "키", "나", "목", "t"); // t = 주관식

        for (Evaluation q : questions) {
            int qid = q.getIdx();
            String exist = null;

            // a) 과거 JSON에 “접두사+idx” 완전일치 키가 있으면 그대로 사용
            for (String pf : knownPrefixes) {
                String cand = pf + qid;
                if (allKeys.contains(cand)) {
                    exist = cand;
                    break;
                }
            }
            // b) 없으면 d3로 접두사 만들어 새 키 생성
            if (exist == null)
                exist = prefixByGroup(q.getD3()) + qid;

            nameMap.put(qid, exist);
        }

        // 4) 타겟 정보
        UserPE target = loginMapper.findByIdWithNames(targetId, year);

        // 5) 페이로드
        FormPayload payload = new FormPayload();
        payload.setQuestions(questions);
        payload.setTarget(target);
        payload.setAnswered(sub != null ? sub.getAnsweredCount() : 0);
        payload.setTotalScore(sub != null ? sub.getTotalScore() : null);
        payload.setAvgScore(sub != null ? sub.getAvgScore() : null);
        payload.setAnswersBundleJson(bundle.toJsonString()); // 뷰(JS)로 바로 주입
        payload.setNameMap(nameMap); // ★ 추가
        log.debug("[prepareForEdit] nameMap={}", nameMap);
        return payload;
    }

    @Transactional
    public long saveEdit(String evaluatorId, String targetId, String year,
            String dataType, String dataEv,
            Map<String, String[]> paramMap) {

        // 1) 질문 + 기존 제출 탐색
        List<Evaluation> questions = evaluationMapper.findByYearAndType(year, dataType);

        EvaluationSubmission latest = submissionMapper.findActiveStrict(year, evaluatorId, targetId, dataType, dataEv);
        if (latest == null)
            latest = submissionMapper.findLatestStrict(year, evaluatorId, targetId, dataType, dataEv);

        // 2) nameMap 재구성 (기존 키가 있으면 유지, 없으면 d3로 생성)
        // → 여기서는 단순히 d3 접두사 + idx로 생성해도 됨(템플릿과 동일)
        Map<Integer, String> nameMap = new LinkedHashMap<>();
        for (Evaluation q : questions) {
            nameMap.put(q.getIdx(), prefixByGroup(q.getD3()) + q.getIdx());
        }

        // 3) 폼 파라미터에서 값 읽어 AnswerBundle 구성
        Map<String, String> radios = new LinkedHashMap<>();
        Map<String, String> essays = new LinkedHashMap<>();

        for (Evaluation q : questions) {
            String key = nameMap.get(q.getIdx());
            String[] raw = paramMap.get(key);
            String v = (raw != null && raw.length > 0) ? raw[0] : null;

            if ("주관식".equals(q.getD3())) {
                // 빈 문자열도 허용 → 저장은 하되 answeredCount 계산에서 제외 가능
                essays.put(key, v != null ? v : "");
            } else {
                if (v != null && !v.isBlank()) {
                    // 라벨(매우우수/우수/보통/미흡/매우미흡)만 허용
                    if (!SCORE.containsKey(v)) {
                        // 이상값은 무시 (혹은 예외)
                        continue;
                    }
                    radios.put(key, v);
                }
            }
        }

        AnswerBundle bundle = new AnswerBundle();
        bundle.setRadios(radios);
        bundle.setEssays(essays);
        String answersJson = bundle.toJsonString();

        // 4) 점수 집계
        int answeredRadio = (int) radios.values().stream().filter(Objects::nonNull).count();
        int answeredEssay = (int) essays.values().stream().filter(s -> s != null && !s.isBlank()).count();

        int answeredCount = answeredRadio + answeredEssay;

        int totalScore = radios.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(SCORE::get)
                .sum();

        Double avgScore = answeredRadio > 0 ? (totalScore * 1.0) / answeredRadio : null;

        // 5) 기존 Active 비활성화 → 새 버전 insert
        int nextVersion = (latest == null || latest.getVersion() == null) ? 1 : (latest.getVersion() + 1);

        submissionMapper.deactivateActive(year, evaluatorId, targetId, dataType, dataEv);

        EvaluationSubmission toSave = new EvaluationSubmission();
        toSave.setEvalYear(Integer.parseInt(year));
        toSave.setEvaluatorId(evaluatorId);
        toSave.setTargetId(targetId);
        toSave.setDataEv(dataEv);
        toSave.setDataType(dataType);
        toSave.setAnsweredCount(answeredCount);
        toSave.setTotalScore(totalScore);
        toSave.setAvgScore(avgScore);
        toSave.setAnswersJson(answersJson);
        toSave.setVersion(nextVersion);
        toSave.setIsActive(1);
        toSave.setDelYn("N");

        submissionMapper.insertSubmission(toSave);

        return toSave.getId();
    }
}