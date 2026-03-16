package com.infolink.collector.domain;

import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "api_endpoint")
@org.hibernate.annotations.Table(appliesTo = "api_endpoint", comment = "외부 API 엔드포인트 정의")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @Column(name = "api_name", length = 100, nullable = false)
    @Comment("API 표시명")
    private String apiName;

    @Column(name = "api_code", length = 50, nullable = false, unique = true)
    @Comment("고유코드")
    private String apiCode;

    @Column(name = "url", length = 500, nullable = false)
    @Comment("호출 URL 템플릿")
    private String url;

    @Column(name = "http_method", length = 10, nullable = false)
    @Comment("HTTP 메서드 (GET/POST)")
    private String httpMethod;

    @Column(name = "content_type", length = 50)
    @Comment("Content-Type (POST body 형식)")
    private String contentType;

    @Column(name = "headers", columnDefinition = "TEXT")
    @Comment("추가 헤더 JSON")
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", length = 20, nullable = false)
    @Comment("인증 유형 (NONE/BASIC/BEARER)")
    private AuthType authType;

    @Column(name = "auth_config", columnDefinition = "TEXT")
    @Comment("인증 설정 JSON")
    private String authConfig;

    @Column(name = "data_root_path", length = 200)
    @Comment("응답 데이터 배열 경로 (테스트 호출 후 지정)")
    private String dataRootPath;

    @Column(name = "target_datasource_id", length = 50)
    @Comment("적재 대상 datasource ID (매핑 단계에서 설정)")
    private String targetDatasourceId;

    @Column(name = "target_table_name", length = 100)
    @Comment("적재 대상 테이블명 (매핑 단계에서 설정)")
    private String targetTableName;

    @Column(name = "upsert_enabled")
    @Comment("UPSERT 사용 여부")
    @Builder.Default
    private Boolean upsertEnabled = false;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;

    @Column(name = "is_active")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", length = 20, nullable = false)
    @Comment("배포 존 (DMZ/INTERNAL)")
    private Zone zone;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "apiEndpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ApiParam> params = new ArrayList<>();

    @OneToMany(mappedBy = "apiEndpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ApiFieldMapping> fieldMappings = new ArrayList<>();

    public enum AuthType {
        NONE, BASIC, BEARER
    }

    public enum Zone {
        DMZ, INTERNAL
    }
}
