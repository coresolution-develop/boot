package com.coresolution.pe.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.CombinedRow;
import com.coresolution.pe.entity.EvalSummaryRow;
import com.coresolution.pe.mapper.AffKpiInfo2025Mapper;
import com.coresolution.pe.mapper.AffKpiMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffKpiService {
    private final AffKpiMapper mapper;
    private final AffKpiInfo2025Mapper kpi2025Mapper;

    public List<String> fetchOrgList(int year) {
        return mapper.selectOrgList(year);
    }

    public List<EvalSummaryRow> fetchEvalSummary(String org, int year, String op) {
        return mapper.selectEvalSummaryByOrgYear(org, year, op);
    }

    public Map<String, Object> fetchMyEvalSummary(String empId, int year) {
        return mapper.selectMyEvalSummary(empId, year);
    }

    public Object fetchCombined(String orgName, int year, String op, String order) {

        // 1) 2025년 이상은 신규 2025용 쿼리 사용
        if (year >= 2025) {
            // orgName == "" 이면 병원 전체, 아니면 해당 병원만
            return kpi2025Mapper.selectCombinedByOrgYear2025(orgName, year, order);
        }

        // 2) 2024년 이하는 기존 쿼리 유지
        return mapper.selectCombinedByOrgYear(orgName, year);
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

    public int applyStaffGrades2025(int year, String orgName) {
        return kpi2025Mapper.applyStaffGrades2025(year, orgName == null ? "" : orgName);
    }

}
