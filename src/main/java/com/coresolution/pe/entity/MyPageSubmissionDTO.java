package com.coresolution.pe.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class MyPageSubmissionDTO {
    private Long id;
    private Integer evalYear;
    private String evaluatorId;
    private String targetId;
    private String dataEv;
    private String dataType;
    private Integer answeredCount;
    private Integer totalScore;
    private Double avgScore;
    private Integer version;
    private Boolean isActive;
    private String delYn;
    private LocalDateTime updatedAt;

    // 파싱된 answers
    private AnswerPayload answers;

    private String rawAnswersJson;
    // ▼ 화면용 정렬 리스트
    private List<Map.Entry<String, String>> radiosList;
    private List<Map.Entry<String, String>> essaysList;

    // (선택) 평가자 표시용
    private String evaluatorName; // 조인 시 채움
    private String evaluatorDept; // 조인 시 채움
    private String evaluatorPos; // 조인 시 채움

    private String essayText; // 평가의견(essays.t43 등)
    private List<String> radioVals20; // 라디오 값 1~20칸(부족하면 null로 패딩)
}