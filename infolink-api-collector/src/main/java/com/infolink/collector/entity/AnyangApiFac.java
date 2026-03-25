package com.infolink.collector.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 안양시 시설정보 (API 수신 데이터)
 */
@Entity
@Table(name = "anyang_api_fac")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnyangApiFac {

    @Id
    @Column(name = "account_no", length = 50)
    @Comment("수용가번호 (UK)")
    private String accountNo;

    @Column(name = "company_cd", length = 10)
    @Comment("업체코드")
    private String companyCd;

    @Column(name = "company_nm", length = 100)
    @Comment("업체명")
    private String companyNm;

    @Column(name = "account_nm", length = 100)
    @Comment("수용가명")
    private String accountNm;

    @Column(name = "status_device", length = 10)
    @Comment("장비상태")
    private String statusDevice;

    @Column(name = "connect_dtm")
    @Comment("연결일시")
    private LocalDateTime connectDtm;

    @Column(name = "state_display", length = 10)
    @Comment("표시상태")
    private String stateDisplay;

    @Column(name = "device_sn", length = 50)
    @Comment("장비 시리얼번호")
    private String deviceSn;

    @Column(name = "gps_latitude", length = 20)
    @Comment("위도")
    private String gpsLatitude;

    @Column(name = "gps_longitude", length = 20)
    @Comment("경도")
    private String gpsLongitude;

    @Column(name = "meter_sn", length = 50)
    @Comment("계량기 시리얼번호")
    private String meterSn;

    @Column(name = "caliber_cd", length = 10)
    @Comment("구경코드")
    private String caliberCd;

    @Column(name = "mt_down", length = 10)
    @Comment("다운여부")
    private String mtDown;

    @Column(name = "mt_down_dtm")
    @Comment("다운일시")
    private LocalDateTime mtDownDtm;

    @Column(name = "mt_last_dtm")
    @Comment("최종검침일시")
    private LocalDateTime mtLastDtm;

    @Column(name = "full_addr", length = 200)
    @Comment("주소")
    private String fullAddr;

    @Column(name = "cdma_no", length = 50)
    @Comment("CDMA 번호")
    private String cdmaNo;

    @Column(name = "nwk", length = 50)
    @Comment("NWK")
    private String nwk;
}
