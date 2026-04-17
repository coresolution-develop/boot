package com.coresolution.pe.entity;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerPayload {
    private Map<String, String> essays; // 주관식
    private Map<String, String> radios; // 객관식
}