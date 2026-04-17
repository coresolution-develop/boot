package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class FormSummary {
    private String formId; // 폼 ID
    private String formName; // 표시명
    private String dataType; // AA/AB
    private String dataEv; // C/D/E/F/G
}
