package com.coresolution.pe.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 직원 엑셀 업로드 파싱 결과.
 * users + roles 외에 행별 오류 목록을 함께 반환한다.
 */
@Data
public class UploadParseResult {

    private List<UserPE>       users  = new ArrayList<>();
    private List<UserrolePE>   roles  = new ArrayList<>();
    private List<RowError>     errors = new ArrayList<>();
    /**
     * 직원 파일에 부서코드가 명시되었지만 sub_management에 없는 경우.
     * key=부서코드, value=부서명 — confirmUpload 시 자동 등록 대상.
     */
    private Map<String,String> deptsToAutoRegister = new LinkedHashMap<>();

    public boolean hasBlockingErrors() {
        return errors.stream().anyMatch(RowError::isBlocking);
    }

    public long blockingCount() {
        return errors.stream().filter(RowError::isBlocking).count();
    }

    public long warningCount() {
        return errors.stream().filter(e -> !e.isBlocking()).count();
    }

    @Data
    public static class RowError {
        /** 엑셀 행 번호 (1-based, 사용자에게 표시) */
        private final int     rowNum;
        /** 문제가 된 컬럼명 */
        private final String  field;
        /** 오류 메시지 */
        private final String  message;
        /**
         * true  → 확정 업로드를 막는 오류 (사원번호 누락, 부서 미등록 등)
         * false → 경고만 (날짜 역전, 전화번호 형식 등)
         */
        private final boolean blocking;
    }
}
