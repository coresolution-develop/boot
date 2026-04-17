package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.entity.FormSummary;
import com.coresolution.pe.mapper.FormMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FormService {
    private final FormMapper formMapper;

    @Transactional(readOnly = true)
    public List<FormSummary> listForms(String year) {
        List<Map<String, String>> rows = formMapper.selectDistinctCombos(year);

        // rows 예: [{dataEv=C, dataType=AA}, {dataEv=E, dataType=AB}, ...]
        List<FormSummary> out = new ArrayList<>();
        for (Map<String, String> r : rows) {
            String ev = r.get("dataEv");
            String dt = r.get("dataType");
            FormSummary f = new FormSummary();
            f.setDataEv(ev);
            f.setDataType(dt);
            f.setFormId(ev + "-" + dt);
            f.setFormName(labelFor(ev, dt));
            out.add(f);
        }
        return out;
    }

    private String labelFor(String ev, String dt) {
        // 회사 룰에 맞춰 자유롭게 변경
        if ("C".equals(ev) && "AA".equals(dt))
            return "진료부 평가 (C/AA)";
        if ("D".equals(ev) && "AA".equals(dt))
            return "경혁팀 평가 (D/AA)";
        if ("E".equals(ev) && "AB".equals(dt))
            return "부서장→부서원 (E/AB)";
        if ("F".equals(ev) && "AA".equals(dt))
            return "부서원→부서장 (F/AA)";
        if ("G".equals(ev) && "AB".equals(dt))
            return "부서원↔부서원 (G/AB)";
        return "기타 (" + ev + "/" + dt + ")";
    }

}
