package com.infolink.collector.entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

/**
 * 네이버 뉴스 수집 API 적재 테이블
 * 테이블명: tm_gd014001
 * UK: orgnl_url (UPSERT conflict key)
 */
@Entity
@Table(name = "tm_gd014001", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tm_gd014001_orgnl_url", columnNames = "orgnl_url")
})
@org.hibernate.annotations.Table(appliesTo = "tm_gd014001", comment = "네이버뉴스수집API")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaverNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "ttl", length = 500, nullable = false)
    @Comment("제목")
    private String ttl;

    @Column(name = "orgnl_url", length = 500, nullable = false)
    @Comment("원본URL")
    private String orgnlUrl;

    @Column(name = "link", length = 1000)
    @Comment("링크")
    private String link;

    @Column(name = "expln", length = 4000)
    @Comment("설명")
    private String expln;

    @Column(name = "pstg_ymd", length = 500)
    @Comment("게시일자")
    private String pstgYmd;

    @Column(name = "press_nm", length = 100)
    @Comment("언론사명")
    @ColumnDefault("'언론사'")
    @Builder.Default
    private String pressNm = "언론사";

    @Column(name = "vstr_cnt")
    @Comment("방문자수")
    @ColumnDefault("0")
    @Builder.Default
    private Long vstrCnt = 0L;

    @Column(name = "use_yn", length = 1)
    @Comment("사용여부")
    @ColumnDefault("'Y'")
    @Builder.Default
    private String useYn = "Y";

    @Column(name = "reg_ymd", length = 8)
    @Comment("등록일자")
    @ColumnDefault("to_char(now(), 'YYYYMMDD')")
    private String regYmd;

    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private String linkStatus = "PENDING";
}
