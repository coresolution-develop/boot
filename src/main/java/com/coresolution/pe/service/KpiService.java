package com.coresolution.pe.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.controller.PageController;
import com.coresolution.pe.entity.CombinedRow;
import com.coresolution.pe.entity.KpiCohortAvg;
import com.coresolution.pe.entity.KpiSummaryRow;
import com.coresolution.pe.entity.KyTotal2025Row;
import com.coresolution.pe.entity.MyKpiMultiRow;
import com.coresolution.pe.entity.MyKpiRow;
import com.coresolution.pe.entity.StaffGradeRow;
import com.coresolution.pe.mapper.KpiInfo2025Mapper;
import com.coresolution.pe.mapper.KpiMapper;
import com.coresolution.pe.service.KpiService.Num;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KpiService {
    private final KpiMapper mapper;
    private final KpiInfo2025Mapper kpi2025Mapper; // 2025부터

    public List<CombinedRow> fetchCombined(String orgName, int year, String op) {

        // 1) 2025년 이상은 신규 2025용 쿼리 사용
        if (year >= 2025) {
            // orgName == "" 이면 병원 전체, 아니면 해당 병원만
            return kpi2025Mapper.selectCombinedByOrgYear2025(orgName, year);
        }

        // 2) 2024년 이하는 기존 쿼리 유지
        return mapper.selectCombinedByOrgYear(orgName, year);

    }

    public List<CombinedRow> selectCombinedByOrgYear2025Medical(String orgName, int year) {
        return kpi2025Mapper.selectCombinedByOrgYear2025Medical(orgName, year);
    }

    public List<String> fetchOrgList(int year) {
        return mapper.selectOrgList(year);
    }

    // Service
    // 팀 전체 KPI (경혁팀 포함)
    public List<KpiSummaryRow> selectKpiForTeam(String orgName, String teamName, int year) {
        if (year >= 2025) {
            return kpi2025Mapper.selectKpiForTeam2025(orgName, teamName, year);
        }
        return mapper.selectKpiForTeam(orgName, teamName, year);
    }

    public MyKpiRow fetchMyKpi(int userId, int year) {
        if (year >= 2025) {
            // 2025년 이상은 kpi_personal_2025 (또는 이후 연도 테이블) 사용
            return kpi2025Mapper.selectMyKpi2025(year, userId);
        }
        // 그 이전 연도는 기존 CTE/뷰 기반 쿼리
        return mapper.selectMyKpi(userId, year);
    }

    public KpiCohortAvg selectDeptRadarAvg(int year, String deptCode) {
        return mapper.selectDeptRadarAvg(year, deptCode);
    }

    public KpiCohortAvg selectOrgRadarAvg(int year, String orgName) {
        if (year >= 2025) {
            return kpi2025Mapper.selectOrgRadarAvg2025(year, orgName);
        }
        return mapper.selectOrgRadarAvg(year, orgName);
    }

    // 경혁팀 개인(팀 기준)
    public KpiSummaryRow selectMyKpiForTeam(String orgName, String teamName, int year, int targetId) {
        if (year >= 2025) {
            return kpi2025Mapper.selectMyKpiForTeam2025(
                    orgName, teamName, year, String.valueOf(targetId));
        }
        return mapper.selectMyKpiForTeam(orgName, teamName, year, targetId);
    }

    // 2025 경혁팀 TOTAL 상세표용
    public KyTotal2025Row selectKyTotalDetail2025(int year, String empId) {
        if (year >= 2025) {
            return kpi2025Mapper.selectKyTotalDetail2025(year, empId);
        }
        return null; // 2024 이하는 기존 로직 사용
    }

    // ➋ 경혁팀 개인(팀 기준) - 2025용: KPI + 다면평가 merge
    public KpiSummaryRow selectMyKpiForTeam2025WithMulti(String orgName,
            String teamName,
            int year,
            int targetId) {
        // KPI 요약 (I~IV) : kpi_personal_2025
        KpiSummaryRow kpiRow = kpi2025Mapper.selectMyKpiForTeam2025(orgName, teamName, year, String.valueOf(targetId));

        // 다면평가(V) : 기존 CTE 쿼리 재사용 (evaluation_submissions 기반)
        KpiSummaryRow multiRow = mapper.selectMyKpiForTeam(orgName, teamName, year, targetId);

        if (kpiRow != null && multiRow != null) {

            // V. 다면평가 10/5/5 + 소계 값만 덮어쓰기
            kpiRow.setVExperience(multiRow.getVExperience());
            kpiRow.setVBossToStaff(multiRow.getVBossToStaff());
            kpiRow.setVStaffToStaff(multiRow.getVStaffToStaff());
            kpiRow.setVSubtotal(multiRow.getVSubtotal());

            // 총점 다시 계산: I(40) + II(15) + III(15) + IV(10) + V(20)
            Double i = Num.d(kpiRow.getISubtotal());
            Double ii = Num.d(kpiRow.getIiSubtotal());
            Double iii = Num.d(kpiRow.getIiiSubtotal());
            Double iv = Num.d(kpiRow.getIvSubtotal());
            Double v = Num.d(kpiRow.getVSubtotal());

            Double total = Num.sum(i, ii, iii, iv, v);
            if (total != null) {
                // KpiSummaryRow 의 total 타입에 맞게 세팅 (BigDecimal 이면 변환)
                kpiRow.setTotal(java.math.BigDecimal.valueOf(Num.round2(total)));
            }
        }

        return kpiRow;
    }

    // KpiService 안에 추가

    /**
     * KPI 팀별 총괄(표 전체) 조회
     * - 2025년 이상: kpi_personal_2025 + 신규 es_pivot 로직
     * - 2024년 이하: 기존 쿼리 그대로
     */
    /**
     * 경혁팀 다면평가 상세(팝업) 조회
     * - 2025년 이상: kpi_personal_2025 + evaluation_submissions(AA/AB) 기반 집계
     * - 2024년 이하: 기존 b0/b1/b2 CTE 로직
     */
    // 경혁팀 다면평가 상세(팝업) 조회
    // 경혁팀 다면평가 상세 (2024 이하 + 2025 이상 공통 사용)
    public KpiSummaryRow selectKyMultiDetailFromEs(
            String orgName,
            String teamName,
            int year,
            int userId) {
        if (year >= 2025) {
            // 2025년 이후: kpi_personal_2025 + evaluation_submissions 기반 단일 행
            // empNo 가 문자열이므로 userId를 문자열로 변환해서 전달
            return kpi2025Mapper.selectMyKpiForTeam2025(
                    orgName,
                    teamName,
                    year,
                    String.valueOf(userId));
        } else {
            // 2024년까지: 기존 CTE(b0/b1/b2) 로직 재사용
            return mapper.selectMyKpiForTeam(
                    orgName,
                    teamName,
                    year,
                    userId);
        }
    }

    public int saveKpiTotalScoreBulk(List<com.coresolution.pe.entity.KpiScoreSaveReq> list, String updatedBy) {
        int success = 0;

        for (com.coresolution.pe.entity.KpiScoreSaveReq r : list) {
            // 2025년 이상만 저장, 그 이하는 필요시 다른 테이블에 분기
            if (r.getYear() >= 2025) {
                if (r.getTotalScore() == null)
                    continue;

                kpi2025Mapper.upsertKpiScore(
                        r.getYear(),
                        r.getHospitalName(), // 필요 없으면 null 또는 제거
                        r.getEmpId(),
                        r.getTotalScore(),
                        r.getMultiInteam10(),
                        r.getMultiBoss5(),
                        r.getMultiClinic5(),
                        updatedBy);
                success++;
            }
        }
        return success;
    }

    public final class Num {
        private Num() {
        }

        public static Double d(Object v) {
            if (v == null)
                return null;
            String s = String.valueOf(v).trim();
            if (s.isEmpty() || s.equals("-"))
                return null;
            // "132,988", "90.43%", "▲3.83%", "▼5.44%", "1.25" 등 처리
            s = s.replace(",", "")
                    .replace("%", "")
                    .replace("▲", "")
                    .replace("▼", "")
                    .replaceAll("[^0-9.-]", ""); // 또는 "[^0-9.\-]"
            try {
                return Double.valueOf(s);
            } catch (Exception e) {
                return null;
            }
        }

        public static Integer i(Object v) {
            Double d = d(v);
            return d == null ? null : d.intValue();
        }

        public static Double sum(Double... xs) {
            double t = 0;
            boolean any = false;
            for (Double x : xs) {
                if (x != null) {
                    t += x;
                    any = true;
                }
            }
            return any ? t : null;
        }

        public static Double round2(Double x) {
            return x == null ? null : Math.round(x * 100.0) / 100.0;
        }
    }

    private String grade(Double total) {
        if (total == null)
            return "-";
        if (total >= 95)
            return "S";
        if (total >= 90)
            return "A+";
        if (total >= 85)
            return "A";
        if (total >= 80)
            return "B+";
        if (total >= 75)
            return "B";
        return "C";
    }

    private static Double d(Object o) {
        return Num.d(o);
    } // 가독성용

    private static Double pick(Map<String, Object> r, String... keys) {
        for (String k : keys) {
            Object v = r.get(k);
            if (v != null)
                return Num.d(v);
        }
        return null;
    }

    @Transactional
    public int saveStaffKpiResults2025(int year, String orgName, String actor, String mode) {
        String m = (mode == null ? "NEW" : mode.trim().toUpperCase());
        if ("OLD".equals(m)) {
            return kpi2025Mapper.upsertStaffKpiEvalResult2025Old(year, orgName == null ? "" : orgName, actor);
        }
        // default = NEW
        return kpi2025Mapper.upsertStaffEvalResult2025New(year, orgName == null ? "" : orgName, actor);
    }

    // @Transactional
    // public int applyStaffGrades2025(int year, String orgName, String actor) {
    // return kpi2025Mapper.applyStaffGrades2025(year, orgName == null ? "" :
    // orgName, actor);
    // }

    // @Transactional
    // public int applyStaffGrades2025(int year, String orgName) {
    // return kpi2025Mapper.updateEvalGrade(year, orgName == null ? "" : orgName);
    // }

    @Transactional
    public int saveClinicKpiResults2025(int year, String orgName, String actor) {
        return kpi2025Mapper.upsertClinicKpiEvalClinic2025(year, orgName == null ? "" : orgName, actor);
    }

    @Transactional(readOnly = true)
    public StaffGradeRow selectMyStaffGrade(int y, String targetId) {
        return kpi2025Mapper.selectMyStaffGrade(y, targetId);
    }

    @Transactional
    public int applyStaffGrades2025(int year, String orgName) {
        return kpi2025Mapper.applyStaffGrades2025(year, orgName == null ? "" : orgName);
    }

}
