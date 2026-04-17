package com.coresolution.pe.entity;

import java.util.Date;

import lombok.Data;

@Data
public class InstitutionAdmin {

    /** PK */
    private int id;

    /** 소속 기관 FK */
    private int institutionId;

    /** 로그인 ID */
    private String loginId;

    /** BCrypt 해시 비밀번호 */
    private String pwd;

    /** 관리자 성명 */
    private String name;

    /** 활성 여부 */
    private boolean isActive;

    private Date createdAt;

    // ── 조회용 (JOIN 결과, DB 컬럼 아님) ──────────────────

    /** institutions.name (JOIN으로 채움) */
    private String institutionName;

    /** institutions.code (JOIN으로 채움) */
    private String institutionCode;
}
