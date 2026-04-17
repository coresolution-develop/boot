package com.coresolution.pe.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.ReleaseWindow;
import com.coresolution.pe.entity.ReleaseWindowRow;
import com.coresolution.pe.mapper.AffReleaseWindowMapper;
import com.coresolution.pe.mapper.ReleaseWindowMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffReleaseWindowService {

    private final AffReleaseWindowMapper mapper;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // ----- helpers -----
    private static String nz(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static String up(String s) {
        return nz(s).toUpperCase();
    }

    private static String toNullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static void validate(ReleaseWindowRow r) {
        if (r.getEvalYear() < 2000 || r.getEvalYear() > 2100)
            throw new IllegalArgumentException("연도가 유효하지 않습니다.");
        if (nz(r.getDataType()).isEmpty())
            throw new IllegalArgumentException("유형(AC/AD/AE)이 필요합니다.");
        if (nz(r.getDataEv()).isEmpty())
            throw new IllegalArgumentException("관계(A/B/C/D)가 필요합니다.");
        if (r.getOpenAt() == null || r.getCloseAt() == null)
            throw new IllegalArgumentException("오픈/마감 시간이 필요합니다.");
        if (!r.getCloseAt().isAfter(r.getOpenAt()))
            throw new IllegalArgumentException("마감은 오픈 이후여야 합니다.");
    }

    // ----- queries -----
    @Transactional(readOnly = true)
    public List<ReleaseWindowRow> list(Integer year, String type, String ev, String cName, String subCode,
            int limit, int offset) {
        return mapper.findList(
                year,
                toNullIfBlank(type),
                toNullIfBlank(ev),
                toNullIfBlank(cName),
                toNullIfBlank(subCode),
                limit, offset);
    }

    @Transactional(readOnly = true)
    public int count(Integer year, String type, String ev, String cName, String subCode) {
        return mapper.countList(
                year,
                toNullIfBlank(type),
                toNullIfBlank(ev),
                toNullIfBlank(cName),
                toNullIfBlank(subCode));
    }

    // ----- mutations -----
    @Transactional
    public long upsert(ReleaseWindowRow r, String operator) {
        // 표준화
        r.setDataType(up(r.getDataType()));
        r.setDataEv(up(r.getDataEv()));
        r.setCName(nz(r.getCName()));
        r.setSubCode(nz(r.getSubCode()));

        validate(r);
        r.setCreatedBy(operator);
        r.setUpdatedBy(operator);
        mapper.upsert(r);
        return 1L; // 필요하면 findBestMatch로 id 재조회 가능
    }

    @Transactional
    public int updateById(ReleaseWindowRow r, String operator) {
        // id는 컨트롤러에서 세팅
        r.setDataType(up(r.getDataType()));
        r.setDataEv(up(r.getDataEv()));
        r.setCName(nz(r.getCName()));
        r.setSubCode(nz(r.getSubCode()));

        validate(r);
        r.setUpdatedBy(operator);
        return mapper.updateById(r);
    }

    @Transactional
    public int toggle(long id, boolean enabled, String operator) {
        return mapper.toggleEnabled(id, enabled, operator);
    }

    @Transactional
    public int softDelete(long id, String operator) {
        return mapper.softDelete(id, operator);
    }

    // ----- gate check -----
    /** 게이트 체크에 사용: 가장 구체적인 정책(부서→병원→전역) 반환 */
    @Transactional(readOnly = true)
    public ReleaseWindowRow findBestMatch(int year, String type, String ev, String cName, String subCode) {
        return mapper.findBestMatch(year, up(type), up(ev), nz(cName), nz(subCode));
    }

    /** 주어진 윈도우가 현재 열려있는지 여부 (정책이 없거나 비활성 = 열림) */
    public boolean isOpen(ReleaseWindowRow w, ZoneId zone) {
        if (w == null)
            return true; // 정책이 없거나 비활성인 경우 열어 둠
        LocalDateTime now = LocalDateTime.now(zone);
        return !now.isBefore(w.getOpenAt()) && !now.isAfter(w.getCloseAt());
    }

    /** 편의 메서드: 파라미터로 즉시 열림 여부 확인 */
    @Transactional(readOnly = true)
    public boolean isOpenNow(int year, String type, String ev, String cName, String subCode, ZoneId zone) {
        ReleaseWindowRow w = findBestMatch(year, type, ev, cName, subCode);
        return isOpen(w, zone);
    }

    public ReleaseWindow findEarliestForUser(int year, String cName, String subCode) {
        ReleaseWindow ac = mapper.findNearest(year, "AC", cName, subCode);
        ReleaseWindow ad = mapper.findNearest(year, "AD", cName, subCode);
        ReleaseWindow ae = mapper.findNearest(year, "AE", cName, subCode);

        ReleaseWindow earliest = null;

        for (ReleaseWindow w : new ReleaseWindow[] { ac, ad, ae }) {
            if (w == null)
                continue;
            if (earliest == null || w.getOpenAt().isBefore(earliest.getOpenAt())) {
                earliest = w;
            }
        }

        // 전부 null이면 null 리턴 → 이 계열사에 대한 차단 설정 없음
        return earliest;
    }

    public boolean isBeforeOpen(ReleaseWindow w) {
        if (w == null || w.getOpenAt() == null)
            return false; // 윈도우 없으면 모달 없음
        ZonedDateTime now = ZonedDateTime.now(KST);
        return now.toLocalDateTime().isBefore(w.getOpenAt());
    }

    public long daysUntilOpen(ReleaseWindow w) {
        if (w == null || w.getOpenAt() == null)
            return 0;
        ZonedDateTime now = ZonedDateTime.now(KST);
        return java.time.Duration.between(now.toLocalDateTime(), w.getOpenAt()).toDays();
    }
}
