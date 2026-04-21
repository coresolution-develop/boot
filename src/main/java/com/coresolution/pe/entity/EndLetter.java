package com.coresolution.pe.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EndLetter {

    private int id;
    private int evalYear;
    private String institutionName;
    private String content;  // 본문 텍스트 (줄바꿈 포함 전문)
    private String updatedAt;
}
