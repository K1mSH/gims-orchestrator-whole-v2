package com.infolink.agent.bojo.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_SEC_JEWON")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvSecJewon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "OBSV_CODE", nullable = false)
    private String obsvCode;

    @Column(name = "OBSV_NAME")
    private String obsvName;

    @Column(name = "WELL")
    private Long well;

    @Column(name = "SIDO")
    private String sido;

    @Column(name = "SIGUNGU")
    private String sigungu;

    @Column(name = "UPMYUNDO")
    private String upmyundo;

    @Column(name = "BUNJI")
    private String bunji;

    @Column(name = "RI")
    private String ri;

    @Column(name = "X")
    private String x;

    @Column(name = "Y")
    private String y;

    @Column(name = "PYOGO")
    private Double pyogo;

    @Column(name = "INSDATE")
    private LocalDateTime insdate;

    @Column(name = "GULDEP")
    private Double guldep;

    @Column(name = "GULDIA")
    private Double guldia;

    @Column(name = "REGDATE")
    private LocalDateTime regdate;

    @Column(name = "CASING_HEIGHT")
    private Double casingHeight;

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
