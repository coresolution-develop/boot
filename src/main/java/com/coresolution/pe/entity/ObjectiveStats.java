package com.coresolution.pe.entity;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class ObjectiveStats {
    private Map<String, Double> myArea100;
    private Map<String, Double> evArea100;
    private Map<String, Double> allArea100;
    private Map<String, Double> deptEvArea100;
    private Map<String, Double> deptAllArea100;

    private Map<String, Map<String, Integer>> myAreaDist;

    private Double myOverall100;
    private Double evOverall100;
    private Double allOverall100;
    private Double deptEvOverall100;
    private Double deptAllOverall100;

    // ✅ “동일 평가구간 평균”으로 화면에 쓸 대표 비교축
    private Double cohortOverall100;

    private List<String> labels;

    // 내러티브/요약
    private Map<String, List<String>> areaNarr;
    private Map<String, String> areaSummary;
}
