package com.coresolution.pe.controller;

import java.io.InputStream;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coresolution.pe.entity.SubManagement;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.entity.UserrolePE;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.security.InstitutionAdminContext;
import com.coresolution.pe.service.AffPoiMakeExcelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 계열사 기관 관리자 전용 엑셀 업로드 컨트롤러.
 * <p>
 * - 모든 파싱 후 {@code c_name} 을 세션의 {@code institutionName} 으로 강제 주입
 * - reset 은 해당 기관 범위만 삭제
 * - 공통 {@link AffExcelContoller} 의 {@code /aff/excel/**} 과 독립적으로 동작
 * </p>
 */
@Slf4j
@Controller
@RequestMapping("/aff/inst-admin/excel")
@PreAuthorize("hasRole('INST_ADMIN')")
@RequiredArgsConstructor
public class AffInstAdminExcelController {

    private final AffPoiMakeExcelService poiService;
    private final AffLoginMapper         affLoginMapper;

    private static final String SESSION_USERS  = "aff_ia_usersList";
    private static final String SESSION_ROLES  = "aff_ia_roleList";
    private static final String SESSION_SUBDEP = "aff_ia_subList";

    private String resolveInstitutionName(HttpServletRequest request) {
        String name = InstitutionAdminContext.getInstitutionName(request);
        if (name == null || name.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "기관 정보가 세션에 없습니다. 다시 로그인해주세요.");
        }
        return name;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 직원 업로드
    // ══════════════════════════════════════════════════════════════════════

    /**
     * POST /aff/inst-admin/excel/upload
     * 엑셀 파싱 → c_name 강제 주입 → 미리보기 페이지
     */
    @PostMapping("/upload")
    public String uploadUsers(@RequestParam("file") MultipartFile file,
                              @RequestParam("year") int year,
                              HttpServletRequest request,
                              HttpSession session,
                              Model model,
                              RedirectAttributes ra) {

        String institutionName = resolveInstitutionName(request);

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "파일을 선택해 주세요.");
            return "redirect:/aff/inst-admin/userDataUpload?year=" + year;
        }

        try (InputStream is = file.getInputStream();
             XSSFWorkbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            List<UserPE> users = poiService.parseUsers(sheet, year);
            // c_name 강제 주입 — 기관 범위 이탈 방지
            for (UserPE u : users) {
                u.setCName(institutionName);
            }

            // 역할 목록 (parseUsers 반환값에서 분리하거나 별도 파싱)
            List<UserrolePE> roles = poiService.parseRoles(sheet, year);

            session.setAttribute(SESSION_USERS, users);
            session.setAttribute(SESSION_ROLES, roles);

            model.addAttribute("userList",       users);
            model.addAttribute("roleList",       roles);
            model.addAttribute("year",           year);
            model.addAttribute("institutionName", institutionName);

            return "aff/inst-admin/uploadPreview";

        } catch (Exception e) {
            log.error("[AffInstAdminExcel] 업로드 파싱 오류", e);
            ra.addFlashAttribute("error", "업로드 실패: " + e.getMessage());
            return "redirect:/aff/inst-admin/userDataUpload?year=" + year;
        }
    }

    /**
     * POST /aff/inst-admin/excel/confirmUpload
     * 세션 데이터 DB 저장
     */
    @PostMapping("/confirmUpload")
    public String confirmUpload(@RequestParam("year") int year,
                                HttpServletRequest request,
                                HttpSession session,
                                RedirectAttributes ra) {

        String institutionName = resolveInstitutionName(request);

        @SuppressWarnings("unchecked")
        List<UserPE> users = (List<UserPE>) session.getAttribute(SESSION_USERS);
        @SuppressWarnings("unchecked")
        List<UserrolePE> roles = (List<UserrolePE>) session.getAttribute(SESSION_ROLES);

        if (users == null || roles == null) {
            ra.addFlashAttribute("error", "업로드된 데이터가 없습니다. 다시 업로드해주세요.");
            return "redirect:/aff/inst-admin/userDataUpload?year=" + year;
        }

        // 2차 방어: c_name 이 다른 행은 모두 제거
        users = users.stream()
                     .filter(u -> institutionName.equals(u.getCName()))
                     .toList();

        poiService.saveUsersAndRoles(users, roles, year);

        session.removeAttribute(SESSION_USERS);
        session.removeAttribute(SESSION_ROLES);

        ra.addFlashAttribute("success", "직원 데이터 " + users.size() + "명이 저장되었습니다.");
        return "redirect:/aff/inst-admin/userList?year=" + year;
    }

    /**
     * POST /aff/inst-admin/excel/reset
     * 해당 기관의 직원·역할 데이터만 삭제
     */
    @PostMapping("/reset")
    public String resetUsers(@RequestParam("year") String year,
                             HttpServletRequest request,
                             RedirectAttributes ra) {

        String institutionName = resolveInstitutionName(request);

        try {
            int yearInt = Integer.parseInt(year);
            affLoginMapper.deleteRolesByOrg(yearInt, institutionName);
            int deleted = affLoginMapper.deleteUsersByOrg(yearInt, institutionName);
            ra.addFlashAttribute("success",
                    institutionName + "의 " + year + "년도 직원 데이터 "
                    + deleted + "명이 초기화되었습니다.");
        } catch (Exception e) {
            log.error("[AffInstAdminExcel] 데이터 초기화 오류", e);
            ra.addFlashAttribute("error", "초기화 중 오류 발생: " + e.getMessage());
        }
        return "redirect:/aff/inst-admin/userDataUpload?year=" + year;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 부서 업로드
    // ══════════════════════════════════════════════════════════════════════

    /**
     * POST /aff/inst-admin/excel/subupload
     * 부서 파일 파싱 → 미리보기
     */
    @PostMapping("/subupload")
    public String uploadDepts(@RequestParam("file") MultipartFile file,
                              @RequestParam("year") int year,
                              HttpServletRequest request,
                              HttpSession session,
                              Model model,
                              RedirectAttributes ra) {

        String institutionName = resolveInstitutionName(request);

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "파일을 선택해 주세요.");
            return "redirect:/aff/inst-admin/userDataUpload?year=" + year;
        }

        try (InputStream is = file.getInputStream();
             XSSFWorkbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            List<SubManagement> deptList = poiService.parseDepartments(sheet, year);

            session.setAttribute(SESSION_SUBDEP, deptList);

            model.addAttribute("subList",        deptList);
            model.addAttribute("year",           year);
            model.addAttribute("institutionName", institutionName);

            return "aff/inst-admin/subPreview";

        } catch (Exception e) {
            log.error("[AffInstAdminExcel] 부서 업로드 파싱 오류", e);
            ra.addFlashAttribute("error", "업로드 실패: " + e.getMessage());
            return "redirect:/aff/inst-admin/userDataUpload?year=" + year;
        }
    }

    /**
     * POST /aff/inst-admin/excel/subconfirm
     * 부서 DB 저장
     */
    @PostMapping("/subconfirm")
    public String confirmDepts(@RequestParam("year") int year,
                               HttpServletRequest request,
                               HttpSession session,
                               RedirectAttributes ra) {

        resolveInstitutionName(request); // 권한 확인

        @SuppressWarnings("unchecked")
        List<SubManagement> subList =
            (List<SubManagement>) session.getAttribute(SESSION_SUBDEP);

        if (subList == null || subList.isEmpty()) {
            ra.addFlashAttribute("error", "업로드된 부서 목록이 없습니다.");
            return "redirect:/aff/inst-admin/userDataUpload?year=" + year;
        }

        try {
            poiService.saveDepartments(subList, year);
            session.removeAttribute(SESSION_SUBDEP);
            ra.addFlashAttribute("success", "부서 " + subList.size() + "개가 저장되었습니다.");
        } catch (Exception e) {
            log.error("[AffInstAdminExcel] 부서 저장 오류", e);
            ra.addFlashAttribute("error", "저장 중 오류: " + e.getMessage());
        }

        return "redirect:/aff/inst-admin/subManagement?year=" + year;
    }
}
