package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 수질검사항목별기준 제공용 테이블 (Type B — B14/B15)
 *
 * 레거시: OPN searchInspection + searchAllInspection
 * 원본: Oracle TM_GD30310 (레거시) → TM_GD110310 (표준화) + TC_GD00002 JOIN
 * 전처리: JOIN (공통코드 NGW_0026 → 항목명 조회)
 * 복합 PK: ugwtr_exmn_cd + data_crtr_yr + wq_insp_artcl_cd
 */
@Entity
@Table(name = "api_prv_inspection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ApiPrvInspection.PK.class)
public class ApiPrvInspection {

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private String ugwtrExmnCd;
        private String dataCrtrYr;
        private String wqInspArtclCd;
    }

    /** 지하수조사코드 — 레거시: JOSACODE → UGWTR_EXMN_CD */
    @Id
    @Column(name = "ugwtr_exmn_cd", length = 3)
    private String ugwtrExmnCd;

    /** 자료기준연도 — 레거시: DTA_STDR_YEAR → DATA_CRTR_YR */
    @Id
    @Column(name = "data_crtr_yr", length = 4)
    private String dataCrtrYr;

    /** 수질검사항목코드 — 레거시: QLTWTR_INSPCT_IEM_CODE → WQ_INSP_ARTCL_CD */
    @Id
    @Column(name = "wq_insp_artcl_cd", length = 4)
    private String wqInspArtclCd;

    /** 항목명(코드내용) — 레거시: CODE_CTNT(remarkCtnt) */
    @Column(name = "code_cn", length = 200)
    private String codeCn;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
