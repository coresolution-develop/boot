package com.coresolution.pe.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 평가 구간 규칙 — admin_mapping_rule 테이블과 매핑.
 *
 * targetScope 값:
 *   SAME_DEPT     : 평가자와 같은 sub_code 안에서 target 조건 적용
 *   ALL_PREFIX    : targetSubPrefix로 시작하는 모든 부서에서 target 조건 적용
 *   SPECIFIC_ROLE : target_role 조건에 맞는 사람 전체 (기관 내)
 *   SPECIFIC_TEAM : target_team_code에 해당하는 사람 전체 (기관 내)
 */
@Data
public class EvalMappingRule {
    private Long id;
    private int evalYear;
    private String ruleName;

    // ── 평가자 조건 (null/빈값 = 제한 없음) ───────────────────────────
    private String evaluatorRole;       // sub_head / sub_member / GH_TEAM / team_head / one_person_sub / medical_leader
    private String evaluatorSubPrefix;  // 부서코드 접두어 (예: A = 진료부 전체)
    private String evaluatorTeamCode;   // team_code (예: GH_TEAM)

    // ── 대상자 조건 (null/빈값 = 제한 없음) ───────────────────────────
    private String targetRole;
    private String targetSubPrefix;
    private String targetTeamCode;

    // ── 범위 ─────────────────────────────────────────────────────────
    private String targetScope;         // SAME_DEPT / ALL_PREFIX / SPECIFIC_ROLE / SPECIFIC_TEAM

    // ── 평가 설정 ─────────────────────────────────────────────────────
    private String dataEv;              // A/B/C/D/E/F/G
    private String dataType;            // AA/AB
    private String evalTypeCode;        // GH_TO_GH / SUB_HEAD_TO_MEMBER 등 (선택)

    // ── 기관 조건 (null/빈값 = 전체 기관 적용) ────────────────────────
    private String cName;

    // ── 메타 ─────────────────────────────────────────────────────────
    private boolean excludeSelf = true;
    private boolean enabled     = true;
    private String  createdBy;
    private String  updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
