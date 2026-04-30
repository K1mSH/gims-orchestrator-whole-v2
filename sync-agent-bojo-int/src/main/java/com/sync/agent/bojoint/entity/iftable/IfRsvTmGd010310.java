package com.sync.agent.bojoint.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * IF_RSV 약수터 제원정보 엔티티 (Internal Oracle).
 * 테이블: IF_RSV_TM_GD010310
 * 테이블 코멘트는 DDL 또는 별도 SQL 로 관리 (다른 IF_RSV entity 와 일관 — 어노테이션 미사용).
 */
@Entity
@Table(name = "IF_RSV_TM_GD010310",
       indexes = @Index(name = "idx_if_rsv_tm_gd010310_exec_id", columnList = "execution_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvTmGd010310 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    @Comment("내부 ID (PK)")
    private Long id;

    @Column(name = "SN")
    @Comment("일련번호")
    private Long sn;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "SEQ", length = 10)
    @Comment("순번")
    private String seq;

    @Column(name = "BRNCH_NO", length = 10)
    @Comment("지점번호")
    private String brnchNo;

    @Column(name = "BRNCH_NM", length = 100)
    @Comment("지점명")
    private String brnchNm;

    @Column(name = "BRNCH_STD_CD", length = 20)
    @Comment("지점표준코드")
    private String brnchStdCd;

    @Column(name = "INFO_CRT_INST_NM", length = 50)
    @Comment("정보생성기관명")
    private String infoCrtInstNm;

    @Column(name = "CHRTC_MCLSF", length = 4)
    @Comment("특성중분류")
    private String chrtcMclsf;

    @Column(name = "CHRTC_SCLSF", length = 4)
    @Comment("특성소분류")
    private String chrtcSclsf;

    @Column(name = "CTPV_NM", length = 40)
    @Comment("시도명")
    private String ctpvNm;

    @Column(name = "SGG_NM", length = 30)
    @Comment("시군구명")
    private String sggNm;

    @Column(name = "ADDR", length = 500)
    @Comment("주소")
    private String addr;

    @Column(name = "STDG_CD", length = 10)
    @Comment("법정동코드")
    private String stdgCd;

    @Column(name = "XCRD", length = 20)
    @Comment("X좌표")
    private String xcrd;

    @Column(name = "YCRD", length = 20)
    @Comment("Y좌표")
    private String ycrd;

    @Column(name = "ABL_YN", length = 4)
    @Comment("폐지여부")
    private String ablYn;

    @Column(name = "ABL_YMD", length = 13)
    @Comment("폐지일자")
    private String ablYmd;

    @Column(name = "DAY01_AVG_USR_CNT", length = 10)
    @Comment("1일평균이용자수")
    private String day01AvgUsrCnt;

    @Column(name = "PIC", length = 60)
    @Comment("담당자")
    private String pic;

    @Column(name = "INSTL_YMD", length = 10)
    @Comment("설치일자")
    private String instlYmd;

    @Column(name = "DEL_YN", length = 4)
    @Comment("삭제여부")
    private String delYn;

    @Column(name = "PIC_NM", length = 60)
    @Comment("담당자명")
    private String picNm;

    @Column(name = "PIC_CNPL", length = 50)
    @Comment("담당자연락처")
    private String picCnpl;

    @Column(name = "BNO", length = 30)
    @Comment("건물번호")
    private String bno;

    @Column(name = "LCTN_LOTNO", length = 20)
    @Comment("소재지_지번")
    private String lctnLotno;

    @Column(name = "RMRK", length = 500)
    @Comment("비고")
    private String rmrk;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "SOURCE_REFS", length = 4000)
    @Comment("원본 참조키")
    private String sourceRefs;

    @Column(name = "LINK_STATUS", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    @Builder.Default
    private String linkStatus = "PENDING";

    @Column(name = "EXTRACTED_AT")
    @Comment("추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "UPDATED_AT")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "EXECUTION_ID")
    @Comment("처리 실행 ID")
    private String executionId;
}
