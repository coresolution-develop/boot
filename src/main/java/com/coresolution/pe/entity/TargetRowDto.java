package com.coresolution.pe.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 테이블 렌더를 위해 필요한 최소 필드 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetRowDto {
    private String targetId;
    private String targetName;
    private String subName; // 부서명
    private String dataEv; // C/D/E/F/G
    private String dataType; // AA/AB
    private String formId; // 선택
    private String formName; // 선택
    private String source; // "CUSTOM" or "DEFAULT" (effective에서 배지 표시용)

}
