package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;

/**
 * 수질측정망검사결과 제공용 테이블
 */
@Entity
@Table(name = "api_prv_tm_gd110302")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd110302 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sn;

    /** 수질검사일련번호 */
    @Column(nullable = false)
    private Long wq_insp_sn;

    /** 수질검사항목코드 */
    @Column(length = 4, nullable = false)
    private String wq_insp_artcl_cd;

    /** 결과값 */
    @Column(length = 20)
    private String rslt_vl;
}
