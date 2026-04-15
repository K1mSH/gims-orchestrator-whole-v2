package com.sync.agent.bojoint.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_TM_GD010310")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvTmGd010310 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SN")
    private Long sn;

    // ========== 비즈니스 컬럼 ==========

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

    // ========== IF 메타 컬럼 ==========

    @Column(name = "SOURCE_REFS", length = 4000, nullable = false)
    private String sourceRefs;

    @Column(name = "LINK_STATUS", length = 20)
    @Builder.Default
    private String linkStatus = "PENDING";

    @Column(name = "EXTRACTED_AT")
    private LocalDateTime extractedAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "EXECUTION_ID")
    private String executionId;
}
