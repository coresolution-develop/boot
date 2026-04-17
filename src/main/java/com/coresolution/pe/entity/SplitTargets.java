package com.coresolution.pe.entity;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class SplitTargets {

    private List<UserPE> defaultOnly;
    private List<UserPE> customOnly;
    private List<UserPE> overlap; // 화면에서 안 쓰면 무시
    private Map<String, List<UserPE>> defaultGrouped;
    private Map<String, List<UserPE>> customGrouped;

}
