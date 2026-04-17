package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coresolution.pe.mapper.YearMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class YearService {
    private final YearMapper yearMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    /** 평가 연도 목록 조회. currentEvalYear는 데이터 유무와 관계없이 항상 포함 */
    public List<String> getYears() {
        List<String> years = yearMapper.selectYearsFromTargets();
        if (years == null || years.isEmpty()) {
            years = yearMapper.selectYearsFromUsers();
        }
        if (years == null) {
            years = new ArrayList<>();
        } else {
            years = new ArrayList<>(years);
        }
        String currentStr = String.valueOf(currentEvalYear);
        if (!years.contains(currentStr)) {
            years.add(0, currentStr); // 현재 연도를 맨 앞에
        } else if (!years.get(0).equals(currentStr)) {
            years.remove(currentStr);
            years.add(0, currentStr); // 현재 연도를 맨 앞으로
        }
        return years;
    }

}
