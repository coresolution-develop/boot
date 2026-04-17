package com.coresolution.pe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 배치 INSERT용 역할 플랫 DTO */
@Data
@AllArgsConstructor
public class RoleRow {
    private String userId;
    private String role;
    private int    evalYear;
}
