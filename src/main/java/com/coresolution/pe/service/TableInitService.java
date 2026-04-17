package com.coresolution.pe.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coresolution.pe.mapper.AffTableInitMapper;
import com.coresolution.pe.mapper.TableInitMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TableInitService {

    private static final Logger log = LoggerFactory.getLogger(TableInitService.class);

    private final TableInitMapper peMapper;
    private final AffTableInitMapper affMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    /**
     * 평가 구간 규칙 테이블 보장 (연도 무관, 서버 기동 시 1회).
     */
    public void ensureEvalMappingRuleTableExists() {
        peMapper.createEvalMappingRuleTable();
        log.info("[TableInit] eval_mapping_rule 테이블 확인/생성 완료");
    }

    /**
     * PE 테이블 생성 보장.
     * users_${year}, user_roles_${year} 가 없으면 전년도 구조를 복사해서 생성.
     */
    public void ensurePeTablesExist(int year) {
        int baseYear = resolveBaseYear(year, "personnel_evaluation", peMapper);
        if (baseYear == -1) {
            throw new IllegalStateException(
                    "PE 기준 테이블을 찾을 수 없습니다. DB에 users_" + (year - 1) + " 또는 users_" + currentEvalYear + " 테이블이 필요합니다.");
        }

        if (peMapper.tableExists("personnel_evaluation", "users_" + year) == 0) {
            log.info("[TableInit][PE] users_{} 없음 → users_{} 기반으로 생성", year, baseYear);
            peMapper.createUsersTable(year, baseYear);
        }
        if (peMapper.tableExists("personnel_evaluation", "user_roles_" + year) == 0) {
            log.info("[TableInit][PE] user_roles_{} 없음 → user_roles_{} 기반으로 생성", year, baseYear);
            peMapper.createUserRolesTable(year, baseYear);
        }
    }

    /**
     * AFF 테이블 생성 보장.
     * users_${year}, user_roles_${year} 가 없으면 전년도 구조를 복사해서 생성.
     */
    public void ensureAffTablesExist(int year) {
        int baseYear = resolveAffBaseYear(year);
        if (baseYear == -1) {
            throw new IllegalStateException(
                    "AFF 기준 테이블을 찾을 수 없습니다. DB에 users_" + (year - 1) + " 또는 users_" + currentEvalYear + " 테이블이 필요합니다.");
        }

        if (affMapper.tableExists("personnel_evaluation_aff", "users_" + year) == 0) {
            log.info("[TableInit][AFF] users_{} 없음 → users_{} 기반으로 생성", year, baseYear);
            affMapper.createUsersTable(year, baseYear);
        }
        if (affMapper.tableExists("personnel_evaluation_aff", "user_roles_" + year) == 0) {
            log.info("[TableInit][AFF] user_roles_{} 없음 → user_roles_{} 기반으로 생성", year, baseYear);
            affMapper.createUserRolesTable(year, baseYear);
        }
    }

    /**
     * 기준 연도 결정: year-1 이 있으면 그것을, 없으면 currentEvalYear 사용.
     */
    private int resolveBaseYear(int year, String schema, TableInitMapper mapper) {
        int prevYear = year - 1;
        if (mapper.tableExists(schema, "users_" + prevYear) > 0) {
            return prevYear;
        }
        if (mapper.tableExists(schema, "users_" + currentEvalYear) > 0) {
            return currentEvalYear;
        }
        return -1;
    }

    /**
     * PE 관리자 계정 시딩.
     * users_${year} 에 adminId 가 없으면 baseYear 테이블에서 복사.
     */
    public void seedPeAdminIfAbsent(int year, String adminId) {
        int baseYear = resolveBaseYear(year, "personnel_evaluation", peMapper);
        if (baseYear == -1) {
            log.warn("[TableInit][PE] 관리자 시딩 불가 - 기준 테이블 없음");
            return;
        }
        if (peMapper.adminExistsInUsers(year, adminId) == 0) {
            log.info("[TableInit][PE] users_{} 에 관리자({}) 없음 → users_{} 에서 복사", year, adminId, baseYear);
            peMapper.copyAdminUser(year, baseYear, adminId);
            peMapper.fixAdminEvalYear(year, adminId);
        }
        if (peMapper.adminExistsInRoles(year, adminId) == 0) {
            log.info("[TableInit][PE] user_roles_{} 에 관리자({}) 없음 → user_roles_{} 에서 복사", year, adminId, baseYear);
            peMapper.copyAdminRoles(year, baseYear, adminId);
            peMapper.fixAdminRolesEvalYear(year, adminId);
        }
    }

    /**
     * AFF 관리자 계정 시딩.
     * users_${year} 에 adminId 가 없으면 baseYear 테이블에서 복사.
     */
    public void seedAffAdminIfAbsent(int year, String adminId) {
        int baseYear = resolveAffBaseYear(year);
        if (baseYear == -1) {
            log.warn("[TableInit][AFF] 관리자 시딩 불가 - 기준 테이블 없음");
            return;
        }
        if (affMapper.adminExistsInUsers(year, adminId) == 0) {
            log.info("[TableInit][AFF] users_{} 에 관리자({}) 없음 → users_{} 에서 복사", year, adminId, baseYear);
            affMapper.copyAdminUser(year, baseYear, adminId);
            affMapper.fixAdminEvalYear(year, adminId);
        }
        if (affMapper.adminExistsInRoles(year, adminId) == 0) {
            log.info("[TableInit][AFF] user_roles_{} 에 관리자({}) 없음 → user_roles_{} 에서 복사", year, adminId, baseYear);
            affMapper.copyAdminRoles(year, baseYear, adminId);
            affMapper.fixAdminRolesEvalYear(year, adminId);
        }
    }

    private int resolveAffBaseYear(int year) {
        int prevYear = year - 1;
        if (affMapper.tableExists("personnel_evaluation_aff", "users_" + prevYear) > 0) {
            return prevYear;
        }
        if (affMapper.tableExists("personnel_evaluation_aff", "users_" + currentEvalYear) > 0) {
            return currentEvalYear;
        }
        return -1;
    }
}
