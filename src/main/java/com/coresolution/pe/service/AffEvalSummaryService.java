package com.coresolution.pe.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.TargetLight;
import com.coresolution.pe.mapper.AffEvalResultMapper;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * AFF 계열사 평가 총괄표 빌드 서비스.
 * EV 코드: A=부서장↔부서장, B=부서장→부서원, C=부서원→부서장, D=부서원↔부서원
 */
@Service
@RequiredArgsConstructor
public class AffEvalSummaryService {

    private final AffEvalResultMapper mapper;

    @Data
    @Builder
    public static class Cell {
        BigDecimal score100;
        Integer resp;
    }

    @Data
    @Builder
    public static class Row {
        String empId;
        String empName;
        String deptName;
        String position;
        Map<String, Cell> ev;
    }

    @Data
    @Builder
    public static class SummaryVM {
        int year;
        List<Row> rows;
        List<String> evOrder;
        Map<String, String> evPath;
        int totalEmployees;
        String orgName;
    }

    /** AFF EV 코드 라벨 */
    public static final Map<String, String> DEFAULT_EV_PATH = Map.of(
            "A", "부서장 ↔ 부서장",
            "B", "부서장 → 부서원",
            "C", "부서원 → 부서장",
            "D", "부서원 ↔ 부서원");

    public static final List<String> EV_ORDER = List.of("A", "B", "C", "D");

    public SummaryVM buildSummary(int year, String orgName) {
        List<TargetLight> targets = mapper.selectAllTargetsForYearAndOrg(year, orgName);
        List<Row> rows = new ArrayList<>();

        for (TargetLight t : targets) {
            Map<String, Cell> byEv = new LinkedHashMap<>();

            for (String ev : EV_ORDER) {
                var agg = mapper.selectEvAggOne(year, t.getEmpId(), ev);
                if (agg == null || agg.getScore100() == null) {
                    byEv.put(ev, Cell.builder().score100(null).resp(0).build());
                } else {
                    byEv.put(ev, Cell.builder()
                            .score100(agg.getScore100())
                            .resp(agg.getResp() == null ? 0 : agg.getResp())
                            .build());
                }
            }

            rows.add(Row.builder()
                    .empId(t.getEmpId())
                    .empName(t.getEmpName())
                    .deptName(t.getDeptName())
                    .position(t.getPosition())
                    .ev(byEv)
                    .build());
        }

        return SummaryVM.builder()
                .year(year)
                .rows(rows)
                .evOrder(EV_ORDER)
                .evPath(DEFAULT_EV_PATH)
                .totalEmployees(rows.size())
                .orgName(orgName)
                .build();
    }
}
