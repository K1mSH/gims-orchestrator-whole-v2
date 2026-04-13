package com.sync.agent.bojoint.entity.target;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD970002")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd970002 {

    @Id
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    @Column(name = "CHCK_MTNC_SAREA_CD", length = 3)
    private String chckMtncSareaCd;

    @Column(name = "USE_YN", columnDefinition = "CHAR(1)")
    private String useYn;

    @Column(name = "ADJC_WTOBS_ID", length = 40)
    private String adjcWtobsId;

    @Column(name = "RNSTN_ID", length = 40)
    private String rnstnId;

    @Column(name = "WLSTN_ID", length = 40)
    private String wlstnId;

    @Column(name = "TELNO", length = 100)
    private String telno;

    @Column(name = "RNFMT_YN", columnDefinition = "CHAR(1)")
    private String rnfmtYn;

    @Column(name = "CMN_MTHD_CN", length = 500)
    private String cmnMthdCn;

    @Column(name = "DMND_YN", columnDefinition = "CHAR(1)")
    private String dmndYn;

    @Column(name = "MGAGC_NM")
    private String mgagcNm;

    @Column(name = "CNST_INST_CN", length = 100)
    private String cnstInstCn;

    @Column(name = "OBSVTR_ENG_NM", length = 100)
    private String obsvtrEngNm;

    @Column(name = "OBSVTR_ENG_LOTNO_ADDR", length = 1000)
    private String obsvtrEngLotnoAddr;

    @Column(name = "OBSVTR_ENG_ADDR", length = 1000)
    private String obsvtrEngAddr;

    @Column(name = "RDNM", length = 250)
    private String rdnm;

    @Column(name = "OBSVTR_SPWR_KND_CD", columnDefinition = "CHAR(1)")
    private String obsvtrSpwrKndCd;

    @Column(name = "RNFMT_MSRMT_YN", columnDefinition = "CHAR(1)")
    private String rnfmtMsrmtYn;

    @Column(name = "CHCK_MTNC_BSN_CD", length = 3)
    private String chckMtncBsnCd;

    @Column(name = "MOTN_MOD_VL", length = 1)
    private String motnModVl;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
