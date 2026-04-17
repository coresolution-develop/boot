-- ============================================================
-- V1: 기관(institution) 멀티테넌시 스키마 추가
-- 실행 순서: 1→2→3→4→5 순서 준수
-- 주의: 실행 전 R3 리스크 확인 쿼리(하단) 먼저 수행할 것
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- [사전 확인] sub_code 중복 기관 존재 여부 (결과가 있으면 수동 처리 필요)
-- SELECT sub_code, GROUP_CONCAT(DISTINCT c_name) AS orgs, COUNT(DISTINCT c_name) AS cnt
-- FROM personnel_evaluation.users_2026
-- WHERE c_name IS NOT NULL AND sub_code IS NOT NULL
-- GROUP BY sub_code
-- HAVING COUNT(DISTINCT c_name) > 1;
-- ────────────────────────────────────────────────────────────


-- ──────────────────────────────────
-- 1) institutions 테이블 생성
-- ──────────────────────────────────
CREATE TABLE IF NOT EXISTS personnel_evaluation.institutions (
    id         INT          NOT NULL AUTO_INCREMENT COMMENT '기관 PK',
    code       VARCHAR(50)  NOT NULL                COMMENT '기관 코드 (유니크, 영문/숫자/언더스코어)',
    name       VARCHAR(200) NOT NULL                COMMENT '기관명 — users_YYYY.c_name 값과 1:1 매핑',
    is_active  TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부 (1=활성, 0=비활성)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_institutions_code (code),
    UNIQUE KEY uq_institutions_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='기관(병원) 마스터';


-- ──────────────────────────────────
-- 2) institution_admins 테이블 생성
-- ──────────────────────────────────
CREATE TABLE IF NOT EXISTS personnel_evaluation.institution_admins (
    id             INT          NOT NULL AUTO_INCREMENT COMMENT '기관 관리자 PK',
    institution_id INT          NOT NULL                COMMENT '소속 기관 FK',
    login_id       VARCHAR(100) NOT NULL                COMMENT '로그인 ID (유니크)',
    pwd            VARCHAR(255) NOT NULL                COMMENT 'BCrypt 해시 비밀번호',
    name           VARCHAR(100) NOT NULL                COMMENT '관리자 성명',
    is_active      TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_inst_admin_login_id (login_id),
    CONSTRAINT fk_inst_admin_institution
        FOREIGN KEY (institution_id)
        REFERENCES personnel_evaluation.institutions (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='기관별 관리자 계정 (연도 독립)';


-- ──────────────────────────────────
-- 3) sub_management 에 institution_id 컬럼 추가
-- ──────────────────────────────────
ALTER TABLE personnel_evaluation.sub_management
    ADD COLUMN IF NOT EXISTS institution_id INT NULL
        COMMENT '소속 기관 FK (NULL = 슈퍼 어드민 전용)',
    ADD CONSTRAINT fk_sub_mgmt_institution
        FOREIGN KEY (institution_id)
        REFERENCES personnel_evaluation.institutions (id)
        ON DELETE SET NULL ON UPDATE CASCADE;


-- ──────────────────────────────────
-- 4) 기존 c_name → institutions 마이그레이션
--    code 는 c_name 에서 공백→_ 치환 후 대문자화
--    (코드 충돌 시 수동으로 code 값을 직접 수정할 것)
-- ──────────────────────────────────
INSERT INTO personnel_evaluation.institutions (code, name)
SELECT
    -- 코드: 공백·괄호·특수문자 제거 후 대문자화, 중복 방지용 임시 규칙
    UPPER(
        REPLACE(REPLACE(REPLACE(REPLACE(
            c_name,
        ' ', '_'), '(', ''), ')', ''), '-', '_')
    ) AS code,
    c_name AS name
FROM (
    SELECT DISTINCT c_name
    FROM personnel_evaluation.users_2026          -- ★ 연도 테이블에 맞게 수정
    WHERE c_name IS NOT NULL AND c_name <> ''
) t
ON DUPLICATE KEY UPDATE name = VALUES(name);      -- 재실행 안전


-- ──────────────────────────────────
-- 5) sub_management.institution_id 역채움
--    users_YYYY ↔ sub_management 을 c_name+sub_code 로 조인
--    sub_code 가 복수 기관에 걸쳐 있으면 아래 쿼리 실행 후 NULL 잔존 → 수동 처리
-- ──────────────────────────────────
UPDATE personnel_evaluation.sub_management sm
JOIN (
    SELECT DISTINCT
        u.sub_code,
        u.eval_year,
        i.id AS institution_id
    FROM personnel_evaluation.users_2026 u         -- ★ 연도 테이블에 맞게 수정
    JOIN personnel_evaluation.institutions i
      ON i.name = u.c_name
    WHERE u.sub_code IS NOT NULL
      AND u.c_name   IS NOT NULL
) derived
  ON derived.sub_code  = sm.sub_code
 AND derived.eval_year = sm.eval_year
SET sm.institution_id = derived.institution_id
WHERE sm.institution_id IS NULL;


-- ──────────────────────────────────
-- [검증] 역채움 후 NULL 잔존 확인
-- SELECT * FROM personnel_evaluation.sub_management WHERE institution_id IS NULL;
-- ──────────────────────────────────
