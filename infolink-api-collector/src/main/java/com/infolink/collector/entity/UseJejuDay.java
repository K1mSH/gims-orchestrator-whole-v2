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
 * 제주 일일이용량
 * - 원본: USE_JEJU_DAY (외부 시스템이 적재)
 * - DDL: OBSRVT_ID VARCHAR(30), OBSR_DE VARCHAR(8), USGQTY NUMBER, ...
 * - 복합PK: OBSRVT_ID + OBSR_DE
 * - 우리 역할: SND로 퍼나르기만 담당 (적재 관여 X)
 */
@Entity
@Table(name = "use_jeju_day")
@org.hibernate.annotations.Table(appliesTo = "use_jeju_day", comment = "제주 일일이용량 (외부 적재)")
@IdClass(UseJejuDay.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UseJejuDay {

    @Id
    @Column(name = "obsrvt_id", length = 30)
    @Comment("관측점 ID (PK1)")
    private String obsrvtId;

    @Id
    @Column(name = "obsr_de", length = 8)
    @Comment("관측일 yyyyMMdd (PK2)")
    private String obsrDe;

    @Column(name = "usgqty")
    @Comment("사용량")
    private Double usgqty;

    @Column(name = "last_mesure_value")
    @Comment("최종 계량값")
    private Double lastMesureValue;

    @Column(name = "frst_mesure_value")
    @Comment("최초 계량값")
    private Double frstMesureValue;

    @Column(name = "dta_sttus_code", length = 2)
    @Comment("데이터 상태 코드")
    private String dtaSttusCode;

    @Builder.Default
    @Column(name = "link_status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING'")
    @Comment("SND 연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private String obsrvtId;
        private String obsrDe;
    }
}
