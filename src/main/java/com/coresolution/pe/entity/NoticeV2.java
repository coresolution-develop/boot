package com.coresolution.pe.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class NoticeV2 {
    private Integer id;
    private Integer evalYear;
    private String title;
    private String bodyMd; // 마크다운 또는 HTML
    private Boolean pinned;
    private Integer sortOrder;
    private LocalDateTime publishFrom;
    private LocalDateTime publishTo;
    private Boolean isActive;
    private String versionTag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
