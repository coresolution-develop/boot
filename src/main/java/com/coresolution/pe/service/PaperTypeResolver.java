package com.coresolution.pe.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.coresolution.pe.mapper.AffPaperRuleMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaperTypeResolver {
    private final AffPaperRuleMapper ruleMapper;

    private static final Map<String, String> DEFAULT_PAPER = Map.of("A", "AC", "C", "AC", "B", "AD", "D", "AD");

    public String resolveByCName(String cName, String evalType) {
        if (cName != null && evalType != null) {
            String v = ruleMapper.findPaperTypeByCName(cName, evalType);
            if (v != null && !v.isBlank())
                return v;
        }
        return DEFAULT_PAPER.getOrDefault(evalType, "AC");
    }
}
