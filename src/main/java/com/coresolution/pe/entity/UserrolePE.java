package com.coresolution.pe.entity;

import java.util.List;

public class UserrolePE {
    private int idx;
    private String userId;
    private List<Role> roles;
    private int evalYear;

    public UserrolePE() {

    }

    public UserrolePE(int idx, String userId, List<Role> roles, int evalYear) {
        this.idx = idx;
        this.userId = userId;
        this.roles = roles;
        this.evalYear = evalYear;
    }

    public enum Role {
        TEAM_HEAD,
        TEAM_MEMBER,
        SUB_HEAD,
        SUB_MEMBER,
        ONE_PERSON_SUB,
        MEDICAL_LEADER,
        AFF_ORG_HEAD,
        AFF_AGC_HEAD,
        AFF_SUB_HEAD
    }

    /**
     * @return the idx
     */
    public int getIdx() {
        return idx;
    }

    /**
     * @param idx the idx to set
     */
    public void setIdx(int idx) {
        this.idx = idx;
    }

    /**
     * @return the user_id
     */
    public String getUserId() {
        return userId;
    }

    /**
     * @param user_id the user_id to set
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * @return the roles
     */
    public List<Role> getRoles() {
        return roles;
    }

    /**
     * @param roles the roles to set
     */
    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    /**
     * @return the eval_year
     */
    public int getEvalYear() {
        return evalYear;
    }

    /**
     * @param eval_year the eval_year to set
     */
    public void setEvalYear(int evalYear) {
        this.evalYear = evalYear;
    }

    @Override
    public String toString() {
        return "UserrolePE [idx=" + idx + ", user_id=" + userId + ", roles=" + roles + ", eval_year=" + evalYear
                + "]";
    }
}
