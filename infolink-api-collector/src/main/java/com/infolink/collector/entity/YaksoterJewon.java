package com.infolink.collector.entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * 약수터 제원정보 수집 API 적재 테이블 (tm_gd010310).
 * UK = (brnch_no, brnch_std_cd) — 자연키 (B-1 정책, 4/29 정정).
 * 같은 약수터 재호출 시 ON CONFLICT DO UPDATE 로 최신값 갱신.
 */
@Entity
@Table(name = "tm_gd010310", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tm_gd010310_brnch", columnNames = {"brnch_no", "brnch_std_cd"})
})
@org.hibernate.annotations.Table(appliesTo = "tm_gd010310", comment = "약수터제원정보수집API")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YaksoterJewon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "seq", length = 10)
    @Comment("순번 (API rowno)")
    private String seq;

    @Column(name = "brnch_no", length = 10)
    @Comment("지점번호")
    private String brnchNo;

    @Column(name = "brnch_nm", length = 100)
    @Comment("지점명")
    private String brnchNm;

    @Column(name = "brnch_std_cd", length = 20)
    @Comment("지점표준코드")
    private String brnchStdCd;

    @Column(name = "info_crt_inst_nm", length = 50)
    @Comment("정보생성기관명")
    private String infoCrtInstNm;

    @Column(name = "chrtc_mclsf", length = 4)
    @Comment("특성중분류")
    private String chrtcMclsf;

    @Column(name = "chrtc_sclsf", length = 4)
    @Comment("특성소분류")
    private String chrtcSclsf;

    @Column(name = "ctpv_nm", length = 40)
    @Comment("시도명")
    private String ctpvNm;

    @Column(name = "sgg_nm", length = 30)
    @Comment("시군구명")
    private String sggNm;

    @Column(name = "addr", length = 500)
    @Comment("주소")
    private String addr;

    @Column(name = "stdg_cd", length = 10)
    @Comment("법정동코드")
    private String stdgCd;

    @Column(name = "xcrd", length = 20)
    @Comment("X좌표")
    private String xcrd;

    @Column(name = "ycrd", length = 20)
    @Comment("Y좌표")
    private String ycrd;

    @Column(name = "abl_yn", length = 4)
    @Comment("폐지여부")
    private String ablYn;

    @Column(name = "abl_ymd", length = 13)
    @Comment("폐지일자")
    private String ablYmd;

    @Column(name = "day01_avg_usr_cnt", length = 10)
    @Comment("1일평균이용자수")
    private String day01AvgUsrCnt;

    @Column(name = "pic", length = 60)
    @Comment("담당자")
    private String pic;

    @Column(name = "instl_ymd", length = 10)
    @Comment("설치일자")
    private String instlYmd;

    @Column(name = "del_yn", length = 4)
    @Comment("삭제여부")
    private String delYn;

    @Column(name = "pic_nm", length = 60)
    @Comment("담당자명")
    private String picNm;

    @Column(name = "pic_cnpl", length = 50)
    @Comment("담당자연락처")
    private String picCnpl;

    @Column(name = "bno", length = 30)
    @Comment("건물번호")
    private String bno;

    @Column(name = "lctn_lotno", length = 20)
    @Comment("소재지_지번")
    private String lctnLotno;

    @Column(name = "rmrk", length = 500)
    @Comment("비고")
    private String rmrk;

    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private String linkStatus = "PENDING";
}
