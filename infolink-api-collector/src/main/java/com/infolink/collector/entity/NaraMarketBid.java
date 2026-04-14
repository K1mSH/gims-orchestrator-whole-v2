package com.infolink.collector.entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * 나라장터 공고 수집 API 적재 테이블
 * 테이블명: tm_gd014000
 * UK: bid_pbanc_no (UPSERT conflict key)
 */
@Entity
@Table(name = "tm_gd014000", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tm_gd014000_bid_pbanc_no", columnNames = "bid_pbanc_no")
})
@org.hibernate.annotations.Table(appliesTo = "tm_gd014000", comment = "나라장터_공고수집API")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaraMarketBid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "type", length = 100)
    @Comment("유형")
    private String type;

    @Column(name = "bid_pbanc_no", length = 100)
    @Comment("입찰공고번호")
    private String bidPbancNo;

    @Column(name = "bid_pbanc_nm", length = 1000)
    @Comment("입찰공고명")
    private String bidPbancNm;

    @Column(name = "dmd_inst_nm", length = 200)
    @Comment("수요기관명")
    private String dmdInstNm;

    @Column(name = "bid_ddln_dt", length = 100)
    @Comment("입찰마감일시")
    private String bidDdlnDt;

    @Column(name = "bid_pbanc_dtl_lnkg", length = 1000)
    @Comment("입찰공고상세연결")
    private String bidPbancDtlLnkg;

    @Column(name = "use_yn", length = 1)
    @Comment("사용여부")
    @ColumnDefault("'Y'")
    @Builder.Default
    private String useYn = "Y";

    @Column(name = "reg_ymd", length = 8)
    @Comment("등록일자")
    @ColumnDefault("to_char(now(), 'YYYYMMDD')")
    private String regYmd;

    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private String linkStatus = "PENDING";
}
