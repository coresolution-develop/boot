package com.coresolution.pe.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class EvaluationSubmission {
    private Long id;
    private Integer evalYear;
    private String evaluatorId;
    private String targetId;
    private String dataEv;
    private String dataType;
    private Integer answeredCount;
    private Integer totalScore;
    private Double avgScore;
    private Integer version;
    private Integer isActive;
    private String delYn;
    private LocalDateTime updatedAt;
    private String answersJson;
    private String evaluatorName;
    private String evaluatorDept;
    private String evaluatorPos;
}