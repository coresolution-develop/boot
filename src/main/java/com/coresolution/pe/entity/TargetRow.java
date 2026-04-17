package com.coresolution.pe.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetRow {

    private String targetId;
    private String evalTypeCode;

    // 🔹 추가: 실제 EV/TYPE 코드
    private String dataEv; // D, E ...
    private String dataType; // AA, AB ...
}
