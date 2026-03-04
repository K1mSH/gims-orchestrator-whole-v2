package com.sync.orchestrator.domain.zone;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Zone별 프록시 Agent URL 설정
 * DB 조회(연결 테스트, 테이블/컬럼 스키마, 실행 데이터)를 해당 zone의 프록시 Agent로 라우팅
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

    @Column(name = "proxy_agent_url", length = 500, nullable = false)
    private String proxyAgentUrl;  // http://192.168.1.100:8083

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
