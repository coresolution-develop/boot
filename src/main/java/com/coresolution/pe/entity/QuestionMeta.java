package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class QuestionMeta {
    private Integer idx; // 33..64
    private String areaLabel; // 섬김/배움/키움/나눔/목표관리
    private String typeCode; // AA/AB
    private String labelText; // 문항(평가요소)

}
