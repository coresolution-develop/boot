package com.coresolution.pe.entity;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class PairMeta {
    private String targetId;
    private String dataEv; // C/D/E/F/G ...
    private String dataType; // AA/AB
    private String reason;
    private String source;
    private String evalTypeCode; // nullable

}
