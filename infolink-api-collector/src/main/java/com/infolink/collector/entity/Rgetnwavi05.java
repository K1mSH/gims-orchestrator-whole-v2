package com.infolink.collector.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 제주 수질검사
 * - 원본: RGETNWAVI05 (새올 DB)
 * - 레거시: RgetnwaviProgram → selectSujil.json → MERGE INTO RGETNWAVI05
 * - PK: PERM_NT_NO
 */
@Entity
@Table(name = "rgetnwavi05")
@org.hibernate.annotations.Table(appliesTo = "rgetnwavi05", comment = "제주 수질검사")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnwavi05 {

    @Id
    @Column(name = "perm_nt_no", length = 50)
    @Comment("허가신고번호 (PK)")
    private String permNtNo;

    @Column(name = "rel_trans_cgg_code", length = 10)
    @Comment("시군구코드 (제주시=6510000, 서귀포시=6520000)")
    private String relTransCggCode;

    @Column(name = "qw_isp_sno", length = 20)
    @Comment("수질검사일련번호 (acceptNum 하이픈 뒤)")
    private String qwIspSno;

    @Column(name = "qw_isp_sort_code", length = 5)
    @Comment("검사구분코드 (음용수(원수)=A, else=D)")
    private String qwIspSortCode;

    @Column(name = "first_reg_dthr")
    @Comment("등록일시")
    private LocalDateTime firstRegDthr;
}
