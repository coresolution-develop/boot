package com.coresolution.pe.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ReleaseGate {
    private Long id;
    private String pageKey; // 'user_result'
    private Integer evalYear; // 2025
    private LocalDateTime openAt; // yyyy-MM-dd HH:mm:ss
    private LocalDateTime closeAt;
    private Boolean enabled;
}