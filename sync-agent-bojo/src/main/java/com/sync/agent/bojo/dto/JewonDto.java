package com.sync.agent.bojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 제원(관측소 기본정보) DTO
 * Source: PostgreSQL sec_jewon_view
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JewonDto {

    /** 관측소 코드 (PK) */
    private String obsvCode;

    /** 관측소명 */
    private String obsvName;

    /** 관정번호 */
    private String well;

    /** 시도 */
    private String sido;

    /** 시군구 */
    private String sigungu;

    /** 읍면동 */
    private String upmyundo;

    /** 번지 */
    private String bunji;

    /** 리 */
    private String ri;

    /** X 좌표 */
    private Double x;

    /** Y 좌표 */
    private Double y;

    /** 표고 */
    private Double pyogo;

    /** 설치일 */
    private String insdate;

    /** 굴착심도 */
    private Double guldep;

    /** 굴착경 */
    private Double guldia;

    /** 등록일 */
    private String regdate;

    /** 케이싱 높이 */
    private Double casingHeight;

    // Target 테이블 추가 컬럼들
    private String regionCode;
    private String roadaddr;
    private String mgrOrg;
    private String remark;
    private String gigwanitem;
    private String gigwanmethod;
    private String yongdoCd;
    private String umyong;
    private String frontImg;
    private String nearImg;
    private Integer gennum;
    private String inpermNo;
    private String obsvType;
}
