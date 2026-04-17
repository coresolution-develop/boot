package com.coresolution.pe.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KpiInfoGeneral2025Mapper {

    // 필요하면 전체 초기화용
    @Delete("TRUNCATE TABLE personnel_evaluation.kpi_info_general_2025")
    void truncateAll();

    @Insert("""
            INSERT INTO personnel_evaluation.kpi_info_general_2025 (
                kcol01,  -- 소속기관명
                kcol02,  -- 사번
                kcol03,  -- 총점
                kcol04,  -- 신환 입원 연계 건수
                kcol05,  -- 장례 연계 건수
                kcol06,  -- 입원+장례 연계 건수
                kcol07,  -- 개인지표(홍보)
                kcol08,  -- 100점 환산(홍보)
                kcol09,  -- Remarks(홍보)
                kcol10,  -- 참여횟수(봉사)
                kcol11,  -- 개인지표(봉사)
                kcol12,  -- 100점 환산(봉사)
                kcol13,  -- Remarks(봉사)
                kcol14,  -- 교육 이수율(%)
                kcol15,  -- 개인지표(교육)
                kcol16,  -- 100점 환산(교육)
                kcol17   -- Remarks(교육)
            ) VALUES (
                #{kcol01},
                #{kcol02},
                #{kcol03},
                #{kcol04},
                #{kcol05},
                #{kcol06},
                #{kcol07},
                #{kcol08},
                #{kcol09},
                #{kcol10},
                #{kcol11},
                #{kcol12},
                #{kcol13},
                #{kcol14},
                #{kcol15},
                #{kcol16},
                #{kcol17}
            )
            ON DUPLICATE KEY UPDATE
                kcol01 = VALUES(kcol01),
                kcol03 = VALUES(kcol03),
                kcol04 = VALUES(kcol04),
                kcol05 = VALUES(kcol05),
                kcol06 = VALUES(kcol06),
                kcol07 = VALUES(kcol07),
                kcol08 = VALUES(kcol08),
                kcol09 = VALUES(kcol09),
                kcol10 = VALUES(kcol10),
                kcol11 = VALUES(kcol11),
                kcol12 = VALUES(kcol12),
                kcol13 = VALUES(kcol13),
                kcol14 = VALUES(kcol14),
                kcol15 = VALUES(kcol15),
                kcol16 = VALUES(kcol16),
                kcol17 = VALUES(kcol17)
            """)
    int upsertRow(Map<String, Object> row);
}