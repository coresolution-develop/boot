package com.coresolution.pe.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.entity.Evaluation;
import com.coresolution.pe.mapper.EvaluationMapper;
import com.coresolution.pe.security.InstitutionAdminContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기관 관리자 — 문제은행(AA/AB) 업로드 전용 컨트롤러.
 *
 * <p>Excel 형식 (AA 또는 AB 공통)</p>
 * <pre>
 * 행0 (헤더): 그룹 | 문항
 * 행1~:      섬김 | 문항텍스트
 *            배움 | ...
 *            키움 | ...
 *            나눔 | ...
 *            목표관리 | ...
 *            주관식 | 주관식 문항
 * </pre>
 */
@Slf4j
@Controller
@RequestMapping("/pe/inst-admin/evaluation")
@PreAuthorize("hasRole('INST_ADMIN')")
@RequiredArgsConstructor
public class InstAdminEvalController {

    private final EvaluationMapper evaluationMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    private static final Set<String> VALID_GROUPS =
            Set.of("섬김", "배움", "키움", "나눔", "목표관리", "주관식");
    private static final String SESSION_KEY = "eval_preview";

    // ── 템플릿 Excel 다운로드 ───────────────────────────────────────

    @GetMapping("/template")
    public void downloadTemplate(@RequestParam String type,
                                 HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"question_template_" + type + ".xlsx\"");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet(type + " 문항");

            // 헤더
            Row hdr = sh.createRow(0);
            hdr.createCell(0).setCellValue("그룹");
            hdr.createCell(1).setCellValue("문항");

            // 샘플 데이터
            String[][] samples = type.equals("AA")
                ? new String[][]{
                    {"섬김",  "섬기는 자세로 업무를 수행하고 있다."},
                    {"섬김",  "상대방을 먼저 배려하는 태도를 보인다."},
                    {"배움",  "자기 계발을 위해 꾸준히 노력한다."},
                    {"배움",  "새로운 지식을 습득하여 업무에 적용한다."},
                    {"키움",  "구성원의 성장을 위해 지원한다."},
                    {"키움",  "주변 동료에게 긍정적인 영향을 준다."},
                    {"나눔",  "조직 내 소통과 협력에 적극 참여한다."},
                    {"나눔",  "정보와 경험을 동료와 공유한다."},
                    {"목표관리", "목표 대비 성과를 달성한다."},
                    {"목표관리", "업무 계획을 수립하고 실행한다."},
                    {"주관식", "이 직원에 대한 종합 의견을 자유롭게 작성해 주세요."},
                  }
                : new String[][]{
                    {"섬김",  "섬기는 자세로 업무를 수행하고 있다."},
                    {"섬김",  "상대방을 먼저 배려하는 태도를 보인다."},
                    {"섬김",  "타인을 향한 언어와 행동이 배려 깊다."},
                    {"섬김",  "고객 및 내부 구성원 서비스에 충실하다."},
                    {"배움",  "자기 계발을 위해 꾸준히 노력한다."},
                    {"배움",  "새로운 지식을 습득하여 업무에 적용한다."},
                    {"배움",  "교육이나 연수에 적극 참여한다."},
                    {"배움",  "변화에 유연하게 적응한다."},
                    {"키움",  "구성원의 성장을 위해 지원한다."},
                    {"키움",  "주변 동료에게 긍정적인 영향을 준다."},
                    {"키움",  "팀원의 역량 향상에 기여한다."},
                    {"키움",  "멘토링이나 코칭 역할을 수행한다."},
                    {"나눔",  "조직 내 소통과 협력에 적극 참여한다."},
                    {"나눔",  "정보와 경험을 동료와 공유한다."},
                    {"나눔",  "팀 목표 달성을 위해 헌신한다."},
                    {"나눔",  "갈등 상황에서 중재 역할을 한다."},
                    {"목표관리", "목표 대비 성과를 달성한다."},
                    {"목표관리", "업무 계획을 수립하고 실행한다."},
                    {"목표관리", "우선순위를 명확히 파악하고 처리한다."},
                    {"목표관리", "성과 관리를 위해 지속적으로 노력한다."},
                    {"주관식", "이 직원에 대한 종합 의견을 자유롭게 작성해 주세요."},
                  };

            for (int i = 0; i < samples.length; i++) {
                Row row = sh.createRow(i + 1);
                row.createCell(0).setCellValue(samples[i][0]);
                row.createCell(1).setCellValue(samples[i][1]);
            }
            sh.setColumnWidth(0, 14 * 256);
            sh.setColumnWidth(1, 60 * 256);
            wb.write(response.getOutputStream());
        }
    }

    // ── 파일 업로드 → 파싱 → 세션 저장 ───────────────────────────

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam String type,
                         @RequestParam(required = false) String year,
                         HttpServletRequest request,
                         RedirectAttributes ra) {
        if (year == null || !year.matches("\\d{4}")) year = String.valueOf(currentEvalYear);
        if (!"AA".equals(type) && !"AB".equals(type)) {
            ra.addFlashAttribute("error", "올바른 문항 유형을 선택하세요 (AA 또는 AB).");
            return "redirect:/pe/inst-admin/evaluation?year=" + year;
        }
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("error", "파일을 선택하세요.");
            return "redirect:/pe/inst-admin/evaluation?year=" + year;
        }

        List<String> errors = new ArrayList<>();
        List<Evaluation> parsed = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int yearInt = Integer.parseInt(year);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // 헤더 스킵
                if (isRowEmpty(row))      continue;

                int displayRow = row.getRowNum() + 1;
                String d3 = getCellStr(row.getCell(0)).trim();
                String d1 = getCellStr(row.getCell(1)).trim();

                if (d3.isEmpty() || d1.isEmpty()) {
                    errors.add(d3.isEmpty()
                        ? displayRow + "행: 그룹이 비어 있습니다."
                        : displayRow + "행: 문항 텍스트가 비어 있습니다.");
                    continue;
                }
                if (!VALID_GROUPS.contains(d3)) {
                    errors.add(displayRow + "행: 알 수 없는 그룹 「" + d3
                        + "」. 사용 가능: 섬김/배움/키움/나눔/목표관리/주관식");
                    continue;
                }

                Evaluation ev = new Evaluation();
                ev.setD1(d1);
                ev.setD2(type);
                ev.setD3(d3);
                ev.setEval_year(yearInt);
                parsed.add(ev);
            }
        } catch (Exception e) {
            log.error("문제은행 파싱 오류", e);
            ra.addFlashAttribute("error", "파일 파싱 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/pe/inst-admin/evaluation?year=" + year;
        }

        if (parsed.isEmpty()) {
            ra.addFlashAttribute("error", "파싱된 문항이 없습니다. 파일 형식을 확인하세요.");
            return "redirect:/pe/inst-admin/evaluation?year=" + year;
        }

        // 세션에 파싱 결과 저장
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY, parsed);
        session.setAttribute(SESSION_KEY + "_errors", errors);
        session.setAttribute(SESSION_KEY + "_type",   type);
        session.setAttribute(SESSION_KEY + "_year",   year);

        return "redirect:/pe/inst-admin/evaluation/preview";
    }

    // ── 미리보기 ────────────────────────────────────────────────

    @GetMapping("/preview")
    public String preview(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session == null) return "redirect:/pe/inst-admin/evaluation";

        @SuppressWarnings("unchecked")
        List<Evaluation> list   = (List<Evaluation>) session.getAttribute(SESSION_KEY);
        @SuppressWarnings("unchecked")
        List<String> errors     = (List<String>)     session.getAttribute(SESSION_KEY + "_errors");
        String type = (String) session.getAttribute(SESSION_KEY + "_type");
        String year = (String) session.getAttribute(SESSION_KEY + "_year");

        if (list == null || type == null) return "redirect:/pe/inst-admin/evaluation";

        // 그룹별 집계
        Map<String, List<Evaluation>> grouped = new LinkedHashMap<>();
        for (Evaluation ev : list) {
            grouped.computeIfAbsent(ev.getD3(), k -> new ArrayList<>()).add(ev);
        }

        long objectiveCount = list.stream().filter(e -> !"주관식".equals(e.getD3())).count();
        long subjectiveCount = list.stream().filter(e -> "주관식".equals(e.getD3())).count();

        model.addAttribute("questionList",    list);
        model.addAttribute("grouped",         grouped);
        model.addAttribute("errors",          errors);
        model.addAttribute("type",            type);
        model.addAttribute("year",            year);
        model.addAttribute("objectiveCount",  objectiveCount);
        model.addAttribute("subjectiveCount", subjectiveCount);
        model.addAttribute("hasErrors",       errors != null && !errors.isEmpty());
        return "pe/inst-admin/evalPreview";
    }

    // ── 확정 저장 ────────────────────────────────────────────────

    @PostMapping("/confirm")
    public String confirm(HttpServletRequest request, RedirectAttributes ra) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            ra.addFlashAttribute("error", "세션이 만료되었습니다. 다시 업로드하세요.");
            return "redirect:/pe/inst-admin/evaluation";
        }

        @SuppressWarnings("unchecked")
        List<Evaluation> list = (List<Evaluation>) session.getAttribute(SESSION_KEY);
        String type = (String) session.getAttribute(SESSION_KEY + "_type");
        String year = (String) session.getAttribute(SESSION_KEY + "_year");

        if (list == null || type == null || year == null) {
            ra.addFlashAttribute("error", "저장할 데이터가 없습니다. 다시 업로드하세요.");
            return "redirect:/pe/inst-admin/evaluation";
        }

        // 기존 삭제 후 신규 삽입
        evaluationMapper.deleteByYearAndType(Integer.parseInt(year), type);
        for (Evaluation ev : list) {
            evaluationMapper.insert(ev);
        }

        // 세션 정리
        session.removeAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY + "_errors");
        session.removeAttribute(SESSION_KEY + "_type");
        session.removeAttribute(SESSION_KEY + "_year");

        ra.addFlashAttribute("success",
                type + " 문제은행 " + list.size() + "문항이 저장되었습니다.");
        return "redirect:/pe/inst-admin/evaluation?year=" + year;
    }

    // ── 기존 문항 삭제 ────────────────────────────────────────────

    @PostMapping("/clear")
    public String clear(@RequestParam String type,
                        @RequestParam String year,
                        RedirectAttributes ra) {
        int deleted = evaluationMapper.deleteByYearAndType(Integer.parseInt(year), type);
        ra.addFlashAttribute("success",
                type + " 문제은행 " + deleted + "문항이 삭제되었습니다.");
        return "redirect:/pe/inst-admin/evaluation?year=" + year;
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────

    private String getCellStr(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c != null && c.getCellType() != CellType.BLANK
                    && !getCellStr(c).isBlank()) return false;
        }
        return true;
    }
}
