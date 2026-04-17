package com.coresolution.pe.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.EvalSubmissionRow;
import com.coresolution.pe.entity.EvaluationSubmission;
import com.coresolution.pe.entity.TargetLight;
import com.coresolution.pe.mapper.EvalResultMapper;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EvalSummaryService {
    private final EvalResultMapper mapper;
    private final com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

    // 점수 매핑/순서 — 기존과 동일
    private static final Map<String, Integer> SCORE = Map.of(
            "매우우수", 5, "우수", 4, "보통", 3, "미흡", 2, "매우미흡", 1);

    // AA/AB 구분
    private static String typeByEv(String ev) {
        return switch (ev) {
            case "A", "E", "G" -> "AB"; // 20문항
            default -> "AA"; // 10문항
        };
    }

    @Data
    @Builder
    public static class Cell {
        BigDecimal score100; // 100점 환산
        Integer resp; // 응답수(제출 수)
    }

    @Data
    @Builder
    public static class Row {
        String empId;
        String empName;
        String deptName;
        String position;
        Map<String, Cell> ev; // key: A~G
    }

    @Data
    @Builder
    public static class SummaryVM {
        int year;
        List<Row> rows; // 직원별 행
        List<String> evOrder; // ["A","B",...,"G"]
        Map<String, String> evPath; // 라벨
        int totalEmployees;
        // 드롭다운 옵션
        String orgName; // 선택된 기관명(필터 표시용)
    }

    public SummaryVM buildSummary(int year, String orgName, Map<String, String> evPath) {
        var targets = mapper.selectAllTargetsForYearAndOrg(year, orgName);

        var evOrder = List.of("A", "B", "C", "D", "E", "F", "G");
        var rows = new ArrayList<Row>();

        for (var t : targets) {
            var byEv = new LinkedHashMap<String, Cell>();

            for (var ev : evOrder) {
                var a = mapper.selectEvAggOne(year, t.getEmpId(), ev);
                if (a == null || a.getScore100() == null) {
                    byEv.put(ev, Cell.builder().score100(null).resp(0).build());
                } else {
                    byEv.put(ev, Cell.builder()
                            .score100(a.getScore100())
                            .resp(a.getResp() == null ? 0 : a.getResp())
                            .build());
                }
            }

            for (String code : evOrder) {
                byEv.putIfAbsent(code, Cell.builder().score100(null).resp(0).build());
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
                .evOrder(evOrder)
                .evPath(evPath)
                .totalEmployees(rows.size())
                .orgName(orgName)
                .build();
    }

    // private Cell computeScore100(List<EvalSubmissionRow> subs) {
    // int resp = (subs == null) ? 0 : subs.size();
    // if (resp == 0)
    // return Cell.builder().score100(null).resp(0).build();

    // double avg = subs.stream()
    // .map(EvalSubmissionRow::getTotalScore) // Stream<Integer>
    // .filter(Objects::nonNull)
    // .mapToInt(Integer::intValue)
    // .average()
    // .orElse(Double.NaN);

    // Integer score100 = Double.isFinite(avg) ? (int) Math.round(avg) : null;

    // return Cell.builder().score100(score100 == null ? null :
    // score100.doubleValue())
    // .resp(resp)
    // .build();
    // }
}