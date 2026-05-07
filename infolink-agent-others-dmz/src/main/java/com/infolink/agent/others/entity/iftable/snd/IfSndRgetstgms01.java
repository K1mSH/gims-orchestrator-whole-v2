package com.infolink.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 제주 이용시설 이용실태정보 엔티티.
 *
 * <p>SND 파이프라인에서 Target DB의 이용실태 데이터를 IF_SND 테이블로 추출할 때 사용된다.
 * {@code source_refs}에 UK, {@code execution_id}에 인덱스가 설정되어 있다.</p>
 *
 * <p>테이블: {@code if_snd_rgetstgms01}</p>
 *
 * @see com.infolink.collector.entity.Rgetstgms01
 */
@Entity
@Table(name = "if_snd_rgetstgms01",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_rgetstgms01_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_rgetstgms01_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_rgetstgms01", comment = "IF_SND 제주 이용시설 이용실태정보")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndRgetstgms01 {

    @Id
    @Column(name = "perm_nt_no", length = 50)
    @Comment("허가신고번호 (PK)")
    private String permNtNo;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "rel_trans_cgg_code", length = 10)
    @Comment("시군구코드")
    private String relTransCggCode;

    @Column(name = "yy_gbn", length = 10)
    @Comment("연도구분")
    private String yyGbn;

    @Column(name = "sf_team_code", length = 10)
    @Comment("담당팀코드")
    private String sfTeamCode;

    @Column(name = "perm_nt_form_code", length = 5)
    @Comment("허가/신고 구분")
    private String permNtFormCode;

    @Column(name = "regn_code", length = 50)
    @Comment("지역코드")
    private String regnCode;

    @Column(name = "san", length = 10)
    @Comment("산")
    private String san;

    @Column(name = "bunji", length = 20)
    @Comment("번지")
    private String bunji;

    @Column(name = "ho", length = 10)
    @Comment("호")
    private String ho;

    @Column(name = "litd_dg", length = 10)
    @Comment("경도 도")
    private String litdDg;

    @Column(name = "litd_mint", length = 10)
    @Comment("경도 분")
    private String litdMint;

    @Column(name = "litd_sc", length = 10)
    @Comment("경도 초")
    private String litdSc;

    @Column(name = "lttd_dg", length = 10)
    @Comment("위도 도")
    private String lttdDg;

    @Column(name = "lttd_mint", length = 10)
    @Comment("위도 분")
    private String lttdMint;

    @Column(name = "lttd_sc", length = 10)
    @Comment("위도 초")
    private String lttdSc;

    @Column(name = "elev", length = 20)
    @Comment("표고")
    private String elev;

    @Column(name = "uwater_srv_code", length = 5)
    @Comment("용도코드")
    private String uwaterSrvCode;

    @Column(name = "pub_pri_gbn", length = 5)
    @Comment("공공/민간")
    private String pubPriGbn;

    @Column(name = "pota_yn", length = 2)
    @Comment("음용여부")
    private String potaYn;

    @Column(name = "y_use_qua", length = 20)
    @Comment("연간사용량")
    private String yUseQua;

    @Column(name = "uwater_souc_code", length = 5)
    @Comment("수원코드")
    private String uwaterSoucCode;

    @Column(name = "dph", length = 20)
    @Comment("심도")
    private String dph;

    @Column(name = "dig_diam", length = 20)
    @Comment("굴착구경")
    private String digDiam;

    @Column(name = "pump_hrp", length = 20)
    @Comment("펌프마력")
    private String pumpHrp;

    @Column(name = "rwt_cap", length = 20)
    @Comment("저수량")
    private String rwtCap;

    @Column(name = "pipe_diam", length = 20)
    @Comment("관경")
    private String pipeDiam;

    @Column(name = "nat_wtlv", length = 20)
    @Comment("자연수위")
    private String natWtlv;

    @Column(name = "stb_wtlv", length = 20)
    @Comment("안정수위")
    private String stbWtlv;

    @Column(name = "frw_pln_qua", length = 20)
    @Comment("양수계획량")
    private String frwPlnQua;

    @Column(name = "first_reg_dthr")
    @Comment("최초등록일시")
    private LocalDateTime firstRegDthr;

    @Column(name = "last_mod_dthr")
    @Comment("최종수정일시")
    private LocalDateTime lastModDthr;

    @Column(name = "uwater_dtl_srv_code", length = 5)
    @Comment("세부용도코드")
    private String uwaterDtlSrvCode;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK)")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    @Comment("추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "execution_id")
    @Comment("처리 실행 ID")
    private String executionId;
}
