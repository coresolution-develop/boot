package com.coresolution.pe.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ReleaseWindowRow {
    private Long id;
    private int evalYear;
    private String dataType;
    private String dataEv;
    private String cName; // '' == 전역
    private String subCode; // '' == 전역/병원단위
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private boolean enabled;
    private String delYn;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}