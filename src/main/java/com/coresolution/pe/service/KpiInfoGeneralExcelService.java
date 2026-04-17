package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.mapper.KpiInfoGeneral2025Mapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KpiInfoGeneralExcelService {

    private final KpiInfoGeneral2025Mapper generalMapper;

    // ✅ 엑셀 표시값 그대로 가져오기(과학표기/소수점 등 안전)
    private static final DataFormatter DF = new DataFormatter();

    /**
     * 일반 직원용 KPI 엑셀 파싱
     * B3 셀부터 데이터 시작, B열~R열까지 kcol01~kcol17 매핑
     */
    public List<Map<String, Object>> parseGeneralSheet(Sheet sheet) {
        List<Map<String, Object>> list = new ArrayList<>();
        int firstRow = 2; // 0-based index: 2 == 엑셀 3행
        int lastRow = sheet.getLastRowNum();

        // ✅ 수식 평가기(수식 셀의 "계산된 값"을 문자열로 받기 위해)
        FormulaEvaluator evaluator = sheet.getWorkbook()
                .getCreationHelper()
                .createFormulaEvaluator();

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

                String value = getStringCell(cell, evaluator);

                if ("kcol02".equals(key) && value != null) {
                    value = value.trim().replaceAll("\\.0+$", "");
                }

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

    private String getStringCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null)
            return null;
        // ✅ 엑셀에 "보이는 값" 기준으로 문자열 변환(수식도 계산값 반영)
        String v = DF.formatCellValue(cell, evaluator);
        if (v == null)
            return null;

        v = v.trim();
        return v.isEmpty() ? null : v;
    };

    @Transactional
    public int saveAll(List<Map<String, Object>> rows) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            // ✅ 저장 직전에도 한번 더 방어(혹시 Map에 다른 타입이 섞일 경우 대비)
            Object idObj = row.get("kcol02");
            if (idObj != null) {
                String id = idObj.toString().trim().replaceAll("\\.0+$", "");
                row.put("kcol02", id.isEmpty() ? null : id);
            }

            count += generalMapper.upsertRow(row);
        }
        return count;
    }
}