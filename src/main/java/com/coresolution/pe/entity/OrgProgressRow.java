package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class OrgProgressRow {
    private String orgName;
    private int totalPairs;
    private int completedPairs;
    private int pendingPairs;
    private double progress; // %
    private String updatedAt; // ISO/문자열
}