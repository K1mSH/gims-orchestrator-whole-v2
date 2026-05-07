package com.infolink.agent.bojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 연계(동기화 추적) DTO
 * Target: Oracle link_ngwis 테이블
 * 마지막 동기화 시점을 추적하여 증분 동기화에 사용
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkDto {

    /** 관측소 코드 (PK) */
    private String obsvCode;

    /** 마지막 관측일자 (YYYYMMDD 또는 YYYY-MM-DD) */
    private String obsvDate;

    /** 마지막 관측시간 (HHMM 또는 HH:MM) */
    private String obsvTime;
}
