package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.mapper.AffKpiInfoGeneral2025Mapper;
import com.coresolution.pe.mapper.KpiInfoGeneral2025Mapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffKpiInfoGeneralExcelService {

    private final AffKpiInfoGeneral2025Mapper generalMapper;

    /**
     * 일반 직원용 KPI 엑셀 파싱
     * B3 셀부터 데이터 시작, B열~R열까지 kcol01~kcol17 매핑
     */
    public List<Map<String, Object>> parseGeneralSheet(Sheet sheet) {
        List<Map<String, Object>> list = new ArrayList<>();
        int firstRow = 2; // 0-based index: 2 == 엑셀 3행
        int lastRow = sheet.getLastRowNum();

        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;

            Map<String, Object> map = new HashMap<>();
            boolean allEmpty = true;

            for (int i = 0; i < 17; i++) {
                int colIndex = i + 1; // B열부터
                Cell cell = row.getCell(colIndex);
                String key = String.format("kcol%02d", i + 1); // kcol01 ~ kcol17

                String value = getStringCell(cell);
                if (value != null && !value.isBlank()) {
                    allEmpty = false;
                    map.put(key, value);
                } else {
                    map.put(key, null);
                }
            }

            if (!allEmpty) {
                list.add(map);
            }
        }
        return list;
    }

    private String getStringCell(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    @Transactional
    public int saveAll(List<Map<String, Object>> rows) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            count += generalMapper.upsertRow(row);
        }
        return count;
    }
}