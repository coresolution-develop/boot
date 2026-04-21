package com.coresolution.pe.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UploadParseResult;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.entity.UserrolePE;
import com.coresolution.pe.service.BankService;
import com.coresolution.pe.service.KpiInfoExcelService;
import com.coresolution.pe.service.KpiInfoGeneralExcelService;
import com.coresolution.pe.service.PeService;
import com.coresolution.pe.service.PoiMakeExcelService;
import com.coresolution.pe.service.TableInitService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/excel")
@RequiredArgsConstructor
public class ExcelContoller {

    private final PoiMakeExcelService poiService;
    private final PeService peService;
    private final KpiInfoExcelService kpiInfoExcelService;
    private final KpiInfoGeneralExcelService kpiInfoGeneralExcelService;
    private final TableInitService tableInitService;
    @Autowired
    private BankService bankService;

    @Value("${app.admin.sub-code}")
    private String adminSubCode;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    // private final PESideService pesService;

    // ───────────────────────────────────────────────────────────
    // 1) 직원 템플릿 다운로드 (GET /excel/downloadTemplate)
    // ───────────────────────────────────────────────────────────
    @GetMapping("/downloadTemplate")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // ── 공통 스타일 헬퍼 ──────────────────────────────────
            // 안내행 스타일
            CellStyle noticeStyle = wb.createCellStyle();
            Font noticeFont = wb.createFont();
            noticeFont.setBold(true);
            noticeFont.setColor(IndexedColors.DARK_RED.getIndex());
            noticeFont.setFontHeightInPoints((short) 10);
            noticeStyle.setFont(noticeFont);
            noticeStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            noticeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 헤더 스타일 (짙은 남색 배경 + 흰 굵은 글씨)
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            setBorder(headerStyle);

            // 선택 헤더 스타일 (회색 배경)
            CellStyle optHeaderStyle = wb.createCellStyle();
            Font optFont = wb.createFont();
            optFont.setBold(true);
            optFont.setColor(IndexedColors.WHITE.getIndex());
            optFont.setFontHeightInPoints((short) 10);
            optHeaderStyle.setFont(optFont);
            optHeaderStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            optHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            optHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            optHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            optHeaderStyle.setWrapText(true);
            setBorder(optHeaderStyle);

            // 샘플 데이터 스타일 (연한 하늘색 배경)
            CellStyle sampleStyle = wb.createCellStyle();
            sampleStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            sampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sampleStyle.setAlignment(HorizontalAlignment.CENTER);
            sampleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(sampleStyle);

            // 0/1 전용 샘플 스타일 (중앙정렬)
            CellStyle boolSampleStyle = wb.createCellStyle();
            boolSampleStyle.cloneStyleFrom(sampleStyle);
            boolSampleStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFSheet sheet = wb.createSheet("직원명부");

            // ── 1행: 안내 문구 ──────────────────────────────────
            XSSFRow notice = sheet.createRow(0);
            notice.setHeightInPoints(20);
            Cell noticeCell = notice.createCell(1);
            noticeCell.setCellValue(
                "※ B3 셀부터 데이터를 입력하세요. 1·2행은 삭제하지 마세요. " +
                "파란 샘플 행(3·4행)은 작성 후 삭제하고 업로드하세요.");
            noticeCell.setCellStyle(noticeStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 1, 16));

            // ── 2행: 컬럼 헤더 ─────────────────────────────────
            String[] headers = {
                "기관명", "소속기관", "부서명", "사원번호", "직책", "이름",
                "입사일\n(yyyy-MM-dd)", "퇴사일\n(없으면 공백)",
                "핸드폰번호",
                "경혁팀장\n(1=해당\n0=없음)",
                "경혁팀원\n(1=해당\n0=없음)",
                "부서장\n(1=해당\n0=없음)",
                "1인부서\n(1=해당\n0=없음)",
                "의료리더\n(1=해당\n0=없음)",
                "평가제외\n(1=제외\n0=포함)",
                "[선택]\n부서코드"
            };
            XSSFRow headerRow = sheet.createRow(1);
            headerRow.setHeightInPoints(48);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i + 1);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(i == 15 ? optHeaderStyle : headerStyle);
            }

            // ── 샘플 데이터 2건 ────────────────────────────────
            Object[][] samples = {
                {"효사랑전주요양병원", "사랑모아",   "간호부",   "100001", "간호사", "홍길동",
                 "2022-03-01", "",           "010-1234-5678", 1, 1, 0, 0, 0, 0, "N01"},
                {"효사랑가족요양병원", "효사랑가족", "원무부",   "100002", "원무팀장", "김철수",
                 "2020-07-15", "2025-12-31", "010-9876-5432", 0, 0, 1, 0, 0, 0, ""}
            };
            for (int r = 0; r < samples.length; r++) {
                XSSFRow row = sheet.createRow(r + 2);
                row.setHeightInPoints(18);
                for (int c = 0; c < samples[r].length; c++) {
                    Cell cell = row.createCell(c + 1);
                    Object val = samples[r][c];
                    if (val instanceof Integer) {
                        cell.setCellValue((Integer) val);
                        cell.setCellStyle(boolSampleStyle);
                    } else {
                        cell.setCellValue(val.toString());
                        cell.setCellStyle(sampleStyle);
                    }
                }
            }

            // ── 0/1 컬럼에 드롭다운 데이터 검증 (K~P열 = index 10~15) ──
            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            DataValidationConstraint dvConstraint =
                dvHelper.createExplicitListConstraint(new String[]{"0", "1"});
            org.apache.poi.ss.util.CellRangeAddressList boolRange =
                new org.apache.poi.ss.util.CellRangeAddressList(2, 200, 10, 15);
            DataValidation dv = dvHelper.createValidation(dvConstraint, boolRange);
            dv.setShowErrorBox(true);
            dv.createErrorBox("입력 오류", "0 또는 1만 입력 가능합니다.");
            sheet.addValidationData(dv);

            // ── 열 너비 ────────────────────────────────────────
            int[] colWidths = {3000, 3000, 3500, 2800, 2800, 2500,
                               3500, 3500, 3200,
                               2200, 2200, 2200, 2200, 2200, 2200,
                               3500};
            for (int i = 0; i < colWidths.length; i++) {
                sheet.setColumnWidth(i + 1, colWidths[i]);
            }

            // ── 틀 고정 (3행부터 스크롤) ──────────────────────
            sheet.createFreezePane(0, 2);

            // ── 출력 ──────────────────────────────────────────
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            byte[] content = baos.toByteArray();

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            httpHeaders.setContentDisposition(ContentDisposition.attachment()
                .filename("직원명부_템플릿.xlsx", StandardCharsets.UTF_8).build());
            httpHeaders.setContentLength(content.length);
            return new ResponseEntity<>(content, httpHeaders, HttpStatus.OK);
        }
    }

    /** 셀 스타일에 얇은 테두리 4방향 적용 */
    private static void setBorder(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    // ───────────────────────────────────────────────────────────
    // 2) 업로드(Excel→미리보기) (POST /excel/upload)
    // ───────────────────────────────────────────────────────────
    @PostMapping("/upload")
    public String uploadExcel(@RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            Model model,
            HttpSession session,
            RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "파일을 선택해 주세요.");
            return "redirect:/excel/uploadStatus?year=" + year;
        }
        try (InputStream is = file.getInputStream();
                Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);

            // 파싱 + 검증 통합 실행
            UploadParseResult parsed = poiService.parseUsersWithValidation(sheet, year);

            session.setAttribute("usersList",           parsed.getUsers());
            session.setAttribute("roleList",            parsed.getRoles());
            session.setAttribute("deptsToAutoRegister", parsed.getDeptsToAutoRegister());
            model.addAttribute("userList",    parsed.getUsers());
            model.addAttribute("roleList",    parsed.getRoles());
            model.addAttribute("parseErrors", parsed.getErrors());
            model.addAttribute("hasBlockingErrors",   parsed.hasBlockingErrors());
            model.addAttribute("blockingCount",       parsed.blockingCount());
            model.addAttribute("warningCount",        parsed.warningCount());
            model.addAttribute("autoRegisterDepts",   parsed.getDeptsToAutoRegister());
            model.addAttribute("year", year);
            return "Excel/preview";

        } catch (Exception e) {
            ra.addFlashAttribute("error", "업로드 실패: " + e.getMessage());
            return "redirect:/excel/uploadStatus?year=" + year;
        }
    }

    // ───────────────────────────────────────────────────────────
    // 3) 업로드 확정(DB 저장) (POST /excel/confirmUpload)
    // ───────────────────────────────────────────────────────────
    @PostMapping("/confirmUpload")
    public String confirmUpload(@RequestParam("year") int year,
            HttpSession session,
            Model model) {
        @SuppressWarnings("unchecked")
        List<UserPE> users = (List<UserPE>) session.getAttribute("usersList");
        @SuppressWarnings("unchecked")
        List<UserrolePE> roles = (List<UserrolePE>) session.getAttribute("roleList");
        if (users == null || roles == null) {
            model.addAttribute("error", "업로드된 데이터가 없습니다.");
            return "Excel/uploadStatus";
        }
        // 미등록 부서 포함 시 차단
        List<String> unknownDepts = users.stream()
                .filter(u -> u.getSubCode() == null || u.getSubCode().isEmpty())
                .map(UserPE::getId)
                .collect(java.util.stream.Collectors.toList());
        if (!unknownDepts.isEmpty()) {
            model.addAttribute("message",
                    "미등록 부서가 포함된 직원이 있습니다 (사원번호: "
                    + String.join(", ", unknownDepts.subList(0, Math.min(5, unknownDepts.size())))
                    + (unknownDepts.size() > 5 ? " 외 " + (unknownDepts.size()-5) + "명" : "")
                    + "). 먼저 부서를 등록해 주세요.");
            model.addAttribute("year", year);
            return "Excel/uploadStatus";
        }
        tableInitService.ensurePeTablesExist(year);

        // 직원 파일에 부서코드가 명시되어 자동등록이 필요한 부서 처리
        @SuppressWarnings("unchecked")
        java.util.Map<String,String> deptsToReg =
            (java.util.Map<String,String>) session.getAttribute("deptsToAutoRegister");
        if (deptsToReg != null && !deptsToReg.isEmpty()) {
            for (java.util.Map.Entry<String,String> e : deptsToReg.entrySet()) {
                if (peService.countByCodeAndYear(e.getKey(), year) == 0) {
                    SubManagement sm = new SubManagement();
                    sm.setSubCode(e.getKey());
                    sm.setSubName(e.getValue());
                    sm.setEvalYear(year);
                    peService.subinsert(sm);
                }
            }
        }

        poiService.saveUsersAndRoles(users, roles, year);
        model.addAttribute("message", "DB 저장 성공!");
        session.removeAttribute("usersList");
        session.removeAttribute("roleList");
        model.addAttribute("year", year);
        return "Excel/uploadStatus";
    }

    // ───────────────────────────────────────────────────────────
    // 4) 상태 페이지 (GET /excel/uploadStatus)
    // ───────────────────────────────────────────────────────────
    @GetMapping("/uploadStatus")
    public String uploadStatus(@RequestParam("year") int year, Model model) {
        // RedirectAttributes 로 넘어온 success/error 메시지 렌더링
        model.addAttribute("year", year);
        return "Excel/uploadStatus";
    }

    // ───────────────────────────────────────────────────────────
    /** 1) 부서 엑셀 템플릿 다운로드 */
    @GetMapping("/subtemplate")
    public void subdownloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
            "attachment; filename*=UTF-8''%EB%B6%80%EC%84%9C_%ED%85%9C%ED%94%8C%EB%A6%BF.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // 안내 스타일
            CellStyle noticeStyle = wb.createCellStyle();
            Font noticeFont = wb.createFont();
            noticeFont.setBold(true);
            noticeFont.setColor(IndexedColors.DARK_RED.getIndex());
            noticeFont.setFontHeightInPoints((short) 10);
            noticeStyle.setFont(noticeFont);
            noticeStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            noticeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 헤더 스타일
            CellStyle headerStyle = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true);
            hf.setColor(IndexedColors.WHITE.getIndex());
            hf.setFontHeightInPoints((short) 11);
            headerStyle.setFont(hf);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headerStyle);

            // 샘플 스타일
            CellStyle sampleStyle = wb.createCellStyle();
            sampleStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            sampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sampleStyle.setAlignment(HorizontalAlignment.CENTER);
            sampleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(sampleStyle);

            XSSFSheet sheet = wb.createSheet("부서목록");

            // 1행: 안내
            XSSFRow notice = sheet.createRow(0);
            notice.setHeightInPoints(20);
            Cell nc = notice.createCell(1);
            nc.setCellValue("※ B3 셀부터 데이터를 입력하세요. 파란 샘플 행(3·4행)은 삭제 후 업로드하세요.");
            nc.setCellStyle(noticeStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 1, 2));

            // 2행: 헤더
            XSSFRow headerRow = sheet.createRow(1);
            headerRow.setHeightInPoints(22);
            String[] cols = {"부서명", "부서코드"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = headerRow.createCell(i + 1);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            // 3·4행: 샘플
            String[][] samples = {{"간호부", "N01"}, {"원무부", "N02"}};
            for (int r = 0; r < samples.length; r++) {
                XSSFRow row = sheet.createRow(r + 2);
                row.setHeightInPoints(18);
                for (int c = 0; c < 2; c++) {
                    Cell cell = row.createCell(c + 1);
                    cell.setCellValue(samples[r][c]);
                    cell.setCellStyle(sampleStyle);
                }
            }

            sheet.setColumnWidth(1, 5000);
            sheet.setColumnWidth(2, 4000);
            sheet.createFreezePane(0, 2);

            wb.write(response.getOutputStream());
        }
    }

    /** 2) 업로드 & 미리보기 */
    @PostMapping("/subupload")
    public String subuploadDepartments(
            MultipartFile file,
            int year,
            HttpSession session,
            Model model) throws Exception {
        if (file.isEmpty()) {
            model.addAttribute("message", "파일을 선택해주세요.");
            return "Excel/subuploadStatus";
        }

        try (InputStream is = file.getInputStream();
                var workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            // 시트 파싱
            List<SubManagement> deptList = poiService.parseDepartments(sheet, year);

            // ➡️ 여기서 세션에 담아 줍니다.
            session.setAttribute("subList", deptList);
            // 모델에 담아서 Thymeleaf preview 페이지에 띄워줄 수 있습니다
            model.addAttribute("subList", deptList);
            model.addAttribute("year", year);
            return "Excel/departmentPreview"; // 미리보기용 뷰
        }
    }

    /** 3) 최종 DB 반영 */
    @PostMapping("/subconfirm")
    public String subconfirmUpload(
            HttpSession session,
            Model model,
            @RequestParam("year") Integer year) {
        @SuppressWarnings("unchecked")
        List<SubManagement> subList = (List<SubManagement>) session.getAttribute("subList");

        if (subList == null || subList.isEmpty()) {
            model.addAttribute("message", "업로드된 부서 목록이 없습니다.");
            return "Excel/subuploadStatus";
        }

        try {
            // 👉 리스트와 연도를 함께 넘깁니다.
            poiService.saveDepartments(subList, year, null); // 슈퍼 어드민: institution_id = null
            model.addAttribute("message", "부서 정보를 성공적으로 저장했습니다.");
            // 세션에 담긴 리스트는 비워 줘도 좋습니다.
            session.removeAttribute("subList");
        } catch (Exception e) {
            model.addAttribute("message", "저장 중 오류 발생: " + e.getMessage());
        }
        return "Excel/subuploadStatus";
    }

    /** 4‑2) 잘못 올렸을 때 데이터 초기화 (관리자 제외) */
    @PostMapping("/reset")
    public String resetUploadedData(@RequestParam("year") int year,
            RedirectAttributes ra) {
        try {
            poiService.resetUsersAndRolesExceptCurrentAdmin(year);
            ra.addFlashAttribute("message", "데이터를 초기화했습니다. (관리자 계정 제외)");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "초기화 중 오류 발생: " + e.getMessage());
        }
        return "redirect:/excel/uploadStatus?year=" + year;
    }

    /**
     * 부서 업로드 초기화 (관리자 부서만 남기고 삭제)
     */
    @PostMapping("/subManagementreset")
    public String resetSubManagement(@RequestParam("year") int year,
            RedirectAttributes ra) {
        poiService.resetYearlyDepartments(year, adminSubCode);
        ra.addFlashAttribute("message", "부서 데이터가 초기화되었습니다.");
        return "redirect:/pe/admin/subManagement?year=" + year;
    }

    // ───────────────────────────────────────────────────────────
    /** 1) 문제은행 엑셀 템플릿 다운로드 */
    @GetMapping("/evaltemplate")
    public void evaldownloadTemplate(HttpServletResponse response) throws IOException {
        String fileName = "department_template.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("문제은행 템플릿");
            // 1행 안내
            var row0 = sheet.createRow(0);
            row0.createCell(1).setCellValue("샘플을 참고, B3 행부터 데이터를 작성해주세요.");

            // 2행 헤더
            var header = sheet.createRow(1);
            header.createCell(1).setCellValue("문제");
            header.createCell(2).setCellValue("문제유형");
            header.createCell(3).setCellValue("구분");

            workbook.write(response.getOutputStream());
        }
    }

    /** 2) 문제은행 엑셀 업로드 */
    @PostMapping("/evalupload")
    public String uploadEvalTemplate(@RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam(name = "mode", defaultValue = "UPSERT") String mode,
            RedirectAttributes ra) {
        try {
            var result = bankService.importExcel(file, year, mode);
            StringBuilder msg = new StringBuilder();
            msg.append("업로드 완료 - 추가: ").append(result.getInserted())
                    .append("건, 수정: ").append(result.getUpdated())
                    .append("건, 건너뜀: ").append(result.getSkipped()).append("건");
            if (!result.getErrors().isEmpty()) {
                msg.append(" / 오류 ").append(result.getErrors().size()).append("건 (상세는 로그)");
            }
            // 필요하면 에러를 flash로도 넘기세요.
            ra.addFlashAttribute("message", msg.toString());
            ra.addFlashAttribute("errors", result.getErrors());
        } catch (Exception e) {
            ra.addFlashAttribute("message", "업로드 실패: " + e.getMessage());
        }
        return "redirect:/pe/admin/evaluation?year=" + year;
    }

    // 2-1) 미리보기 (엑셀 업로드 → 파싱만)
    @PostMapping("/evalpreview")
    public String preview(@RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam(name = "mode", defaultValue = "UPSERT") String mode,
            HttpSession session,
            Model model,
            RedirectAttributes ra) {
        try {
            var result = bankService.parseExcel(file);
            model.addAttribute("year", year);
            model.addAttribute("mode", mode);
            model.addAttribute("rows", result.getRows());
            model.addAttribute("parseErrors", result.getErrors());
            model.addAttribute("skipped", result.getSkipped());

            // 세션에 저장 (확정 시 사용)
            session.setAttribute("evalRows", result.getRows());
            session.setAttribute("evalMode", mode);
            session.setAttribute("evalYear", year);

            return "Excel/evalPreview"; // 미리보기 페이지(타임리프)
        } catch (Exception e) {
            ra.addFlashAttribute("message", "파싱 실패: " + e.getMessage());
            return "redirect:/pe/admin/evaluation?year=" + year;
        }
    }

    // 2-2) 업로드 확정(DB 저장)
    @PostMapping("/evalconfirmUpload")
    public String confirmUpload(HttpSession session, RedirectAttributes ra) {
        @SuppressWarnings("unchecked")
        List<Evaluation> rows = (List<Evaluation>) session.getAttribute("evalRows");
        String mode = (String) session.getAttribute("evalMode");
        Integer year = (Integer) session.getAttribute("evalYear");

        if (rows == null || year == null) {
            ra.addFlashAttribute("error", "업로드된 데이터가 없습니다.");
            return "redirect:/pe/admin/evaltemplate/uploadStatus?year=" + (year == null ? 0 : year);
        }

        var result = bankService.saveParsed(rows, year, mode == null ? "UPSERT" : mode);

        // 세션 정리
        session.removeAttribute("evalRows");
        session.removeAttribute("evalMode");
        session.removeAttribute("evalYear");

        ra.addFlashAttribute("message",
                "저장 완료 - 추가: " + result.getInserted()
                        + " / 수정: " + result.getUpdated()
                        + " / 실패: " + result.getFailed());

        if (!result.getErrors().isEmpty()) {
            ra.addFlashAttribute("errors", result.getErrors());
        }
        return "redirect:/pe/admin/evaltemplate/uploadStatus?year=" + year;
    }

    // 2-3) 상태 페이지
    @GetMapping("/evaluploadStatus")
    public String evaluploadStatus(@RequestParam("year") int year, Model model) {
        model.addAttribute("year", year);
        return "Excel/EvaluploadStatus";
    }

    @GetMapping("/kpi/template")
    public void downloadKpiTemplate(HttpServletResponse response) throws IOException {
        String fileName = "kpi_info_" + currentEvalYear + "_template.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("KPI_" + currentEvalYear);

            // 1행 안내
            var row0 = sheet.createRow(0);
            row0.createCell(1).setCellValue("샘플을 참고, B3 행부터 데이터를 작성해주세요.");

            // 2행 컬럼 헤더
            var header = sheet.createRow(1);
            for (int c = 1; c <= 82; c++) {
                header.createCell(c).setCellValue(String.format("kcol%02d", c));
            }

            workbook.write(response.getOutputStream());
        }
    }

    @PostMapping("/kpi/upload")
    public String uploadKpiExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year, // 🔹 추가
            HttpSession session,
            Model model,
            RedirectAttributes ra) {

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "파일을 선택해 주세요.");
            return "redirect:/excel/kpi/uploadStatus";
        }

        try (InputStream is = file.getInputStream();
                Workbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            List<Map<String, Object>> rows = kpiInfoExcelService.parseKpiSheet(sheet);

            session.setAttribute("kpiRows", rows);
            session.setAttribute("kpiYear", year); // 🔹 업로드한 연도도 세션에 저장

            model.addAttribute("rows", rows);
            model.addAttribute("rowCount", rows.size());
            model.addAttribute("year", year);

            return "Excel/kpiPreview";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "업로드 실패: " + e.getMessage());
            return "redirect:/excel/kpi/uploadStatus";
        }
    }

    @PostMapping("/kpi/confirm")
    public String confirmKpiUpload(
            HttpSession session,
            RedirectAttributes ra) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) session.getAttribute("kpiRows");
        Integer year = (Integer) session.getAttribute("kpiYear"); // 🔹 추가

        if (rows == null || rows.isEmpty()) {
            ra.addFlashAttribute("error", "업로드된 KPI 데이터가 없습니다.");
            return "redirect:/excel/kpi/uploadStatus";
        }

        if (year == null) {
            ra.addFlashAttribute("error", "평가연도 정보가 없습니다. 다시 업로드해 주세요.");
            return "redirect:/excel/kpi/uploadStatus";
        }

        try {
            int inserted = kpiInfoExcelService.saveAll(rows, year); // 🔹 year 같이 전달
            ra.addFlashAttribute("message", "KPI 정보 저장 완료 (" + inserted + "건)");
            session.removeAttribute("kpiRows");
            session.removeAttribute("kpiYear");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "저장 중 오류: " + e.getMessage());
        }

        return "redirect:/excel/kpi/uploadStatus";
    }

    @GetMapping("/kpi/uploadStatus")
    public String kpiUploadStatus(Model model) {
        // flash attribute의 message / error 를 출력하는 간단한 페이지
        return "Excel/kpiUploadStatus";
    }

    @GetMapping("/kpi/general/template")
    public void downloadKpiGeneralTemplate(HttpServletResponse response) throws IOException {
        String fileName = "kpi_info_general_" + currentEvalYear + "_template.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("KPI_GENERAL_" + currentEvalYear);

            // 1행 안내
            var row0 = sheet.createRow(0);
            row0.createCell(1).setCellValue("샘플을 참고, B3 행부터 데이터를 작성해주세요. (사번이 PK)");

            // 2행 헤더 (B열부터)
            var header = sheet.createRow(1);
            String[] headers = {
                    "소속기관명", // kcol01
                    "사번", // kcol02
                    "총점", // kcol03
                    "개인별 신환 입원 연계 건수", // kcol04
                    "개인별 장례 연계 건수", // kcol05
                    "개인별 입원+장례 연계건수 (㉠+㉡)", // kcol06
                    "㉮ 개인지표 (배점:20점)", // kcol07
                    "100점 만점 환산 점수 (홍보)", // kcol08
                    "개인별 Remarks (홍보)", // kcol09
                    "개인별 참여 횟수 (봉사)", // kcol10
                    "개인지표 (배점:15점) (봉사)", // kcol11
                    "100점 만점 환산 점수 (봉사)", // kcol12
                    "개인별 Remarks (봉사)", // kcol13
                    "개인별 교육 이수율(%)", // kcol14
                    "개인지표 (배점:15점) (교육)", // kcol15
                    "100점 만점 환산 점수 (교육)", // kcol16
                    "개인별 Remarks (교육)" // kcol17
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i + 1).setCellValue(headers[i]);
            }

            workbook.write(response.getOutputStream());
        }
    }

    @PostMapping("/kpi/general/upload")
    public String uploadKpiGeneralExcel(
            @RequestParam("file") MultipartFile file,
            HttpSession session,
            Model model,
            RedirectAttributes ra) {

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "파일을 선택해 주세요.");
            return "redirect:/excel/kpi/general/uploadStatus";
        }

        try (InputStream is = file.getInputStream();
                Workbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            List<Map<String, Object>> rows = kpiInfoGeneralExcelService.parseGeneralSheet(sheet);

            session.setAttribute("kpiGeneralRows", rows);
            model.addAttribute("rows", rows);
            model.addAttribute("rowCount", rows.size());

            return "Excel/kpiGeneralPreview";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "업로드 실패: " + e.getMessage());
            return "redirect:/excel/kpi/general/uploadStatus";
        }
    }

    @PostMapping("/kpi/general/confirm")
    public String confirmKpiGeneralUpload(
            HttpSession session,
            RedirectAttributes ra) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) session.getAttribute("kpiGeneralRows");

        if (rows == null || rows.isEmpty()) {
            ra.addFlashAttribute("error", "업로드된 일반 KPI 데이터가 없습니다.");
            return "redirect:/excel/kpi/general/uploadStatus";
        }

        try {
            int affected = kpiInfoGeneralExcelService.saveAll(rows);
            ra.addFlashAttribute("message", "일반 KPI 정보 저장 완료 (" + affected + "건)");
            session.removeAttribute("kpiGeneralRows");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "저장 중 오류: " + e.getMessage());
        }

        return "redirect:/excel/kpi/general/uploadStatus";
    }
}
