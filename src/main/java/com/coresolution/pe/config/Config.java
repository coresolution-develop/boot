package com.coresolution.pe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.coresolution.pe.controller.PageController;
import com.coresolution.pe.service.CommentSummarizer;
import com.coresolution.pe.service.LocalHeuristicSummarizer;
import com.coresolution.pe.service.OpenAiCommentSummarizer;

@Configuration
public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Bean
    public CommentSummarizer commentSummarizer() {
        boolean hasKey = (apiKey != null && !apiKey.isBlank());
        log.info("[PE] CommentSummarizer bean -> {} (model={})",
                hasKey ? "OpenAiCommentSummarizer" : "LocalHeuristicSummarizer",
                model);
        return hasKey ? new OpenAiCommentSummarizer(apiKey, model)
                : new LocalHeuristicSummarizer();
    }
}
