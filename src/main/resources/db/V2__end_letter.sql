-- ============================================================
-- V2: 평가완료 편지(end_letter) 테이블 추가
-- 기관 관리자가 연도별로 평가완료 후 보여줄 메시지를 설정
-- ============================================================

CREATE TABLE IF NOT EXISTS personnel_evaluation.end_letter (
    id               INT          NOT NULL AUTO_INCREMENT,
    eval_year        INT          NOT NULL,
    institution_name VARCHAR(200) NOT NULL,
    content          TEXT,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_year_inst (eval_year, institution_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='평가완료 메시지 (기관별·연도별)';
