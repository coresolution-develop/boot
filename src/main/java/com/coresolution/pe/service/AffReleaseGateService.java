package com.coresolution.pe.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.ReleaseGate;
import com.coresolution.pe.mapper.AdminMapper;
import com.coresolution.pe.mapper.AffAdminMapper;
import com.coresolution.pe.mapper.AffExcelMapper;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.mapper.ExcelMapper;
import com.coresolution.pe.mapper.LoginMapper;

@Service
public class AffReleaseGateService {

    private final AffLoginMapper loginMapper;
    private final AffExcelMapper excelMapper;
    private final AffAdminMapper adminmapper;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public AffReleaseGateService(AffLoginMapper loginMapper, AffExcelMapper excelMapper, AffAdminMapper adminmapper) {
        this.loginMapper = loginMapper;
        this.excelMapper = excelMapper;
        this.adminmapper = adminmapper;
    }

    public ReleaseGate getOrNull(String pageKey, int year) {
        return adminmapper.findOne(pageKey, year).orElse(null);
    }

    public boolean isOpenNow(String pageKey, int year) {
        var g = adminmapper.findOne(pageKey, year).orElse(null);
        if (g == null || !Boolean.TRUE.equals(g.getEnabled()))
            return false;
        var now = ZonedDateTime.now(KST);
        var open = g.getOpenAt() == null ? null : g.getOpenAt().atZone(KST);
        var close = g.getCloseAt() == null ? null : g.getCloseAt().atZone(KST);
        if (open == null)
            return false; // 공개일 미정이면 닫힘 취급
        if (now.isBefore(open))
            return false; // 공개 전
        if (close != null && now.isAfter(close))
            return false; // 마감 이후
        return true;
    }

    public void saveGate(String pageKey, int year, java.time.LocalDateTime openAt,
            java.time.LocalDateTime closeAt, boolean enabled) {
        var g = new ReleaseGate();
        g.setPageKey(pageKey);
        g.setEvalYear(year);
        g.setOpenAt(openAt);
        g.setCloseAt(closeAt);
        g.setEnabled(enabled);
        adminmapper.upsert(g);
    }

    public Integer latestOpenYear(String pageKey) {
        return adminmapper.findLatestOpenYear(pageKey);
    }

    /** not-open 화면에서 표시할 공개일(로컬 KST) */
    public Optional<LocalDateTime> getOpenAtKst(String pageKey, int year) {
        return adminmapper.findOne(pageKey, year)
                .filter(g -> Boolean.TRUE.equals(g.getEnabled()))
                .map(g -> g.getOpenAt()) // DATETIME(=timezone-free) 가정
                .map(dt -> dt); // 그대로 KST로 취급 (JDBC URL이 serverTimezone=Asia/Seoul)
    }
}
