package com.coresolution.pe.entity;

import lombok.Data;

@Data
public class EvalPolicy {
    private String orgCode;
    private String leaderScope; // ALL_MEDICAL | OWN_SUB | CUSTOM
    private String leaderSublist; // CSV (CUSTOM일 때만)
}