package com.coresolution.pe.entity;

public class NoticeVo {

    private int idx;
    private String d1;
    private String d2;
    private String d3;

    public int getIdx() {
        return idx;
    }

    public void setIdx(int idx) {
        this.idx = idx;
    }

    public String getD1() {
        return d1;
    }

    public void setD1(String d1) {
        this.d1 = d1;
    }

    public String getD2() {
        return d2;
    }

    public void setD2(String d2) {
        this.d2 = d2;
    }

    public String getD3() {
        return d3;
    }

    public void setD3(String d3) {
        this.d3 = d3;
    }

    @Override
    public String toString() {
        return "NoticeVo [idx=" + idx + ", d1=" + d1 + ", d2=" + d2 + ", d3=" + d3 + "]";
    }

}
