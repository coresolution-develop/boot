package com.coresolution.pe.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EvalAnswer {
    private Integer evalYear;
    private String evaluatorId;
    private String targetId;
    private String dataEv; // C/D/E/F/G
    private String dataType; // AA/AB
    private String qName; // 섬33 / t43 등
    private String qLabel; // 매우우수/...
    private Integer qScore; // 10/8/6/4/2 or 5~1
    private String textAnswer;
}