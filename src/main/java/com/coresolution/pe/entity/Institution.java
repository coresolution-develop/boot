package com.coresolution.pe.entity;

import java.util.Date;

import lombok.Data;

@Data
public class Institution {

    /** PK */
    private int id;

    /** 기관 코드 (유니크, 영문+숫자+언더스코어) */
    private String code;

    /** 기관명 — users_YYYY.c_name 과 1:1 대응 */
    private String name;

    /** 활성 여부 (true = 활성) */
    private boolean isActive;

    private Date createdAt;
}
