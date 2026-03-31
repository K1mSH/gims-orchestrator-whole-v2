package com.infolink.collector.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

/**
 * 제주 수질검사내역
 * - 원본: RGETNWAVI06 (새올 DB)
 * - 레거시: RgetnwaviProgram → selectSujil.json → MERGE INTO RGETNWAVI06
 * - 복합PK: PERM_NT_NO + LIST_CODE
 */
@Entity
@Table(name = "rgetnwavi06")
@org.hibernate.annotations.Table(appliesTo = "rgetnwavi06", comment = "제주 수질검사내역")
@IdClass(Rgetnwavi06.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnwavi06 {

    @Id
    @Column(name = "perm_nt_no", length = 50)
    @Comment("허가신고번호 (PK1)")
    private String permNtNo;

    @Id
    @Column(name = "list_code", length = 30)
    @Comment("항목코드 (PK2, 영문)")
    private String listCode;

    @Column(name = "rel_trans_cgg_code", length = 10)
    @Comment("시군구코드")
    private String relTransCggCode;

    @Column(name = "qw_isp_sno", length = 20)
    @Comment("수질검사일련번호")
    private String qwIspSno;

    @Column(name = "qw_isp_sort_code", length = 5)
    @Comment("검사구분코드 (A/D)")
    private String qwIspSortCode;

    @Column(name = "rt", length = 50)
    @Comment("검사결과값")
    private String rt;

    @Column(name = "elig_yn", length = 2)
    @Comment("적합여부 (고정값 1)")
    private String eligYn;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String permNtNo;
        private String listCode;
    }
}
