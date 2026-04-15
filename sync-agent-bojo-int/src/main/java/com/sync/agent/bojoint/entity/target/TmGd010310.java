package com.sync.agent.bojoint.entity.target;

import javax.persistence.*;
import lombok.*;

/**
 * 약수터 제원정보 Target 엔티티.
 * 테이블: TM_GD010310
 * PK/UK 없음 (레거시 동일). SN은 JPA @Id용 IDENTITY.
 */
@Entity
@Table(name = "TM_GD010310")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd010310 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    private Long sn;

    @Column(name = "SEQ", length = 10)
    private String seq;

    @Column(name = "BRNCH_NO", length = 10, nullable = false)
    private String brnchNo;

    @Column(name = "BRNCH_NM", length = 100, nullable = false)
    private String brnchNm;

    @Column(name = "BRNCH_STD_CD", length = 20, nullable = false)
    private String brnchStdCd;

    @Column(name = "INFO_CRT_INST_NM", length = 50, nullable = false)
    private String infoCrtInstNm;

    @Column(name = "CHRTC_MCLSF", length = 4)
    private String chrtcMclsf;

    @Column(name = "CHRTC_SCLSF", length = 4)
    private String chrtcSclsf;

    @Column(name = "CTPV_NM", length = 40)
    private String ctpvNm;

    @Column(name = "SGG_NM", length = 30)
    private String sggNm;

    @Column(name = "ADDR", length = 500, nullable = false)
    private String addr;

    @Column(name = "STDG_CD", length = 10, nullable = false)
    private String stdgCd;

    @Column(name = "XCRD", length = 20)
    private String xcrd;

    @Column(name = "YCRD", length = 20)
    private String ycrd;

    @Column(name = "ABL_YN", length = 4, nullable = false)
    private String ablYn;

    @Column(name = "ABL_YMD", length = 13)
    private String ablYmd;

    @Column(name = "DAY01_AVG_USR_CNT", length = 10, nullable = false)
    private String day01AvgUsrCnt;

    @Column(name = "PIC", length = 60)
    private String pic;

    @Column(name = "INSTL_YMD", length = 10, nullable = false)
    private String instlYmd;

    @Column(name = "DEL_YN", length = 4, nullable = false)
    private String delYn;

    @Column(name = "PIC_NM", length = 60, nullable = false)
    private String picNm;

    @Column(name = "PIC_CNPL", length = 50, nullable = false)
    private String picCnpl;

    @Column(name = "BNO", length = 30)
    private String bno;

    @Column(name = "LCTN_LOTNO", length = 20)
    private String lctnLotno;

    @Column(name = "RMRK", length = 500)
    private String rmrk;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;
}
