package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class CustomInsertReq {
    private String targetId; // 필수
    private String dataEv; // 예: C/D/E/F/G
    private String dataType; // 예: AA/AB
    private Long formId; // nullable
    private String reason; // nullable

    private String source;
    private String evalTypeCode; // nullable
}
