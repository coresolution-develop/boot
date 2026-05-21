-- ============================================================
-- V4: institutions 테이블에 kind 컬럼 추가 (PE / AFF 구분)
-- 배타적 분류: 한 기관은 PE 또는 AFF 중 하나만 사용
-- ============================================================

-- 1) kind 컬럼 추가 (기본값 'PE' — 기존 데이터는 모두 PE로 간주)
ALTER TABLE personnel_evaluation.institutions
    ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'PE'
        COMMENT '기관 종류: PE(직원평가) | AFF(계열사)';

-- 2) CHECK 제약 (MySQL 8.0.16+ 지원)
ALTER TABLE personnel_evaluation.institutions
    ADD CONSTRAINT chk_institutions_kind CHECK (kind IN ('PE','AFF'));

-- 3) 인덱스 — 종류별 조회 빈도 고려
CREATE INDEX idx_institutions_kind
    ON personnel_evaluation.institutions (kind);

-- 4) (선택) 기존 데이터 백필 — DEFAULT 'PE' 가 이미 적용되었으므로 별도 UPDATE 불필요.
--    AFF 측에서 실제로 운영 중인 기관이 있다면 운영자가 수동으로 다음 쿼리 실행:
--    UPDATE personnel_evaluation.institutions SET kind = 'AFF' WHERE id IN (...);
