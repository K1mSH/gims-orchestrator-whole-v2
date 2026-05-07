package com.infolink.agent.bojo.entity.iftable.saeol;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_RGETNMNFE01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvRgetnmnfe01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SNO")
    private Long sno;

    @Column(name = "UPCH_REGNUM", length = 36)
    private String upchRegnum;

    @Column(name = "UPCH_SNO", length = 3)
    private String upchSno;

    @Column(name = "UPCH_GBN", columnDefinition = "CHAR(1)")
    private String upchGbn;

    @Column(name = "MPW_EQI_GBN", columnDefinition = "CHAR(1)")
    private String mpwEqiGbn;

    @Column(name = "NM", length = 60)
    private String nm;

    @Column(name = "SID", length = 36)
    private String sid;

    @Column(name = "SID_SNO", length = 3)
    private String sidSno;

    @Column(name = "ADDR", length = 200)
    private String addr;

    @Column(name = "JCMP_YMD", columnDefinition = "CHAR(8)")
    private String jcmpYmd;

    @Column(name = "RSGN_YMD", columnDefinition = "CHAR(8)")
    private String rsgnYmd;

    @Column(name = "TELNO", length = 30)
    private String telno;

    @Column(name = "EQI_NM", length = 100)
    private String eqiNm;

    @Column(name = "LEAS_YN", columnDefinition = "CHAR(1)")
    private String leasYn;

    @Column(name = "LEAS_STDT", columnDefinition = "CHAR(8)")
    private String leasStdt;

    @Column(name = "LEAS_ENDDT", columnDefinition = "CHAR(8)")
    private String leasEnddt;

    @Column(name = "MPW_REM", length = 1000)
    private String mpwRem;

    @Column(name = "EQI_REM", length = 1000)
    private String eqiRem;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "USR_ID", length = 20)
    private String usrId;

    @Column(name = "LAST_CORT_ID", length = 20)
    private String lastCortId;

    @Column(name = "RDN_CGG_CODE", length = 5)
    private String rdnCggCode;

    @Column(name = "RDN_CODE", length = 7)
    private String rdnCode;

    @Column(name = "RDN_UMD_GBN", length = 1)
    private String rdnUmdGbn;

    @Column(name = "RDN_UMD_CODE", length = 10)
    private String rdnUmdCode;

    @Column(name = "RDN_BDNG_ORI_NO", length = 5)
    private String rdnBdngOriNo;

    @Column(name = "RDN_BDNG_SUB_NO", length = 5)
    private String rdnBdngSubNo;

    @Column(name = "RDN_BDNG_FLR_GBN", length = 1)
    private String rdnBdngFlrGbn;

    @Column(name = "RDN_SPEC_ADDR", length = 200)
    private String rdnSpecAddr;

    @Column(name = "RDN_WHL_ADDR", length = 500)
    private String rdnWhlAddr;

    @Column(name = "RDN_POST_NO", length = 6)
    private String rdnPostNo;

    @Column(name = "RDN_ADMDNG_CODE", length = 10)
    private String rdnAdmdngCode;

    @Column(name = "RDN_NM", length = 80)
    private String rdnNm;

    @Column(name = "RDN_UMD_NM", length = 30)
    private String rdnUmdNm;

    @Column(name = "RDN_ADMDNG_NM", length = 30)
    private String rdnAdmdngNm;

    @Column(name = "REL_TRANS_CGG_CODE", length = 8)
    private String relTransCggCode;

    @Column(name = "SOURCE_REFS", length = 4000)
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
