package com.coresolution.pe.service;

import java.util.List;

public interface CommentSummarizer {
    /**
     * @param comments  코멘트 원문 리스트
     * @param localeTag "ko", "en" 등 (출력 언어 힌트용)
     * @param maxChars  프롬프트에 넣을 최대 길이(초과분 자르기)
     */
    String summarize(List<String> comments, String localeTag, int maxChars);

}
