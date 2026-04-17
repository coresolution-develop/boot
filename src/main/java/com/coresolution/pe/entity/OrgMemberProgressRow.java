package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class OrgMemberProgressRow {
    private String targetId;
    private String targetName;
    private String position;
    private String teamCode;
    private String subCode; // ✅ 추가
    private String deptName; // ✅ 추가
    private int needPairs;
    private int donePairs;
    private int pendingPairs;
    private double progress; // %
    private String updatedAt; // 문자열 or ISO
}