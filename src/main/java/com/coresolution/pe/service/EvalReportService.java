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
import org.springframework.stereotype.Service;

import com.coresolution.pe.controller.AffPageController;
import com.coresolution.pe.entity.CommentReportDTO;
import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.ObjectiveInsights;
import com.coresolution.pe.entity.ObjectiveStats;
import com.coresolution.pe.entity.OrgContext;
import com.coresolution.pe.entity.QuestionMeta;
import com.coresolution.pe.entity.ReportAggregate;
import com.coresolution.pe.entity.ReportVM;
import com.coresolution.pe.mapper.EvalResultMapper;
import com.coresolution.pe.mapper.EvalSummaryMapper;
import com.coresolution.pe.mapper.OrgProfileMapper;
import com.coresolution.pe.service.EvalReportService.RowAgg;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EvalReportService {
    private final EvalResultMapper mapper;
    private final EvalSummaryMapper sumMapper; // ⬅️ 추가 (위 인터페이스)
    private final OrgProfileMapper orgMapper;

    private static final Logger log = LoggerFactory.getLogger(EvalReportService.class);

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

    // ✅ 경혁팀 코드(프로젝트 실제 값에 맞게 유지/수정)
    private static final String KY_TEAM_CODE = "GH_TEAM";

    // ✅ "부서장" 판정용 role 값(프로젝트 실제 값에 맞게 수정 필요 가능)
    private static final String ROLE_LEADER = "sub_head";

    // ----------------------------
    // ✅ 추가: 사용자 컨텍스트(부서/기관/팀) 조회용
    // ----------------------------
    private static final class UserCtx {
        final String deptCode; // 부서 코드
        final String orgName; // 기관명(c_name 등)
        final String teamCode; // 팀 코드

        UserCtx(String deptCode, String orgName, String teamCode) {
            this.deptCode = deptCode;
            this.orgName = orgName;
            this.teamCode = teamCode;
        }

        boolean isKyTeam() {
            return teamCode != null && teamCode.equalsIgnoreCase(KY_TEAM_CODE);
        }
    }

    private UserCtx resolveUserCtx(int evalYear, String userId, @Nullable String fallbackDeptCodeOrName) {
        try {
            Map<String, Object> row = mapper.selectUserContext(evalYear, userId);
            if (row != null) {
                String dept = asStr(row.get("deptCode"));
                String org = asStr(row.get("orgName"));
                String team = asStr(row.get("teamCode"));
                return new UserCtx(dept, org, team);
            }
        } catch (Exception e) {
            log.warn("selectUserContext failed year={} userId={}", evalYear, userId, e);
        }

        // fallback (기존 subCode 인자에 의존하던 흐름 보호)
        // fallbackDeptCodeOrName가 부서코드인지 기관명인지 애매할 수 있으므로,
        // 여기서는 deptCode에만 넣고 orgName은 비움.
        return new UserCtx(fallbackDeptCodeOrName, null, null);
    }

    private static String asStr(Object o) {
        if (o == null)
            return null;
        String s = String.valueOf(o);
        return (s.isBlank() ? null : s);
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

    public ReportVM buildOne(int evalYear, String targetId, String dataEv, String relationPath, boolean withAI) {
        System.out.printf("[buildOne] year=%d, targetId=%s, ev=%s%n", evalYear, targetId, dataEv);
        // --- 기본 데이터 로딩
        final var subs = mapper.selectReceivedAllByOneEv(evalYear, targetId, dataEv);
        System.out.printf("[buildOne] subs.size=%d (예: 223108이면 9건 나와야 함)%n",
                subs != null ? subs.size() : -1);
        final String typeCode = typeByEv(dataEv);
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

        final Integer essayIdx = mapper.selectEssayIdx(evalYear, typeCode);
        final String essayKey = (essayIdx == null) ? null : "t" + essayIdx;

        for (var s : subs) {
            submissionCount++;
            Map<String, Object> root;
            try {
                root = om.readValue(s.getAnswersJson(), Map.class);
            } catch (Exception e) {
                continue;
            }

            // --- 에세이 텍스트 수집 ---
            Object essaysObj = root.get("essays");
            if (essaysObj instanceof Map<?, ?> map && essayKey != null) {
                Object v = map.get(essayKey);
                if (v instanceof String txt && !txt.isBlank()) {
                    essays.add(txt.trim());
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

                int idx = toIdx(k);
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

        // ⛔ withAI=false: 생성 금지, "같은 해시"의 캐시만 노출. 다르면 업데이트 안내
        if (!withAI) {
            String summaryText = "요약을 생성하려면 [코어 AI 요약] 버튼을 눌러주세요.";
            var saved = sumMapper.selectOne(evalYear, targetId, dataEv, "ESSAY");

            if (saved != null && "READY".equals(saved.get("status"))) {
                Object savedHash = saved.get("input_hash");
                if (inputHash.equals(savedHash)) {
                    summaryText = enforceLexicon((String) saved.get("summary"));
                } else {
                    summaryText = "내용이 변경되었습니다. 최신 요약을 보려면 [코어 AI 요약] 버튼을 눌러 갱신하세요.";
                }
            }

            return ReportVM.builder()
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
                    .build();
        }

        // ✅ withAI=true: 에세이 1개만 있어도 생성
        if (essayCount < 1) {
            String msg = "에세이 코멘트가 없어 요약을 생성할 수 없습니다.";
            return ReportVM.builder()
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
                    .build();
        }

        // 캐시 재사용 (같은 입력 해시)
        var saved = sumMapper.selectOne(evalYear, targetId, dataEv, "ESSAY");
        if (saved != null && inputHash.equals(saved.get("input_hash")) && "READY".equals(saved.get("status"))) {
            String summary = enforceLexicon((String) saved.get("summary"));
            return ReportVM.builder()
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
                    .build();
        }

        // 새로 생성 → upsert
        String summary;
        String status = "READY";
        String err = null;
        String modelUsed = (summarizer instanceof OpenAiCommentSummarizer) ? "gpt-4o-mini" : "local-heuristic";

        try {
            if (summarizer instanceof OpenAiCommentSummarizer oa) {
                // 🔹 평가구간 별 system/user 프롬프트 선택
                String sys = EssayPrompt.systemForEv(dataEv);
                String user = EssayPrompt.user(
                        evalYear,
                        relationPath,
                        dataEv,
                        essays,
                        respCount,
                        essayCount);
                // 적당한 maxTokens 값은 상황에 맞게 (예: 1500~3000)
                summary = oa.summarizeWithSystem(sys, user, locale, 2000);
            } else {
                // 기존 로컬 요약기(프롬프트 개념 없는 경우)는 그냥 코멘트 리스트만 전달
                summary = summarizer.summarize(essays, locale, 8000);
            }

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

        sumMapper.upsert(evalYear, targetId, dataEv, "ESSAY",
                inputHash, essayCount, respCount, locale, modelUsed, summary, status, err);

        return ReportVM.builder()
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
                    - 영역은 다음 5개로 고정입니다(순서 유지): ["섬김","배움","키움","나눔","목표관리"].
                    [어휘 규칙]
                    - '코호트'/'cohort'라는 단어는 절대 사용하지 말고, 항상 '동일 평가구간' 또는 '동일 평가구간 평균'을 쓰십시오.

                    [출력 형식(JSON만)]
                    - 반드시 하나의 json 객체(JSON object)만 출력해야 합니다.
                    {
                    "title": string,
                    "summary": string,        // 6~7문장
                    "tone": string,
                    "metrics": { "responses": number, "essays": 0, "score_overall": number },
                    "overall_comparison": { "my": number, "same_period": number },
                    "by_area": {
                        "섬김":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "배움":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "키움":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "나눔":  {"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]},
                        "목표관리":{"my":number,"same_period":number,"summary":string,"strengths":string[],"improvements":string[]}
                    },
                    "strengths": string[],
                    "improvements": string[],
                    "next_actions": string[]
                    }
                    """;
        }

        // ✅ 평가구간별로 역할만 다르게 설명하되,
        // "A/B/C/D구간" 같은 코드는 리포트 문장에 쓰지 말라고 명시
        public static String systemForEv(String ev) {
            ev = (ev == null) ? "" : ev.trim().toUpperCase();

            String prefix;
            switch (ev) {
                case "A" -> prefix = """
                        [진료팀장 > 진료부 평가 리포트]

                        - 이 리포트는 진료팀장이 진료부 구성원을 평가한 결과를 바탕으로 작성합니다.
                        - 진료팀장 관점에서 진료부 구성원의 전문성, 협업, 책임감, 환자 중심 태도를 해석하십시오.
                        - 리포트 본문에서는 'A구간'과 같은 코드 명칭은 사용하지 말고, 반드시 '진료팀장 > 진료부'와 같은 관계 표현만 사용하십시오.
                        출력은 반드시 하나의 **json object**로 작성해야 합니다.
                        JSON 스키마:
                        {
                        "title": string,
                        "summary": string,
                        "strengths": string[],
                        "improvements": string[],
                        "tone": string
                        }
                        """;
                case "B" -> prefix = """
                        [진료부 > 경혁팀 평가 리포트]

                        - 이 리포트는 진료부가 경혁팀을 평가한 결과를 바탕으로 작성합니다.
                        - 진료 현장의 요구와 경혁팀의 지원·협업·소통 수준을 중심으로 해석하십시오.
                        - 리포트 본문에서는 'B구간'과 같은 코드 명칭은 사용하지 말고, 반드시 '진료부 > 경혁팀'과 같은 관계 표현만 사용하십시오.
                        출력은 반드시 하나의 **json object**로 작성해야 합니다.
                        JSON 스키마:
                        {
                        "title": string,
                        "summary": string,
                        "strengths": string[],
                        "improvements": string[],
                        "tone": string
                        }
                        """;
                case "C" -> prefix = """
                        [경혁팀 > 진료부 평가 리포트]

                        - 이 리포트는 경혁팀이 진료부를 평가한 결과를 바탕으로 작성합니다.
                        - 병원 전략·프로세스 관점에서 진료부의 협력도, 변화 수용, 커뮤니케이션, 조직 기여도를 해석하십시오.
                        - 리포트 본문에서는 'C구간'과 같은 코드 명칭은 사용하지 말고, 반드시 '경혁팀 > 진료부'와 같은 관계 표현만 사용하십시오.
                        출력은 반드시 하나의 **json object**로 작성해야 합니다.
                        JSON 스키마:
                        {
                        "title": string,
                        "summary": string,
                        "strengths": string[],
                        "improvements": string[],
                        "tone": string
                        }
                        """;
                case "D" -> prefix = """
                        [경혁팀 > 경혁팀 평가 리포트]

                        - 이 리포트는 경혁팀 구성원들이 서로를 평가한 결과를 바탕으로 작성합니다.
                        - 경영혁신팀 내부의 협업, 실행력, 문제 해결, 소통, 조직문화 기여 측면에서 강점과 개선점을 해석하십시오.
                        - 리포트 본문에서는 'D구간'과 같은 코드 명칭은 사용하지 말고, 반드시 '경혁팀 > 경혁팀'과 같은 관계 표현만 사용하십시오.
                        출력은 반드시 하나의 **json object**로 작성해야 합니다.
                        JSON 스키마:
                        {
                        "title": string,
                        "summary": string,
                        "strengths": string[],
                        "improvements": string[],
                        "tone": string
                        }
                        """;
                case "E" -> prefix = """
                        [부서장 > 부서원 평가 리포트]

                        - 이 리포트는 부서장이 부서원을 평가한 결과를 바탕으로 작성합니다.
                        - 상급자 관점에서 업무 수행, 책임감, 조직 기여도, 성장 가능성을 중점적으로 해석하십시오.
                        - 리포트 본문에서는 'E구간'과 같은 코드 명칭은 사용하지 말고, 반드시 '부서장 > 부서원'과 같은 관계 표현만 사용하십시오.
                        출력은 반드시 하나의 **json object**로 작성해야 합니다.
                        JSON 스키마:
                        {
                        "title": string,
                        "summary": string,
                        "strengths": string[],
                        "improvements": string[],
                        "tone": string
                        }
                        """;
                case "F" -> prefix = """
                        [부서원 > 부서장 평가 리포트]

                        - 이 리포트는 부서원이 부서장을 평가한 결과를 바탕으로 작성합니다.
                        - 구성원 입장에서 느끼는 리더십, 소통, 의사결정, 공정성, 지원 수준을 중심으로 점수를 해석하십시오.
                        - 리포트 본문에서는 'F구간'과 같은 코드 명칭은 사용하지 말고, 반드시 '부서원 > 부서장'과 같은 관계 표현만 사용하십시오.
                        출력은 반드시 하나의 **json object**로 작성해야 합니다.
                        JSON 스키마:
                        {
                        "title": string,
                        "summary": string,
                        "strengths": string[],
                        "improvements": string[],
                        "tone": string
                        }
                        """;
                case "G" -> prefix = """
                        [부서원 > 부서원 평가 리포트]

                        - 이 리포트는 부서원들이 서로를 평가한 결과를 바탕으로 작성합니다.
                        - 협업, 팀워크, 동료 지원, 커뮤니케이션 관점에서 강점과 개선점을 정리하십시오.
                        - 리포트 본문에서는 'G구간'과 같은 코드 명칭은 사용하지 말고, 반드시 '부서원 > 부서원'과 같은 관계 표현만 사용하십시오.
                        출력은 반드시 하나의 **json object**로 작성해야 합니다.
                        JSON 스키마:
                        {
                        "title": string,
                        "summary": string,
                        "strengths": string[],
                        "improvements": string[],
                        "tone": string
                        }
                        """;
                default -> {
                    return system();
                }
            }

            return prefix + "\n\n" + system();
        }

        public static String userWithOrg(
                int year,
                String relationLabel, // 예: "경혁팀 > 경혁팀"
                String dataEv, // A/B/C/D/E/F/G (메타 용도)
                double myOverall100,
                double cohortOverall100,
                List<ScoreItem> items,
                CommentReportDTO.Org org) {

            StringBuilder sb = new StringBuilder();
            sb.append("연도: ").append(year).append("\n");
            sb.append("평가구간 코드: ").append(dataEv).append("\n");
            sb.append("평가구간 관계: ").append(relationLabel).append("\n");
            sb.append("※ 리포트 문장에서는 'A/B/C/D구간'과 같은 코드 명칭을 사용하지 말고, 반드시 '")
                    .append(relationLabel)
                    .append("'과 같은 관계 표현만 사용하십시오.\n\n");

            sb.append("내 종합점수(100): ").append(Math.round(myOverall100)).append("\n");
            sb.append("동일 평가구간 종합점수(100): ").append(Math.round(cohortOverall100)).append("\n");
            sb.append("영역= [섬김, 배움, 키움, 나눔, 목표관리]\n\n");

            sb.append("[영역별 점수]\n");
            for (ScoreItem it : items) {
                sb.append("• [").append(it.area()).append("] ").append(it.label())
                        .append(" | my=").append(Math.round(it.my100()))
                        .append(", same_period=").append(Math.round(it.cohort100()))
                        .append("\n");
            }

            if (org != null) {
                sb.append("\n[조직 정보]\n");
                sb.append("- 조직명: ").append(nz(org.getName())).append("\n");
                sb.append("- 미션: ").append(nz(org.getMission())).append("\n");
                sb.append("- 비전: ").append(nz(org.getVision())).append("\n");
            }

            sb.append("\n※ 출력은 반드시 json 형식의 하나의 객체(JSON object)로만 작성하십시오.\n");
            sb.append("※ summary의 첫 문장은 다음 형식으로 시작하십시오.\n");
            sb.append("  \"").append(year)
                    .append("년 ").append(relationLabel)
                    .append(" 평가에서 개인 종합점수 X점, 동일 평가구간 평균 Y점입니다.\" 형태로 작성하고, 여기서 X와 Y를 실제 수치로 대체하십시오.\n");
            sb.append("※ 중요: 응답 형식이 json_object 이므로, 반드시 하나의 json 객체만 출력해야 합니다.\n");
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
                        - json만 출력(추가 텍스트 금지).

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
        ReportVM vmE = buildOne(year, targetId, "E", relationPath + "·E", false);
        ReportVM vmG = buildOne(year, targetId, "G", relationPath + "·G", false);

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
        // 0) idx→area 매핑 (기존과 동일)
        var qbankAll = mapper.selectQuestionBankAll(evalYear);
        Map<Integer, String> idxToArea = new LinkedHashMap<>();
        for (var q : qbankAll)
            idxToArea.put(q.getIdx(), q.getAreaLabel());

        // 1) 내(대상자) 영역 평균/분포: rowList에서 산출 (기존 computeObjectiveStats의 1) 블록 그대로 재사용)
        Map<String, Double> myArea5 = new LinkedHashMap<>();
        Map<String, Long> myCnt = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> myAreaDist = new LinkedHashMap<>();
        Map<String, List<String>> areaNarr = buildAreaNarratives(rowList);
        Map<String, String> areaSummary = buildAreaSummaries(rowList, "TOTAL"); // 관점 문구 필요 없으면 "G"로 둬도 됨

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

        // 2) TOTAL의 “동일 평가구간 평균” = E + G 합산
        List<EvalSubmissionRow> evSubs = new ArrayList<>();
        evSubs.addAll(mapper.selectAllSubmissionsByEv(evalYear, "E"));
        evSubs.addAll(mapper.selectAllSubmissionsByEv(evalYear, "G"));
        Map<String, Double> evArea5 = avgByAreaFromSubs(evSubs, idxToArea);

        // 3) 전직원 평균(연도 전체): 기존대로
        var allSubs = mapper.selectAllSubmissionsByYear(evalYear);
        Map<String, Double> allArea5 = avgByAreaFromSubs(allSubs, idxToArea);

        // 4) 부서+구간 평균(TOTAL) = (E+G) AND subCode
        Map<String, Double> deptEvArea5 = new LinkedHashMap<>();
        if (subCode != null && !subCode.isBlank()) {
            List<EvalSubmissionRow> deptEvSubs = new ArrayList<>();
            deptEvSubs.addAll(mapper.selectAllSubmissionsByEvAndDept(evalYear, "E", subCode));
            deptEvSubs.addAll(mapper.selectAllSubmissionsByEvAndDept(evalYear, "G", subCode));
            deptEvArea5 = avgByAreaFromSubs(deptEvSubs, idxToArea);
        }

        // 5) 부서 평균(연도+부서): 기존대로
        Map<String, Double> deptAllArea5 = new LinkedHashMap<>();
        if (subCode != null && !subCode.isBlank()) {
            var deptAllSubs = mapper.selectAllSubmissionsByYearAndDept(evalYear, subCode);
            deptAllArea5 = avgByAreaFromSubs(deptAllSubs, idxToArea);
        }

        // 6) 100점 환산 + overall 산출 (기존과 동일)
        List<String> labels = new ArrayList<>(new LinkedHashSet<>(myArea5.keySet()));
        labels.sort(Comparator.comparingInt(EvalReportService::areaIdx).thenComparing(Comparator.naturalOrder()));

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

    public ReportAggregate buildAggregate(int year, String targetId, String ev, String relationPath, String subCode,
            boolean withAI) {

        ReportVM vm = buildOne(year, targetId, ev, relationPath, withAI);

        // ✅ targetId 기준으로 dept/org/team 조회 (이게 평균을 “고정”시키는 핵심)
        UserCtx ctx = resolveUserCtx(year, targetId, subCode);
        String deptCode = ctx.deptCode;
        String orgName = ctx.orgName;
        String teamCode = ctx.teamCode;

        // --- 조직 프로필 로딩: 기존에는 subCode를 병원명처럼 사용했는데,
        // ✅ 이제 orgName(기관명)을 우선 사용
        Map<String, Object> orgRow = null;
        if (orgName != null && !orgName.isBlank()) {
            orgRow = orgMapper.selectByName(orgName);
        }
        if (orgRow == null && subCode != null && !subCode.isBlank()) {
            // fallback (기존 데이터 흐름 유지)
            orgRow = orgMapper.selectByName(subCode);
        }
        if (orgRow == null) {
            orgRow = orgMapper.selectAnyOne();
        }

        CommentReportDTO.Org orgDto = null;
        if (orgRow != null) {
            orgDto = new CommentReportDTO.Org();
            orgDto.setName((String) orgRow.getOrDefault("c_name2", orgRow.getOrDefault("c_name", "")));
            orgDto.setMission((String) orgRow.getOrDefault("mission", ""));
            String visionJoined = joinVisionJson(orgRow.get("vision_json"), om);
            orgDto.setVision(visionJoined);
            orgDto.setCiWordmarkUrl(null);
            orgDto.setCiSymbolUrl(null);
        }

        // --- (에세이) JSON 파싱: 기존 유지
        CommentReportDTO report = null;
        try {
            String text = vm.getEssaySummary();
            if (text != null) {
                String json = normalizeJsonEnvelope(text);
                if (json != null && json.trim().startsWith("{")) {
                    report = om.readValue(json.trim(), CommentReportDTO.class);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse essaySummary json for ev={} targetId={}", ev, targetId, e);
        }

        // ✅ 객관식 통계: “동일 평가구간 평균”을 ev별로 기관/팀/부서로 고정
        ObjectiveStats stats0 = computeObjectiveStats(year, ev, vm.getRows(), deptCode, orgName, teamCode);

        double cohortOverall = (stats0.getDeptEvOverall100() != null && stats0.getDeptEvOverall100() > 0)
                ? stats0.getDeptEvOverall100()
                : Optional.ofNullable(stats0.getEvOverall100()).orElse(0.0);

        ObjectiveStats stats = stats0.toBuilder()
                .cohortOverall100(cohortOverall)
                .areaNarr(buildAreaNarratives(vm.getRows()))
                .build();

        // --- 점수기반 리포트(SCORE) 이하: 기존 유지
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
            List<ScoreItem> items = topKScoreItems(stats, 10);

            String sys = ScorePrompt.systemForEv(ev);
            String user = ScorePrompt.userWithOrg(
                    year,
                    relationPath,
                    ev,
                    stats.getMyOverall100(),
                    Optional.ofNullable(stats.getCohortOverall100()).orElse(0.0),
                    items,
                    null);

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
        }

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

        if (scoreReport != null && orgDto != null) {
            if (scoreReport.getOrg() == null)
                scoreReport.setOrg(orgDto);
            String newSummary = weaveOrgIntoSummary(
                    Optional.ofNullable(scoreReport.getSummary()).orElse(""),
                    orgDto,
                    stats);
            scoreReport.setSummary(newSummary);
        }

        ObjectiveInsights ins = deriveInsights(stats);

        // ✅ QuestionDist도 동일 스코프로 고정 (중요)
        List<QuestionDist> qdists = computeQuestionDists(year, ev, deptCode, orgName, teamCode);

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

        return new ReportAggregate(vm, report, stats, scoreReport);
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

    // ----------------------------
    // ✅ computeObjectiveStats: 동일 평가구간 평균(deptEv*)을 ev별로 “기관/팀/부서”로 고정
    // ----------------------------
    private ObjectiveStats computeObjectiveStats(
            int evalYear,
            String dataEv,
            List<RowAgg> rowList,
            @Nullable String deptCode,
            @Nullable String orgName,
            @Nullable String teamCode) {
        var qbankAll = mapper.selectQuestionBankAll(evalYear);
        Map<Integer, String> idxToArea = new LinkedHashMap<>();
        for (var q : qbankAll)
            idxToArea.put(q.getIdx(), q.getAreaLabel());

        // 1) 내 영역 평균/분포
        Map<String, Double> myArea5 = new LinkedHashMap<>();
        Map<String, Long> myCnt = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> myAreaDist = new LinkedHashMap<>();
        Map<String, List<String>> areaNarr = buildAreaNarratives(rowList);
        Map<String, String> areaSummary = buildAreaSummaries(rowList, dataEv);

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

        // 2) 구간 평균(전사) - 기존 유지
        var evSubs = mapper.selectAllSubmissionsByEv(evalYear, dataEv);
        Map<String, Double> evArea5 = avgByAreaFromSubs(evSubs, idxToArea);

        // 3) 전직원 평균(연도 전체) - 기존 유지
        var allSubs = mapper.selectAllSubmissionsByYear(evalYear);
        Map<String, Double> allArea5 = avgByAreaFromSubs(allSubs, idxToArea);

        // 4) ✅ “동일 평가구간 평균” (deptEv*) = ev별로 모집단을 고정
        List<EvalSubmissionRow> cohortSubs = List.of();

        if ("F".equalsIgnoreCase(dataEv)) {
            // ✅ 부서원 → 부서장 평균 = “기관 평균(부서장 target만)”
            if (orgName != null && !orgName.isBlank()) {
                cohortSubs = mapper.selectAllSubmissionsByEvAndOrgAndTargetRole(evalYear, "F", orgName, ROLE_LEADER);
            } else {
                // orgName 없으면 fallback: 전사 평균으로
                cohortSubs = mapper.selectAllSubmissionsByEv(evalYear, "F");
            }
        } else if ("B".equalsIgnoreCase(dataEv)
                && teamCode != null
                && teamCode.equalsIgnoreCase(KY_TEAM_CODE)) {

            // ✅ 진료부(A0%) → 경혁팀(GH_TEAM) 평균
            cohortSubs = mapper.selectMedicalToTeamCohort(evalYear, teamCode, orgName);

        } else if ("D".equalsIgnoreCase(dataEv) && teamCode != null && teamCode.equalsIgnoreCase(KY_TEAM_CODE)) {
            // ✅ 경혁팀 → 경혁팀 평균 = “경혁팀(team) 평균”
            cohortSubs = mapper.selectAllSubmissionsByEvAndTeam(evalYear, "D", teamCode);
        } else {
            // 기본: 부서 평균 (기존과 동일)
            if (deptCode != null && !deptCode.isBlank()) {
                cohortSubs = mapper.selectAllSubmissionsByEvAndDept(evalYear, dataEv, deptCode);
            } else {
                cohortSubs = mapper.selectAllSubmissionsByEv(evalYear, dataEv);
            }
        }

        Map<String, Double> deptEvArea5 = avgByAreaFromSubs(cohortSubs, idxToArea);

        // 5) 부서 평균(연도+부서) - 기존 유지
        Map<String, Double> deptAllArea5 = new LinkedHashMap<>();
        if (deptCode != null && !deptCode.isBlank()) {
            var deptAllSubs = mapper.selectAllSubmissionsByYearAndDept(evalYear, deptCode);
            deptAllArea5 = avgByAreaFromSubs(deptAllSubs, idxToArea);
        }

        // 6) 100점 환산 + overall
        List<String> labels = idxToArea.values().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted(Comparator.comparingInt(EvalReportService::areaIdx)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();

        Map<String, Double> myArea100 = to100Nullable(myArea5, labels);
        Map<String, Double> evArea100 = to100Nullable(evArea5, labels);
        Map<String, Double> allArea100 = to100Nullable(allArea5, labels);
        Map<String, Double> deptEvArea100 = to100Nullable(deptEvArea5, labels);
        Map<String, Double> deptAllArea100 = to100Nullable(deptAllArea5, labels);

        double myOverall100 = avgIgnoreNull(myArea100);
        double evOverall100 = avgIgnoreNull(evArea100);
        double allOverall100 = avgIgnoreNull(allArea100);
        double deptEvOverall100 = avgIgnoreNull(deptEvArea100);
        double deptAllOverall100 = avgIgnoreNull(deptAllArea100);

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

    private Map<String, Double> to100Nullable(Map<String, Double> area5, List<String> labels) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String a : labels) {
            Double v5 = area5.get(a);
            out.put(a, (v5 == null ? null : v5 * 20.0));
        }
        return out;
    }

    private double avgIgnoreNull(Map<String, Double> m) {
        double sum = 0.0;
        int n = 0;
        for (Double v : m.values()) {
            if (v != null) {
                sum += v;
                n++;
            }
        }
        return (n == 0) ? 0.0 : (sum / n);
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

    // ----------------------------
    // ✅ computeQuestionDists도 동일 스코프로 고정 (중요)
    // ----------------------------
    public List<QuestionDist> computeQuestionDists(
            int evalYear,
            String dataEv,
            @Nullable String deptCode,
            @Nullable String orgName,
            @Nullable String teamCode) {
        var metas = mapper.selectQuestionBankAll(evalYear);
        Map<Integer, QuestionMeta> idxToMeta = new LinkedHashMap<>();
        for (var m : metas)
            idxToMeta.put(m.getIdx(), m);

        final List<String> ORDER = List.of("매우우수", "우수", "보통", "미흡", "매우미흡");
        Map<Integer, Map<String, Integer>> qCounts = new LinkedHashMap<>();
        idxToMeta.forEach((idx, meta) -> {
            var zero = new LinkedHashMap<String, Integer>();
            ORDER.forEach(k -> zero.put(k, 0));
            qCounts.put(idx, zero);
        });

        // ✅ 동일 모집단 선택
        List<EvalSubmissionRow> subs;

        if ("F".equalsIgnoreCase(dataEv)) {
            if (orgName != null && !orgName.isBlank()) {
                subs = mapper.selectAllSubmissionsByEvAndOrgAndTargetRole(evalYear, "F", orgName, ROLE_LEADER);
            } else {
                subs = mapper.selectAllSubmissionsByEv(evalYear, "F");
            }
        } else if ("D".equalsIgnoreCase(dataEv) && teamCode != null && teamCode.equalsIgnoreCase(KY_TEAM_CODE)) {
            subs = mapper.selectAllSubmissionsByEvAndTeam(evalYear, "D", teamCode);
        } else if (deptCode != null && !deptCode.isBlank()) {
            subs = mapper.selectAllSubmissionsByEvAndDept(evalYear, dataEv, deptCode);
        } else {
            subs = mapper.selectAllSubmissionsByEv(evalYear, dataEv);
        }

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

        final int MIN_RESP = 0;
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
    private String areaHeadline(double posPctArea, double negPctArea, String areaName, String ev) {
        String subject; // 누가 평가했는가
        String target; // 누구의 무엇을

        switch (ev) {
            case "E" -> { // 부서장 → 부서원
                subject = "상급자는 ";
                target = "부서원의 ";
            }
            case "F" -> { // 부서원 → 부서장
                subject = "부서원들은 ";
                target = "부서장의 ";
            }
            case "G" -> { // 부서원 ↔ 부서원
                subject = "동료들은 ";
                target = "서로의 ";
            }
            case "C" -> { // 경혁팀 → 진료부
                subject = "경혁팀은 ";
                target = "진료부의 ";
            }
            case "D" -> { // 경혁팀 ↔ 경혁팀
                subject = "경혁팀 구성원들은 ";
                target = "팀 내부의 ";
            }
            default -> {
                // A/B/TOTAL 등: 기존 generic 문장 유지
                if (posPctArea >= 75)
                    return areaName + " 영역은 전반적으로 매우 우수한 평가를 받았습니다.";
                if (posPctArea >= 60)
                    return areaName + " 영역은 전반적으로 우수한 평가를 받았습니다.";
                if (negPctArea >= 30)
                    return areaName + " 영역은 전반적으로 개선이 필요합니다.";
                return areaName + " 영역은 전반적으로 안정적인 평가를 보였습니다.";
            }
        }

        String areaChunk = target + areaName + " 영역을 ";

        if (posPctArea >= 75) {
            return subject + areaChunk + "전반적으로 매우 우수하게 평가했습니다.";
        }
        if (posPctArea >= 60) {
            return subject + areaChunk + "전반적으로 우수하게 평가했습니다.";
        }
        if (negPctArea >= 30) {
            return subject + areaChunk + "전반적으로 개선이 필요하다고 보고 있습니다.";
        }
        return subject + areaChunk + "대체로 안정적인 수준이라고 평가하고 있습니다.";
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

    // ✅ ev(구간)에 따라 관점(상급자/동료/부서원 등)을 바꿔주는 버전
    private Map<String, String> buildAreaSummaries(List<RowAgg> rowList, String ev) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rowList == null || rowList.isEmpty())
            return out;

        // 1) 영역별로 묶기
        Map<String, List<RowAgg>> byArea = new LinkedHashMap<>();
        for (var r : rowList)
            byArea.computeIfAbsent(r.getArea(), k -> new ArrayList<>()).add(r);

        String evCode = ev == null ? "" : ev.trim().toUpperCase();

        for (var entry : byArea.entrySet()) {
            String area = entry.getKey();
            List<RowAgg> rows = entry.getValue();
            rows.sort(Comparator.comparing(RowAgg::getIdx));

            int muu = 0, us = 0, bt = 0, mh = 0, mmh = 0;

            class QPol {
                final int idx;
                final String label;
                final double pos, neg, pol;

                QPol(int idx, String label, double pos, double neg, double pol) {
                    this.idx = idx;
                    this.label = label;
                    this.pos = pos;
                    this.neg = neg;
                    this.pol = pol;
                }

                double pol() {
                    return pol;
                }

                String label() {
                    return label;
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
                    String aff = normalizeAsAffirmative(
                            Optional.ofNullable(r.getLabel()).orElse(""));
                    qpols.add(new QPol(r.getIdx(), aff, pos, neg, pos - neg));
                }
            }

            int total = muu + us + bt + mh + mmh;
            if (total <= 0) {
                out.put(area, area + " 영역은 응답이 충분하지 않아 요약을 제공하지 않습니다.");
                continue;
            }

            double posPctArea = (muu + us) * 100.0 / total;
            double negPctArea = (mh + mmh) * 100.0 / total;

            // 상위 긍정 문항 1~2개 → tail 용
            qpols.sort(Comparator.comparingDouble(QPol::pol).reversed());
            List<String> topPositiveLabels = qpols.stream()
                    .limit(2)
                    .map(q -> trimTopic(q.label()))
                    .filter(s -> s != null && !s.isBlank())
                    .toList();

            // ★ ev별로 관점이 들어간 head 문장 생성
            String head = areaHeadline(posPctArea, negPctArea, area, evCode);
            String tail = buildEmphasisFromLabels(topPositiveLabels);

            String finalLine;
            if (tail != null) {
                String headNoPeriod = head.replaceAll("[\\.!…]+$", "");
                finalLine = headNoPeriod + ". " + tail;
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
            Map<String, Double> avg100ByAxis,
            boolean withAI) {

        // 0) 다면평가(E/G) 코멘트 가져오기
        // evaluation_submissions.answers_json 안의 essays 를 buildOne()이 이미 모아줌
        ReportVM vmE = buildOne(year, userId, "E", relationLabel + "·E", false);
        ReportVM vmG = buildOne(year, userId, "G", relationLabel + "·G", false);

        List<String> comments = new ArrayList<>();
        if (vmE.getEssayList() != null)
            comments.addAll(vmE.getEssayList());
        if (vmG.getEssayList() != null)
            comments.addAll(vmG.getEssayList());

        int respCount = Optional.ofNullable(vmE.getOverallResp()).orElse(0)
                + Optional.ofNullable(vmG.getOverallResp()).orElse(0);
        int essayCount = comments.size();

        // 0-1) 시그니처에 코멘트 해시까지 포함 (코멘트가 바뀌면 다시 생성되도록)
        String commentsHash = sha256Hex(String.join("\n---\n", comments));

        // 0-2) 누락된 숨은 키 보정 (0점 문제 예방)
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

        // 2) 시그니처(캐시 키) — 코멘트 해시까지 포함
        String signature = new StringBuilder()
                .append("ky_total=").append(Math.round(kpi100.getOrDefault("_TOTAL_my", total100)))
                .append("|same=").append(Math.round(kpi100.getOrDefault("_TOTAL_same", 0.0)))
                .append("|kpi=")
                .append(Math.round(kpi100.getOrDefault("I 재무성과(40)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("II 고객서비스(15)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("III 프로세스혁신(15)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("IV 학습성장(10)→100", 0.0))).append(",")
                .append(Math.round(kpi100.getOrDefault("V 다면평가(20)→100", 0.0)))
                .append("|comments=").append(commentsHash)
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

            // ✅ 코멘트와 축별 평균, 메트릭까지 모두 넣어서 프롬프트 생성
            String usr = KyTotalPrompt.user(
                    year,
                    relationLabel,
                    kpi100,
                    avg100ByAxis, // 축별 팀 평균
                    comments, // evaluation_submissions.answers_json 의 essays
                    respCount,
                    essayCount,
                    payload);

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
                    json = """
                            {"title":"종합평가(KPI+다면평가) AI 리포트","summary":"생성 실패","strengths":[],"improvements":[],"tone":"neutral","metrics":{"responses":0,"essays":0,"score_overall":0},"overall_comparison":{"my":0,"same_period":0},"kpi":{},"by_area":{},"next_actions":[]}
                            """;
                }
            } catch (Exception ex) {
                status = "ERROR";
                err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                json = """
                        {"title":"종합평가(KPI+다면평가) AI 리포트","summary":"생성 오류","strengths":[],"improvements":[],"tone":"neutral","metrics":{"responses":0,"essays":0,"score_overall":0},"overall_comparison":{"my":0,"same_period":0},"kpi":{},"by_area":{},"next_actions":[]}
                        """;
            }
            json = enforceLexicon(json);

            sumMapper.upsert(year, userId, "TOTAL", KIND,
                    sigHash, essayCount, respCount,
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
                    당신은 병원 인사평가 시스템의 '경혁팀 종합(TOTAL)' AI 평가 분석 엔진입니다.
                    아래 규칙을 절대적으로 우선 적용하여 json 리포트를 생성하십시오.

                    ────────────────────────
                    [가장 중요한 구조 규칙 – 반드시 지킬 것]
                    ────────────────────────
                    1. 출력은 오직 JSON 한 개만 허용됩니다. JSON 이외의 텍스트, 설명, 마크다운, 주석을 절대 출력하지 마십시오.

                    2. JSON의 배열 필드는 아래 조건을 **반드시** 만족해야 합니다.
                    - "strengths": 항상 **정확히 3개의 문자열 요소**를 가져야 합니다.
                        예) "strengths": ["문장1", "문장2", "문장3"]
                    - "improvements": 항상 **정확히 3개의 문자열 요소**를 가져야 합니다.
                        예) "improvements": ["문장1", "문장2", "문장3"]
                    - "next_actions": **최소 3개의 문자열 요소**를 가져야 합니다.
                        예) "next_actions": ["문장1", "문장2", "문장3"] 또는 그 이상
                    - 각 영역별 by_area.*.strengths: **최소 1개 이상**
                    - 각 영역별 by_area.*.improvements: **최소 1개 이상**

                    3. 데이터(코멘트, 점수)가 부족하더라도,
                    - strengths, improvements, next_actions, by_area.*.strengths, by_area.*.improvements 는
                        절대로 빈 배열([])이 되어서는 안 됩니다.
                    - 데이터가 부족하면, 점수와 역할 정보를 기반으로 **합리적으로 추론**하여 문장을 채우십시오.
                    - 특히, 최상위 "strengths"와 "improvements"는 무조건 3개를 채우십시오.

                    4. JSON을 출력하기 **직전**에, 다음을 스스로 점검하십시오.
                    - strengths 배열 길이 === 3 ?
                    - improvements 배열 길이 === 3 ?
                    - next_actions 배열 길이 ≥ 3 ?
                    - by_area의 5개 축 각각에 대해 strengths.length ≥ 1, improvements.length ≥ 1 ?
                    위 조건 중 하나라도 만족하지 못하면, 문장을 추가/보완하여 조건을 충족시킨 후에만 JSON을 출력하십시오.

                    ────────────────────────
                    [입력 데이터]
                    ────────────────────────
                    입력에는 다음 정보가 포함됩니다.
                    - (1) 5개 축 점수(개인·동일 평가구간 평균, 모두 100점 환산)
                    - (2) 원점수(합 100점 기준)
                    - (3) 코멘트(에세이, 여러 평가자의 정성 코멘트가 합쳐진 텍스트)
                    - (4) 숨은 합계 키

                    시스템은 위 데이터를 JSON 형태 또는 사람이 읽을 수 있는 텍스트로 제공하며,
                    당신은 이를 분석해 인사평가 '경혁팀 종합(TOTAL)' 요약 리포트를 생성해야 합니다.

                    ────────────────────────
                    [역할 및 기본 규칙]
                    ────────────────────────
                    1. 보고 대상은 항상 '개인'입니다.

                    2. '코호트'라는 용어를 절대 사용하지 말고,
                    항상 '동일 평가구간'이라고만 표기합니다.

                    3. 개인정보/실명 처리:
                    - 실명, 사번, 병원명, 세부 팀명 등 식별 가능한 정보는
                        요약 시 직무/역할 수준(예: "부서장", "동료", "의사", "경영혁신팀 구성원")으로만 표현합니다.
                    - 비난, 비속어, 과도한 감정 표현은 직무 관찰에 기반한 중립적 표현으로 재서술합니다.

                    4. KPI 5축 (모두 100점 스케일):
                    - I 재무성과(40)→100
                    - II 고객서비스(15)→100
                    - III 프로세스혁신(15)→100
                    - IV 학습성장(10)→100
                    - V 다면평가(20)→100

                    ────────────────────────
                    [summary 작성 규칙 – 내용 길이]
                    ────────────────────────
                    summary 길이 규칙(최소 6문장, 500~800자, 아래 4가지 구성요소 포함)은
                    이 프롬프트에서 두 번째로 중요한 규칙입니다.
                    (가장 중요한 규칙은 상단의 배열 길이 규칙입니다.)

                    1. summary는 반드시 한국어로 작성합니다.

                    2. 첫 문장은 반드시 다음 형식으로 시작합니다.
                    "개인의 종합점수 X점, 동일 평가구간 평균 Y점입니다."
                    - X에는 개인의 총점(100점 스케일)을,
                    - Y에는 동일 평가구간 평균 총점(100점 스케일)을 넣습니다.

                    3. summary 전체는 한 단락으로 작성하며,
                    최소 6문장 이상, 대략 500~800자 분량으로 작성합니다.

                    4. summary에는 다음 4가지를 모두 포함해야 합니다.

                    (1) 1문장: 종합점수와 동일 평가구간 평균 비교
                        - 예: "개인의 종합점수 X점, 동일 평가구간 평균 Y점입니다."

                    (2) 2~3문장: 5개 축 중 상/하위 축에 대한 구체 설명
                        - 가장 높은 축과 두 번째로 높은 축의 점수와 강점(행동·태도)을 설명합니다.
                        - 가장 낮은 축(또는 하위 축)의 점수와, 어떤 부분이 부족하거나 개선 여지가 있는지 설명합니다.
                        - 단순히 "낮다/높다"로만 끝내지 말고,
                            "어떤 업무 방식·행동 패턴 때문에 그렇게 평가되었는지"를 서술합니다.

                    (3) 2문장 이상: 정성 코멘트에서 반복적으로 나타나는 강점 패턴
                        - 동료·상급자·의사 등 평가자들이 공통적으로 언급하는 협업 태도, 책임감, 소통 방식,
                            전문성, 현장 지원 태도 등을 정리합니다.
                        - 가능하면 구체적인 상황이나 예시를 간단히 묘사합니다.
                            (예: "업무 마감이 촉박한 상황에서도 ○○하는 경향이 있다" 등)

                    (4) 1~2문장: 향후 성장 방향 또는 리스크/주의점
                        - 현재 강점을 어떻게 유지·확장할지,
                        - 낮은 축을 보완하지 않을 경우 어떤 리스크가 있을지,
                            다음 평가에서 중점적으로 관리해야 할 포인트를 제시합니다.

                    5. 코멘트에 부서원/부서장/동료/의사/경영혁신팀 등 역할 정보가 보일 경우,
                    최소 1문장 이상은
                    "어떤 관점에서 그런 평가가 나왔는지"를 설명하는 데 사용합니다.
                    (예: "동료 관점에서는 협업 태도와 소통이 긍정적으로 평가되었으며, 상급자 관점에서는 ○○이 강조되었습니다." 등)

                    6. 작성된 summary가 6문장 미만이라고 판단되면,
                    내용을 보완하여 6문장 이상이 될 때까지 문장을 추가하여 확장하십시오.

                    ────────────────────────
                    [by_area 및 목록 필드 작성 규칙]
                    ────────────────────────
                    1. by_area.*.summary 규칙:
                    - 각 영역(I~V)의 summary는 최소 2문장 이상 작성합니다.
                    - 첫 문장에는 반드시 해당 축의 점수(개인 vs 동일 평가구간 평균)를 언급합니다.
                        (예: "I 재무성과는 개인 90점, 동일 평가구간 평균 85점으로, 평균보다 높은 수준입니다.")
                    - 두 번째 문장부터는 정성 코멘트 패턴과 연결해
                        "어떤 행동/태도가 그렇게 평가된 이유인지"를 설명합니다.

                    2. 최상위 strengths / improvements / next_actions 개수 규칙:
                    - strengths 배열에는 반드시 **핵심역량 3가지를 정확히 3개** 작성합니다.
                        - 내용이 다소 비슷하더라도, 서로 다른 표현과 관점을 사용하여 3개를 채웁니다.
                        - 정성 코멘트가 부족하더라도, 점수(특히 높은 축)를 근거로 합리적으로 추론하여 3개를 작성합니다.
                        - 절대로 strengths 배열을 빈 배열로 두거나 1개, 2개만 작성해서는 안 됩니다.
                    - improvements 배열에는 반드시 **발전과제 3가지를 정확히 3개** 작성합니다.
                        - 각 과제는 서로 다른 측면을 다루되, 완전히 동떨어지지 않도록 합니다.
                        - 정성 코멘트가 부족하더라도, 낮은 축/보완 필요 영역을 근거로 합리적으로 추론하여 3개를 작성합니다.
                        - 절대로 improvements 배열을 빈 배열로 두거나 1개, 2개만 작성해서는 안 됩니다.
                    - next_actions 배열에는 **최소 3개 이상**의 구체적인 실행 계획을 작성합니다.
                        - 가능하다면 3개로 맞추되, 내용상 필요하면 4개 이상이 될 수 있습니다.

                    3. by_area.*.strengths / by_area.*.improvements 규칙:
                    - 각 영역(I~V)의 strengths 배열은 **최소 1개 이상** 작성합니다.
                        - 해당 축에서 상대적으로 긍정적으로 평가된 행동·태도·역량을 짧은 문장으로 정리합니다.
                        - 정성 코멘트가 거의 없더라도, 점수 수준(높음/보통)을 근거로 추론하여 1개 이상 작성합니다.
                    - 각 영역(I~V)의 improvements 배열도 **최소 1개 이상** 작성합니다.
                        - 해당 축에서 보완하면 좋을 구체적인 포인트를 제시합니다.
                        - 점수가 높더라도, "강점을 유지·확장하기 위한 발전 방향" 형태로 1개 이상 작성할 수 있습니다.
                    - 절대로 by_area.*.strengths 또는 by_area.*.improvements를 빈 배열로 두지 마십시오.

                    ────────────────────────
                    [개선과제/실행계획 작성 규칙]
                    ────────────────────────
                    1. improvements, by_area.*.improvements, next_actions의 각 항목은
                    구체적인 행동 + 기간 + 측정 기준을 포함해야 합니다.

                    예시 템플릿:
                    - "향후 6개월 동안 월 1회 ○○를 수행하고, 그 결과를 분기별로 점검한다."
                    - "3개월 내에 △△ 교육을 이수하고, 이후 평가면담에서 적용 사례를 2건 이상 공유한다."
                    - "다음 분기까지 주간 회의에서 개선 아이디어를 최소 1건씩 제안하고,
                        채택된 과제 실행 여부를 팀 내에서 공유한다."

                    2. 가능한 경우, 각 항목은 평가 점수 및 정성 코멘트에서 드러난 실제 이슈와 직접 연결되도록 작성합니다.

                    ────────────────────────
                    [tone 필드 작성 규칙]
                    ────────────────────────
                    1. tone 필드에는 전체 보고서의 분위기를 한 단어나 짧은 구로 요약합니다.
                    - 예: "균형 잡힌 개선 중심 피드백", "강점은 높지만 과제도 분명한 피드백" 등.
                    2. 점수대와 코멘트 내용을 보고,
                    격려 중심 / 개선 중심 / 균형 중심 등 적절한 톤을 선택합니다.
                    3. 과도한 감정 표현은 피하고, 전문적인 HR 리포트 톤을 유지합니다.

                    ────────────────────────
                    [출력 형식 (json only)]
                    ────────────────────────
                    1. title은 반드시 "종합평가(KPI+다면평가) AI 리포트""로 고정합니다.

                    2. 출력은 오직 json 한 개만 허용됩니다.
                    - json 이외의 텍스트, 마크다운, 설명, 주석, 사족을 절대 출력하지 마십시오.

                    3. json 스키마는 다음과 같습니다.

                    {
                    "title": "종합평가(KPI+다면평가) AI 리포트",
                    "summary": string,
                    "strengths": string[],          // 항상 길이 3
                    "improvements": string[],       // 항상 길이 3
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
                    "next_actions": string[]        // 길이 최소 3
                    }

                    지금부터 위 규칙을 모두 지키면서,
                    입력된 데이터를 기반으로 유효한 json 한 개만 출력하십시오.

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
                    4) JSON만 출력하며 title은 "종합평가(KPI+다면평가) AI 리포트".
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

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    /**
     * TOTAL(비-경혁팀) 요약을 항상 JSON object로 강제 래핑.
     * - 모델이 문단 텍스트를 주더라도 이 메서드 지나면 무조건 JSON으로 바뀜.
     */
    private String ensureTotalJson(String raw,
            int year,
            String relationLabel,
            ObjectiveStats stats,
            Map<String, Double> kpi100,
            int respCount,
            int essayCount) {
        String t = normalizeJsonEnvelope(raw);
        if (t != null && t.trim().startsWith("{")) {
            // 이미 JSON object 형식이면 그대로 사용
            return t.trim();
        }

        String summaryText = (t == null) ? "" : t.trim();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", "[종합평가] AI 리포트");
        root.put("summary", summaryText);
        root.put("tone", "차분하고 건설적");

        // metrics
        Map<String, Object> metrics = new LinkedHashMap<>();
        double myOverall = Math.round(
                Optional.ofNullable(stats)
                        .map(ObjectiveStats::getMyOverall100)
                        .orElse(0.0));
        double sameOverall = Math.round(
                Optional.ofNullable(stats)
                        .map(ObjectiveStats::getCohortOverall100)
                        .orElse(0.0));
        metrics.put("responses", respCount);
        metrics.put("essays", essayCount);
        metrics.put("score_overall", myOverall);
        root.put("metrics", metrics);

        // 전체 비교
        Map<String, Object> overallComp = new LinkedHashMap<>();
        overallComp.put("my", myOverall);
        overallComp.put("same_period", sameOverall);
        root.put("overall_comparison", overallComp);

        // KPI 그대로 실어주기 (없으면 빈 맵)
        root.put("kpi", kpi100 != null ? new LinkedHashMap<>(kpi100) : new LinkedHashMap<>());

        // by_area: 영역별 간단 요약 구성
        Map<String, Object> byArea = new LinkedHashMap<>();
        if (stats != null && stats.getLabels() != null) {
            for (String area : stats.getLabels()) {
                Map<String, Object> a = new LinkedHashMap<>();
                double myA = Math.round(stats.getMyArea100().getOrDefault(area, 0.0));
                double sameA = Math.round(
                        Optional.ofNullable(stats.getDeptEvArea100())
                                .map(m -> m.getOrDefault(area, 0.0))
                                .filter(v -> v > 0)
                                .orElse(stats.getEvArea100().getOrDefault(area, 0.0)));

                a.put("my", myA);
                a.put("same_period", sameA);

                String areaSummary = null;
                if (stats.getAreaSummary() != null) {
                    areaSummary = stats.getAreaSummary().get(area);
                }
                if (areaSummary == null || areaSummary.isBlank()) {
                    areaSummary = area + " 영역은 전반적으로 안정적인 평가를 보였습니다.";
                }
                a.put("summary", areaSummary);
                a.put("strengths", List.of());
                a.put("improvements", List.of());
                byArea.put(area, a);
            }
        }
        root.put("by_area", byArea);

        // 상위 strengths / improvements / next_actions 가 비어있으면 최소한 빈 배열
        root.put("strengths", List.of());
        root.put("improvements", List.of());
        root.put("next_actions", List.of());

        try {
            return om.writeValueAsString(root);
        } catch (Exception e) {
            // 최악의 경우에도 최소한 JSON object는 보장
            return "{\"title\":\"[종합평가] AI 리포트\",\"summary\":\"" +
                    escapeJson(summaryText) + "\"}";
        }
    }

    public final class EssayPrompt {

        private EssayPrompt() {
        }

        // 공통: JSON 스키마 + 규칙
        private static String baseSchema() {
            return """
                    ────────────────────────
                    [출력 형식 – JSON만 허용]
                    ────────────────────────
                    1. 출력은 반드시 **하나의 JSON 객체**만 허용됩니다.
                    - JSON 앞뒤에 다른 설명, 마크다운, 자연어 텍스트를 절대 붙이지 마십시오.
                    2. JSON 스키마는 다음과 같습니다.

                    {
                    "title": string,          // 예: "[직원 평가] AI 리포트"
                    "summary": string,        // 5~7문장 정도의 한국어 단락
                    "strengths": string[],    // 항상 정확히 3개
                    "improvements": string[], // 항상 정확히 3개
                    "tone": string,           // 리포트의 분위기를 한 줄로 요약
                    "metrics": {
                        "responses": number,    // 응답자 수(모르면 0)
                        "essays": number        // 코멘트 개수(모르면 0)
                    }
                    }

                    3. "strengths" 와 "improvements" 배열 규칙:
                    - 항상 **정확히 3개의 문자열 요소**를 포함해야 합니다.
                    - 데이터가 부족하더라도, 점수와 코멘트 내용을 근거로
                        합리적으로 추론하여 3개를 채우십시오.
                    - 절대로 0개, 1개, 2개만 넣지 말고, 꼭 3개를 채워야 합니다.

                    4. summary 규칙:
                    - 한국어로 5~7문장 정도의 하나의 단락으로 작성합니다.
                    - 반복적으로 등장하는 강점/개선 요구를 중심으로 서술합니다.
                    - 비속어·감정적인 표현은 전문적인 HR 용어로 완화합니다.

                    5. JSON을 출력하기 직전에 아래를 스스로 점검하십시오.
                    - strengths.length === 3 ?
                    - improvements.length === 3 ?
                    - summary는 비어 있지 않은가 ?
                    이 조건을 만족하지 못하면 내용을 보완한 뒤에만 JSON을 출력하십시오.
                    """;
        }

        /** 평가구간별 system 프롬프트 선택 */
        public static String systemForEv(String ev) {
            ev = ev == null ? "" : ev.trim().toUpperCase();

            String prefix;
            switch (ev) {
                case "E" -> prefix = """
                        당신은 병원 인사평가 시스템에서 '상급자→부서원(E구간)' 코멘트만을 요약하는 HR 리포트 도우미입니다.
                        - 대상: 상급자가 부서원을 평가한 코멘트
                        - 초점: 업무 성과, 리더십 잠재력, 조직 기여도, 태도 등을 상급자의 시각에서 정리
                        - 어조: 차분하고 전문적인 피드백, 격려 + 명확한 개선 방향 제시
                        - 개인정보/실명은 직무 수준으로 일반화 (예: "부서장", "동료" 등)
                        """;
                case "G" -> prefix = """
                        당신은 병원 인사평가 시스템에서 '동료→부서원(G구간)' 코멘트만을 요약하는 HR 리포트 도우미입니다.
                        - 대상: 동료가 서로를 평가한 코멘트
                        - 초점: 협업 태도, 커뮤니케이션, 팀워크, 현장 지원, 배려 등
                        - 어조: 동료 입장을 반영하되, 감정 표현은 중립적인 HR 용어로 재정리
                        - 비난/비속어는 직무 관찰 기반의 표현으로 바꿔주세요.
                        """;
                case "C" -> prefix = """
                        당신은 '원장/경영진→직원(C구간)' 코멘트를 요약하는 HR 리포트 도우미입니다.
                        - 대상: 경영진 시각에서 본 전략 기여, 조직 문화 기여
                        - 초점: 병원 미션/비전과의 정렬, 중장기 관점의 강점/리스크
                        - 어조: 전략적·거시적인 관점에서 정리
                        """;
                case "D" -> prefix = """
                        당신은 '직원→경영진(D구간)' 코멘트를 요약하는 HR 리포트 도우미입니다.
                        - 대상: 구성원이 경영진/리더십을 어떻게 인식하는지에 대한 코멘트
                        - 초점: 소통, 의사결정 투명성, 현장 이해도, 지원 체계 등
                        - 어조: 사실 기반 관찰 + 개선 제안 위주, 감정적인 표현은 중립적으로 변환
                        """;
                default -> prefix = """
                        당신은 병원 인사평가 시스템에서 정성 코멘트를 요약하는 HR 리포트 도우미입니다.
                        - 평가구간 특성을 고려해, 반복되는 강점/개선 요구를 분리해서 정리합니다.
                        - 비난/비속어는 중립적인 HR 언어로 재작성합니다.
                        - 개인정보/실명은 직무 수준(부서장, 동료, 의료진 등)으로 일반화합니다.
                        """;
            }

            // 평가구간 설명 + 공통 JSON 스키마 규칙을 붙여서 반환
            return prefix + "\n\n" + baseSchema();

        }

        /** ev별 user 프롬프트: 연도/관계/코멘트들을 한 번에 전달 */
        public static String user(
                int year,
                String relationPath, // "부서장 평가", "부서원 평가" 등 화면에 쓰는 라벨
                String dataEv,
                List<String> comments,
                int respCount,
                int essayCount) {
            StringBuilder sb = new StringBuilder();
            sb.append("연도: ").append(year).append("\n");
            sb.append("평가구간 코드: ").append(dataEv).append("\n");
            sb.append("화면 관계 라벨: ").append(relationPath).append("\n");
            sb.append("응답자 수(추정): ").append(respCount).append("\n");
            sb.append("코멘트 개수: ").append(essayCount).append("\n\n");

            sb.append("[코멘트 원문 목록] (--- 로 구분)\n");
            if (comments == null || comments.isEmpty()) {
                sb.append("(코멘트가 없습니다)\n");
            } else {
                for (String c : comments) {
                    if (c == null || c.isBlank())
                        continue;
                    sb.append("---\n").append(c.trim()).append("\n");
                }
            }

            sb.append("\n요약 규칙:\n");
            sb.append("1) 반복적으로 등장하는 강점/우려사항을 3~5문장으로 한국어로 정리하세요.\n");
            sb.append("2) 구체적인 행동·태도·상황을 중심으로 설명하고, 단순 형용사 나열은 피하세요.\n");
            sb.append("3) 인신공격성 표현은 직무 관련 피드백으로 재구성하세요.\n");
            sb.append("""

                    위 코멘트를 분석하여 JSON 리포트를 생성하십시오.
                    - 출력은 반드시 하나의 JSON 객체만 작성합니다.
                    - JSON 외의 자연어 설명, 마크다운, 주석 등은 절대 포함하지 마십시오.
                    """);
            return sb.toString();
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
