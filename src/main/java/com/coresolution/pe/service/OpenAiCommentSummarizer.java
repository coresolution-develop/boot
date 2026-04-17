package com.coresolution.pe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 기반 코멘트 요약기 (견고 버전)
 * - OkHttp 타임아웃 확장 (callTimeout=0 무제한, read/write 110s)
 * - 429/5xx 및 SocketTimeout에서 지수 백오프 재시도
 * - JSON 모드(response_format=json_object) 강제
 * - max_tokens 지정으로 응답 과대 방지
 */
public class OpenAiCommentSummarizer implements CommentSummarizer {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final OkHttpClient client;
    private final ObjectMapper om = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final int maxAttempts;
    private final int defaultMaxTokens;

    /**
     * 기본 생성자: gpt-4o-mini, 시도 3회, max_tokens=900
     */
    public OpenAiCommentSummarizer(String apiKey, String model) {
        this(apiKey, (model == null || model.isBlank()) ? "gpt-4o-mini" : model,
                3, 900,
                new OkHttpClient.Builder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .readTimeout(Duration.ofSeconds(110))
                        .writeTimeout(Duration.ofSeconds(110))
                        .callTimeout(Duration.ZERO) // 전체 데드라인 제거
                        .retryOnConnectionFailure(true)
                        .build());
    }

    /**
     * 고급 생성자(세밀 제어)
     */
    public OpenAiCommentSummarizer(String apiKey,
            String model,
            int maxAttempts,
            int defaultMaxTokens,
            OkHttpClient client) {
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "gpt-4o-mini" : model;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.defaultMaxTokens = Math.max(200, defaultMaxTokens);
        this.client = client;
    }

    // ==========================
    // Public API
    // ==========================

    /**
     * 기존 CommentSummarizer 인터페이스 구현.
     * comments → 하나의 system/user 프롬프트로 JSON 리포트 생성.
     */
    @Override
    public String summarize(List<String> comments, String localeTag, int maxChars) {
        if (comments == null || comments.isEmpty())
            return "요약할 코멘트가 없습니다.";

        String joined = String.join("\n---\n", comments);
        if (joined.length() > maxChars) {
            joined = joined.substring(0, Math.max(0, maxChars)) + "\n...(중략)";
        }

        final int essaysCount = comments.size();
        final String locale = (localeTag == null || localeTag.isBlank()) ? "ko" : localeTag;

        String system = """
                당신은 인사평가 코멘트 요약 리포트를 작성하는 도우미입니다.
                요구사항:
                - 개인정보(이메일/전화/주민번호 등)와 평가자 실명은 제거/일반화.
                - 비난/비속어는 중립적으로 재서술.
                - 반드시 아래 JSON 스키마에 '정확히' 맞춰서만 출력(추가 텍스트 금지).
                - title은 반드시 "[종합평가] AI 리포트"로 고정합니다.
                {
                  "title": string,
                  "summary": string,            // 6~7문장
                  "strengths": string[],        // 길이 3
                  "improvements": string[],     // 길이 3
                  "tone": string,               // 1줄
                  "metrics": {
                    "responses": number,        // 응답자 수(미제공시 essays 동일)
                    "essays": number,           // 코멘트 개수
                    "score_overall": number     // 미제공이면 0
                  },
                  "next_actions": string[]      // 선택(없으면 빈 배열)
                }
                """;

        String user = """
                다음 직원 평가 코멘트들을 한국어 JSON 리포트로 요약하세요.
                - locale="%s"
                - essays=%d
                - responses는 별도값 미제공이므로 essays와 동일
                - score_overall은 정보 없음 → 0
                코멘트(--- 구분):
                ---
                %s
                """.formatted(locale, essaysCount, joined);

        return summarizeWithSystem(system, user, locale, defaultMaxTokens);
    }

    /**
     * 점수기반 요약 등에서 사용하는 오버로드.
     * 컨트롤러/서비스에서 system & user를 직접 구성해 전달.
     */
    public String summarizeWithSystem(String system, String user, String locale, int maxTokens) {
        try {
            String sys = (system == null) ? "" : system;
            String usr = (user == null) ? "" : user;
            String mergedLower = (sys + " " + usr).toLowerCase();

            // 프롬프트에 'json'이라는 단어가 있으면 JSON 모드로 판단
            boolean wantsJson = mergedLower.contains("json");

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("max_tokens", Math.max(200, maxTokens));

            List<Map<String, Object>> messages;

            if (wantsJson) {
                // ✅ JSON 모드: guard system + response_format=json_object
                String guardSystem = "You must always respond with a single valid json object. " +
                        "The output must strictly be json.";

                messages = List.of(
                        Map.of("role", "system", "content", guardSystem),
                        Map.of("role", "system", "content", sys),
                        Map.of("role", "user", "content", usr));
                payload.put("response_format", Map.of("type", "json_object"));
            } else {
                // ✅ 텍스트 모드: 그냥 일반 chat completion
                messages = List.of(
                        Map.of("role", "system", "content", sys),
                        Map.of("role", "user", "content", usr));
                // response_format 설정 안 함 (=> 400 절대 안 남)
            }

            payload.put("messages", messages);

            RequestBody body = RequestBody.create(om.writeValueAsString(payload), JSON);
            Request req = new Request.Builder()
                    .url(OPENAI_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            String raw = callOpenAIWithRetry(req, maxAttempts);
            JsonNode root = om.readTree(raw);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return "요약을 생성하지 못했습니다: 빈 choices.";
            }

            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                return "요약을 생성하지 못했습니다: 빈 content.";
            }

            // 🔹 텍스트 모드: 그냥 내용 그대로 반환
            if (!wantsJson) {
                return content.trim();
            }

            // 🔹 JSON 모드: JSON 파싱 시도 (스키마는 너무 빡세게 보지 말고, 최소만 체크)
            try {
                JsonNode json = om.readTree(content);
                // title / summary 정도만 대충 확인하고, 부족해도 그냥 content 리턴
                if (json.path("title").isMissingNode() || json.path("summary").isMissingNode()) {
                    // 이전처럼 "요약 생성 실패: ..." 로 바꾸지 말고,
                    // 일단 모델이 준 content 그대로 돌려보냄
                    return content.trim();
                }
                return content.trim();
            } catch (Exception badJson) {
                // JSON 파싱이 안 되면 그냥 원문 그대로 반환
                return content.trim();
            }

        } catch (Exception e) {
            return "요약 생성 중 오류: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    // ==========================
    // Internal helpers
    // ==========================

    /**
     * 429/5xx 및 타임아웃에서 지수 백오프 재시도.
     */
    private String callOpenAIWithRetry(Request req, int maxAttempts) throws Exception {
        int attempt = 0;
        long sleepMs = 800; // 초기 backoff
        while (true) {
            attempt++;
            try (Response res = client.newCall(req).execute()) {
                String raw = (res.body() != null) ? res.body().string() : "";
                if (!res.isSuccessful()) {
                    int code = res.code();
                    // 429, 5xx → 재시도 대상
                    if ((code == 429 || code >= 500) && attempt < maxAttempts) {
                        Thread.sleep(sleepMs);
                        sleepMs = Math.min((long) (sleepMs * 1.8), 6000);
                        continue;
                    }
                    throw new RuntimeException("HTTP " + code + " / " + raw);
                }
                return raw;
            } catch (InterruptedIOException e) {
                if (attempt < maxAttempts) {
                    Thread.sleep(sleepMs);
                    sleepMs = Math.min((long) (sleepMs * 1.8), 6000);
                    continue;
                }
                throw e;
            }
        }
    }
}
