package com.coresolution.pe.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AdminDefaultTarget {
    private int evalYear;
    private String userId;
    private String targetId;
    private String evalTypeCode;
    private String dataEv;
    private String dataType;
    private String formId;
}
