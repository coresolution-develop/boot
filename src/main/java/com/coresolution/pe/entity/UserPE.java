package com.coresolution.pe.entity;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class UserPE {
    private int idx;
    private String cName;
    private String cName2;
    private String subCode;
    private String teamCode;
    private String position;
    private String id;
    private String pwd;
    private String name;
    private Date createAt;
    private Date deleteAt;
    private String phone;
    private String delYn;
    private List<String> roles;
    // 유형
    private String roleType;
    private int evalYear;
    private String subName; // 조회용: s.sub_name
    private String teamName; // 조회용: t.team_name

    private String evalTypeCode; // 조회용: c.eval_type_code

    // 🔹 추가: 실제 EV/TYPE 코드
    private String dataEv; // D, E ...
    private String dataType; // AA, AB ...

    /** user_roles 테이블에서 GROUP_CONCAT 으로 가져온 역할 CSV (예: "SUB_HEAD,TEAM_MEMBER") */
    private String rolesCsv;

    public boolean isPasswordSet() {
        // DB에 "null" 문자열이 들어간 경우까지 방어
        return this.pwd != null
                && !this.pwd.isBlank()
                && !"null".equalsIgnoreCase(this.pwd);
    }
}
