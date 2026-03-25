package com.infolink.collector.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 안양시 이용량 데이터 (API 수신 데이터)
 * 복합 PK: account_no + meter_dtm
 */
@Entity
@Table(name = "anyang_api_data")
@IdClass(AnyangApiData.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnyangApiData {

    @Id
    @Column(name = "account_no", length = 50)
    @Comment("수용가번호")
    private String accountNo;

    @Id
    @Column(name = "meter_dtm")
    @Comment("검침일시")
    private LocalDateTime meterDtm;

    @Column(name = "value")
    @Comment("검침값")
    private Long value;

    @Column(name = "digits")
    @Comment("소수점 자릿수")
    private Integer digits;

    @Column(name = "leak_state", length = 10)
    @Comment("누수상태")
    private String leakState;

    @Column(name = "term_batt")
    @Comment("단말기 배터리")
    private Integer termBatt;

    @Column(name = "m_low_batt", length = 10)
    @Comment("저전압 여부")
    private String mLowBatt;

    @Column(name = "m_leak", length = 10)
    @Comment("누수 여부")
    private String mLeak;

    @Column(name = "m_over_load", length = 10)
    @Comment("과부하 여부")
    private String mOverLoad;

    @Column(name = "m_reverse", length = 10)
    @Comment("역류 여부")
    private String mReverse;

    @Column(name = "m_not_use", length = 10)
    @Comment("미사용 여부")
    private String mNotUse;

    @Column(name = "db_in_dtm")
    @Comment("DB 입력일시")
    private LocalDateTime dbInDtm;

    @Column(name = "db_in_seq")
    @Comment("DB 입력 시퀀스")
    private Long dbInSeq;

    @Column(name = "last_meter_value")
    @Comment("최종 검침값")
    private Long lastMeterValue;

    @Column(name = "usgqty")
    @Comment("사용량 (현재값 - 이전 최종검침값)")
    private Long usgqty;

    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String accountNo;
        private LocalDateTime meterDtm;
    }
}
