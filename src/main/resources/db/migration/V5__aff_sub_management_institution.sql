-- ============================================================
-- V5: AFF sub_management 에 institution_id 추가
-- 그동안 모든 AFF 기관이 같은 sub_management 풀을 공유하여
-- 동일 sub_code 사용 시 sub_name 이 덮어쓰여지는 위험이 있었음.
-- ============================================================

-- 1) institution_id 컬럼 추가 (NULL 허용 — 기존 행도 백필 후 NOT NULL 로 강제하지 않음)
ALTER TABLE personnel_evaluation_aff.sub_management
    ADD COLUMN institution_id INT NULL
        COMMENT '소속 기관 FK (personnel_evaluation.institutions.id, AFF kind 만 허용)';

-- 2) 인덱스 — 기관 단위 조회 빈도 높음
CREATE INDEX idx_aff_sub_mgmt_institution
    ON personnel_evaluation_aff.sub_management (institution_id, eval_year);

-- 3) 기존 데이터 백필
--    현재 personnel_evaluation_aff.sub_management 에 있는 행은 모두
--    "주식회사 조이"(id=6) 가 업로드한 것으로 확인됨 (DBA 검증 결과)
--    다른 AFF 기관이 있다면 운영자가 별도로 UPDATE 해야 함
UPDATE personnel_evaluation_aff.sub_management
   SET institution_id = 6
 WHERE institution_id IS NULL
   AND eval_year = 2026;

-- 4) (선택) 다른 연도 데이터가 있다면 운영자가 수동 매핑:
--    UPDATE personnel_evaluation_aff.sub_management SET institution_id = ? WHERE eval_year = ? AND institution_id IS NULL;
