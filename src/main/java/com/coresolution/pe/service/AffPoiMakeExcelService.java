package com.coresolution.pe.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.entity.UserrolePE;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffPoiMakeExcelService {

    private final PeAffService peService;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate; // spring-jdbc 의존 필요

    /** 사용자 목록 파싱 */
    public List<UserPE> parseUsers(Sheet sheet, int year) {
        List<UserPE> users = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() < 2 || isRowEmpty(row))
                continue;
            // 헤더 정의 (주석과 실제 인덱스 일치시킴)
            // B:기관명, C:소속기관, D:부서명, E:사번, F:직책, G:이름
            // H:입사일, I:퇴사일, J:핸드폰
            // K:기관장여부, L:소속장여부, M:부서장여부, N:평가제외여부

            UserPE vo = new UserPE();
            vo.setCName(getCellString(row.getCell(1))); // B
            vo.setCName2(getCellString(row.getCell(2))); // C
            String subName = getCellString(row.getCell(3)); // D
            String subCode = peService.getSubcode(subName, year);
            vo.setSubCode(subCode);
            vo.setId(getCellString(row.getCell(4))); // E
            vo.setPosition(getCellString(row.getCell(5))); // F
            vo.setName(getCellString(row.getCell(6))); // G
            vo.setCreateAt(getCellDate(row.getCell(7))); // H
            vo.setDeleteAt(getCellDate(row.getCell(8))); // I
            vo.setPhone(getCellString(row.getCell(9))); // J

            // ⭐ 계열사: 팀이 없음 → 항상 NO_TEAM
            vo.setTeamCode("NO_TEAM");

            // ⭐ 평가제외 여부는 N열(13) — 기존 코드의 15는 잘못된 참조였음
            String delFlag = getCellString(row.getCell(13)); // N
            vo.setDelYn("TRUE".equalsIgnoreCase(delFlag) ? "Y" : "N");

            vo.setEvalYear(year);
            users.add(vo);
        }
        return users;
    }

    /** 권한 목록 파싱 */
    public List<UserrolePE> parseRoles(Sheet sheet, int year) {
        List<UserrolePE> roles = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() < 2 || isRowEmpty(row))
                continue;

            String userId = getCellString(row.getCell(4)); // E: 사번

            // K:기관장, L:소속장, M:부서장
            boolean orgHead = "TRUE".equalsIgnoreCase(getCellString(row.getCell(10))); // K
            boolean agcHead = "TRUE".equalsIgnoreCase(getCellString(row.getCell(11))); // L
            boolean subHead = "TRUE".equalsIgnoreCase(getCellString(row.getCell(12))); // M

            List<UserrolePE.Role> list = new ArrayList<>();
            if (orgHead)
                list.add(UserrolePE.Role.AFF_ORG_HEAD);
            if (agcHead)
                list.add(UserrolePE.Role.AFF_AGC_HEAD);
            if (subHead)
                list.add(UserrolePE.Role.AFF_SUB_HEAD);

            // 아무 체크도 없으면 기본 일반 구성원
            if (list.isEmpty())
                list.add(UserrolePE.Role.SUB_MEMBER);

            roles.add(new UserrolePE(0, userId, list, year));
        }
        return roles;
    }

    /** DB 반영: insert/update + 권한 재등록 */
    @Transactional
    public void saveUsersAndRoles(List<UserPE> users,
            List<UserrolePE> roles,
            int year) {
        // 1) 사용자 insert/update
        for (UserPE u : users) {
            UserPE exist = peService.findUserById(u.getId(), year);
            if (exist != null) {
                peService.getUserExcelUpdate(u);
            } else {
                peService.getUserExcelUpload(u);
            }
        }

        // 2) 권한 초기화 후 재등록
        peService.getRoleDelete(year);
        for (UserrolePE ur : roles) {
            for (UserrolePE.Role r : ur.getRoles()) {
                peService.getRoleExcelUpload(ur.getUserId(), r.name(), year);
            }
        }
    }

    /** 부서(Subject) 시트에서 데이터 파싱 */
    public List<SubManagement> parseDepartments(Sheet sheet, int year) {
        List<SubManagement> list = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Row row : sheet) {
            if (row.getRowNum() < 2 || isRowEmpty(row))
                continue;

            SubManagement sub = new SubManagement();
            // B열: 부서명, C열: 부서코드
            sub.setSubName(getCellString(row.getCell(1)));
            sub.setSubCode(getCellString(row.getCell(2)));
            sub.setEvalYear(year);
            list.add(sub);
        }
        return list;
    }

    /** DB 반영: 기존 부서 삭제 → 새로 삽입 */
    @Transactional
    public void saveDepartments(List<SubManagement> subs, int year) {
        // 1) 입력받은 subs 리스트가 비어있으면 에러
        if (subs == null || subs.isEmpty()) {
            throw new IllegalStateException("업로드된 부서 목록이 없습니다.");
        }
        for (SubManagement s : subs) {
            // 1) 파라미터에 확실히 연도를 넣어 줍니다
            s.setEvalYear(year);

            // 2) 같은 코드+연도 레코드가 있으면 UPDATE, 없으면 INSERT
            if (peService.countByCodeAndYear(s.getSubCode(), year) > 0) {
                peService.subupdate(s);
            } else {
                peService.subinsert(s);
            }
        }
    }
    // ────────────────────────────────────────────────────────────
    // 아래는 Excel parsing 헬퍼 메서드
    // ────────────────────────────────────────────────────────────

    private String getCellString(Cell cell) {
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private Date getCellDate(Cell cell) {
        try {
            if (cell == null)
                return null;
            if (cell.getCellType() == CellType.STRING) {
                return sdf.parse(cell.getStringCellValue());
            } else if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c != null && c.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * 지정 연도의 users_<year> 테이블과 권한 테이블을
     * 현재 로그인한 관리자 계정(ID)만 제외하고 모두 삭제
     */
    @Transactional
    public void resetUsersAndRolesExceptCurrentAdmin(int year) {
        String adminId = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        peService.deleteRolesByYearExcept(year, adminId);
        peService.deleteUsersByYearExcept(year, adminId);
        // 3) AUTO_INCREMENT 리셋: 남은 레코드(pk=1 이 관리자라면) 다음 번호를 2로 설정
        String tableName = "personnel_evaluation.users_" + year;
        jdbcTemplate.execute(
                "ALTER TABLE " + tableName + " AUTO_INCREMENT = 2");
    }

    @Transactional
    public void resetYearlyDepartments(int year, String adminSubCode) {
        // 1) 해당 연도의 adminCode 를 제외한 모든 행 삭제
        peService.deleteByYearExcept(year, adminSubCode);
        // 2) AUTO_INCREMENT 초기화
        peService.resetAutoIncrement();
    }

}
