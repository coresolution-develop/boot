-- ============================================================
-- V6: institutions 테이블에 agc_code 추가 (AGC 그룹 식별)
-- AGC(소속 그룹) = 여러 ORG(개별 기관)를 묶는 상위 분류
-- 예: agc_code='JEONGSUNG_MOA' → 정성모아 1·2·3병원 + 정성 사랑모아 1·2·3병원
-- ============================================================

ALTER TABLE personnel_evaluation.institutions
    ADD COLUMN agc_code VARCHAR(50) NULL
        COMMENT 'AGC(소속 그룹) 식별 코드. 같은 값을 가진 institution 끼리 한 그룹으로 평가 자동 생성됨';

CREATE INDEX idx_institutions_agc_code
    ON personnel_evaluation.institutions (agc_code);

-- (운영자 수동 백필 예시)
-- UPDATE personnel_evaluation.institutions SET agc_code='JEONGSUNG_MOA'
--   WHERE name IN ('정성모아1병원','정성모아2병원','정성모아3병원',
--                  '정성 사랑모아1병원','정성 사랑모아2병원','정성 사랑모아3병원');
-- UPDATE personnel_evaluation.institutions SET agc_code='SARANG_MOA'
--   WHERE name IN ('사랑모아1병원','사랑모아2병원','사랑모아3병원');
-- UPDATE personnel_evaluation.institutions SET agc_code='JAYA'
--   WHERE name LIKE '자야%';
