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
 * 이용량 상태 데이터
 * - 원본: USE_STATUS (source.xml select_use_status_data)
 * - 컬럼: SN, TELNO, OBSR_DT, LAST_CHANGE_DT, STAT
 */
@Entity
@Table(name = "use_status_data")
@org.hibernate.annotations.Table(appliesTo = "use_status_data", comment = "이용량 상태 데이터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UseStatusData {

    @Id
    @Column(name = "sn")
    @Comment("일련번호 (PK)")
    private Long sn;

    @Column(name = "telno", length = 20)
    @Comment("전화번호")
    private String telno;

    @Column(name = "obsr_dt")
    @Comment("관측일시")
    private LocalDateTime obsrDt;

    @Column(name = "last_change_dt")
    @Comment("최종변경일시")
    private LocalDateTime lastChangeDt;

    @Column(name = "stat", length = 5)
    @Comment("상태 (Y/N)")
    private String stat;

    @Builder.Default
    @Column(name = "link_status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING'")
    @Comment("SND 연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";
}
