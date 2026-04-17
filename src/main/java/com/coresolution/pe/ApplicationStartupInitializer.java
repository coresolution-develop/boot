package com.coresolution.pe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.coresolution.pe.service.TableInitService;

import lombok.RequiredArgsConstructor;

/**
 * 서버 기동 시 현재 평가 연도의 테이블/관리자 계정을 자동으로 보장합니다.
 * - users_${year}, user_roles_${year} 테이블이 없으면 전년도 구조를 복사해 생성
 * - 새 연도 테이블에 관리자 계정이 없으면 전년도에서 복사해 시딩
 * (PE: personnel_evaluation, AFF: personnel_evaluation_aff 모두 처리)
 */
@Component
@RequiredArgsConstructor
public class ApplicationStartupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupInitializer.class);

    private final TableInitService tableInitService;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    @Value("${app.admin.id}")
    private String adminId;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[Startup] 평가 연도 {} 테이블 초기화 시작", currentEvalYear);

        // ─── 공통 (연도 무관) 테이블 ─────────────────────────────
        try {
            tableInitService.ensureEvalMappingRuleTableExists();
        } catch (Exception e) {
            log.error("[Startup] eval_mapping_rule 테이블 초기화 실패: {}", e.getMessage(), e);
        }

        // ─── PE 영역 ──────────────────────────────────────────
        try {
            tableInitService.ensurePeTablesExist(currentEvalYear);
            tableInitService.seedPeAdminIfAbsent(currentEvalYear, adminId);
            log.info("[Startup][PE] 초기화 완료");
        } catch (Exception e) {
            log.error("[Startup][PE] 초기화 실패: {}", e.getMessage(), e);
        }

        // ─── AFF 영역 ─────────────────────────────────────────
        try {
            tableInitService.ensureAffTablesExist(currentEvalYear);
            tableInitService.seedAffAdminIfAbsent(currentEvalYear, adminId);
            log.info("[Startup][AFF] 초기화 완료");
        } catch (Exception e) {
            log.error("[Startup][AFF] 초기화 실패: {}", e.getMessage(), e);
        }

        log.info("[Startup] 평가 연도 {} 테이블 초기화 완료", currentEvalYear);
    }
}
