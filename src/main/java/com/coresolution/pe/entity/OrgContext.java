package com.coresolution.pe.entity;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrgContext {
    private List<String> mission; // 이미지인 경우 한 줄 요약으로 정리
    private List<String> vision; // 문장 리스트
    private List<String> coreValues; // ["섬김","배움","키움","나눔"]
    // 필요하면 CI 다운로드 링크도 여기에
    private String ciPngUrl;
    private String ciAiUrl;
}
