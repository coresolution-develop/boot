package com.coresolution.pe.entity;

import java.math.BigDecimal;

public class StaffGradeRow {
    private BigDecimal totalScore; // kpi_eval_result_staff.total_score
    private Integer percentile; // kpi_eval_result_staff.percentile (1~100)
    private String evalGrade; // kpi_eval_result_staff.eval_grade (A+~E)

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getPercentile() {
        return percentile;
    }

    public void setPercentile(Integer percentile) {
        this.percentile = percentile;
    }

    public String getEvalGrade() {
        return evalGrade;
    }

    public void setEvalGrade(String evalGrade) {
        this.evalGrade = evalGrade;
    }
}