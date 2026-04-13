package com.sync.agent.bojoint.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_TB_JEJU_JEWON")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvTbJejuJewon {

    @Column(name = "ID", nullable = false)
    private String id;

    @Column(name = "OBSRVT_ID", length = 30)
    private String obsrvtId;

    @Column(name = "OBSRVT_NM", length = 100)
    private String obsrvtNm;

    @Column(name = "SPOT_NM", length = 100)
    private String spotNm;

    @Column(name = "LO_VALUE", length = 20)
    private String loValue;

    @Column(name = "LA_VALUE", length = 20)
    private String laValue;

    @Column(name = "TMX_VALUE")
    private Long tmxValue;

    @Column(name = "TMY_VALUE")
    private Long tmyValue;

    @Column(name = "EXTN_CSNG_CALBR", length = 5)
    private String extnCsngCalbr;

    @Column(name = "BUNJI", length = 40)
    private String bunji;

    @Column(name = "SIGUN_NM", length = 40)
    private String sigunNm;

    @Column(name = "EMD_NM", length = 40)
    private String emdNm;

    @Column(name = "HO", length = 40)
    private String ho;

    @Column(name = "LI_NM", length = 40)
    private String liNm;

    @Column(name = "DRNK_AT", length = 1)
    private String drnkAt;

    @Column(name = "USE_AT", length = 1)
    private String useAt;

    @Column(name = "AL_VALUE", length = 50)
    private String alValue;

    @Column(name = "WAL")
    private Long wal;

    @Column(name = "UGRWTR_PRPOS_CODE", length = 2)
    private String ugrwtrPrposCode;

    @Column(name = "LEGALDONG_CODE", length = 10)
    private String legaldongCode;

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
