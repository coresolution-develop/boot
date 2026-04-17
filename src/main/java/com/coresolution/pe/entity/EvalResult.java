package com.coresolution.pe.entity;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EvalResult {
    private Integer id;
    private String evaluatorId;
    private String targetId;
    private Integer evalYear;
    private String dataType; // AA/AB
    private String dataEv; // C/D/E/F/G...
    private Integer questionIdx; // evaluation.idx
    private Integer score; // 객관식 점수(있다면)
    private String textAnswer; // 주관식(있다면)
    private Date createdAt;
    private Date updatedAt;
}