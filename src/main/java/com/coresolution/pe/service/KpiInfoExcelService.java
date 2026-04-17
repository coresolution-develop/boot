package com.coresolution.pe.service;

import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.KpiPersonal2025;
import com.coresolution.pe.mapper.KpiInfo2025Mapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KpiInfoExcelService {

    private final KpiInfo2025Mapper kpiMapper;

    public List<Map<String, Object>> parseKpiSheet(Sheet sheet) {
        List<Map<String, Object>> list = new ArrayList<>();
        int firstRow = 2; // B3부터
        int lastRow = sheet.getLastRowNum();

        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;

            Map<String, Object> map = new HashMap<>();
            boolean allEmpty = true;

            for (int c = 1; c <= 82; c++) { // B열부터 82개
                Cell cell = row.getCell(c);
                String key = String.format("kcol%02d", c); // kcol01...

                String value = getStringCell(cell);
                if (value != null && !value.isBlank()) {
                    allEmpty = false;
                }
                map.put(key, value);
            }

            if (!allEmpty) {
                list.add(map);
            }
        }
        return list;
    }

    // 기존에 쓰던 getStringCell 그대로 두면 됩니다.
    private String getStringCell(Cell cell) {
        if (cell == null)
            return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();

            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    // 핵심: 117004.0 -> 117004, 1.17004E5 -> 117004 로 안전 변환
                    yield java.math.BigDecimal.valueOf(cell.getNumericCellValue())
                            .stripTrailingZeros()
                            .toPlainString();
                }
            }

            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());

            case FORMULA -> {
                // 수식 셀도 "수식 문자열"이 아니라 "계산 결과"를 넣어야 함
                yield switch (cell.getCachedFormulaResultType()) {
                    case STRING -> cell.getStringCellValue().trim();
                    case NUMERIC -> java.math.BigDecimal.valueOf(cell.getNumericCellValue())
                            .stripTrailingZeros()
                            .toPlainString();
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    default -> null;
                };
            }

            default -> null;
        };
    }

    @Transactional
    public int saveAll(List<Map<String, Object>> rows, int evalYear) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            KpiPersonal2025 dto = mapRowToDto(row, evalYear);
            count += kpiMapper.insert(dto);
        }
        return count;
    }

    // Map(kcol01~82) -> DTO 변환
    private KpiPersonal2025 mapRowToDto(Map<String, Object> row, int evalYear) {
        KpiPersonal2025 dto = new KpiPersonal2025();
        dto.setEvalYear(evalYear);

        // 🔹 Map 은 parseKpiSheet 에서 String 으로 넣었으므로 String 캐스팅
        dto.setHospitalName(str(row, "kcol01")); // 병원명
        dto.setEmpId(str(row, "kcol02")); // 사번
        dto.setTotalScore(bd(row, "kcol03")); // 총점

        // 병상가동률 블럭
        dto.setBedOcc2024Pct(bd(row, "kcol04"));
        dto.setBedOcc2025Pct(bd(row, "kcol05"));
        dto.setBedOccYoyPct(bd(row, "kcol06"));
        dto.setBedOccRemarks(str(row, "kcol07"));
        dto.setBedOcc2024Score(bd(row, "kcol08"));
        dto.setBedOccYoyScore(bd(row, "kcol09"));
        dto.setBedOccSubtotalA(bd(row, "kcol10"));

        // 일당진료비 블럭
        dto.setDayRev2024(intVal(row, "kcol11"));
        dto.setDayRev2025(intVal(row, "kcol12"));
        dto.setDayRevYoyPct(bd(row, "kcol13"));
        dto.setDayRevRemarks(str(row, "kcol14"));
        dto.setDayRevYoyScore(bd(row, "kcol15"));
        dto.setDayRevTargetScore(bd(row, "kcol16"));
        dto.setDayRevSubtotalB(bd(row, "kcol17"));

        // 매출 블럭
        dto.setSales2024(longVal(row, "kcol18"));
        dto.setSales2025(longVal(row, "kcol19"));
        dto.setSalesYoyPct(bd(row, "kcol20"));
        dto.setSalesYoyScore(bd(row, "kcol21"));
        dto.setSalesTargetScore(bd(row, "kcol22"));
        dto.setSalesSubtotalC(bd(row, "kcol23"));
        dto.setFinanceRemarks(str(row, "kcol24"));
        dto.setFinanceTotal(bd(row, "kcol25"));

        // 만족도
        dto.setGuardianSat5(bd(row, "kcol26"));
        dto.setPatientSat5(bd(row, "kcol27"));
        dto.setCsTotal10(bd(row, "kcol28"));

        // 위해사건
        dto.setIncidentRate2024(bd(row, "kcol29"));
        dto.setIncidentRate2025(bd(row, "kcol30"));
        dto.setIncidentDiffPt(bd(row, "kcol31"));
        dto.setIncidentCurrScore(bd(row, "kcol32"));
        dto.setIncidentDiffScore(bd(row, "kcol33"));
        dto.setIncidentTotal(bd(row, "kcol34"));
        dto.setIncidentRemarks(str(row, "kcol35"));

        // 홍보/참여(1점)
        dto.setPromoGoal2025(intVal(row, "kcol36"));
        dto.setPromoPersonalCount(intVal(row, "kcol37"));
        dto.setPromoDailyAvg(bd(row, "kcol38"));
        dto.setPromoScore1(bd(row, "kcol39"));

        // 입원/장례 연계
        dto.setLinkGoal(intVal(row, "kcol40"));
        dto.setLinkInpatientCnt(intVal(row, "kcol41"));
        dto.setLinkFuneralCnt(intVal(row, "kcol42"));
        dto.setLinkTotalCnt(intVal(row, "kcol43"));
        dto.setLinkPersonalScore6(bd(row, "kcol44"));
        dto.setLinkDeptRatePct(bd(row, "kcol45"));
        dto.setLinkDeptScore1(bd(row, "kcol46"));
        dto.setLinkTotalScore7(bd(row, "kcol47"));
        dto.setLinkRemarks(str(row, "kcol48"));

        // 참여 5점
        dto.setAct5Goal2025(intVal(row, "kcol49"));
        dto.setAct5PersonalCount(intVal(row, "kcol50"));
        dto.setAct5Score5(bd(row, "kcol51"));

        // QI 활동
        dto.setQiTopic(str(row, "kcol52"));
        dto.setQiRole(str(row, "kcol53"));
        dto.setQiRoleScore2(bd(row, "kcol54"));
        dto.setQiFinalOrg(str(row, "kcol55"));
        dto.setQiAward(str(row, "kcol56"));
        dto.setQiDeptAwardScore2(bd(row, "kcol57"));
        dto.setQiDeptPartScore1(bd(row, "kcol58"));
        dto.setQiTotalScore3(bd(row, "kcol59"));
        dto.setQiRemarks(str(row, "kcol60"));

        // 교육
        dto.setEduGoal(intVal(row, "kcol61"));
        dto.setEduPersonalRatePct(bd(row, "kcol62"));
        dto.setEduPersonalScore2(bd(row, "kcol63"));
        dto.setEduDeptRatePct(bd(row, "kcol64"));
        dto.setEduDeptScore1(bd(row, "kcol65"));
        dto.setEduTotalScore3(bd(row, "kcol66"));
        dto.setEduRemarks(str(row, "kcol67"));

        // 동호회/자원봉사
        dto.setClubGoal(intVal(row, "kcol68"));
        dto.setClubPersonalCount(intVal(row, "kcol69"));
        dto.setClubPersonalScore3(bd(row, "kcol70"));
        dto.setClubDeptRatePct(bd(row, "kcol71"));
        dto.setClubDeptScore1(bd(row, "kcol72"));
        dto.setClubDeptRemarks(str(row, "kcol73"));
        dto.setVolunteerScore4(bd(row, "kcol74"));

        // 독서토론
        dto.setBookGoal(intVal(row, "kcol75"));
        dto.setBookAttendCount(intVal(row, "kcol76"));
        dto.setBookAttendScore2(bd(row, "kcol77"));
        dto.setBookPresentCount(intVal(row, "kcol78"));
        dto.setBookPresentScore1(bd(row, "kcol79"));
        dto.setBookTotalScore3(bd(row, "kcol80"));
        dto.setBookRemarks(str(row, "kcol81"));

        // kcol82 는 여분/예비용으로 남겨두었거나, 추가항목이 있으면 dto 에 필드를 추가해 연결

        return dto;
    }

    // ───── 헬퍼 메서드들 ─────────────────────────────

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString().trim();
    }

    private java.math.BigDecimal bd(Map<String, Object> row, String key) {
        String s = str(row, key);
        if (s == null || s.isEmpty())
            return null;
        try {
            return new java.math.BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intVal(Map<String, Object> row, String key) {
        String s = str(row, key);
        if (s == null || s.isEmpty())
            return null;
        try {
            return Integer.valueOf(s.split("\\.")[0]); // 엑셀에서 123.0 형태 오는 것 방지
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longVal(Map<String, Object> row, String key) {
        String s = str(row, key);
        if (s == null || s.isEmpty())
            return null;
        try {
            return Long.valueOf(s.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}