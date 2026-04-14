package com.sync.agent.bojoint.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_TM_GD014001")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvTmGd014001 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SN")
    private Long sn;

    @Column(name = "TTL", length = 500, nullable = false)
    private String ttl;

    @Column(name = "ORGNL_URL", length = 500, nullable = false)
    private String orgnlUrl;

    @Column(name = "LINK", length = 1000)
    private String link;

    @Column(name = "EXPLN", length = 4000)
    private String expln;

    @Column(name = "PSTG_YMD", length = 500)
    private String pstgYmd;

    @Column(name = "PRESS_NM", length = 100)
    private String pressNm;

    @Column(name = "VSTR_CNT")
    private Long vstrCnt;

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
