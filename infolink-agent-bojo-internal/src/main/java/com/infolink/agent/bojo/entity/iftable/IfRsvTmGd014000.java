package com.infolink.agent.bojo.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_TM_GD014000")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvTmGd014000 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

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
