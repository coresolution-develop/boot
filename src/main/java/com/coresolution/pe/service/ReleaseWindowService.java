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
public class ReleaseWindowService {

    private final ReleaseWindowMapper mapper;

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

    // ----- year copy -----
    /**
     * fromYear 의 모든 창구 설정을 toYear 로 복사.
     * offsetDays 만큼 날짜를 이동 (보통 365 또는 366).
     * 이미 toYear 에 동일한 조합이 있으면 SKIP.
     * @return 새로 삽입된 행 수
     */
    @Transactional
    public int copyFromYear(int fromYear, int toYear, int offsetDays, String operator) {
        if (fromYear == toYear) throw new IllegalArgumentException("원본과 대상 연도가 같습니다.");
        if (fromYear < 2000 || toYear < 2000) throw new IllegalArgumentException("연도가 유효하지 않습니다.");
        return mapper.copyFromYear(fromYear, toYear, offsetDays, operator);
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
            return true; // 정책이 없으면 열림(기존 정책 유지)
        LocalDateTime now = LocalDateTime.now(zone);

        // openAt <= now < closeAt
        return !now.isBefore(w.getOpenAt()) && now.isBefore(w.getCloseAt());
    }

    /** 편의 메서드: 파라미터로 즉시 열림 여부 확인 */
    @Transactional(readOnly = true)
    public boolean isOpenNow(int year, String type, String ev, String cName, String subCode, ZoneId zone) {
        ReleaseWindowRow w = findBestMatch(year, type, ev, cName, subCode);
        return isOpen(w, zone);
    }

    public ReleaseWindow findEarliestForUser(String year, String dataEv, String cName, String subCode) {
        ReleaseWindow aa = mapper.findNearest(year, "AA", dataEv, cName, subCode);
        ReleaseWindow ab = mapper.findNearest(year, "AB", dataEv, cName, subCode);
        if (aa == null)
            return ab;
        if (ab == null)
            return aa;
        return aa.getOpenAt().isBefore(ab.getOpenAt()) ? aa : ab;
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
        LocalDateTime now = LocalDateTime.now(KST);
        return Math.max(0, java.time.Duration.between(now, w.getOpenAt()).toDays());
    }

    public boolean isAfterClose(ReleaseWindow w) {
        if (w == null || w.getCloseAt() == null)
            return false;
        LocalDateTime now = LocalDateTime.now(KST);
        // now >= closeAt 이면 마감
        return !now.isBefore(w.getCloseAt());
    }

    public long daysSinceClose(ReleaseWindow w) {
        if (w == null || w.getCloseAt() == null)
            return 0;
        LocalDateTime now = LocalDateTime.now(KST);
        return Math.max(0, java.time.Duration.between(w.getCloseAt(), now).toDays());
    }

    public enum GateStatus {
        OPEN, BEFORE_OPEN, AFTER_CLOSE
    }

    public static class GateCheck {
        public final GateStatus status;
        public final LocalDateTime openAt; // 안내용(가장 가까운 오픈)
        public final LocalDateTime closeAt; // 안내용(가장 최근 마감)

        public GateCheck(GateStatus status, LocalDateTime openAt, LocalDateTime closeAt) {
            this.status = status;
            this.openAt = openAt;
            this.closeAt = closeAt;
        }
    }

    @Transactional(readOnly = true)
    public GateCheck checkStrictForUser(int year, String cName, String subCode) {
        var list = mapper.findAllForUser(year, nz(cName), nz(subCode));

        // 정책이 하나도 없으면 열어둠(기존 정책 유지)
        if (list == null || list.isEmpty()) {
            return new GateCheck(GateStatus.OPEN, null, null);
        }

        LocalDateTime now = LocalDateTime.now(KST);

        LocalDateTime nearestOpen = null; // now 이후 중 가장 가까운 오픈
        LocalDateTime latestClose = null; // now 이전/이후 무관하게 가장 최근 close (안내용)

        boolean anyBefore = false;
        boolean anyAfter = false;

        for (var w : list) {
            if (w.getOpenAt() != null && now.isBefore(w.getOpenAt())) {
                anyBefore = true;
                if (nearestOpen == null || w.getOpenAt().isBefore(nearestOpen))
                    nearestOpen = w.getOpenAt();
            }
            if (w.getCloseAt() != null) {
                if (latestClose == null || w.getCloseAt().isAfter(latestClose))
                    latestClose = w.getCloseAt();
                // now >= closeAt 이면 마감 후(차단)
                if (!now.isBefore(w.getCloseAt()))
                    anyAfter = true;
            }
        }

        // “그냥 다 막기”: 하나라도 오픈 전/마감 후면 차단
        if (anyAfter)
            return new GateCheck(GateStatus.AFTER_CLOSE, nearestOpen, latestClose);
        if (anyBefore)
            return new GateCheck(GateStatus.BEFORE_OPEN, nearestOpen, latestClose);
        return new GateCheck(GateStatus.OPEN, null, latestClose);
    }

    @Transactional(readOnly = true)
    public GateCheck checkStrictWithExceptionForUser(int year, String cName, String subCode,
            String dataType, String dataEv) {

        GateCheck gate = checkStrictForUser(year, cName, subCode); // 기존 로직 그대로

        // 이미 열려 있으면 그대로 통과
        if (gate.status == GateStatus.OPEN)
            return gate;

        // 닫혀있어도 예외가 열려 있으면 OPEN 처리
        boolean excOpen = mapper.isReleaseExceptionOpen(year, nz(cName), nz(subCode), up(dataEv), up(dataType));
        if (excOpen) {
            // 예외로 OPEN 처리: 안내용 시간(openAt/closeAt)은 기존 gate 값 유지
            return new GateCheck(GateStatus.OPEN, gate.openAt, gate.closeAt);
        }

        return gate; // BEFORE_OPEN/AFTER_CLOSE 유지
    }
}
