package com.sync.agent.bojoint.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD970101")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd970101 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RSLT_ID")
    private Long rsltId;

    @Column(name = "RSLT_DT")
    private LocalDateTime rsltDt;

    @Column(name = "OBSRVN_INST_ID", length = 20)
    private String obsrvnInstId;

    @Column(name = "OBSRVN_ARTCL_ID", nullable = false)
    private Long obsrvnArtclId;

    @Column(name = "UNIT_ID", nullable = false)
    private Long unitId;

    @Column(name = "EXS_ID")
    private Long exsId;

    @Column(name = "MTHD_ID", nullable = false)
    private Long mthdId;

    @Column(name = "OBSRVN_PIC_ID", length = 40)
    private String obsrvnPicId;

    @Column(name = "BRNCH_ID", nullable = false)
    private Long brnchId;

    @Column(name = "HR_GAP_VL")
    private Long hrGapVl;

    @Column(name = "HR_UNIT_ID")
    private Long hrUnitId;

    @Column(name = "MSRINS_SNSR_ID", length = 20)
    private String msrinsSnsrId;

    @Column(name = "GNRL_CTGRY_MNG_TRM_NM", length = 100)
    private String gnrlCtgryMngTrmNm;

    @Column(name = "VL_TYPE_MNG_TRM_NM", length = 100)
    private String vlTypeMngTrmNm;

    @Column(name = "DATA_TYPE_MNG_TRM_NM", length = 100)
    private String dataTypeMngTrmNm;

    @Column(name = "TAG_CN", length = 4000)
    private String tagCn;

    @Column(name = "FRST_REG_DT")
    private LocalDateTime frstRegDt;

    @Column(name = "LAST_CHG_DT")
    private LocalDateTime lastChgDt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
