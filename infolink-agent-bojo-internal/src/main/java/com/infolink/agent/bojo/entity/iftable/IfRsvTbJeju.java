package com.infolink.agent.bojo.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_TB_JEJU")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvTbJeju {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "RID")
    private Long rid;

    @Column(name = "OBSRVT_ID", length = 30)
    private String obsrvtId;

    @Column(name = "YMD", length = 8)
    private String ymd;

    @Column(name = "DATA_TIME", length = 6)
    private String dataTime;

    @Column(name = "GL", length = 20)
    private String gl;

    @Column(name = "SCOND", length = 20)
    private String scond;

    @Column(name = "WTEMP", length = 20)
    private String wtemp;

    @Column(name = "MSN", length = 20)
    private String msn;

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
