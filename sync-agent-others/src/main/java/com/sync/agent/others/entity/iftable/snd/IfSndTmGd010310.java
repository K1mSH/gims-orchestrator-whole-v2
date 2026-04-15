package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 약수터 제원정보 엔티티.
 *
 * <p>SND 파이프라인에서 api_collector DB의 약수터 제원 데이터를 IF_SND 테이블로 추출할 때 사용된다.</p>
 *
 * <p>테이블: {@code if_snd_tm_gd010310}</p>
 */
@Entity
@Table(name = "if_snd_tm_gd010310",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_tm_gd010310_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_tm_gd010310_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_tm_gd010310", comment = "IF_SND 약수터제원")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndTmGd010310 {

    @Id
    @Column(name = "sn")
    @Comment("일련번호 (PK)")
    private Long sn;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "seq", length = 10)
    @Comment("순번")
    private String seq;

    @Column(name = "brnch_no", length = 10, nullable = false)
    @Comment("지점번호")
    private String brnchNo;

    @Column(name = "brnch_nm", length = 100, nullable = false)
    @Comment("지점명")
    private String brnchNm;

    @Column(name = "brnch_std_cd", length = 20, nullable = false)
    @Comment("지점표준코드")
    private String brnchStdCd;

    @Column(name = "info_crt_inst_nm", length = 50, nullable = false)
    @Comment("정보생성기관명")
    private String infoCrtInstNm;

    @Column(name = "chrtc_mclsf", length = 4)
    @Comment("특성중분류")
    private String chrtcMclsf;

    @Column(name = "chrtc_sclsf", length = 4)
    @Comment("특성소분류")
    private String chrtcSclsf;

    @Column(name = "ctpv_nm", length = 40)
    @Comment("시도명")
    private String ctpvNm;

    @Column(name = "sgg_nm", length = 30)
    @Comment("시군구명")
    private String sggNm;

    @Column(name = "addr", length = 500, nullable = false)
    @Comment("주소")
    private String addr;

    @Column(name = "stdg_cd", length = 10, nullable = false)
    @Comment("법정동코드")
    private String stdgCd;

    @Column(name = "xcrd", length = 20)
    @Comment("X좌표")
    private String xcrd;

    @Column(name = "ycrd", length = 20)
    @Comment("Y좌표")
    private String ycrd;

    @Column(name = "abl_yn", length = 4, nullable = false)
    @Comment("폐지여부")
    private String ablYn;

    @Column(name = "abl_ymd", length = 13)
    @Comment("폐지일자")
    private String ablYmd;

    @Column(name = "day01_avg_usr_cnt", length = 10, nullable = false)
    @Comment("1일평균이용자수")
    private String day01AvgUsrCnt;

    @Column(name = "pic", length = 60)
    @Comment("담당자")
    private String pic;

    @Column(name = "instl_ymd", length = 10, nullable = false)
    @Comment("설치일자")
    private String instlYmd;

    @Column(name = "del_yn", length = 4, nullable = false)
    @Comment("삭제여부")
    private String delYn;

    @Column(name = "pic_nm", length = 60, nullable = false)
    @Comment("담당자명")
    private String picNm;

    @Column(name = "pic_cnpl", length = 50, nullable = false)
    @Comment("담당자연락처")
    private String picCnpl;

    @Column(name = "bno", length = 30)
    @Comment("건물번호")
    private String bno;

    @Column(name = "lctn_lotno", length = 20)
    @Comment("소재지_지번")
    private String lctnLotno;

    @Column(name = "rmrk", length = 500)
    @Comment("비고")
    private String rmrk;

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
