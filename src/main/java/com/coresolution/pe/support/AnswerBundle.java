package com.coresolution.pe.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Data
public class AnswerBundle {
    private Map<String, String> radios = new HashMap<>();
    private Map<String, String> essays = new HashMap<>();

    private static final ObjectMapper om = new ObjectMapper();

    /**
     * answers_json 이
     * 1) {"radios": {...}, "essays": {...}} (권장 신포맷)
     * 2) "키=값,키=값,..." 또는 "키:값,..." (구포맷)
     * 3) null/빈값
     * 인 모든 경우를 안전하게 처리한다.
     */
    public static AnswerBundle fromJsonOrLegacy(String answersJson) {
        AnswerBundle b = new AnswerBundle();
        if (answersJson == null || answersJson.trim().isEmpty())
            return b;

        String s = answersJson.trim();

        // ① JSON 오브젝트로 보이면 그대로 파싱
        if (s.startsWith("{")) {
            try {
                Map<String, Object> root = om.readValue(s, new TypeReference<>() {
                });
                Object r = root.get("radios");
                Object e = root.get("essays");
                if (r instanceof Map<?, ?> rr) {
                    rr.forEach((k, v) -> b.radios.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                }
                if (e instanceof Map<?, ?> ee) {
                    ee.forEach((k, v) -> b.essays.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                }
                return b;
            } catch (Exception ignore) {
                // JSON 파싱 실패 시 구포맷으로 폴백
            }
        }

        // ② JSON 문자열 값(예: "\"a=1,b=2\"")이면 따옴표 제거
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }

        // ③ 구포맷: "키=값,키=값" 혹은 "키:값,..." → radios/essays에 모두 반영
        // 에세이 키 규칙(t43 등)이 있다면 필요 시 여기서 필터링/분기
        String[] pairs = s.split("\\s*,\\s*");
        for (String pair : pairs) {
            if (pair.isBlank())
                continue;
            String[] kv = pair.split("\\s*[=:]\\s*", 2);
            String k = kv[0];
            String v = kv.length > 1 ? kv[1] : "";
            // 간단 규칙: 키가 't'로 시작하면 에세이, 아니면 라디오
            if (k.startsWith("t") || k.startsWith("T")) {
                b.essays.put(k, v);
            } else {
                b.radios.put(k, v);
            }
        }
        return b;
    }

    /** 뷰에 그대로 주입할 JSON 문자열 */
    public String toJsonString() {
        try {
            Map<String, Object> root = new HashMap<>();
            root.put("radios", radios == null ? Collections.emptyMap() : radios);
            root.put("essays", essays == null ? Collections.emptyMap() : essays);
            return om.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"radios\":{},\"essays\":{}}";
        }
    }
}