package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
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
@Table(name = "api_prv_inspection",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_inspection_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_inspection", comment = "수질검사항목별기준 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "ugwtr_exmn_cd", length = 3)
    @Comment("지하수조사코드")
    private String ugwtrExmnCd;

    @Column(name = "data_crtr_yr", length = 4)
    @Comment("자료기준연도")
    private String dataCrtrYr;

    @Column(name = "wq_insp_artcl_cd", length = 4)
    @Comment("수질검사항목코드")
    private String wqInspArtclCd;

    @Column(name = "code_cn", length = 200)
    @Comment("항목명(코드내용)")
    private String codeCn;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    @Comment("실행 ID")
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    @Comment("소스 참조")
    private String sourceRefs;

    @Column(name = "updated_at")
    @Comment("갱신 시각")
    private LocalDateTime updatedAt;
}
