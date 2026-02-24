package com.sync.orchestrator.domain.datasource;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class DatasourceDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank
        private String datasourceId;
        @NotBlank
        private String datasourceName;
        @NotNull
        private DbType dbType;
        @NotBlank
        private String host;
        @NotNull
        private Integer port;
        @NotBlank
        private String databaseName;
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        private String description;
        /** DB가 위치한 네트워크 존 (EXTERNAL, DMZ, INTERNAL_COMMON, INTERNAL_SERVICE) */
        private String zone;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String datasourceName;
        private DbType dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
        private String description;
        private String zone;
        private Boolean isActive;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;  // sourceRef에서 사용하는 숫자 ID
        private String datasourceId;
        private String datasourceName;
        private DbType dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        // password는 응답에서 제외
        private String description;
        private String zone;
        private Boolean isActive;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(Datasource datasource) {
            return Response.builder()
                    .id(datasource.getId())
                    .datasourceId(datasource.getDatasourceId())
                    .datasourceName(datasource.getDatasourceName())
                    .dbType(datasource.getDbType())
                    .host(datasource.getHost())
                    .port(datasource.getPort())
                    .databaseName(datasource.getDatabaseName())
                    .username(datasource.getUsername())
                    // password 제외
                    .description(datasource.getDescription())
                    .zone(datasource.getZone())
                    .isActive(datasource.getIsActive())
                    .createdAt(datasource.getCreatedAt())
                    .updatedAt(datasource.getUpdatedAt())
                    .build();
        }
    }

    /**
     * Agent 내부용 연결 정보 (자격증명 포함, 복호화된 상태)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionInfo {
        private String datasourceId;
        private String dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionTestRequest {
        @NotNull
        private DbType dbType;
        @NotBlank
        private String host;
        @NotNull
        private Integer port;
        @NotBlank
        private String databaseName;
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        /** DB가 위치한 네트워크 존 - 해당 zone의 master Agent가 테스트 수행 */
        private String zone;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionTestResponse {
        private boolean success;
        private String message;
        private Long responseTimeMs;
    }

    /**
     * Agent 등록 시 선택 목록용 간단한 응답
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimpleResponse {
        private String datasourceId;
        private String datasourceName;
        private DbType dbType;

        public static SimpleResponse from(Datasource datasource) {
            return SimpleResponse.builder()
                    .datasourceId(datasource.getDatasourceId())
                    .datasourceName(datasource.getDatasourceName())
                    .dbType(datasource.getDbType())
                    .build();
        }
    }

    /**
     * sourceRef 해석용 lookup 데이터
     * ID → 이름 매핑을 제공
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SourceRefLookup {
        /** datasource id (Long) → datasource 이름 */
        private java.util.Map<Long, String> datasources;
        /** table id (Long) → table 이름 */
        private java.util.Map<Long, String> tables;
    }

    // ========== 테이블/컬럼 관련 DTO ==========

    /**
     * DB에서 검색한 테이블 정보 (검색 결과)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableSearchResult {
        private String tableName;
        private String tableType;  // TABLE, VIEW
        private String remarks;
    }

    /**
     * DB에서 검색한 컬럼 정보 (검색 결과)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnSearchResult {
        private String columnName;
        private String dataType;
        private Integer columnSize;
        private Boolean isNullable;
        private Boolean isPrimaryKey;
        private String remarks;
    }

    /**
     * 테이블 등록 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableCreateRequest {
        @NotBlank
        private String tableName;
        private String tableAlias;
        private String description;
        private java.util.List<ColumnCreateRequest> columns;
    }

    /**
     * 컬럼 등록 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnCreateRequest {
        @NotBlank
        private String columnName;
        private String columnAlias;
        private String dataType;
        private Boolean isPrimaryKey;
        private Boolean isNullable;
        private String description;
    }

    /**
     * 등록된 테이블 응답
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableResponse {
        private Long id;
        private String datasourceId;
        private String tableName;
        private String tableAlias;
        private String description;
        private java.util.List<ColumnResponse> columns;
        private java.time.LocalDateTime createdAt;

        public static TableResponse from(DatasourceTable table) {
            return TableResponse.builder()
                    .id(table.getId())
                    .datasourceId(table.getDatasourceId())
                    .tableName(table.getTableName())
                    .tableAlias(table.getTableAlias())
                    .description(table.getDescription())
                    .columns(table.getColumns().stream()
                            .map(ColumnResponse::from)
                            .collect(java.util.stream.Collectors.toList()))
                    .createdAt(table.getCreatedAt())
                    .build();
        }
    }

    /**
     * 등록된 컬럼 응답
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnResponse {
        private Long id;
        private String columnName;
        private String columnAlias;
        private String dataType;
        private Boolean isPrimaryKey;
        private Boolean isNullable;
        private String description;

        public static ColumnResponse from(DatasourceColumn column) {
            return ColumnResponse.builder()
                    .id(column.getId())
                    .columnName(column.getColumnName())
                    .columnAlias(column.getColumnAlias())
                    .dataType(column.getDataType())
                    .isPrimaryKey(column.getIsPrimaryKey())
                    .isNullable(column.getIsNullable())
                    .description(column.getDescription())
                    .build();
        }
    }
}
