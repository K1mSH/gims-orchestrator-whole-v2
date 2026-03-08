package com.sync.orchestrator.domain.zone;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "zone_config")
@org.hibernate.annotations.Table(appliesTo = "zone_config", comment = "네트워크 존 설정")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneConfig {

    @Id
    @Column(name = "zone", length = 50)
    @Comment("존 이름 (PK)")
    private String zone;

    @Column(name = "short_code", length = 5, nullable = false)
    @Comment("존 약어 (E/D/IC/IS)")
    private String shortCode;

    @Column(name = "proxy_agent_url", length = 500, nullable = false)
    @Comment("프록시 에이전트 URL")
    private String proxyAgentUrl;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;

    @Column(name = "is_active")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;
}
