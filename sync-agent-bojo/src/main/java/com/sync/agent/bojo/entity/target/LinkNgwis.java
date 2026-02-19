package com.sync.agent.bojo.entity.target;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * LINK_NGWIS - 연계 테이블 (DMZ)
 * 외부 업체 DB에서 최초 데이터 가져올 때 동기화 시점 추적용
 * IF 테이블 없이 단독 운영
 *
 * 사용: relay-dmz-rsv-bojo (쓰기)
 */
@Entity
@Table(name = "link_ngwis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkNgwis {

    @Id
    @Column(name = "obsv_code")
    private String obsvCode;

    @Column(name = "obsv_date")
    private LocalDateTime obsvDate;

    @Column(name = "obsv_time")
    private String obsvTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
