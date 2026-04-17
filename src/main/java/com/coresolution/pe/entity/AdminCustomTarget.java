package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class AdminCustomTarget {
    private int evalYear;
    private String userId;
    private String targetId;
    private String evalTypeCode; // nullable
    private String dataEv; // C/D/E/F/G ...
    private String dataType; // AA/AB
    private Long formId; // nullable

    public enum Action {
        ADD, REMOVE
    }
}