-- ============================================================
-- V3: end_letter 테이블에 배경 이미지 URL 컬럼 추가
-- ============================================================

ALTER TABLE personnel_evaluation.end_letter
    ADD COLUMN bg_image_url VARCHAR(500) DEFAULT NULL
        COMMENT '배경 이미지 경로 (/uploads/bg/xxx.jpg)';
