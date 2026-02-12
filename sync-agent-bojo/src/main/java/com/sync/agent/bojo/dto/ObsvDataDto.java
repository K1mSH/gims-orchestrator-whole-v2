package com.sync.agent.bojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관측데이터 DTO
 * Source: PostgreSQL sec_obsvdata_view
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObsvDataDto {

    /** 관측소 코드 */
    private String obsvCode;

    /** 관측일자 (YYYYMMDD) */
    private String obsvDate;

    /** 관측시간 (HHMM) */
    private String obsvTime;

    /** 지하수위심도 (Groundwater Depth) */
    private Double gwdep;

    /** 지하수온 (Groundwater Temperature) */
    private Double gwtemp;

    /** 전기전도도 (Electrical Conductivity) */
    private Double ec;

    /** 비고 */
    private String remark;
}
