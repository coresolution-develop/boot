package com.coresolution.pe.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 마이그레이션 전략 커스터마이즈.
 * <p>
 * MySQL은 DDL 트랜잭션을 지원하지 않아 마이그레이션 중 실패하면
 * {@code flyway_schema_history} 에 success=false 행이 남고 다음 기동을 막는다.
 * 매 기동마다 {@code repair()} 를 먼저 호출하여 실패 entry 를 제거한 뒤 migrate 한다.
 * 운영 영향 없음 — 성공한 마이그레이션 이력은 보존된다.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairAndMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
