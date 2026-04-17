package com.coresolution.pe.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FormMapper {

    List<Map<String, String>> selectDistinctCombos(String year);
    
}
