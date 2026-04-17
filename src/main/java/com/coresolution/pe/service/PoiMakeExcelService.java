package com.coresolution.pe.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.RoleRow;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UploadParseResult;
import com.coresolution.pe.entity.UploadParseResult.RowError;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.entity.UserrolePE;
import com.coresolution.pe.mapper.ExcelMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PoiMakeExcelService {

    private final PeService   peService;
    private final ExcelMapper excelMapper;
    private final JdbcTemplate jdbcTemplate;

    // ── 지원 날짜 포맷 (멀티포맷 파싱) ──────────────────────────────────
    private static final List<String> DATE_PATTERNS = Arrays.asList(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd"
    );

    // ════════════════════════════════════════════════════════════════════
    // 1.  사용자 파싱 + 검증 (통합 메서드)
    // ════════════════════════════════════════════════════════════════════

    /**
     * 직원명부 시트를 파싱하여 유저/역할 목록과 행별 오류를 함께 반환한다.
     *
     * 컬럼 구조 (B열 = index 1 기준):
     *  1:기관명 2:소속기관 3:부서명 4:사원번호 5:직책 6:이름
     *  7:입사일 8:퇴사일 9:핸드폰
     * 10:경혁팀장여부 11:경혁팀여부 12:부서장여부
     * 13:1인부서여부 14:의료리더여부 15:평가제외여부
     */
    public UploadParseResult parseUsersWithValidation(Sheet sheet, int year) {
        UploadParseResult result = new UploadParseResult();
        Set<String> seenIds = new HashSet<>();  // 중복 사원번호 감지

        for (Row row : sheet) {
            if (row.getRowNum() < 2 || isRowEmpty(row)) continue;
            int displayRow = row.getRowNum() + 1; // 사용자에게 보여줄 행 번호 (1-based)

            UserPE vo = new UserPE();

            // ── 기본 필드 ────────────────────────────────────────────────
            vo.setCName(getCellString(row.getCell(1)));
            vo.setCName2(getCellString(row.getCell(2)));

            String subName   = getCellString(row.getCell(3));
            String explicitCode = getCellString(row.getCell(16)); // Q열: 선택적 부서코드

            String subCode;
            if (!explicitCode.isEmpty()) {
                // 직원 파일에 부서코드가 직접 기재된 경우
                subCode = explicitCode;
                if (peService.countByCodeAndYear(subCode, year) == 0) {
                    // sub_management에 없으면 자동등록 대기 목록에 추가
                    result.getDeptsToAutoRegister().put(subCode, subName);
                }
            } else {
                // 기존 방식: 부서명으로 코드 조회
                subCode = (subName.isEmpty()) ? "" : peService.getSubcode(subName, year);
                if ((subCode == null || subCode.isEmpty()) && !subName.isEmpty()) {
                    result.getErrors().add(new RowError(displayRow, "부서명",
                            "미등록 부서: \"" + subName + "\". Q열(부서코드)을 입력하면 자동 등록됩니다.", true));
                    subCode = "";
                }
            }
            vo.setSubCode(subCode == null ? "" : subCode);

            String id = getCellString(row.getCell(4));
            if (id.isEmpty()) {
                result.getErrors().add(new RowError(displayRow, "사원번호",
                        "사원번호가 비어 있습니다.", true));
            } else if (seenIds.contains(id)) {
                result.getErrors().add(new RowError(displayRow, "사원번호",
                        "중복된 사원번호: " + id, true));
            } else {
                seenIds.add(id);
            }
            vo.setId(id);

            vo.setPosition(getCellString(row.getCell(5)));

            String name = getCellString(row.getCell(6));
            if (name.isEmpty()) {
                result.getErrors().add(new RowError(displayRow, "이름",
                        "이름이 비어 있습니다.", true));
            }
            vo.setName(name);

            // ── 날짜 ─────────────────────────────────────────────────────
            Date createAt  = getCellDate(row.getCell(7));
            Date deleteAt  = getCellDate(row.getCell(8));
            if (createAt != null && deleteAt != null && createAt.after(deleteAt)) {
                result.getErrors().add(new RowError(displayRow, "입사일/퇴사일",
                        "입사일이 퇴사일보다 늦습니다.", false));
            }
            vo.setCreateAt(createAt);
            vo.setDeleteAt(deleteAt);

            vo.setPhone(getCellString(row.getCell(9)));

            // ── 팀코드 (경혁팀여부 L열=11) ────────────────────────────────
            boolean isTeam = isTrueCell(row.getCell(11));
            vo.setTeamCode(isTeam ? "GH_TEAM" : "NO_TEAM");

            // ── 평가제외 (P열=15) ─────────────────────────────────────────
            vo.setDelYn(isTrueCell(row.getCell(15)) ? "Y" : "N");

            vo.setEvalYear(year);
            result.getUsers().add(vo);

            // ── 역할 ─────────────────────────────────────────────────────
            List<UserrolePE.Role> roleList = new ArrayList<>();
            boolean teamLeader  = isTrueCell(row.getCell(10)); // K열
            boolean teamMember  = isTrueCell(row.getCell(11)); // L열
            boolean subHead     = isTrueCell(row.getCell(12)); // M열
            boolean onePerson   = isTrueCell(row.getCell(13)); // N열
            boolean medLeader   = isTrueCell(row.getCell(14)); // O열

            if (teamLeader)      roleList.add(UserrolePE.Role.TEAM_HEAD);
            else if (teamMember) roleList.add(UserrolePE.Role.TEAM_MEMBER);
            if (subHead)         roleList.add(UserrolePE.Role.SUB_HEAD);
            if (onePerson)       roleList.add(UserrolePE.Role.ONE_PERSON_SUB);
            if (medLeader)       roleList.add(UserrolePE.Role.MEDICAL_LEADER);
            if (roleList.isEmpty()) roleList.add(UserrolePE.Role.SUB_MEMBER);

            result.getRoles().add(new UserrolePE(0, id, roleList, year));
        }
        return result;
    }

    /** 하위호환용 - 기존 코드가 분리 호출하는 경우를 위해 유지 */
    public List<UserPE> parseUsers(Sheet sheet, int year) {
        return parseUsersWithValidation(sheet, year).getUsers();
    }

    public List<UserrolePE> parseRoles(Sheet sheet, int year) {
        return parseUsersWithValidation(sheet, year).getRoles();
    }

    // ════════════════════════════════════════════════════════════════════
    // 2.  부서 파싱
    // ════════════════════════════════════════════════════════════════════

    public List<SubManagement> parseDepartments(Sheet sheet, int year) {
        List<SubManagement> list = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() < 2 || isRowEmpty(row)) continue;
            SubManagement sub = new SubManagement();
            sub.setSubName(getCellString(row.getCell(1)));
            sub.setSubCode(getCellString(row.getCell(2)));
            sub.setEvalYear(year);
            if (!sub.getSubName().isEmpty() && !sub.getSubCode().isEmpty()) {
                list.add(sub);
            }
        }
        return list;
    }

    // ════════════════════════════════════════════════════════════════════
    // 3.  DB 저장 — 배치 처리
    // ════════════════════════════════════════════════════════════════════

    /**
     * 직원 + 역할을 DB에 저장한다.
     * INSERT ON DUPLICATE KEY UPDATE 방식으로 신규/수정을 배치 처리한다.
     */
    @Transactional
    public void saveUsersAndRoles(List<UserPE> users,
                                  List<UserrolePE> roles,
                                  int year) {
        if (users == null || users.isEmpty()) return;

        // ① 직원 배치 upsert
        excelMapper.batchUpsertUsers(users, year);

        // ② 역할 초기화 후 배치 INSERT
        excelMapper.getRoleDelete(year);
        List<RoleRow> flatRoles = roles.stream()
                .flatMap(ur -> ur.getRoles().stream()
                        .map(r -> new RoleRow(ur.getUserId(), r.name(), year)))
                .collect(Collectors.toList());
        if (!flatRoles.isEmpty()) {
            excelMapper.batchInsertRoles(flatRoles, year);
        }
    }

    @Transactional
    public void saveDepartments(List<SubManagement> subs, int year) {
        if (subs == null || subs.isEmpty())
            throw new IllegalStateException("업로드된 부서 목록이 없습니다.");
        for (SubManagement s : subs) {
            s.setEvalYear(year);
            if (peService.countByCodeAndYear(s.getSubCode(), year) > 0) {
                peService.subupdate(s);
            } else {
                peService.subinsert(s);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 4.  초기화
    // ════════════════════════════════════════════════════════════════════

    @Transactional
    public void resetUsersAndRolesExceptCurrentAdmin(int year) {
        String adminId = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        peService.deleteRolesByYearExcept(year, adminId);
        peService.deleteUsersByYearExcept(year, adminId);
        jdbcTemplate.execute(
                "ALTER TABLE personnel_evaluation.users_" + year + " AUTO_INCREMENT = 2");
    }

    @Transactional
    public void resetYearlyDepartments(int year, String adminSubCode) {
        peService.deleteByYearExcept(year, adminSubCode);
        peService.resetAutoIncrement();
    }

    // ════════════════════════════════════════════════════════════════════
    // 헬퍼 메서드
    // ════════════════════════════════════════════════════════════════════

    /**
     * 셀 값을 String으로 변환한다.
     * - 숫자 셀은 정수로 변환 (123456.0 → "123456")
     * - 앞뒤 공백 자동 제거 (trim)
     */
    private String getCellString(Cell cell) {
        if (cell == null) return "";
        String raw = switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            default -> "";
        };
        return raw == null ? "" : raw.trim();
    }

    /**
     * 0/1, "0"/"1", "TRUE"/"FALSE", true/false 셀을 모두 boolean으로 해석한다.
     * 헤더 안내가 TRUE/FALSE이지만 실제 입력은 0/1인 경우에도 동작한다.
     */
    private boolean isTrueCell(Cell cell) {
        if (cell == null) return false;
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> cell.getNumericCellValue() != 0;
            case STRING  -> {
                String s = cell.getStringCellValue().trim();
                yield "1".equals(s)
                        || "TRUE".equalsIgnoreCase(s)
                        || "Y".equalsIgnoreCase(s)
                        || "yes".equalsIgnoreCase(s);
            }
            case FORMULA -> {
                try { yield cell.getBooleanCellValue(); }
                catch (Exception e) { yield false; }
            }
            default -> false;
        };
    }

    /**
     * 날짜 셀을 Date로 변환한다.
     * - 숫자(시리얼) 셀: DateUtil로 자동 변환 (날짜 포맷 여부와 무관)
     * - 문자열 셀: 여러 포맷 순서대로 파싱 시도
     */
    private Date getCellDate(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            // 날짜 포맷 여부와 관계없이 시리얼 번호를 날짜로 변환
            // (실제 엑셀 파일은 날짜 셀로 저장되므로 isCellDateFormatted()도 true)
            try { return cell.getDateCellValue(); }
            catch (Exception ignored) { return null; }
        }
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return null;
            for (String pattern : DATE_PATTERNS) {
                try {
                    SimpleDateFormat fmt = new SimpleDateFormat(pattern);
                    fmt.setLenient(false);
                    return fmt.parse(s);
                } catch (ParseException ignored) { }
            }
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c != null && c.getCellType() != CellType.BLANK
                    && !getCellString(c).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
