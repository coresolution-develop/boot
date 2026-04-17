package com.coresolution.pe.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TargetLight {
    private String empId;
    private String empName;
    private String deptName;
    private String position;
}