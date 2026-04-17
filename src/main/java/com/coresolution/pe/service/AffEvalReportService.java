package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.AffReportAggregate;
import com.coresolution.pe.entity.AffReportVM;
import com.coresolution.pe.entity.CommentReportDTO;
import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.ObjectiveInsights;
import com.coresolution.pe.entity.ObjectiveStats;
import com.coresolution.pe.entity.OrgContext;
import com.coresolution.pe.entity.QuestionMeta;
import com.coresolution.pe.entity.ReportAggregate;
import com.coresolution.pe.mapper.AffEvalResultMapper;
import com.coresolution.pe.mapper.AffEvalSummaryMapper;
import com.coresolution.pe.mapper.AffOrgProfileMapper;
import com.coresolution.pe.mapper.EvalResultMapper;
import com.coresolution.pe.mapper.EvalSummaryMapper;
import com.coresolution.pe.mapper.OrgProfileMapper;
import com.coresolution.pe.service.AffEvalReportService.RowAgg;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffEvalReportService {
    private final AffEvalResultMapper mapper;
    private final AffEvalSummaryMapper sumMapper; // ⬅️ 추가 (위 인터페이스)
    private final AffOrgProfileMapper orgMapper;

    private static final Logger log = LoggerFactory.getLogger(AffEvalReportService.class);

    private static final int MIN_RESP = 0;
    private final com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
    private final CommentSummarizer summarizer;
    private static final Map<String, Integer> SCORE = Map.of(
            "매우우수", 5, "우수", 4, "보통", 3, "미흡", 2, "매우미흡", 1);
    private static final List<String> ORDER = List.of("매우우수", "우수", "보통", "미흡", "매우미흡");
    private static final java.util.regex.Pattern TAIL_NUM = java.util.regex.Pattern.compile("(\\d+)$");

    private static int toIdx(String key) {
        var m = TAIL_NUM.matcher(key == null ? "" : key);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static final List<String> AREA_ORDER = List.of("섬김", "배움", "키움", "나눔", "목표관리");

    private static int areaIdx(String a) {
        int i = AREA_ORDER.indexOf(a);
        return i >= 0 ? i : Integer.MAX_VALUE;
    }

    // (선택) 영역 접두어가 필요하면 이렇게 뽑을 수 있어요
    private static String areaPrefix(String key) {
        if (key == null)
            return null;
        int idx = toIdx(key);
        if (idx < 0)
            return null;
        String idxStr = String.valueOf(idx);
        return key.substring(0, key.length() - idxStr.length()); // 예: "섬", "배", "키", "나", "목"
    }

    // ev → 타입 코드
    public static String typeByEv(String ev) {
        if ("A".equals(ev) || "E".equals(ev) || "G".equals(ev)) {
            return "AB"; // 20문항
        } else {
            return "AA"; // 10문항
        }
    }

    /**
     * 코호트(= 동일 ev + 동일 부서) 평균을 100점 스케일로 계산
     * 
     * @param evalYear        연도
     * @param dataEv          선택된 평가구간 코드 (예: "F", "D"...)
     * @param subCode         대상자의 부서 코드
     * @param excludeTargetId (선택) 자신을 평균에서 제외하려면 주입, 아니면 null
     */
    private double computeCohortOverall100(int evalYear, String dataEv, String subCode, String excludeTargetId) {
        // 같은 평가구간 + 같은 부서의 제출 전부
        List<EvalSubmissionRow> subs = mapper.selectAllSubmissionsByEvAndDept(evalYear, dataEv, subCode);
        String typeCode = typeByEv(dataEv);

        double weightedSumForAvg = 0.0; // 분자(AA는 ×2)
        int baseCntForAvg = 0; // 분모(원래 cnt)
        boolean isAA = "AA".equals(typeCode);

        for (EvalSubmissionRow s : subs) {
            if (excludeTargetId != null && excludeTargetId.equals(s.getTargetId())) {
                // 자신을 빼고 싶으면 제외
                continue;
            }
            Map<String, Object> root;
            try {
                root = om.readValue(s.getAnswersJson(), Map.class);
            } catch (Exception e) {
                continue;
            }
            Object radiosObj = root.get("radios");
            if (!(radiosObj instanceof Map<?, ?> radiosMap))
                continue;

            int sum = 0, cnt = 0; // 한 제출의 총점/개수
            for (var e : ((Map<?, ?>) radiosMap).entrySet()) {
                Object valObj = e.getValue();
                if (!(valObj instanceof String choice))
                    continue;
                // SCORE는 네가 이미 쓰는 매핑 { “매우우수”:5, ... } 그대로 사용
                Integer score = SCORE.get(choice);
                if (score == null)
                    continue;
                sum += score;
                cnt += 1;
            }
            if (cnt == 0)
                continue;

            // 코호트 누적치(AA면 분자만 ×2)
            weightedSumForAvg += isAA ? (sum * 2.0) : sum;
            baseCntForAvg += cnt;
        }

        if (baseCntForAvg == 0)
            return 0.0;
        int perRespMax = isAA ? 10 : 5; // 응답 1개 최대치
        double overall100 = (weightedSumForAvg / (perRespMax * baseCntForAvg)) * 100.0;
        return overall100;
    }

    public AffReportVM buildOne(int evalYear, String targetId, String dataEv, String relationPath, boolean withAI) {
        System.out.printf("[buildOne] year=%d, targetId=%s, ev=%s%n", evalYear, targetId, dataEv);

        // --- 기본 데이터 로딩
        final var subs = mapper.selectReceivedAllByOneEv(evalYear, targetId, dataEv);
        System.out.printf("[buildOne] subs.size=%d (예: 223108이면 9건 나와야 함)%n",
                subs != null ? subs.size() : -1);

        String typeCode = null;
        if (subs != null && !subs.isEmpty()) {
            var first = subs.get(0);
            try {
                var getter = first.getClass().getMethod("getDataType");
                Object v = getter.invoke(first);
                if (v instanceof String s && !s.isBlank()) {
                    typeCode = s; // AC / AD / AE / AA / AB ...
                }
            } catch (Exception ignore) {
                // getDataType() 없으면 무시
            }
        }
        if (typeCode == null) {
            typeCode = typeByEv(dataEv); // 병원 환경 등 기존 규칙 fallback
        }
        System.out.printf("[buildOne] resolved typeCode=%s%n", typeCode);

        final var qbank = java.util.Optional.ofNullable(
                mapper.selectQuestionBankByType(evalYear, typeCode)).orElseGet(java.util.List::of);

        // qbank 비어있으면 안전한 빈 VM 반환
        final var rows = new java.util.LinkedHashMap<Integer, RowAgg>();
        for (var q : qbank) {
            var zero = new java.util.LinkedHashMap<String, Integer>();
            ORDER.forEach(k -> zero.put(k, 0));
            rows.put(q.getIdx(), RowAgg.builder()
                    .area(q.getAreaLabel()).idx(q.getIdx()).label(q.getLabelText())
                    .counts(zero).avg(0.0).resp(0).build());
        }

        final var essays = new java.util.ArrayList<String>();
        int submissionCount = 0;

        // final Integer essayIdx = mapper.selectEssayIdx(evalYear, typeCode);
        // final String essayKey = (essayIdx == null) ? null : "t" + essayIdx;

        for (var s : subs) {
            submissionCount++;
            Map<String, Object> root;
            try {
                root = om.readValue(s.getAnswersJson(), Map.class);
            } catch (Exception e) {
                continue;
            }

            // --- 에세이 텍스트 수집 (모든 값) ---
            Object essaysObj = root.get("essays");
            if (essaysObj instanceof Map<?, ?> map) {
                for (Object v : map.values()) {
                    if (v instanceof String txt && !txt.isBlank()) {
                        essays.add(txt.trim());
                    }
                }
            }

            // --- 라디오 집계 ---
            Object radiosObj = root.get("radios");
            if (!(radiosObj instanceof Map<?, ?>))
                continue;
            Map<?, ?> radiosMap = (Map<?, ?>) radiosObj;

            for (var e : radiosMap.entrySet()) {
                Object keyObj = e.getKey();
                Object valObj = e.getValue();
                if (!(keyObj instanceof String k) || !(valObj instanceof String choice))
                    continue;

                int idx = toIdx(k); // r33 → 33
                var row = rows.get(idx);
                if (row == null)
                    continue;
                if (!row.getCounts().containsKey(choice))
                    continue;

                row.getCounts().put(choice, row.getCounts().get(choice) + 1);
                row.setResp(row.getResp() + 1);
            }
        }

        double weightedSumForAvg = 0; // 분자만 가중치
        int baseCntForAvg = 0; // 분모는 원래 cnt
        double totalScoreSum = 0;
        int totalScoreCnt = 0;
        final boolean isAA = "AA".equals(typeCode);

        for (var r : rows.values()) {
            int sum = 0, cnt = 0;
            for (var k : ORDER) {
                int c = r.getCounts().getOrDefault(k, 0);
                sum += c * SCORE.getOrDefault(k, 0);
                cnt += c;
            }
            r.setAvg(cnt == 0 ? 0.0 : (sum * 1.0 / cnt));
            weightedSumForAvg += isAA ? (sum * 2) : sum;
            baseCntForAvg += cnt;
            totalScoreSum += sum;
            totalScoreCnt += cnt;
        }

        final int qCount = rows.size();
        double questionAvg = 0.0;
        if (qCount > 0 && totalScoreCnt > 0) {
            double avgPerQuestion = totalScoreSum * 1.0 / totalScoreCnt; // 5점 만점
            questionAvg = isAA ? avgPerQuestion * 2 : avgPerQuestion; // AA는 ×2 (10점 만점)
        }

        final var totals = new java.util.LinkedHashMap<String, Integer>();
        ORDER.forEach(k -> totals.put(k, 0));
        rows.values().forEach(r -> ORDER.forEach(k -> totals.put(k, totals.get(k) + r.getCounts().getOrDefault(k, 0))));
        final int totalCnt = totals.values().stream().mapToInt(Integer::intValue).sum();

        final var ratio = new java.util.LinkedHashMap<String, Double>();
        for (var k : ORDER)
            ratio.put(k, totalCnt == 0 ? 0.0 : 100.0 * totals.get(k) / totalCnt);

        final var list = new java.util.ArrayList<>(rows.values());
        list.sort(java.util.Comparator.comparing(RowAgg::getIdx));

        // (기존) 영역(rowspan) 계산 로직 그대로

        // --- 여기서 영역별 점수(레이더용) 계산 ---
        Map<String, Double> areaScorePct = computeAreaScorePercent(typeCode, list);

        // 영역(rowspan)
        for (int i = 0, n = list.size(); i < n;) {
            String area = list.get(i).getArea();
            int j = i + 1;
            while (j < n && java.util.Objects.equals(area, list.get(j).getArea()))
                j++;
            int span = j - i;
            for (int k = i; k < j; k++) {
                list.get(k).setGroupFirst(k == i);
                list.get(k).setGroupSize(span);
            }
            i = j;
        }

        final double overallAvg = baseCntForAvg == 0 ? 0.0 : (weightedSumForAvg / baseCntForAvg);
        final int perRespMax = isAA ? 10 : 5;
        final double overallScore100 = baseCntForAvg == 0
                ? 0.0
                : (weightedSumForAvg / (perRespMax * baseCntForAvg)) * 100.0;

        final String locale = "ko";
        final int respCount = submissionCount;
        final int essayCount = essays.size();

        // 🔑 현재 입력 해시를 항상 계산 (캐시 일관성 판단용)
        final String joined = String.join("\n---\n", essays);
        final String inputHash = sha256Hex(joined);

        // ─────────────────────────────────────────────
        // ⛔ withAI=false: 캐시만 읽기, DB 오류는 조용히 무시
        // ─────────────────────────────────────────────
        if (!withAI) {
            String summaryText = "요약을 생성하려면 [코어 AI 요약] 버튼을 눌러주세요.";

            Map<String, Object> saved = null;
            try {
                saved = sumMapper.selectOne(evalYear, targetId, dataEv, "ESSAY");
            } catch (DataAccessException ex) {
                // 계열사 DB처럼 evaluation_comment_summary 테이블이 없을 때 여기로 옴
                System.err.printf(
                        "[buildOne] sumMapper.selectOne() 실패 (ESSAY 캐시 조회 스킵) year=%d, targetId=%s, ev=%s, msg=%s%n",
                        evalYear, targetId, dataEv, ex.getMessage());
                saved = null;
            }

            if (saved != null && "READY".equals(saved.get("status"))) {
                Object savedHash = saved.get("input_hash");
                if (inputHash.equals(savedHash)) {
                    summaryText = enforceLexicon((String) saved.get("summary"));
                } else {
                    summaryText = "내용이 변경되었습니다. 최신 요약을 보려면 [코어 AI 요약] 버튼을 눌러 갱신하세요.";
                }
            }

            return AffReportVM.builder()
                    .dataEv(dataEv).relationPath(relationPath).typeCode(typeCode)
                    .rows(list)
                    .overallAvg(overallAvg)
                    .overallScore100(overallScore100)
                    .questionAvg(questionAvg)
                    .overallResp(respCount)
                    .ratio(ratio)
                    .essayList(essays)
                    .essaySummary(summaryText)
                    .essayCount(essayCount)
                    .areaScorePct(areaScorePct) // 🔹 새 필드
                    .build();
        }

        // ─────────────────────────────────────────────
        // ✅ withAI=true: 에세이 1개만 있어도 생성
        // ─────────────────────────────────────────────
        if (essayCount < 1) {
            String msg = "에세이 코멘트가 없어 요약을 생성할 수 없습니다.";
            return AffReportVM.builder()
                    .dataEv(dataEv).relationPath(relationPath).typeCode(typeCode)
                    .rows(list)
                    .overallAvg(overallAvg)
                    .overallScore100(overallScore100)
                    .questionAvg(questionAvg)
                    .overallResp(respCount)
                    .ratio(ratio)
                    .essayList(essays)
                    .essaySummary(msg)
                    .essayCount(essayCount)
                    .areaScorePct(areaScorePct) // 🔹 새 필드
                    .build();
        }

        // 캐시 재사용 (같은 입력 해시) – 역시 DB 오류는 무시
        Map<String, Object> saved = null;
        try {
            saved = sumMapper.selectOne(evalYear, targetId, dataEv, "ESSAY");
        } catch (DataAccessException ex) {
            System.err.printf(
                    "[buildOne] sumMapper.selectOne() 실패 (ESSAY 캐시 조회 스킵) year=%d, targetId=%s, ev=%s, msg=%s%n",
                    evalYear, targetId, dataEv, ex.getMessage());
            saved = null;
        }

        if (saved != null && inputHash.equals(saved.get("input_hash")) && "READY".equals(saved.get("status"))) {
            String summary = enforceLexicon((String) saved.get("summary"));
            return AffReportVM.builder()
                    .dataEv(dataEv).relationPath(relationPath).typeCode(typeCode)
                    .rows(list)
                    .overallAvg(overallAvg)
                    .overallScore100(overallScore100)
                    .questionAvg(questionAvg)
                    .overallResp(respCount)
                    .ratio(ratio)
                    .essayList(essays)
                    .essaySummary(summary)
                    .essayCount(essayCount)
                    .areaScorePct(areaScorePct) // 🔹 새 필드
                    .build();
        }

        // 새로 생성 → upsert (upsert 에러도 잡아서 캐시만 포기)
        String summary;
        String status = "READY";
        String err = null;
        String modelUsed = (summarizer instanceof OpenAiCommentSummarizer) ? "gpt-4o-mini" : "local-heuristic";

        try {
            summary = summarizer.summarize(essays, locale, 8000);
            if (summary == null || summary.isBlank()) {
                status = "ERROR";
                err = "빈 요약 반환";
                summary = "요약 생성에 실패했습니다.";
            }
        } catch (Exception ex) {
            status = "ERROR";
            err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            summary = "요약 생성 중 오류가 발생했습니다.";
        }
        summary = enforceLexicon(summary);

        try {
            sumMapper.upsert(evalYear, targetId, dataEv, "ESSAY",
                    inputHash, essayCount, respCount, locale, modelUsed, summary, status, err);
        } catch (DataAccessException ex) {
            System.err.printf(
                    "[buildOne] sumMapper.upsert() 실패 (ESSAY 캐시 저장 스킵) year=%d, targetId=%s, ev=%s, msg=%s%n",
                    evalYear, targetId, dataEv, ex.getMessage());
            // 캐시에 못 넣는 것뿐, 화면에는 summary 그대로 보여주면 됨
        }

        return AffReportVM.builder()
                .dataEv(dataEv).relationPath(relationPath).typeCode(typeCode)
                .rows(list)
                .overallAvg(overallAvg)
                .overallScore100(overallScore100)
                .questionAvg(questionAvg)
                .overallResp(respCount)
                .ratio(ratio)
                .essayList(essays)
                .essaySummary(summary)
                .essayCount(essayCount)
                .areaScorePct(areaScorePct) // 🔹 새 필드
                .build();
    }

    // 점수 DTO (간단)
    public static final class ScoreItem {
        private final String area;
        private final String label;
        private final double my100;
        private final double cohort100;

        public ScoreItem(String area, String label, double my100, double cohort100) {
            this.area = area;
            this.label = label;
            this.my100 = my100;
            this.cohort100 = cohort100;
        }

        public String area() {
            return area;
        }

        public String label() {
            return label;
        }

        public double my100() {
            return my100;
        }

        public double cohort100() {
            return cohort100;
        }
    }

    public final class ScorePrompt {
        public static String system() {
            return """
                    당신은 인사평가 '점수기반' 요약 리포트를 작성하는 도우미입니다.
                    - title은 반드시 "[종합 역량지표] AI 리포트"로 고정합니다.

                    [입력 성격]
                    - 입력엔 문항/영역별 '정량 점수'만 들어옵니다. 코멘트(서술형)는 포함되지 않습니다.
                    - 영역 이름과 개수는 고정되어 있지 않으며,
                      user 메시지의 [영역 목록]과 [영역별 점수] 섹션에 제공된 이름과 순서를 그대로 사용해야 합니다.
                      예: ["근무태도","리더쉽","조직관리","업무처리","소통 및 화합"] 또는
                          ["근무태도","처리능력","업무실적"] 등.

                    [어휘 규칙]
                    - '코호트'/'cohort'라는 단어는 절대 사용하지 말고, 항상 '동일 평가구간' 또는 '동일 평가구간 평균'을 쓰십시오.

                    [길이/구성 규칙]
                    - summary는 **최소 7문장, 가능하면 9~11문장**으로 작성합니다.
                    - summary 안에서 **입력으로 주어진 모든 영역 각각에 대해 적어도 1문장 이상** 언급합니다.
                    - 응답자 수가 적은 경우에는,
                      "응답 수가 많지 않지만 현재 결과를 기준으로 보면..."처럼
                      데이터 한계를 언급하면서도, 강점/보완점/향후 방향을 충분히 설명합니다.
                    - strengths에는 3개 이상, improvements에는 3개 이상 항목을 제시합니다.
                    - next_actions에는 실행 가능한 후속 행동을 최소 3개 이상 제시합니다.

                    [출력 형식(JSON만)]
                    - by_area의 키는 user 메시지의 [영역 목록]에 나온 이름을 그대로 사용합니다.

                    {
                      "title": string,
                      "summary": string,
                      "tone": string,
                      "metrics": { "responses": number, "essays": 0, "score_overall": number },
                      "overall_comparison": { "my": number, "same_period": number },

                      "by_area": {
                        // 키 예시 (실제 키는 입력에 따라 달라짐):
                        // "근무태도":  {...},
                        // "리더쉽":    {...},
                        // "조직관리":  {...},
                        // "업무처리":  {...},
                        // "소통 및 화합": {...}
                        "<영역명>": {
                          "my": number,
                          "same_period": number,
                          "summary": string,
                          "strengths": string[],
                          "improvements": string[]
                        }
                      },

                      "strengths": string[],
                      "improvements": string[],
                      "next_actions": string[]
                    }
                    """;
        }

        public static String userWithOrg(
                int year, String relationLabel,
                double myOverall100, double cohortOverall100,
                List<ScoreItem> items, CommentReportDTO.Org org) {

            StringBuilder sb = new StringBuilder();
            sb.append("연도: ").append(year).append("\n");
            sb.append("동일 평가구간 라벨: ").append(relationLabel).append("\n");
            sb.append("내 종합점수(100): ").append(Math.round(myOverall100)).append("\n");
            sb.append("동일 평가구간 종합점수(100): ").append(Math.round(cohortOverall100)).append("\n");

            // 🔹 items에서 영역 목록 추출 (순서 유지)
            java.util.LinkedHashSet<String> areaSet = new java.util.LinkedHashSet<>();
            for (ScoreItem it : items) {
                if (it.area() != null && !it.area().isBlank()) {
                    areaSet.add(it.area());
                }
            }

            sb.append("영역 목록: ").append(areaSet).append("\n");
            sb.append("※ 위 영역 이름과 순서를 그대로 사용하여 by_area 키를 구성하세요.\n\n");

            sb.append("[영역별 점수]\n");
            for (ScoreItem it : items) {
                sb.append("• [").append(it.area()).append("] ").append(it.label())
                        .append(" | my=").append(Math.round(it.my100()))
                        .append(", same_period=").append(Math.round(it.cohort100()))
                        .append("\n");
            }

            // 필요하면, 데이터가 적을 때 안내도 같이 줘도 좋음
            sb.append("\n[해석 가이드]\n");
            sb.append("- 응답 수가 적더라도, 위 점수를 기반으로 각 영역의 상대적인 강점과 보완점, 향후 방향을 충분히 서술하십시오.\n");
            sb.append("- 각 영역에 대해 최소 1문장 이상을 summary에 포함하고, strengths/improvements/next_actions를 풍부하게 작성하십시오.\n");

            return sb.toString();
        }

        private static String nz(String s) {
            return (s == null || s.isBlank()) ? "-" : s;
        }

    }

    public static final class TotalPrompt {

        public static String system() {
            return """
                        당신은 인사평가 '종합(TOTAL)' 요약 리포트를 작성하는 도우미입니다.
                        입력에는 (1) 다면평가 객관식의 '내 점수/영역별 점수/동일 평가구간 평균'(100점 환산),
                               (2) 코멘트 원문(에세이),
                               (3) KPI(홍보공헌/자원봉사/교육이수/다면평가/임의=85) 100점 값이 포함됩니다.
                        규칙:
                        - 개인정보/실명은 제거/일반화.
                        - 비난/비속어는 중립적으로 재서술.
                        - '코호트'라는 단어는 쓰지 말고 '동일 평가구간'으로 표기.
                        - title은 반드시 "[종합평가] AI 리포트"로 고정.
                        - JSON만 출력(추가 텍스트 금지).

                        JSON 스키마:
                        {
                          "title": string,                                  // "[종합평가] AI 리포트"
                          "summary": string,                                // 7~9문장, KPI 연결까지 반영
                          "strengths": string[],                            // 길이 3
                          "improvements": string[],                         // 길이 3
                          "tone": string,                                   // 1문장 톤(예: "차분하고 건설적")
                          "metrics": {
                            "responses": number,                            // 응답자 수(정보 없으면 0)
                            "essays": number,                               // 코멘트 개수
                            "score_overall": number                         // 다면평가 종합(100)
                          },
                          "overall_comparison": { "my": number, "same_period": number },
                          "kpi": {
                            "홍보공헌(100)": number, "자원봉사(100)": number, "교육이수(100)": number,
                            "다면평가(100)": number, "임의데이터(100)": 85
                          },
                          "by_area": {
                            "섬김":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                            "배움":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                            "키움":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                            "나눔":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                            "목표관리":{"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]}
                          },
                          "next_actions": string[]
                        }
                    """;
        }

        public static String user(
                int year,
                String relationLabel,
                ObjectiveStats st, // my/equal-period/all 100, by-area 100 포함
                List<String> comments,
                Map<String, Double> kpi100 // 홍보공헌/자원봉사/교육이수/다면평가/임의(85)
        ) {
            StringBuilder sb = new StringBuilder();
            sb.append("연도: ").append(year).append("\n");
            sb.append("동일 평가구간 라벨: ").append(relationLabel).append("\n");

            double my = Math.round(Optional.ofNullable(st.getMyOverall100()).orElse(0.0));
            double same = Math.round(Optional.ofNullable(
                    // TOTAL에서는 '동일 평가구간 평균' 대신 전사 평균을 대표값으로 사용
                    // (부서/구간 세분값이 있으면 이미 computeObjectiveStats에 세팅되어 옴)
                    Optional.ofNullable(st.getCohortOverall100()).orElse(st.getAllOverall100())).orElse(0.0));
            sb.append("내 종합점수(100): ").append((long) my).append("\n");
            sb.append("동일 평가구간 종합점수(100): ").append((long) same).append("\n\n");

            sb.append("[영역별 점수(100)]\n");
            for (String a : st.getLabels()) {
                double myA = Math.round(st.getMyArea100().getOrDefault(a, 0.0));
                double avgA = Math.round(
                        Optional.ofNullable(st.getDeptEvArea100())
                                .map(m -> m.getOrDefault(a, 0.0))
                                .filter(v -> v > 0)
                                .orElse(st.getEvArea100().getOrDefault(a, 0.0)));
                sb.append("• ").append(a).append(" | my=").append((long) myA)
                        .append(", same_period=").append((long) avgA).append("\n");
            }

            sb.append("\n[KPI 100점 스케일]\n");
            sb.append("홍보공헌=").append(f(kpi100.get("홍보공헌(100)"))).append(", ");
            sb.append("자원봉사=").append(f(kpi100.get("자원봉사(100)"))).append(", ");
            sb.append("교육이수=").append(f(kpi100.get("교육이수(100)"))).append(", ");
            sb.append("다면평가=").append(f(kpi100.get("다면평가(100)"))).append(", ");
            sb.append("임의데이터=85\n");

            sb.append("\n[코멘트 원문(---로 구분)]\n");
            if (comments == null || comments.isEmpty()) {
                sb.append("(코멘트 없음)\n");
            } else {
                for (String c : comments) {
                    if (c == null || c.isBlank())
                        continue;
                    sb.append("---\n").append(c.trim()).append("\n");
                }
            }
            return sb.toString();
        }

        private static String f(Double v) {
            if (v == null)
                return "0";
            return String.valueOf(Math.round(v));
        }
    }

    public CommentReportDTO buildTotalSummary(
            int year,
            String targetId,
            String relationPath, // 화면에 쓰던 라벨 그대로
            @Nullable String subCode, // 부서 코드(있으면 통계 보조)
            double promo100, double vol100, double edu100, double multi100, Map<String, Double> kpi100,
            boolean withAI // 버튼 눌렀을 때만 true
    ) {
        // 1) 다면평가 객관식/코멘트: E(부서장→부서원) + G(부서원→부서원) 합산
        AffReportVM vmE = buildOne(year, targetId, "E", relationPath + "·E", false);
        AffReportVM vmG = buildOne(year, targetId, "G", relationPath + "·G", false);

        // rows 합치기(문항 기준 idx 동일 가정)
        Map<Integer, RowAgg> merged = new LinkedHashMap<>();
        for (RowAgg r : vmE.getRows())
            merged.put(r.getIdx(), deepCopy(r));
        for (RowAgg r : vmG.getRows()) {
            merged.merge(r.getIdx(), r, (a, b) -> {
                // counts/resp/avg 재계산
                Map<String, Integer> m = new LinkedHashMap<>(a.getCounts());
                b.getCounts().forEach((k, v) -> m.put(k, m.getOrDefault(k, 0) + v));
                int resp = a.getResp() + b.getResp();
                int sum = 0, cnt = 0;
                for (var k : ORDER) {
                    int c = m.getOrDefault(k, 0);
                    cnt += c;
                    sum += c * SCORE.getOrDefault(k, 0);
                }
                a.setCounts(m);
                a.setResp(resp);
                a.setAvg(cnt == 0 ? 0.0 : (sum * 1.0 / cnt));
                return a;
            });
        }
        var mergedRows = new ArrayList<>(merged.values());
        mergedRows.sort(Comparator.comparing(RowAgg::getIdx));

        // 코멘트 합치기
        List<String> comments = new ArrayList<>();
        if (vmE.getEssayList() != null)
            comments.addAll(vmE.getEssayList());
        if (vmG.getEssayList() != null)
            comments.addAll(vmG.getEssayList());

        // 2) 객관식 통계(영역/전체 100 환산)
        ObjectiveStats stats0 = computeObjectiveStatsTotal(year, mergedRows, subCode);

        ObjectiveStats stats = stats0.toBuilder()
                .cohortOverall100(
                        (stats0.getCohortOverall100() != null && stats0.getCohortOverall100() > 0)
                                ? stats0.getCohortOverall100()
                                : Optional.ofNullable(stats0.getAllOverall100()).orElse(0.0))
                .build();

        // 3) KPI(100 스케일) – 파라미터 kpi100이 null이면 생성
        Map<String, Double> kpi100Payload;
        if (kpi100 != null && !kpi100.isEmpty()) {
            kpi100Payload = new LinkedHashMap<>(kpi100);
        } else {
            kpi100Payload = new LinkedHashMap<>();
            kpi100Payload.put("홍보공헌(100)", promo100);
            kpi100Payload.put("자원봉사(100)", vol100);
            kpi100Payload.put("교육이수(100)", edu100);
            kpi100Payload.put("다면평가(100)", multi100);
            kpi100Payload.put("임의데이터(100)", 85.0);
        }

        // 4) 입력 시그니처(캐시 키)
        String signature = new StringBuilder()
                .append("overall=").append(Math.round(stats.getMyOverall100()))
                .append("|same=").append(Math.round(Optional.ofNullable(stats.getCohortOverall100()).orElse(0.0)))
                .append("|areas=")
                .append(stats.getLabels().stream()
                        .map(a -> a + ":" + Math.round(stats.getMyArea100().getOrDefault(a, 0.0)) + "/" +
                                Math.round(
                                        Optional.ofNullable(stats.getDeptEvArea100())
                                                .map(m -> m.getOrDefault(a, 0.0))
                                                .filter(v -> v > 0)
                                                .orElse(stats.getEvArea100().getOrDefault(a, 0.0))))
                        .collect(Collectors.joining(",")))
                .append("|kpi=")
                .append(Math.round(kpi100Payload.getOrDefault("홍보공헌(100)", 0.0))).append(",")
                .append(Math.round(kpi100Payload.getOrDefault("자원봉사(100)", 0.0))).append(",")
                .append(Math.round(kpi100Payload.getOrDefault("교육이수(100)", 0.0))).append(",")
                .append(Math.round(kpi100Payload.getOrDefault("다면평가(100)", 0.0))).append(",")
                .append(85)
                .append("|comments=").append(sha256Hex(String.join("\n---\n", comments)))
                .toString();
        String sigHash = sha256Hex(signature);

        // 5) 캐시 조회 — ★ kind는 'SCORE' 로 (스키마 준수)
        Map<String, Object> saved = sumMapper.selectOne(year, targetId, "TOTAL", "SCORE");
        String json = null;
        CommentReportDTO dto = null;
        boolean canReuse = saved != null
                && sigHash.equals(saved.get("input_hash"))
                && "READY".equals(saved.get("status"));
        if (canReuse) {
            json = enforceLexicon(String.valueOf(saved.get("summary")));
        } else if (withAI) {
            // 6) 생성 허용될 때만 OpenAI 호출
            String sys = TotalPrompt.system();
            String user = TotalPrompt.user(year, relationPath, stats, comments, kpi100Payload);

            String modelUsed = (summarizer instanceof OpenAiCommentSummarizer) ? "gpt-4o-mini" : "local-heuristic";
            String status = "READY", err = null;
            try {
                if (summarizer instanceof OpenAiCommentSummarizer oa) {
                    json = oa.summarizeWithSystem(sys, user, "ko", 2000);
                } else {
                    json = summarizer.summarize(List.of(sys + "\n\n" + user), "ko", 8000);
                }
                if (json == null || json.isBlank()) {
                    status = "ERROR";
                    err = "빈 요약 반환";
                    json = "{\"title\":\"[종합평가] AI 리포트\",\"summary\":\"생성 실패\",\"strengths\":[],\"improvements\":[],\"tone\":\"\",\"metrics\":{\"responses\":0,\"essays\":0,\"score_overall\":0},\"next_actions\":[]}";
                }
            } catch (Exception ex) {
                status = "ERROR";
                err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                json = "{\"title\":\"[종합평가] AI 리포트\",\"summary\":\"생성 오류\",\"strengths\":[],\"improvements\":[],\"tone\":\"\",\"metrics\":{\"responses\":0,\"essays\":0,\"score_overall\":0},\"next_actions\":[]}";
            }
            json = enforceLexicon(json);

            // ★ upsert 도 kind='SCORE' 로
            int totalResp = Optional.ofNullable(vmE.getOverallResp()).orElse(0)
                    + Optional.ofNullable(vmG.getOverallResp()).orElse(0);
            sumMapper.upsert(year, targetId, "TOTAL", "SCORE",
                    sigHash, comments.size(), totalResp,
                    "ko", modelUsed, json, status, err);
        }

        // 7) 파싱(있을 때만)
        if (json != null) {
            try {
                json = normalizeJsonEnvelope(json);
                if (json.trim().startsWith("{")) {
                    dto = om.readValue(json, CommentReportDTO.class);
                }
            } catch (Exception ignore) {
            }
        }
        return dto; // null이면 화면에서 TOTAL 요약 박스 숨김
    }

    private ObjectiveStats computeObjectiveStatsTotal(int evalYear, List<RowAgg> rowList, @Nullable String subCode) {

        // 0) idx→area 매핑
        var metas = mapper.selectQuestionBankAll(evalYear);
        Map<Integer, String> idxToArea = new LinkedHashMap<>();
        for (var m : metas)
            idxToArea.put(m.getIdx(), m.getAreaLabel());

        // 1) 내(대상자) 영역 평균/분포는 rowList(=E+G mergedRows)에서
        Map<String, Double> myArea5 = new LinkedHashMap<>();
        Map<String, Long> myCnt = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> myAreaDist = new LinkedHashMap<>();
        Map<String, List<String>> areaNarr = buildAreaNarratives(rowList);
        Map<String, String> areaSummary = buildAreaSummaries(rowList);

        for (var r : rowList) {
            if (r.getArea() == null || r.getArea().isBlank())
                continue;

            myArea5.merge(r.getArea(), r.getAvg(), Double::sum);
            myCnt.merge(r.getArea(), 1L, Long::sum);

            myAreaDist.computeIfAbsent(r.getArea(), k -> {
                var m = new LinkedHashMap<String, Integer>();
                ORDER.forEach(o -> m.put(o, 0));
                return m;
            });

            for (var k : ORDER) {
                int c = r.getCounts().getOrDefault(k, 0);
                myAreaDist.get(r.getArea()).put(k, myAreaDist.get(r.getArea()).get(k) + c);
            }
        }
        myArea5.replaceAll((a, sum) -> sum / myCnt.get(a));

        // 2) TOTAL의 “동일 평가구간(E+G)” 평균(회사 전체)
        var evSubs = new ArrayList<EvalSubmissionRow>();
        evSubs.addAll(mapper.selectAllSubmissionsByEv(evalYear, "E"));
        evSubs.addAll(mapper.selectAllSubmissionsByEv(evalYear, "G"));
        Map<String, Double> evArea5 = avgByAreaFromSubs(evSubs, idxToArea);

        // 3) 전직원 평균(연도 전체)
        var allSubs = mapper.selectAllSubmissionsByYear(evalYear);
        Map<String, Double> allArea5 = avgByAreaFromSubs(allSubs, idxToArea);

        // 4) 부서 평균(선택): (E+G) / (연도 전체)
        Map<String, Double> deptEvArea5 = new LinkedHashMap<>();
        Map<String, Double> deptAllArea5 = new LinkedHashMap<>();
        if (subCode != null && !subCode.isBlank()) {
            var deptEvSubs = new ArrayList<EvalSubmissionRow>();
            deptEvSubs.addAll(mapper.selectAllSubmissionsByEvAndDept(evalYear, "E", subCode));
            deptEvSubs.addAll(mapper.selectAllSubmissionsByEvAndDept(evalYear, "G", subCode));
            deptEvArea5 = avgByAreaFromSubs(deptEvSubs, idxToArea);

            var deptAllSubs = mapper.selectAllSubmissionsByYearAndDept(evalYear, subCode);
            deptAllArea5 = avgByAreaFromSubs(deptAllSubs, idxToArea);
        }

        // 5) 라벨 정렬
        List<String> labels = new ArrayList<>(new LinkedHashSet<>(myArea5.keySet()));
        labels.sort(Comparator.comparingInt(AffEvalReportService::areaIdx).thenComparing(Comparator.naturalOrder()));

        // 6) 100점 환산
        Map<String, Double> myArea100 = to100(myArea5, labels);
        Map<String, Double> evArea100 = to100(evArea5, labels);
        Map<String, Double> allArea100 = to100(allArea5, labels);
        Map<String, Double> deptEvArea100 = to100(deptEvArea5, labels);
        Map<String, Double> deptAllArea100 = to100(deptAllArea5, labels);

        double myOverall100 = avg(myArea100);
        double evOverall100 = avg(evArea100);
        double allOverall100 = avg(allArea100);
        double deptEvOverall100 = avg(deptEvArea100);
        double deptAllOverall100 = avg(deptAllArea100);

        return ObjectiveStats.builder()
                .myArea100(myArea100).evArea100(evArea100).allArea100(allArea100)
                .deptEvArea100(deptEvArea100).deptAllArea100(deptAllArea100)
                .myAreaDist(myAreaDist)
                .myOverall100(myOverall100).evOverall100(evOverall100).allOverall100(allOverall100)
                .deptEvOverall100(deptEvOverall100).deptAllOverall100(deptAllOverall100)
                .labels(labels)
                .areaNarr(areaNarr)
                .areaSummary(areaSummary)
                .build();
    }

    // 작은 헬퍼(딥카피)
    private static RowAgg deepCopy(RowAgg r) {
        Map<String, Integer> m = new LinkedHashMap<>();
        r.getCounts().forEach((k, v) -> m.put(k, v));
        return RowAgg.builder()
                .area(r.getArea())
                .idx(r.getIdx())
                .label(r.getLabel())
                .counts(m)
                .avg(r.getAvg())
                .resp(r.getResp())
                .groupFirst(r.getGroupFirst())
                .groupSize(r.getGroupSize())
                .build();
    }

    private static String enforceLexicon(String s) {
        if (s == null)
            return null;
        // 먼저 긴 구문부터 치환
        s = s.replaceAll("코호트\\s*평균", "동일 평가구간 평균");
        s = s.replaceAll("(?i)cohort\\s*average", "동일 평가구간 평균");
        // 단일 용어 치환
        s = s.replaceAll("코호트", "동일 평가구간");
        s = s.replaceAll("(?i)cohort", "동일 평가구간");
        return s;
    }

    // 1) JSON 문자열 정규화 유틸
    private static String normalizeJsonEnvelope(String s) {
        if (s == null)
            return null;
        String t = s.trim();

        // BOM 제거
        if (!t.isEmpty() && t.charAt(0) == '\uFEFF')
            t = t.substring(1).trim();

        // 1) 양끝에 불필요한 따옴표가 감싸고 있으면 벗기기
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }

        // 2) \" 같은 이스케이프를 실제 문자로 복원 (필요한 경우에 한함)
        // 역슬래시 자체가 두 겹일 수도 있어 한 번 더 풀어줌
        t = t.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");

        return t.trim();
    }

    private static String sha256Hex(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : dig)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * d2 타입(AC/AD/AE)에 따라 문항 배점을 달리 적용하고,
     * RowAgg 리스트를 d3(영역명: area)에 따라 묶어서
     * 영역별 100점 환산 점수를 계산한다.
     *
     * @param typeCode 평가유형 코드(AC, AD, AE)
     * @param rows     buildOne에서 정리된 문항별 집계 리스트
     * @return 영역명 → 0~100 점수(소수 1자리 반올림)
     */
    private Map<String, Double> computeAreaScorePercent(String typeCode, List<RowAgg> rows) {

        // 1) 평가유형별 문항당 만점 설정
        // - AC : 객관식 10문항 → 1문항당 10점
        // - AD : 객관식 20문항 → 1문항당 5점
        // - AE : 객관식 20문항 → 1문항당 5점 (필요 시 조정 가능)
        final int perQuestionMax;
        switch (typeCode) {
            case "AC":
                perQuestionMax = 10;
                break;
            case "AD":
            case "AE":
                perQuestionMax = 5;
                break;
            default:
                // 혹시 모르는 예외 타입은 5점으로 처리
                perQuestionMax = 5;
        }

        // 2) 영역별 합산용 구조체
        class AreaAgg {
            double sumScore; // 실제 합산점수 (문항점수 합)
            double sumMax; // 이 영역의 이론상 최대점수 (문항수 × perQuestionMax)
        }

        Map<String, AreaAgg> areaMap = new LinkedHashMap<>();

        for (RowAgg r : rows) {
            String area = r.getArea();
            if (area == null || area.isBlank())
                continue;

            // 혹시 주관식이 섞여 있다면 스킵 (qbank에서 이미 걸렀다면 없어야 함)
            if ("주관식".equals(area)) {
                continue;
            }

            AreaAgg agg = areaMap.computeIfAbsent(area, a -> new AreaAgg());

            // RowAgg.avg 는 1~5 리커트 평균값 (buildOne에서 이미 계산)
            double avg = r.getAvg(); // 1~5

            // 문항당 점수: (평균/5) × perQuestionMax
            // 응답이 없으면 avg=0이므로 자동으로 0점
            double questionScore = (avg / 5.0) * perQuestionMax;

            agg.sumScore += questionScore;
            agg.sumMax += perQuestionMax;
        }

        // 3) 영역별 0~100 점수로 환산
        Map<String, Double> areaPct = new LinkedHashMap<>();
        for (Map.Entry<String, AreaAgg> e : areaMap.entrySet()) {
            String area = e.getKey();
            AreaAgg agg = e.getValue();
            double pct = 0.0;
            if (agg.sumMax > 0.0) {
                pct = (agg.sumScore / agg.sumMax) * 100.0;
            }
            // 소수 1자리로 반올림
            pct = Math.round(pct * 10.0) / 10.0;
            areaPct.put(area, pct);
        }

        return areaPct;
    }

    public AffReportAggregate buildAggregate(int year, String targetId, String ev, String relationPath, String subCode,
            boolean withAI) {

        // ⬇️ withAI 플래그 전달
        AffReportVM vm = buildOne(year, targetId, ev, relationPath, withAI);

        // --- 조직 프로필 로딩 → CommentReportDTO.Org 로 맵핑
        Map<String, Object> orgRow = null;
        if (subCode != null && !subCode.isBlank()) {
            orgRow = orgMapper.selectByName(subCode); // 병원명으로 우선 시도
        }
        if (orgRow == null) {
            orgRow = orgMapper.selectAnyOne(); // 공통 미션/비전(아무 행)
        }

        CommentReportDTO.Org orgDto = null;
        if (orgRow != null) {
            orgDto = new CommentReportDTO.Org();
            orgDto.setName((String) orgRow.getOrDefault("c_name2", orgRow.getOrDefault("c_name", "")));
            orgDto.setMission((String) orgRow.getOrDefault("mission", ""));
            String visionJoined = joinVisionJson(orgRow.get("vision_json"), om); // JSON 배열 → 1문단
            orgDto.setVision(visionJoined);
            orgDto.setCiWordmarkUrl(null);
            orgDto.setCiSymbolUrl(null);
        }

        // --- (에세이) 모델 JSON → DTO 파싱 (vm 안에 already-summary 문자열이 JSON일 수 있음)
        CommentReportDTO report = null;
        try {
            String text = vm.getEssaySummary();
            if (text != null && text.trim().startsWith("{")) {
                report = om.readValue(text.trim(), CommentReportDTO.class);
            }
        } catch (Exception e) {
            // 로그 있으면 한 줄 정도 남겨두면 디버깅에 좋음
            log.warn("Failed to parse essaySummary json for ev={} targetId={}", ev, targetId, e);
        }

        // --- 객관식 통계
        ObjectiveStats stats0 = computeObjectiveStats(year, ev, vm.getRows(), subCode);

        double cohortOverall = (stats0.getDeptEvOverall100() != null && stats0.getDeptEvOverall100() > 0)
                ? stats0.getDeptEvOverall100()
                : Optional.ofNullable(stats0.getEvOverall100()).orElse(0.0);

        ObjectiveStats stats = stats0.toBuilder()
                .cohortOverall100(cohortOverall)
                // computeObjectiveStats 내부에서 이미 areaNarr를 세팅한다면 아래는 생략 가능
                .areaNarr(buildAreaNarratives(vm.getRows()))
                .build();

        // --- 점수기반 리포트 (SCORE): withAI=false면 캐시만, true면 생성 허용
        String scoreSignature = buildScoreSignature(vm.getRows(), stats);
        String scoreHash = sha256Hex(scoreSignature);

        Map<String, Object> savedScore = sumMapper.selectOne(year, targetId, ev, "SCORE");
        String scoreJson = null;
        CommentReportDTO scoreReport = null;

        boolean canReuse = savedScore != null
                && scoreHash.equals(savedScore.get("input_hash"))
                && "READY".equals(savedScore.get("status"));

        if (canReuse) {
            scoreJson = enforceLexicon((String) savedScore.get("summary"));
        } else if (withAI) {
            // 생성 허용(POST 버튼에서만)
            List<ScoreItem> items = topKScoreItems(stats, 10);
            String sys = ScorePrompt.system();
            String user = ScorePrompt.userWithOrg(
                    year, relationPath,
                    stats.getMyOverall100(),
                    Optional.ofNullable(stats.getCohortOverall100()).orElse(0.0),
                    items, null /* orgDto 공급 안 함 (모델 내 주입 제거) */);

            String modelUsed = (summarizer instanceof OpenAiCommentSummarizer) ? "gpt-4o-mini" : "local-heuristic";
            String status = "READY", err = null;

            try {
                if (summarizer instanceof OpenAiCommentSummarizer oa) {
                    scoreJson = oa.summarizeWithSystem(sys, user, "ko", 1600);
                } else {
                    scoreJson = summarizer.summarize(List.of(sys + "\n\n" + user), "ko", 8000);
                }
                if (scoreJson == null || scoreJson.isBlank()) {
                    status = "ERROR";
                    err = "빈 요약 반환";
                    scoreJson = "{\"title\":\"AI 리포트\",\"summary\":\"생성 실패\",\"strengths\":[],\"improvements\":[],\"tone\":\"\",\"metrics\":{\"responses\":0,\"essays\":0,\"score_overall\":0},\"next_actions\":[]}";
                }
            } catch (Exception ex) {
                status = "ERROR";
                err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                scoreJson = "{\"title\":\"AI 리포트\",\"summary\":\"생성 오류\",\"strengths\":[],\"improvements\":[],\"tone\":\"\",\"metrics\":{\"responses\":0,\"essays\":0,\"score_overall\":0},\"next_actions\":[]}";
            }

            scoreJson = enforceLexicon(scoreJson);
            sumMapper.upsert(year, targetId, ev, "SCORE",
                    scoreHash, 0, vm.getOverallResp(), "ko",
                    modelUsed, scoreJson, status, err);
        } // withAI=false & 캐시 없음이면 scoreJson=null (표시 안 함)

        // --- 점수기반 리포트 파싱
        if (scoreJson != null) {
            try {
                scoreJson = normalizeJsonEnvelope(scoreJson);
                if (scoreJson != null && scoreJson.trim().startsWith("{")) {
                    scoreJson = enforceLexicon(scoreJson);
                    scoreReport = om.readValue(scoreJson.trim(), CommentReportDTO.class);
                }
            } catch (Exception ignore) {
            }
        }

        // 서버 보강(조직 블록/문구 브릿지)
        if (scoreReport != null && orgDto != null) {
            if (scoreReport.getOrg() == null)
                scoreReport.setOrg(orgDto);
            String newSummary = weaveOrgIntoSummary(
                    Optional.ofNullable(scoreReport.getSummary()).orElse(""),
                    orgDto,
                    stats);
            scoreReport.setSummary(newSummary);
        }

        // 🔹 요약이 짧거나 데이터가 적더라도 충분히 풍부하게 보이도록 추가 보강
        if (scoreReport != null) {
            scoreReport = ensureRichScoreSummary(scoreReport, stats);
        }

        // 기존 인사이트 병합 로직 유지
        ObjectiveInsights ins = deriveInsights(stats);
        List<QuestionDist> qdists = computeQuestionDists(year, ev, subCode);

        List<String> insStrengths = new ArrayList<>(Optional.ofNullable(ins.getStrengths()).orElseGet(List::of));
        insStrengths = insStrengths.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .distinct().limit(5).collect(Collectors.toCollection(ArrayList::new));
        ins.setStrengths(insStrengths);

        List<String> insImprovements = new ArrayList<>(Optional.ofNullable(ins.getImprovements()).orElseGet(List::of));
        insImprovements = insImprovements.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .distinct().limit(5).collect(Collectors.toCollection(ArrayList::new));
        ins.setImprovements(insImprovements);

        if (report != null) {
            List<String> strengths = new ArrayList<>(Optional.ofNullable(report.getStrengths()).orElseGet(List::of));
            strengths.addAll(ins.getStrengths());
            strengths = strengths.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                    .distinct().limit(5).collect(Collectors.toCollection(ArrayList::new));
            report.setStrengths(strengths);

            List<String> improvements = new ArrayList<>(
                    Optional.ofNullable(report.getImprovements()).orElseGet(List::of));
            improvements.addAll(ins.getImprovements());
            improvements = improvements.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                    .distinct().limit(5).collect(Collectors.toCollection(ArrayList::new));
            report.setImprovements(improvements);
        }

        return new AffReportAggregate(vm, report, stats, scoreReport);
    }

    // 점수기반 리포트 summary 가 너무 짧으면,
    // 객관식 통계/영역 요약을 붙여서 길이를 보강 (데이터가 적어도 읽을 거리가 나오도록)
    private CommentReportDTO ensureRichScoreSummary(CommentReportDTO dto, ObjectiveStats stats) {
        if (dto == null)
            return null;

        String base = Optional.ofNullable(dto.getSummary()).orElse("").trim();

        // 문장 수 대략 계산 (., ?, !, …, 개행 기준)
        int sentenceCount = 0;
        if (!base.isEmpty()) {
            String[] parts = base.split("[\\.\\?\\!…\\n]+");
            for (String p : parts) {
                if (p != null && !p.trim().isEmpty()) {
                    sentenceCount++;
                }
            }
        }

        // 너무 짧으면(6문장 미만) 강제로 정보 붙이기
        if (sentenceCount >= 6 && stats != null && stats.getAreaSummary() != null
                && !stats.getAreaSummary().isEmpty()) {
            // 이미 충분히 길고, 영역 요약도 있으면 그대로 사용
            return dto;
        }

        StringBuilder sb = new StringBuilder();
        if (!base.isEmpty()) {
            sb.append(ensurePeriod(base)).append(" ");
        }

        if (stats != null) {
            double my = Math.round(stats.getMyOverall100());
            double same = Math.round(
                    Optional.ofNullable(stats.getCohortOverall100())
                            .orElse(Optional.ofNullable(stats.getEvOverall100()).orElse(0.0)));

            // 종합 점수 vs 동일 평가구간
            sb.append(String.format(
                    "현재 종합점수는 약 %d점으로 동일 평가구간 평균(약 %d점)과 비교하여 자신의 강점과 보완이 필요한 지점을 확인할 수 있습니다. ",
                    (int) my, (int) same));

            // 영역 요약(AREA_ORDER 순서대로, 한 줄씩 붙이기)
            Map<String, String> areaSummary = stats.getAreaSummary();
            if (areaSummary != null && !areaSummary.isEmpty()) {
                for (String area : AREA_ORDER) {
                    String line = areaSummary.get(area);
                    if (line == null || line.isBlank())
                        continue;
                    sb.append(ensurePeriod(line)).append(" ");
                }
            } else {
                // areaSummary 가 없어도 기본 문장 하나는 추가
                sb.append("각 영역은 응답자 수가 많지 않더라도, 현재 결과를 기준으로 강점과 개선 포인트가 어느 정도 드러나고 있습니다. ");
            }

            // 데이터가 매우 적을 때 안내 문장
            sb.append("특히 응답자 수가 많지 않은 상황에서도, 이번 결과를 통해 향후 어떤 역량을 유지·강화하고 어떤 부분을 보완해야 할지 방향성을 잡는 데 참고할 수 있습니다.");
        } else {
            // stats 자체가 null인 경우를 대비한 기본 문장
            sb.append("이번 결과는 응답 데이터가 많지 않지만, 현재까지 파악된 점수를 기반으로 강점과 개선 방향을 검토해 볼 수 있습니다.");
        }

        dto.setSummary(sb.toString().trim());
        return dto;
    }

    private List<ScoreItem> topKScoreItems(ObjectiveStats s, int k) {
        List<ScoreItem> all = new ArrayList<>();
        for (String area : s.getLabels()) {
            double my = s.getMyArea100().getOrDefault(area, 0.0);
            // 코호트 세분화가 있으면 그걸 쓰고, 없으면 구간 평균
            double cohort = Optional.ofNullable(s.getDeptEvArea100()).map(m -> m.getOrDefault(area, 0.0))
                    .filter(v -> v > 0).orElse(s.getEvArea100().getOrDefault(area, 0.0));
            all.add(new ScoreItem(area, area + " 전반", my, cohort));
        }
        all.sort((a, b) -> Double.compare(
                Math.abs((b.my100() - b.cohort100())),
                Math.abs((a.my100() - a.cohort100()))));
        return all.stream().limit(Math.max(1, k)).toList();
    }

    private String buildScoreSignature(List<RowAgg> rows, ObjectiveStats s) {
        StringBuilder sb = new StringBuilder();
        sb.append("overall=")
                .append(Math.round(s.getMyOverall100()))
                .append("|cohort=")
                .append(Math.round(Optional.ofNullable(s.getCohortOverall100()).orElse(0.0)));

        for (String area : s.getLabels()) {
            sb.append("|").append(area).append(":")
                    .append(Math.round(s.getMyArea100().getOrDefault(area, 0.0)))
                    .append("/")
                    .append(Math.round(
                            Optional.ofNullable(s.getDeptEvArea100())
                                    .map(m -> m.getOrDefault(area, 0.0))
                                    .filter(v -> v > 0).orElse(s.getEvArea100().getOrDefault(area, 0.0))));
        }
        // 문항 수준까지 반영하려면 rows loop로 avg/분포 요약을 더 붙이세요.
        return sb.toString();
    }

    private ObjectiveStats computeObjectiveStats(int evalYear, String dataEv, List<RowAgg> rowList, String subCode) {
        // 0) idx→area 매핑
        var qbankAll = mapper.selectQuestionBankAll(evalYear);
        Map<Integer, String> idxToArea = new LinkedHashMap<>();
        for (var q : qbankAll)
            idxToArea.put(q.getIdx(), q.getAreaLabel());

        // 1) 내(대상자) 영역 평균/분포는 rowList에서
        Map<String, Double> myArea5 = new LinkedHashMap<>();
        Map<String, Long> myCnt = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> myAreaDist = new LinkedHashMap<>();
        Map<String, List<String>> areaNarr = buildAreaNarratives(rowList); // ⬅️ 추가
        Map<String, String> areaSummary = buildAreaSummaries(rowList); // ★ 추가

        for (var r : rowList) {
            myArea5.merge(r.getArea(), r.getAvg(), Double::sum);
            myCnt.merge(r.getArea(), 1L, Long::sum);
            myAreaDist.computeIfAbsent(r.getArea(), k -> {
                var m = new LinkedHashMap<String, Integer>();
                ORDER.forEach(o -> m.put(o, 0));
                return m;
            });
            for (var k : ORDER) {
                int c = r.getCounts().getOrDefault(k, 0);
                myAreaDist.get(r.getArea()).put(k, myAreaDist.get(r.getArea()).get(k) + c);
            }
        }
        myArea5.replaceAll((a, sum) -> sum / myCnt.get(a));

        // 2) 구간 평균(같은 dataEv)
        var evSubs = mapper.selectAllSubmissionsByEv(evalYear, dataEv);
        Map<String, Integer> evSum = new LinkedHashMap<>(), evCnt = new LinkedHashMap<>();
        for (var s : evSubs) {
            Map<String, Object> root;
            try {
                root = om.readValue(s.getAnswersJson(), Map.class);
            } catch (Exception e) {
                continue;
            }
            Object radios = root.get("radios");
            if (!(radios instanceof Map<?, ?> r))
                continue;
            for (var e : ((Map<?, ?>) r).entrySet()) {
                String k = (String) e.getKey();
                String choice = (String) e.getValue();
                if (k == null || choice == null || !SCORE.containsKey(choice))
                    continue;
                int idx = toIdx(k);
                String area = idxToArea.get(idx);
                if (area == null)
                    continue;
                evSum.merge(area, SCORE.get(choice), Integer::sum);
                evCnt.merge(area, 1, Integer::sum);
            }
        }
        Map<String, Double> evArea5 = new LinkedHashMap<>();
        idxToArea.values().forEach(a -> {
            if (evCnt.containsKey(a))
                evArea5.put(a, evSum.get(a) * 1.0 / evCnt.get(a));
        });

        // 3) 전직원 평균(연도 전체)
        var allSubs = mapper.selectAllSubmissionsByYear(evalYear);
        Map<String, Integer> allSum = new LinkedHashMap<>(), allCnt = new LinkedHashMap<>();
        for (var s : allSubs) {
            Map<String, Object> root;
            try {
                root = om.readValue(s.getAnswersJson(), Map.class);
            } catch (Exception e) {
                continue;
            }
            Object radios = root.get("radios");
            if (!(radios instanceof Map<?, ?> r))
                continue;
            for (var e : ((Map<?, ?>) r).entrySet()) {
                String k = (String) e.getKey();
                String choice = (String) e.getValue();
                if (k == null || choice == null || !SCORE.containsKey(choice))
                    continue;
                int idx = toIdx(k);
                String area = idxToArea.get(idx);
                if (area == null)
                    continue;
                allSum.merge(area, SCORE.get(choice), Integer::sum);
                allCnt.merge(area, 1, Integer::sum);
            }
        }
        Map<String, Double> allArea5 = new LinkedHashMap<>();
        idxToArea.values().forEach(a -> {
            if (allCnt.containsKey(a))
                allArea5.put(a, allSum.get(a) * 1.0 / allCnt.get(a));
        });
        // --- (C) 같은 data_ev + 같은 부서 평균 ★ 추가
        Map<String, Double> deptEvArea5 = new LinkedHashMap<>();
        if (subCode != null && !subCode.isBlank()) {
            var deptEvSubs = mapper.selectAllSubmissionsByEvAndDept(evalYear, dataEv, subCode);
            deptEvArea5 = avgByAreaFromSubs(deptEvSubs, idxToArea);
        }

        // --- (D) 같은 연도 + 같은 부서 평균 ★ 추가
        Map<String, Double> deptAllArea5 = new LinkedHashMap<>();
        if (subCode != null && !subCode.isBlank()) {
            var deptAllSubs = mapper.selectAllSubmissionsByYearAndDept(evalYear, subCode);
            deptAllArea5 = avgByAreaFromSubs(deptAllSubs, idxToArea);
        }
        // 4) 100점 환산 (5점 만점 → ×20)
        // 100점 환산
        List<String> labels = new ArrayList<>(new LinkedHashSet<>(myArea5.keySet()));
        labels.sort(
                Comparator.comparingInt(AffEvalReportService::areaIdx) // ✅ 정적 참조
                        .thenComparing(Comparator.naturalOrder()));

        Map<String, Double> myArea100 = to100(myArea5, labels);
        Map<String, Double> evArea100 = to100(evArea5, labels);
        Map<String, Double> allArea100 = to100(allArea5, labels);
        Map<String, Double> deptEvArea100 = to100(deptEvArea5, labels);
        Map<String, Double> deptAllArea100 = to100(deptAllArea5, labels);

        double myOverall100 = avg(myArea100);
        double evOverall100 = avg(evArea100);
        double allOverall100 = avg(allArea100);
        double deptEvOverall100 = avg(deptEvArea100);
        double deptAllOverall100 = avg(deptAllArea100);

        return ObjectiveStats.builder()
                .myArea100(myArea100).evArea100(evArea100).allArea100(allArea100)
                .deptEvArea100(deptEvArea100).deptAllArea100(deptAllArea100)
                .myAreaDist(myAreaDist)
                .myOverall100(myOverall100).evOverall100(evOverall100).allOverall100(allOverall100)
                .deptEvOverall100(deptEvOverall100).deptAllOverall100(deptAllOverall100)
                .labels(labels)
                .areaNarr(areaNarr) // ⬅️ 추가
                .areaSummary(areaSummary) // ★ 추가
                .build();
    }

    private ObjectiveInsights deriveInsights(ObjectiveStats s) {
        var diffs = new ArrayList<Map.Entry<String, Double>>();
        for (var a : s.getLabels()) {
            diffs.add(Map.entry(a, s.getMyArea100().getOrDefault(a, 0.0) - s.getEvArea100().getOrDefault(a, 0.0)));
        }
        diffs.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));

        List<String> strengths = diffs.stream()
                .filter(e -> e.getValue() >= 3.0).limit(3)
                .map(e -> e.getKey() + " 강점 (평균 대비 +" + String.format("%.1f", e.getValue()) + "점)").toList();
        List<String> improvements = diffs.stream()
                .filter(e -> e.getValue() <= -3.0).sorted(Map.Entry.comparingByValue()).limit(3)
                .map(e -> e.getKey() + " 개선 필요 (평균 대비 " + String.format("%.1f", e.getValue()) + "점)").toList();

        String headline = String.format("종합 %.0f점, 구간 평균 %.0f점, 전체 평균 %.0f점",
                s.getMyOverall100(), s.getEvOverall100(), s.getAllOverall100());

        return ObjectiveInsights.builder()
                .strengths(java.util.List.of()) // 빈 리스트 반환
                .improvements(java.util.List.of()) // 빈 리스트 반환
                .headline(headline)
                .build();
    }

    // 코호트(동일 ev + 부서)까지 지원하는 버전
    public List<QuestionDist> computeQuestionDists(int evalYear, String dataEv, @Nullable String subCode) {
        // 0) idx -> (area, label)
        var metas = mapper.selectQuestionBankAll(evalYear);
        Map<Integer, QuestionMeta> idxToMeta = new LinkedHashMap<>();
        for (var m : metas)
            idxToMeta.put(m.getIdx(), m);

        // 1) 초기화
        final List<String> ORDER = List.of("매우우수", "우수", "보통", "미흡", "매우미흡");
        Map<Integer, Map<String, Integer>> qCounts = new LinkedHashMap<>();
        idxToMeta.forEach((idx, meta) -> {
            var zero = new LinkedHashMap<String, Integer>();
            ORDER.forEach(k -> zero.put(k, 0));
            qCounts.put(idx, zero);
        });

        // 2) 제출 소스 선택: 회사 전체 vs 코호트
        List<EvalSubmissionRow> subs = (subCode == null || subCode.isBlank())
                ? mapper.selectAllSubmissionsByEv(evalYear, dataEv) // 기존
                : mapper.selectAllSubmissionsByEvAndDept(evalYear, dataEv, subCode); // 코호트

        for (var s : subs) {
            Map<String, Object> root;
            try {
                root = om.readValue(s.getAnswersJson(), Map.class);
            } catch (Exception e) {
                continue;
            }
            Object radiosObj = root.get("radios");
            if (!(radiosObj instanceof Map<?, ?> radios))
                continue;

            for (var e : ((Map<?, ?>) radios).entrySet()) {
                String k = (String) e.getKey();
                String choice = (String) e.getValue();
                if (choice == null || !ORDER.contains(choice))
                    continue;
                int idx = toIdx(k);
                var bucket = qCounts.get(idx);
                if (bucket == null)
                    continue;
                bucket.put(choice, bucket.get(choice) + 1);
            }
        }

        // 3) 비율/극성 계산
        final int MIN_RESP = 0; // 응답부족 스킵
        List<QuestionDist> out = new ArrayList<>();
        qCounts.forEach((idx, c) -> {
            int total = c.values().stream().mapToInt(Integer::intValue).sum();
            if (total < MIN_RESP)
                return;
            double pos = (c.getOrDefault("매우우수", 0) + c.getOrDefault("우수", 0)) / (double) total;
            double neg = (c.getOrDefault("미흡", 0) + c.getOrDefault("매우미흡", 0)) / (double) total;
            var meta = idxToMeta.get(idx);
            out.add(QuestionDist.builder()
                    .idx(idx)
                    .area(meta != null ? meta.getAreaLabel() : null)
                    .label(meta != null ? meta.getLabelText() : null)
                    .counts(c)
                    .posRate(pos)
                    .negRate(neg)
                    .polarity(pos - neg)
                    .build());
        });

        out.sort(Comparator.comparingDouble(QuestionDist::getPolarity).reversed());
        return out;
    }

    // 각 영역의 RowAgg 목록을 받아 '문항별 분포'로 내러티브 생성
    private Map<String, List<String>> buildAreaNarratives(List<RowAgg> rowList) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (rowList == null || rowList.isEmpty())
            return out;

        // 영역별로 묶고 문항번호 순 정렬
        Map<String, List<RowAgg>> byArea = new LinkedHashMap<>();
        for (var r : rowList)
            byArea.computeIfAbsent(r.getArea(), k -> new ArrayList<>()).add(r);
        byArea.values().forEach(list -> list.sort(Comparator.comparing(RowAgg::getIdx)));

        for (var entry : byArea.entrySet()) {
            String area = entry.getKey();
            List<String> narrs = new ArrayList<>();

            for (var r : entry.getValue()) {
                Map<String, Integer> c = r.getCounts();
                int muu = c.getOrDefault("매우우수", 0);
                int us = c.getOrDefault("우수", 0);
                int bt = c.getOrDefault("보통", 0);
                int mh = c.getOrDefault("미흡", 0);
                int mmh = c.getOrDefault("매우미흡", 0);
                int total = muu + us + bt + mh + mmh;
                if (total < 5)
                    continue; // 응답 너무 적으면 스킵

                // 최빈값
                String mode = "보통";
                int best = -1;
                for (String k : ORDER) {
                    int v = c.getOrDefault(k, 0);
                    if (v > best) {
                        best = v;
                        mode = k;
                    }
                }

                double posPct = (muu + us) * 100.0 / total;
                double negPct = (mh + mmh) * 100.0 / total;

                String q = Optional.ofNullable(r.getLabel()).orElse("").trim();
                String aff = normalizeAsAffirmative(q);

                // ✨ 숫자 없는 서술식으로 전환
                if (posPct >= 75) {
                    narrs.add(String.format("%s 전반에 대해 매우 우수한 평가를 받았습니다.", aff));
                } else if (posPct >= 60) {
                    narrs.add(String.format("%s에서 우수한 평가를 받았습니다.", aff));
                } else if (negPct >= 30) {
                    narrs.add(String.format("%s에서 개선이 필요합니다.", trimTopic(aff)));
                }

                if (narrs.size() >= 2)
                    break; // 영역당 최대 2문장
            }

            if (narrs.isEmpty())
                narrs.add("특이한 쏠림 없이 고르게 평가되었습니다.");
            out.put(area, narrs);
        }
        return out;
    }

    // 예시: 영역 요약 문장 구성(숫자 표기 제거)
    private String areaHeadline(double posPctArea, double negPctArea, String areaName) {
        if (posPctArea >= 75)
            return areaName + " 영역은 전반적으로 매우 우수한 평가를 받았습니다.";
        if (posPctArea >= 60)
            return areaName + " 영역은 전반적으로 우수한 평가를 받았습니다.";
        if (negPctArea >= 30)
            return areaName + " 영역은 전반적으로 개선이 필요합니다.";
        return areaName + " 영역은 전반적으로 안정적인 평가를 보였습니다.";
    }

    // 예시: 강조 문장(상위 문항 1~2개를 골라 붙이기, 숫자 없이)
    private String areaEmphasis(List<String> topLabels) {
        if (topLabels == null || topLabels.isEmpty())
            return null;
        if (topLabels.size() == 1)
            return "특히 " + topLabels.get(0) + "에서 평가가 우수합니다.";
        return "특히 " + topLabels.get(0) + "와 " + topLabels.get(1) + "에서 평가가 우수합니다.";
    }

    private List<String> buildStrengthBullets(List<QuestionDist> qd) {
        return qd.stream()
                .filter(q -> Double.isFinite(q.getPolarity())) // 유효한 수치만
                .sorted(Comparator.comparingDouble(QuestionDist::getPolarity).reversed())
                .limit(3)
                .map(q -> String.format("[%s] %s (긍정 %.0f%%, 부정 %.0f%%)",
                        nz(q.getArea()),
                        normalizeAsAffirmative(q.getLabel()),
                        q.getPosRate() * 100, q.getNegRate() * 100))
                .toList();
    }

    private List<String> buildImprovementBullets(List<QuestionDist> qd) {
        return qd.stream()
                .filter(q -> Double.isFinite(q.getPolarity()))
                .sorted(Comparator.comparingDouble(QuestionDist::getPolarity)) // 낮은 값(부정 우세) 우선
                .limit(3)
                .map(q -> String.format("[%s] %s 개선 필요 (부정 %.0f%%)",
                        nz(q.getArea()),
                        trimTopic(normalizeAsAffirmative(q.getLabel())),
                        q.getNegRate() * 100))
                .toList();
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    // “~합니까?/인가요?” → “~합니다.” 식으로 간단 변환
    private String normalizeAsAffirmative(String q) {
        if (q == null)
            return "";
        String s = q.trim();
        s = s.replaceAll("\\?$", ""); // 끝 물음표 제거
        s = s.replaceAll("(습니까|합니까|인가요|인가\\?|인가)$", "합니다");
        s = s.replaceAll("(배려합니까)$", "배려합니다");
        s = s.replaceAll("(존중하고 수용합니까)$", "존중하고 수용합니다");
        return s;
    }

    // “~합니다.” 앞부분 주제만 남기기 (개선 문장에 사용)
    private String trimTopic(String s) {
        if (s == null)
            return "";
        // 필요시 더 정교화
        return s.replaceAll("(을|를|에|에서|에게)$", "");
    }

    // 헬퍼들 (같은 클래스 내부)
    private Map<String, Double> avgByAreaFromSubs(List<EvalSubmissionRow> subs, Map<Integer, String> idxToArea) {
        Map<String, Integer> sum = new LinkedHashMap<>(), cnt = new LinkedHashMap<>();
        for (var s : subs) {
            Map<String, Object> root;
            try {
                root = om.readValue(s.getAnswersJson(), Map.class);
            } catch (Exception e) {
                continue;
            }
            Object radios = root.get("radios");
            if (!(radios instanceof Map<?, ?> r))
                continue;
            for (var e : ((Map<?, ?>) r).entrySet()) {
                String k = (String) e.getKey();
                String choice = (String) e.getValue();
                if (k == null || choice == null || !SCORE.containsKey(choice))
                    continue;
                int idx = toIdx(k);
                String area = idxToArea.get(idx);
                if (area == null)
                    continue;
                sum.merge(area, SCORE.get(choice), Integer::sum);
                cnt.merge(area, 1, Integer::sum);
            }
        }
        Map<String, Double> area5 = new LinkedHashMap<>();
        for (var a : idxToArea.values()) {
            if (cnt.containsKey(a))
                area5.put(a, sum.get(a) * 1.0 / cnt.get(a));
        }
        return area5;
    }

    private Map<String, Double> to100(Map<String, Double> area5, List<String> labels) {
        Map<String, Double> z = new LinkedHashMap<>();
        for (var a : labels)
            z.put(a, area5.getOrDefault(a, 0.0) * 20);
        return z;
    }

    private double avg(Map<String, Double> m) {
        return m.values().stream().mapToDouble(d -> d).average().orElse(0);
    }

    // ✅ 퍼센트 표기 제거 & head/tail 결합으로 자연어 한 줄 요약
    private Map<String, String> buildAreaSummaries(List<RowAgg> rowList) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rowList == null || rowList.isEmpty())
            return out;

        // 1) 영역별로 묶기
        Map<String, List<RowAgg>> byArea = new LinkedHashMap<>();
        for (var r : rowList)
            byArea.computeIfAbsent(r.getArea(), k -> new ArrayList<>()).add(r);

        for (var entry : byArea.entrySet()) {
            String area = entry.getKey();
            List<RowAgg> rows = entry.getValue();
            rows.sort(Comparator.comparing(RowAgg::getIdx));

            // 2) 영역 전체 분포 합산 + 문항별 극성 산출
            int muu = 0, us = 0, bt = 0, mh = 0, mmh = 0;
            // record 대신 로컬 클래스로 대체
            class QPol {
                final int idx;
                final String label;
                final double pos;
                final double neg;
                final double pol;

                QPol(int idx, String label, double pos, double neg, double pol) {
                    this.idx = idx;
                    this.label = label;
                    this.pos = pos;
                    this.neg = neg;
                    this.pol = pol;
                }

                public double pol() {
                    return pol;
                }

                public String label() {
                    return label;
                }

                public int idx() {
                    return idx;
                }

                public double pos() {
                    return pos;
                }

                public double neg() {
                    return neg;
                }
            }
            List<QPol> qpols = new ArrayList<>();

            for (var r : rows) {
                Map<String, Integer> c = r.getCounts();
                int _muu = c.getOrDefault("매우우수", 0);
                int _us = c.getOrDefault("우수", 0);
                int _bt = c.getOrDefault("보통", 0);
                int _mh = c.getOrDefault("미흡", 0);
                int _mmh = c.getOrDefault("매우미흡", 0);
                int tot = _muu + _us + _bt + _mh + _mmh;

                muu += _muu;
                us += _us;
                bt += _bt;
                mh += _mh;
                mmh += _mmh;

                if (tot >= 5) {
                    double pos = (_muu + _us) / (double) tot;
                    double neg = (_mh + _mmh) / (double) tot;
                    String aff = normalizeAsAffirmative(Optional.ofNullable(r.getLabel()).orElse(""));
                    qpols.add(new QPol(r.getIdx(), aff, pos, neg, pos - neg));
                }
            }

            int total = muu + us + bt + mh + mmh;
            if (total < 0) {
                out.put(area, area + " 영역은 응답이 충분하지 않아 요약을 제공하지 않습니다.");
                continue;
            }

            double posPctArea = (muu + us) * 100.0 / total;
            double negPctArea = (mh + mmh) * 100.0 / total;

            // 3) 상위 긍정 문항 1~2개 뽑아 tail 구성
            qpols.sort(Comparator.comparingDouble(QPol::pol).reversed());
            List<String> topPositiveLabels = qpols.stream()
                    .limit(2)
                    .map(q -> trimTopic(q.label()))
                    .filter(s -> s != null && !s.isBlank())
                    .toList();

            // 4) head/tail 결합
            String head = areaHeadline(posPctArea, negPctArea, area);
            // 기존: String tail = areaEmphasis(topPositiveLabels);
            String tail = buildEmphasisFromLabels(topPositiveLabels);

            // 한 문장으로 깔끔히 (마침표 중복 방지)
            String finalLine;
            if (tail != null) {
                // head가 이미 마침표일 수도 있으니 제거 후 합치기
                String headNoPeriod = head.replaceAll("[\\.!…]+$", "");
                finalLine = headNoPeriod + ". " + tail; // 항상 1문장 느낌 유지
            } else {
                finalLine = ensurePeriod(head);
            }
            out.put(area, finalLine);
        }
        return out;
    }

    // 라벨을 "공정한 대우·의견 존중", "고객 이해·배려" 같은 짧은 키워드로 압축
    private String compressLabel(String s) {
        if (s == null)
            return "";
        String t = s.trim();

        // 1) 흔한 군더더기·조사·불필요 어구 제거
        t = t.replaceAll("\\?$", "");
        t = t.replaceAll("(을|를|이|가|은|는|에|에서|에게|과|와)$", "");
        t = t.replace("등 ", "");
        t = t.replace("내·외부 ", "");
        t = t.replace("그들의 ", "");
        t = t.replace("직원들의 ", "직원 ");
        t = t.replace("부하직원들을 ", "직원 ");
        t = t.replace("환자 ", "고객 ");
        t = t.replace("고객이 원하는 바를", "고객 요구");
        t = t.replace("원하는 바를", "요구");
        t = t.replace("충족하도록 ", "");
        t = t.replace("대우하며", "대우");
        t = t.replace("존중하고 수용합니다", "의견 존중");
        t = t.replace("배려합니다", "배려");
        t = t.replace("이해하며", "이해");
        t = t.replace("합니다", "");
        t = t.replace("합니까", "");
        t = t.replace("인가요", "");
        t = t.replace("인가", "");

        // 2) 너무 긴 구는 콤마/접속사 기준으로 분할 후 의미어만
        String[] parts = t.split("[,·/]|\\s*(그리고|및|및\\s|또는|혹은)\\s*");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            String w = p.trim();
            if (w.isEmpty())
                continue;
            // 불용어(짧은 기능어) 제거
            if (w.length() <= 1)
                continue;
            // 자주 나오는 형태 정돈
            w = w.replaceAll("\\s+", " ");
            tokens.add(w);
        }

        // 3) 의미 있는 키워드 1~2개만 남기기 (길이/정보량 기준 단순 heuristic)
        tokens = tokens.stream()
                .map(x -> x.replaceAll("(을|를|이|가|은|는|에|에서|에게)$", ""))
                .map(String::trim)
                .filter(x -> x.length() >= 2)
                .distinct()
                .collect(Collectors.toList());

        if (tokens.isEmpty())
            return t;

        // 가벼운 스코어: 길이 우선
        tokens.sort(Comparator.comparingInt(String::length).reversed());

        // 최종 1~2개
        if (tokens.size() >= 2) {
            return tokens.get(0) + "·" + tokens.get(1);
        }
        return tokens.get(0);
    }

    // "특히 ~에서 평가가 우수합니다." 한 절 생성 (키워드 없으면 null)
    private String buildEmphasisFromLabels(List<String> labels) {
        if (labels == null || labels.isEmpty())
            return null;
        // 상위 1~2개 라벨을 압축
        List<String> kws = labels.stream()
                .map(this::compressLabel)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(2)
                .collect(Collectors.toList());
        if (kws.isEmpty())
            return null;

        if (kws.size() == 1) {
            return "특히 " + kws.get(0) + "에 대한 평가가 우수합니다.";
        }
        return "특히 " + kws.get(0) + "와 " + kws.get(1) + "에 대한 평가가 우수합니다.";
    }

    // 문장 끝 마침표 중복 방지
    private static String ensurePeriod(String s) {
        if (s == null || s.isBlank())
            return s;
        char last = s.charAt(s.length() - 1);
        if (last == '.' || last == '!' || last == '…')
            return s;
        return s + ".";
    }

    // EvalReportService 내부 아무 곳(필드 근처)에 상수 팩토리 추가
    private OrgContext buildOrgContext() {
        return OrgContext.builder()
                .mission(List.of(
                        "모두가 어우러져 행복한 세상을 만드는 의료", // 미션 영역은 이미지라 요약문으로
                        "사랑과 나눔의 가치 실천"))
                .vision(List.of(
                        "환자의 몸과 마음을 함께 치료하는 병원",
                        "지속적인 교육과 학습으로 개인과 병원의 성장을 도모하는 병원",
                        "웃음과 즐거운 문화가 있는 병원",
                        "지역사회 봉사와 나눔을 실천하는 병원",
                        "끊임없는 열정과 혁신을 추구하는 병원"))
                .coreValues(List.of("섬김", "배움", "키움", "나눔"))
                // .ciPngUrl("https://jhsarang.imweb.me/download?file=...") // 필요 시
                // .ciAiUrl("https://jhsarang.imweb.me/download?file=...") // 필요 시
                .build();
    }

    // --- 요약에 미션/비전을 자연스럽게 덧붙이는 브릿지 빌더 ---
    private String weaveOrgIntoSummary(String base, CommentReportDTO.Org org, ObjectiveStats stats) {
        if (org == null)
            return ensurePeriod(base);

        // 1) 상위 강점 영역 1~2개 뽑기(동일 평가구간 대비 +차이 큰 것 우선)
        List<String> topAreas = stats != null ? pickTopAreas(stats, 2) : List.of();

        // 2) 미션/비전에서 문장 핵심만 추출 (너무 길면 앞부분만)
        String mission = safeCompact(org.getMission(), 72);
        String vision = safeCompact(org.getVision(), 72);

        // 3) 브릿지 문장 조립 (숫자·퍼센트 없이 서술형)
        StringBuilder bridge = new StringBuilder();

        // 강점 영역을 미션/비전과 연결
        if (!topAreas.isEmpty()) {
            bridge.append("이번 결과는 ");
            bridge.append(String.join("·", topAreas));
            bridge.append(" 측면의 강점이 ");
            if (!isBlank(mission)) {
                bridge.append("기관 미션 ‘").append(mission).append("’과 ");
            }
            if (!isBlank(vision)) {
                bridge.append("비전 ‘").append(vision).append("’에 ");
            }
            if (!isBlank(mission) || !isBlank(vision)) {
                bridge.append("부합하는 흐름을 보여줍니다.");
            } else {
                bridge.append("조직이 지향하는 방향과 맞닿아 있습니다.");
            }
        } else if (!isBlank(mission) || !isBlank(vision)) {
            bridge.append("이번 평가 방향성은 ");
            if (!isBlank(mission))
                bridge.append("미션 ‘").append(mission).append("’과 ");
            if (!isBlank(vision))
                bridge.append("비전 ‘").append(vision).append("’에 ");
            bridge.append("대체로 정렬되어 있습니다.");
        }

        // 4) 최종 합성(마침표 중복 방지)
        String head = ensurePeriod(base);
        String tail = ensurePeriod(bridge.toString());
        if (isBlank(bridge.toString()))
            return head; // 브릿지가 비면 원문 유지
        return head + " " + tail;
    }

    public CommentReportDTO buildKyTotalSummary(
            int year,
            String userId,
            String relationLabel,
            String compareKey,
            double i40, double ii15, double iii15, double iv10, double v20,
            Map<String, Double> kpi100,
            boolean withAI) {

        // 0) 누락된 숨은 키 보정 (0점 문제 예방)
        double total100 = (i40 + ii15 + iii15 + iv10 + v20);
        if (kpi100 == null)
            kpi100 = new LinkedHashMap<>();
        kpi100.putIfAbsent("_TOTAL_my", total100);
        kpi100.putIfAbsent("_TOTAL_same", 0.0); // 호출부에서 실제 팀 평균 합(100) 넣으면 이 값이 대체됨

        // 5축(100)도 혹시 비어있으면 보정
        kpi100.putIfAbsent("I 재무성과(40)→100", (i40 / 40d) * 100d);
        kpi100.putIfAbsent("II 고객서비스(15)→100", (ii15 / 15d) * 100d);
        kpi100.putIfAbsent("III 프로세스혁신(15)→100", (iii15 / 15d) * 100d);
        kpi100.putIfAbsent("IV 학습성장(10)→100", (iv10 / 10d) * 100d);
        kpi100.putIfAbsent("V 다면평가(20)→100", (v20 / 20d) * 100d);

        // 1) 페이로드(원점수 + 100스케일)
        var payload = new LinkedHashMap<String, Object>();
        payload.put("섹션원점수", Map.of(
                "I(40)", i40, "II(15)", ii15, "III(15)", iii15, "IV(10)", iv10, "V(20)", v20,
                "TOTAL(100)", total100));
        payload.put("섹션100점화", kpi100);

        // 2) 시그니처(캐시 키)
        String signature = new StringBuilder()
                .append("ky_total=").append(Math.round(kpi100.getOrDefault("_TOTAL_my", total100)))
                .append("|same=").append(Math.round(kpi100.getOrDefault("_TOTAL_same", 0.0)))
                .append("|kpi=")
                .append(Math.round(kpi100.getOrDefault("I 재무성과(40)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("II 고객서비스(15)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("III 프로세스혁신(15)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("IV 학습성장(10)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("V 다면평가(20)→100", 0.0)))
                .toString();
        String sigHash = sha256Hex(signature);

        // 3) 캐시(kind 분리 권장)
        final String KIND = "SCORE_KY"; // ⚠️ 일반 SCORE와 구분
        Map<String, Object> saved = sumMapper.selectOne(year, userId, "TOTAL", KIND);
        String json = null;
        CommentReportDTO dto = null;

        boolean canReuse = saved != null
                && sigHash.equals(saved.get("input_hash"))
                && "READY".equals(saved.get("status"));
        if (canReuse) {
            json = enforceLexicon(String.valueOf(saved.get("summary")));
        } else if (withAI) {
            String sys = KyTotalPrompt.system();
            // 기존 시그니처를 그대로 사용(호환 오버로드)
            String usr = KyTotalPrompt.user(year, relationLabel, kpi100, payload);

            String modelUsed = (summarizer instanceof OpenAiCommentSummarizer) ? "gpt-4o-mini" : "local-heuristic";
            String status = "READY", err = null;
            try {
                if (summarizer instanceof OpenAiCommentSummarizer oa) {
                    json = oa.summarizeWithSystem(sys, usr, "ko", 1800);
                } else {
                    json = summarizer.summarize(List.of(sys + "\n\n" + usr), "ko", 6000);
                }
                if (json == null || json.isBlank()) {
                    status = "ERROR";
                    err = "빈 요약 반환";
                    json = "{\"title\":\"[경혁팀] 종합평가 AI 리포트\",\"summary\":\"생성 실패\",\"strengths\":[],\"improvements\":[],\"tone\":\"neutral\",\"metrics\":{\"responses\":0,\"essays\":0,\"score_overall\":0},\"overall_comparison\":{\"my\":0,\"same_period\":0},\"kpi\":{},\"by_area\":{},\"next_actions\":[]}";
                }
            } catch (Exception ex) {
                status = "ERROR";
                err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                json = "{\"title\":\"[경혁팀] 종합평가 AI 리포트\",\"summary\":\"생성 오류\",\"strengths\":[],\"improvements\":[],\"tone\":\"neutral\",\"metrics\":{\"responses\":0,\"essays\":0,\"score_overall\":0},\"overall_comparison\":{\"my\":0,\"same_period\":0},\"kpi\":{},\"by_area\":{},\"next_actions\":[]}";
            }
            json = enforceLexicon(json);

            sumMapper.upsert(year, userId, "TOTAL", KIND,
                    sigHash, 0, 0,
                    "ko", modelUsed, json, status, err);
        }

        if (json != null) {
            try {
                json = normalizeJsonEnvelope(json);
                if (json.trim().startsWith("{")) {
                    dto = om.readValue(json, CommentReportDTO.class);
                }
            } catch (Exception ignore) {
            }
        }
        return dto;
    }

    public final class KyTotalPrompt {
        private KyTotalPrompt() {
        }

        public static String system() {
            return """
                    당신은 인사평가 '경혁팀 종합(TOTAL)' 요약 리포트를 작성하는 도우미입니다.
                    입력에는 (1) 5개 축 점수(개인·평균, 100점 환산), (2) 원점수(합 100), (3) 코멘트(에세이), (4) 숨은 합계 키가 포함됩니다.

                    규칙:
                    - 보고 대상은 '개인'입니다. 첫 문장에서 반드시 "개인의 종합점수 X점, 동일 평가구간 평균 Y점"을 명시하세요.
                    - ‘코호트’라는 용어를 쓰지 말고 ‘동일 평가구간’으로 표기합니다.
                    - 개인정보/실명 제거, 비난/비속어는 중립적으로 재서술합니다.
                    - KPI 5축: I 재무성과, II 고객서비스, III 프로세스혁신, IV 학습성장, V 다면평가(모두 100점 스케일).
                    - 개선과제는 구체 행동·기간·측정기준을 포함하여 2~4개 제시합니다.
                    - title은 반드시 "[경혁팀] 종합평가 AI 리포트"로 고정합니다.
                    - 출력은 오직 JSON 한 개(추가 텍스트 금지)입니다.

                    JSON 스키마:
                    {
                      "title": "[경혁팀] 종합평가 AI 리포트",
                      "summary": string,
                      "strengths": string[],
                      "improvements": string[],
                      "tone": string,
                      "metrics": { "responses": number, "essays": number, "score_overall": number },
                      "overall_comparison": { "my": number, "same_period": number },
                      "kpi": {
                        "I 재무성과(40)→100": number,
                        "II 고객서비스(15)→100": number,
                        "III 프로세스혁신(15)→100": number,
                        "IV 학습성장(10)→100": number,
                        "V 다면평가(20)→100": number
                      },
                      "by_area": {
                        "I 재무성과":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "II 고객서비스":{"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "III 프로세스혁신":{"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "IV 학습성장":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "V 다면평가":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]}
                      },
                      "next_actions": string[]
                    }
                    """;
        }

        // ✅ [A] 기존 호출과 100% 호환되는 경량 오버로드 (다른 클래스 수정 필요 없음)
        public static String user(int year, String relationLabel,
                Map<String, Double> kpi100,
                Map<String, Object> payload) {
            return user(year, relationLabel, kpi100,
                    /* avg100ByAxis */ null,
                    /* comments */ null,
                    /* respCount */ 0,
                    /* essayCount */ 0,
                    /* rawSection100 */ payload);
        }

        // ✅ [B] 확장형(필요 시 새 데이터 추가해도 시그니처 고정 가능)
        public static String user(int year,
                String relationLabel,
                Map<String, Double> kpi100,
                @Nullable Map<String, Double> avg100ByAxis, // 축별 팀 평균(선택)
                @Nullable List<String> comments, // 코멘트(선택)
                int respCount, int essayCount,
                @Nullable Map<String, Object> rawSection100 // 원점수/디버깅(선택)
        ) {
            java.util.function.DoubleUnaryOperator r1 = v -> Math.round(v * 10.0) / 10.0;

            double myTotal = r1.applyAsDouble(kpi100.getOrDefault("_TOTAL_my", 0.0));
            double sameTotal = r1.applyAsDouble(kpi100.getOrDefault("_TOTAL_same", 0.0));

            String[] axes = { "I 재무성과", "II 고객서비스", "III 프로세스혁신", "IV 학습성장", "V 다면평가" };
            String[] axisKeys100 = {
                    "I 재무성과(40)→100", "II 고객서비스(15)→100", "III 프로세스혁신(15)→100",
                    "IV 학습성장(10)→100", "V 다면평가(20)→100"
            };

            StringBuilder sb = new StringBuilder();
            sb.append("연도: ").append(year).append("\n");
            sb.append("관계: ").append(relationLabel).append("\n\n");
            sb.append("개인 종합점수(100): ").append(myTotal).append("\n");
            sb.append("동일 평가구간 종합점수(100): ").append(sameTotal).append("\n\n");

            sb.append("[5개 축(100) 개인/동일 평가구간]\n");
            for (int i = 0; i < axes.length; i++) {
                String a = axes[i];
                String k = axisKeys100[i];
                double mine = r1.applyAsDouble(kpi100.getOrDefault(k, 0.0));
                double avg = 0.0;
                if (avg100ByAxis != null) {
                    avg = r1.applyAsDouble(
                            Optional.ofNullable(avg100ByAxis.get(a))
                                    .orElse(avg100ByAxis.getOrDefault(k, 0.0)));
                }
                sb.append("• ").append(a).append(" | my=").append(mine)
                        .append(", same_period=").append(avg).append("\n");
            }

            if (rawSection100 != null && rawSection100.containsKey("섹션원점수")) {
                sb.append("\n[원점수(합=100)]\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = (Map<String, Object>) rawSection100.get("섹션원점수");
                raw.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            }

            sb.append("\n[코멘트 원문(---로 구분)]\n");
            if (comments == null || comments.isEmpty()) {
                sb.append("(코멘트 없음)\n");
            } else {
                for (String c : comments) {
                    if (c != null && !c.isBlank())
                        sb.append("---\n").append(c.trim()).append("\n");
                }
            }

            sb.append("\n[메트릭]\n");
            sb.append("responses=").append(respCount).append(", essays=").append(essayCount).append("\n");

            sb.append("""
                    \n요구사항:
                    1) 첫 문장에 "개인의 종합점수 X점, 팀 평균 Y점"을 명시해 개인 기준으로 서술하세요.
                    2) 5축 중 높은/낮은 축을 1~2문장으로 요약하고 근거(수치)를 붙이세요.
                    3) 개선 과제 2~4개를 불릿으로 제시(구체 행동·기간·측정 기준 포함).
                    4) JSON만 출력하며 title은 "[경혁팀] 종합평가 AI 리포트".
                    """);

            return sb.toString();
        }
    }

    // 상위 강점 영역 n개(동일 평가구간 대비 양의 차이 큰 순)
    private List<String> pickTopAreas(ObjectiveStats s, int k) {
        if (s == null || s.getLabels() == null)
            return List.of();
        // record 사용 대신 로컬 홀더 클래스로 대체 (하위 Java 버전 호환)
        class Diff {
            public final String area;
            public final double d;

            public Diff(String area, double d) {
                this.area = area;
                this.d = d;
            }
        }
        List<Diff> diffs = new ArrayList<>();
        for (String a : s.getLabels()) {
            double my = s.getMyArea100().getOrDefault(a, 0.0);
            double avg = s.getEvArea100().getOrDefault(a, 0.0);
            diffs.add(new Diff(a, my - avg));
        }
        return diffs.stream()
                .filter(d -> d.d > 0.0) // 평균보다 높은 영역
                .sorted((x, y) -> Double.compare(y.d, x.d))
                .limit(Math.max(1, k))
                .map(d -> d.area)
                .toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // 너무 긴 미션/비전은 자연스럽게 줄이기
    private static String safeCompact(String s, int max) {
        if (isBlank(s))
            return "";
        String t = s.strip();
        if (t.length() <= max)
            return t;
        // 문장 경계 기준으로 살짝 자르기
        int cut = Math.max(t.indexOf('。'), Math.max(t.indexOf('.'), t.indexOf('!')));
        if (cut > 0 && cut < max)
            return t.substring(0, cut).strip();
        return t.substring(0, max).strip() + "…";
    }

    // JSON 배열을 받아 "문장1 · 문장2 · ..." 로 합치기
    private String joinVisionJson(Object raw, com.fasterxml.jackson.databind.ObjectMapper om) {
        if (raw == null)
            return "";
        try {
            // raw가 String이면 그대로 파싱, Map이면 toString() 해도 JSON으로 파싱됨
            var list = om.readValue(raw.toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                    });
            return list == null ? "" : String.join(" · ", list);
        } catch (Exception e) {
            return ""; // 파싱 실패해도 서비스 죽지 않게
        }
    }

    @Data
    @Builder
    public static class QuestionDist {
        Integer idx;
        String area; // 섬김/배움/키움/나눔/목표관리
        String label; // 문항 라벨
        Map<String, Integer> counts; // 매우우수~매우미흡
        double posRate; // (매우우수+우수)/총응답
        double negRate; // (미흡+매우미흡)/총응답
        double polarity; // posRate - negRate (극성: +면 긍정 우세, -면 부정 우세)
    }

    @Data
    @Builder
    public static class RowAgg {
        String area;
        Integer idx;
        String label;
        Map<String, Integer> counts;
        Double avg;
        Integer resp;
        // ★ 추가: 영역 묶음을 위한 정보
        Boolean groupFirst; // 이 행이 영역 그룹의 첫 행인지?
        Integer groupSize; // 그룹 전체 행 수(rowspan 값)
    }

}
