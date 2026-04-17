package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class AffAdminDefaultTarget {
    private int    evalYear;
    private String userId;
    private String targetId;
    private String evalTypeCode; // 예: SUB_HEAD_TO_SUB_HEAD / SUB_HEAD_TO_MEMBER ...
    private String dataEv;       // 예: "A","B","C","D"...
    private String dataType;     // 예: "AC","AD","AE"...
    // private Long   formId;     // 더 이상 사용 안 함 (필요 없으면 삭제)
}