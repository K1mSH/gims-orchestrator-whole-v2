package com.sync.agent.bojo.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "link_ngwis")
@org.hibernate.annotations.Table(appliesTo = "link_ngwis", comment = "동기화 시점 추적 (외부 업체 연계)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkNgwis {

    @Id
    @Column(name = "obsv_code")
    @Comment("관측소 코드 (PK)")
    private String obsvCode;

    @Column(name = "obsv_date")
    @Comment("관측 일자")
    private LocalDateTime obsvDate;

    @Column(name = "obsv_time")
    @Comment("관측 시각")
    private String obsvTime;

    @Column(name = "update_time")
    @Comment("업데이트 시각")
    private LocalDateTime updateTime;
}
