package com.coresolution.pe.service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LocalHeuristicSummarizer implements CommentSummarizer {
    private static final Pattern SENT_SPLIT = Pattern.compile("(?<=[.!?。…]|\\n)");

    @Override
    public String summarize(List<String> comments, String localeTag, int maxChars) {
        if (comments == null || comments.isEmpty())
            return "요약할 코멘트가 없습니다.";
        String text = String.join("\n", comments);
        if (text.length() > maxChars)
            text = text.substring(0, maxChars) + "\n...(중략)";

        // 1) 문장 단위 분할
        var sentences = Arrays.stream(SENT_SPLIT.split(text))
                .map(String::trim).filter(s -> s.length() > 5).toList();
        if (sentences.isEmpty())
            return "요약을 생성하지 못했습니다.";

        // 2) 단어 빈도 기반 중요 문장 추정 (아주 단순)
        var freq = new HashMap<String, Integer>();
        for (var s : sentences) {
            for (var w : s.toLowerCase().replaceAll("[^\\p{L}\\p{N}\\s]", " ").split("\\s+")) {
                if (w.isBlank())
                    continue;
                if (w.length() <= 1)
                    continue;
                freq.merge(w, 1, Integer::sum);
            }
        }
        record Scored(String s, int score) {
        }
        var scored = sentences.stream()
                .map(s -> new Scored(s, Arrays.stream(s.toLowerCase().split("\\s+"))
                        .map(w -> w.replaceAll("[^\\p{L}\\p{N}]", ""))
                        .filter(w -> w.length() > 1)
                        .mapToInt(w -> freq.getOrDefault(w, 0)).sum()))
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .toList();

        var core = scored.stream().limit(4).map(sc -> "• " + sc.s).collect(Collectors.joining("\n"));

        return """
                (간이 요약)
                핵심 문장:
                %s

                ※ 정확한 요약을 원하시면 AI 요약(외부 API) 구성을 권장합니다.
                """.formatted(core);
    }
}