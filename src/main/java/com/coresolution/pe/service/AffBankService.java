package com.coresolution.pe.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.mapper.AffEvaluationMapper;
import com.coresolution.pe.mapper.EvaluationMapper;
import com.coresolution.pe.mapper.UserMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffBankService {

    @Autowired
    private AffEvaluationMapper evaluationMapper;
    private static final Set<String> ALLOWED_TYPES = Set.of("AA", "AB", "AC", "AD", "AE"); // 필요 시 더 추가

    private static String normalizeType(String raw) {
        if (raw == null)
            return "";
        String t = raw.trim().toUpperCase();
        // 한글 매핑이 필요하면 여기에 추가
        switch (t) {
            case "객관식":
                return "AA";
            case "주관식":
                return "AB";
            default:
                return t;
        }
    }

    @Transactional
    public UploadResult importExcel(MultipartFile file, int year, String mode /* UPSERT | REPLACE */)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 없습니다.");
        }

        // REPLACE 모드면 해당 연도 전체 삭제 후 다시 채움
        if ("REPLACE".equalsIgnoreCase(mode)) {
            evaluationMapper.deleteByYear(year);
        }

        int inserted = 0, updated = 0, skipped = 0, rownum = 0;
        List<String> errors = new ArrayList<>();

        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            var sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet();
            DataFormatter fmt = new DataFormatter();

            // 데이터 시작: B3 (0-based row=2, col=1..3)
            int firstRow = 2;
            for (int r = firstRow; r <= sheet.getLastRowNum(); r++) {
                rownum = r + 1; // 사람 눈 기준
                var row = sheet.getRow(r);
                if (row == null) {
                    skipped++;
                    continue;
                }

                String d1 = fmt.formatCellValue(row.getCell(1)).trim(); // 문제(B)
                String d2raw = fmt.formatCellValue(row.getCell(2));
                String d2 = normalizeType(d2raw);
                String d3 = fmt.formatCellValue(row.getCell(3)).trim(); // 구분(D)
                // 빈 줄 스킵
                if (d1.isEmpty() && d2.isEmpty() && d3.isEmpty()) {
                    skipped++;
                    continue;
                }

                // 기본 검증
                if (d1.isEmpty()) {
                    errors.add("Row " + rownum + ": 문제(d1) 비어있음");
                    continue;
                }
                if (!ALLOWED_TYPES.contains(d2)) {
                    errors.add("Row " + rownum + ": 문제유형(d2)은 " + ALLOWED_TYPES + " 중 하나여야 함 (입력: " + d2raw + ")");
                    continue;
                }

                Evaluation q = new Evaluation();
                q.setD1(d1);
                q.setD2(d2);
                q.setD3(d3);
                q.setEval_year(year);

                // 유니크키가 있으면 upsert, 없으면 수동 경로
                try {
                    int n = evaluationMapper.upsert(q);
                    // MySQL의 INSERT ... ON DUPLICATE KEY UPDATE 는
                    // insert=1, update=2 반환이 일반적이나 드라이버/모드에 따라 1만 반환하는 경우도 있어요.
                    // 여기선 존재 여부를 다시 체크하기보다, 보수적으로 inserted/updated를 합산 처리.
                    if (n > 0) {
                        inserted++;
                    } // 대략 카운트
                } catch (Exception dupOrNoUk) {
                    // 유니크키가 없거나 기타 사유로 실패 → 수동 경로
                    Integer idx = evaluationMapper.findIdxByYearTypeAndText(year, d2, d1);
                    if (idx == null) {
                        evaluationMapper.insert(q);
                        inserted++;
                    } else {
                        q.setIdx(idx);
                        evaluationMapper.updateOnlyD3(q);
                        updated++;
                    }
                }
            }
        }

        return new UploadResult(inserted, updated, skipped, errors);
    }

    @Getter
    public static class UploadResult {
        private final int inserted;
        private final int updated;
        private final int skipped;
        private final List<String> errors;

        public UploadResult(int inserted, int updated, int skipped, List<String> errors) {
            this.inserted = inserted;
            this.updated = updated;
            this.skipped = skipped;
            this.errors = errors;
        }
    }

    /** ① 엑셀 파싱만 (세션에 담아 미리보기) */
    public ParseResult parseExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("업로드 파일이 없습니다.");

        List<Evaluation> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            var sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : wb.createSheet();
            DataFormatter fmt = new DataFormatter();

            // 데이터 시작: B3 (row index 2), B=1/C=2/D=3
            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                var row = sheet.getRow(r);
                if (row == null) {
                    skipped++;
                    continue;
                }

                String d1 = fmt.formatCellValue(row.getCell(1)).trim();
                String d2raw = fmt.formatCellValue(row.getCell(2));
                String d2 = normalizeType(d2raw);
                String d3 = fmt.formatCellValue(row.getCell(3)).trim();

                if (d1.isEmpty() && d2.isEmpty() && d3.isEmpty()) {
                    skipped++;
                    continue;
                }

                // 기본 검증
                List<String> rowErr = new ArrayList<>();
                if (d1.isEmpty())
                    rowErr.add("문제(d1) 비어있음");
                if (!ALLOWED_TYPES.contains(d2)) {
                    rowErr.add("문제유형(d2)은 " + ALLOWED_TYPES + " 중 하나여야 함 (입력: " + d2raw + ")");
                }

                Evaluation q = new Evaluation();
                q.setD1(d1);
                q.setD2(d2);
                q.setD3(d3);
                rows.add(q);

                if (!rowErr.isEmpty()) {
                    errors.add("Row " + (r + 1) + ": " + String.join(", ", rowErr));
                }
            }
        }
        return new ParseResult(rows, errors, skipped);
    }

    /** ② 확정 저장 (DB 반영) */
    @Transactional
    public SaveResult saveParsed(List<Evaluation> rows, int year, String mode /* UPSERT | REPLACE */) {
        if (rows == null || rows.isEmpty())
            return new SaveResult(0, 0, 0, List.of("저장할 데이터가 없습니다."));

        if ("REPLACE".equalsIgnoreCase(mode)) {
            evaluationMapper.deleteByYear(year);
        }

        int inserted = 0, updated = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        for (Evaluation q : rows) {
            // 파싱 단계에서 이미 1차 검증했지만, 이중 방어
            if (q.getD1() == null || q.getD1().isBlank() ||
                    q.getD2() == null || !(q.getD2().equals("AA") || q.getD2().equals("AB"))) {
                failed++;
                errors.add("유효하지 않은 레코드: " + q);
                continue;
            }
            q.setEval_year(year);

            try {
                // 유니크키( eval_year, d2, d1 )가 있으면 upsert로 깔끔하게 처리됨
                int n = evaluationMapper.upsert(q);
                if (n > 0)
                    inserted++; // 드라이버별 반환값 차이가 있어 insert/updated 구분이 애매하면 inserted로 합산 처리
            } catch (Exception e) {
                // 유니크키가 없거나 기타 예외 → 수동 경로
                Integer idx = evaluationMapper.findIdxByYearTypeAndText(year, q.getD2(), q.getD1());
                if (idx == null) {
                    try {
                        evaluationMapper.insert(q);
                        inserted++;
                    } catch (Exception ex) {
                        failed++;
                        errors.add("INSERT 실패: " + q + " / " + ex.getMessage());
                    }
                } else {
                    q.setIdx(idx);
                    try {
                        evaluationMapper.updateOnlyD3(q);
                        updated++;
                    } catch (Exception ex) {
                        failed++;
                        errors.add("UPDATE 실패: " + q + " / " + ex.getMessage());
                    }
                }
            }
        }
        return new SaveResult(inserted, updated, failed, errors);
    }

    // 결과 DTO들
    @Getter
    @AllArgsConstructor
    public static class ParseResult {
        private final List<Evaluation> rows;
        private final List<String> errors;
        private final int skipped;
    }

    @Getter
    @AllArgsConstructor
    public static class SaveResult {
        private final int inserted;
        private final int updated;
        private final int failed;
        private final List<String> errors;
    }

}
