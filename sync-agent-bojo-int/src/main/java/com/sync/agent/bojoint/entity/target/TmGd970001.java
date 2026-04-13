package com.sync.agent.bojoint.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD970001")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd970001 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    @Column(name = "OBSVTR_NM", length = 100)
    private String obsvtrNm;

    @Column(name = "OBSVTR_ID", length = 30)
    private String obsvtrId;

    @Column(name = "INSTL_YMD", length = 8)
    private String instlYmd;

    @Column(name = "LOT", length = 20)
    private String lot;

    @Column(name = "LAT", length = 20)
    private String lat;

    @Column(name = "STDG_CD", length = 10)
    private String stdgCd;

    @Column(name = "CDSSTM_ID", length = 8)
    private String cdsstmId;

    @Column(name = "STD_BSN_CD", length = 6)
    private String stdBsnCd;

    @Column(name = "BRNCH_TYPE_MNG_TRM_NM", length = 100)
    private String brnchTypeMngTrmNm;

    @Column(name = "VTCL_CRLPT_MNG_TRM_NM", length = 100)
    private String vtclCrlptMngTrmNm;

    @Column(name = "SPCEDATA_TYPE_MNG_TRM_NM", length = 100)
    private String spcedataTypeMngTrmNm;

    @Column(name = "ADDR", length = 250)
    private String addr;

    @Column(name = "RMRK_CN", length = 4000)
    private String rmrkCn;

    @Column(name = "FRST_REG_DT")
    private LocalDateTime frstRegDt;

    @Column(name = "LAST_CHG_DT")
    private LocalDateTime lastChgDt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
