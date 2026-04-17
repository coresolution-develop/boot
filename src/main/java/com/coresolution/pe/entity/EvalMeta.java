package com.coresolution.pe.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Data
public class EvalMeta {

    private String evalTypeCode; // 선택
    private String dataEv; // C/D/E/F/G...
    private String dataType; // AA/AB...
    private String formId; // 선택
}
