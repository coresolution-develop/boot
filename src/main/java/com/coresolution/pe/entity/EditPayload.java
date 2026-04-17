package com.coresolution.pe.entity;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditPayload {
    private Map<String, String> radios;
    private Map<String, String> essays;
}