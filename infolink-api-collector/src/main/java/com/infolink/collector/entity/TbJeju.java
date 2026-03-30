package com.infolink.collector.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * 제주도 보조지하수관측망 수위 관측 데이터
 * - 원본: NGWIS.TB_JEJU (DMZ 보조망 Oracle)
 * - 레거시: InsertJeju → selectObsvData.json API 수집 결과
 * - 테이블 자동 생성 (ddl-auto=update) + 향후 읽기용
 * - 쓰기는 JejuObsvDataExecutor에서 JdbcTemplate으로 수행
 */
@Entity
@Table(name = "tb_jeju", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tb_jeju_obs", columnNames = {"obsrvt_id", "ymd", "data_time", "msn"})
})
@org.hibernate.annotations.Table(appliesTo = "tb_jeju", comment = "제주 보조관측망 수위 관측 데이터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TbJeju {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rid")
    @Comment("PK (Oracle: SEQ_JEJU)")
    private Long rid;

    @Column(name = "obsrvt_id", length = 30)
    @Comment("관측점 ID (= tb_jeju_jewon.obsrvt_id)")
    private String obsrvtId;

    @Column(name = "ymd", length = 8)
    @Comment("관측일 (yyyyMMdd)")
    private String ymd;

    @Column(name = "data_time", length = 6)
    @Comment("관측시각 (HHmmss)")
    private String dataTime;

    @Column(name = "gl", length = 20)
    @Comment("지하수위")
    private String gl;

    @Column(name = "scond", length = 20)
    @Comment("전기전도도")
    private String scond;

    @Column(name = "wtemp", length = 20)
    @Comment("수온")
    private String wtemp;

    @Column(name = "msn", length = 20)
    @Comment("센서 식별 (S11, S21, S22 등)")
    private String msn;
}
