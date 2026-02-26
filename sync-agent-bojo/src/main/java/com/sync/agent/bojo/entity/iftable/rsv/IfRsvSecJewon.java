package com.sync.agent.bojo.entity.iftable.rsv;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;
import java.time.LocalDateTime;

/**
 * IF_RSV_SEC_JEWON - IF 수신용 제원 테이블 (Target DB)
 *
 * 사용: relay-dmz-rsv-bojo (쓰기), loader-dmz (읽기)
 * 흐름: sec_jewon_view (External) → if_rsv_sec_jewon (Target DB) → sec_jewon (Target DB)
 *
 * PK: Auto-generated (외부 DB 10개가 1개 IF_RSV에 연결되므로 Source ID 충돌 가능)
 * UK: source_refs - 외부 DB에 obsv_code 중복이 존재하므로 source_refs로 충돌 판단
 *     source_refs 형식: ["E:datasourceId:tableId:primaryKey"] → 출처별 자연 유일키
 */
@Entity
@Table(name = "if_rsv_sec_jewon",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_rsv_sec_jewon_source_refs",
           columnNames = {"source_refs"}
       ))
@org.hibernate.annotations.Table(appliesTo = "if_rsv_sec_jewon", comment = "IF_RSV 보조지하수관측망 제원 (외부→RCV→IF 적재, Loader가 읽어서 매핑용)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvSecJewon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("자동 증가 PK")
    private Integer id;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsv_code")
    @Comment("관측소 코드")
    private String obsvCode;

    @Column(name = "obsv_name")
    @Comment("관측소 명칭")
    private String obsvName;

    @Column(name = "well")
    @Comment("관정 번호")
    private Integer well;

    @Column(name = "sido")
    @Comment("시도")
    private String sido;

    @Column(name = "sigungu")
    @Comment("시군구")
    private String sigungu;

    @Column(name = "upmyundo")
    @Comment("읍면동")
    private String upmyundo;

    @Column(name = "bunji")
    @Comment("번지")
    private String bunji;

    @Column(name = "ri")
    @Comment("리")
    private String ri;

    @Column(name = "x")
    @Comment("경도 (X좌표)")
    private String x;

    @Column(name = "y")
    @Comment("위도 (Y좌표)")
    private String y;

    @Column(name = "pyogo")
    @Comment("표고 (지반고)")
    private Double pyogo;

    @Column(name = "insdate")
    @Comment("설치일")
    private Date insdate;

    @Column(name = "guldep")
    @Comment("굴착 깊이 (m)")
    private Double guldep;

    @Column(name = "guldia")
    @Comment("굴착 지름 (mm)")
    private Double guldia;

    @Column(name = "regdate")
    @Comment("등록일")
    private Date regdate;

    @Column(name = "casing_height")
    @Comment("케이싱 높이 (m)")
    private Double casingHeight;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK, 외부 DB PK 조합)")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    @Comment("처리 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    @Comment("RCV 추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    @Comment("최종 수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "execution_id")
    @Comment("처리한 실행 ID")
    private String executionId;
}
