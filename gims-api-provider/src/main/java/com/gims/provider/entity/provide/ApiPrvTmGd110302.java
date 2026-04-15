package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;

/**
 * 수질측정망검사결과 제공용 테이블
 * 원본: TM_GD30302 → 표준화: TM_GD110302
 */
@Entity
@Table(name = "API_PRV_TM_GD110302")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd110302 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    private Long sn;

    @Column(name = "WQ_INSP_SN", nullable = false)
    private Long wqInspSn;

    @Column(name = "WQ_INSP_ARTCL_CD", length = 4, nullable = false)
    private String wqInspArtclCd;

    @Column(name = "RSLT_VL", length = 20)
    private String rsltVl;
}
