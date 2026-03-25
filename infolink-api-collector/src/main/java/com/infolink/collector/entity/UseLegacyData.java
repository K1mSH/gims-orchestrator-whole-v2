package com.infolink.collector.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 이용량 결과 기록 (FAC + DATA JOIN 파생)
 */
@Entity
@Table(name = "use_legacy_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UseLegacyData {

    @Id
    @Column(name = "sn")
    @Comment("시퀀스 (db_in_seq)")
    private Long sn;

    @Column(name = "telno", length = 20)
    @Comment("전화번호 (0 + cdma_no 8~18자리)")
    private String telno;

    @Column(name = "obsr_dt")
    @Comment("관측일시 (meter_dtm)")
    private LocalDateTime obsrDt;

    @Column(name = "last_measure_value")
    @Comment("최종검침값")
    private Long lastMeasureValue;

    @Column(name = "usgqty")
    @Comment("사용량")
    private Long usgqty;

    @Column(name = "last_change_dt")
    @Comment("최종변경일시 (db_in_dtm)")
    private LocalDateTime lastChangeDt;
}
