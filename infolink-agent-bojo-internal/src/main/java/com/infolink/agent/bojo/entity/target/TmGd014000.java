package com.infolink.agent.bojo.entity.target;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD014000",
       uniqueConstraints = @UniqueConstraint(name = "UK_TM_GD014000", columnNames = "BID_PBANC_NO"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd014000 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    private Long sn;

    @Column(name = "TYPE", length = 100)
    private String type;

    @Column(name = "BID_PBANC_NO", length = 100)
    private String bidPbancNo;

    @Column(name = "BID_PBANC_NM", length = 1000)
    private String bidPbancNm;

    @Column(name = "DMD_INST_NM", length = 200)
    private String dmdInstNm;

    @Column(name = "BID_DDLN_DT", length = 100)
    private String bidDdlnDt;

    @Column(name = "BID_PBANC_DTL_LNKG", length = 1000)
    private String bidPbancDtlLnkg;

    @Column(name = "USE_YN", length = 1)
    private String useYn;

    @Column(name = "REG_YMD", length = 8)
    private String regYmd;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;
}
