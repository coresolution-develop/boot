package com.coresolution.pe.entity;

// 평균 DTO
public record KpiCohortAvg(
        Double promoAvg100, // I: 홍보공헌 100점
        Double volAvg100, // II: 자원봉사 100점
        Double eduAvg100, // III: 교육이수 100점
        Double multiAvg100 // V: 다면평가 100점(= 70점 환산)
) {
}