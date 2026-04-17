package com.coresolution.pe.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Progress {
    private int answered;
    private int total;
    private boolean completed;
    private Double avg; // 기존 유지(다른 곳에서 쓸 수 있으니까)
    private Integer totalScore; // ★ 추가: 총점
    // ★ 링크 파라미터 대체용
    private String dataType;
    private String dataEv;

}
