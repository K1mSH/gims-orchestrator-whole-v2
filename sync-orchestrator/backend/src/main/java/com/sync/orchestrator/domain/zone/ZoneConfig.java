package com.sync.orchestrator.domain.zone;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Zone별 Master Agent URL 설정
 * Datasource 연결 테스트 시 해당 zone의 master agent로 요청을 프록시하기 위해 사용
 */
@Entity
@Table(name = "zone_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneConfig {

    @Id
    @Column(name = "zone", length = 50)
    private String zone;  // EXTERNAL, DMZ, INTERNAL_COMMON, INTERNAL_SERVICE

    @Column(name = "short_code", length = 5, nullable = false)
    private String shortCode;  // E, D, IC, IS

    @Column(name = "master_agent_url", length = 500, nullable = false)
    private String masterAgentUrl;  // http://192.168.1.100:8081

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
