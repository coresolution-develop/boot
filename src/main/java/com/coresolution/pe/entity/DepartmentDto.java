package com.coresolution.pe.entity;

import java.util.ArrayList;
import java.util.List;

public class DepartmentDto {
    private String subCode;
    private String subName;
    private List<UserPE> users = new ArrayList<>();

    public DepartmentDto(String subName, List<UserPE> users) {
        this.subName = subName;
        this.users = users;
    }

    // getters & setters
    public String getSubCode() {
        return subCode;
    }

    public void setSubCode(String subCode) {
        this.subCode = subCode;
    }

    public String getSubName() {
        return subName;
    }

    public void setSubName(String subName) {
        this.subName = subName;
    }

    public List<UserPE> getUsers() {
        return users;
    }

    public void setUsers(List<UserPE> users) {
        this.users = users;
    }

    @Override
    public String toString() {
        return "DepartmentDto{subCode='" + subCode + "', subName='" + subName
                + "', users=" + (users == null ? 0 : users.size()) + "}";
    }
}
