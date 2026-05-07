package com.infolink.agent.common.config;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC용 테이블명 해석 유틸리티 (JdbcTemplate 사용 시)
 *
 * ============================================================
 * CaseAwareNamingStrategy vs JdbcTableNameResolver 역할 구분
 * ============================================================
 *
 * 1. CaseAwareNamingStrategy (JPA/Hibernate용)
 *    - Hibernate가 Entity → SQL 생성할 때 적용
 *    - 정적 규칙 기반: camelCase → snake_case + 대/소문자 변환
 *    - @Table, @Column 어노테이션과 함께 동작
 *    - 예: Entity 필드 obsvCode → SQL의 OBSV_CODE 또는 obsv_code
 *
 * 2. JdbcTableNameResolver (JdbcTemplate용) - 이 클래스
 *    - 직접 SQL을 작성할 때 사용
 *    - 동적 메타데이터 조회: DB에서 실제 테이블명 확인
 *    - 대소문자 변형을 시도하여 존재하는 테이블 찾음
 *    - 예: "sec_obsvdata_view" → DB에서 "SEC_OBSVDATA_VIEW" 발견 시 반환
 *
 * 왜 분리했나?
 * - JPA는 Hibernate가 SQL을 생성하므로 NamingStrategy로 제어 가능
 * - JdbcTemplate은 개발자가 직접 SQL을 작성하므로 메타데이터 조회 필요
 * - PostgreSQL은 따옴표 없으면 소문자로 변환하므로 정확한 이름 필요
 * ============================================================
 */
@Slf4j
public class JdbcTableNameResolver {

    // 캐시: "datasourceId:logicalName" -> "actualName"
    private static final Map<String, String> tableNameCache = new ConcurrentHashMap<>();

    /**
     * 논리적 테이블명을 schema와 table로 분리한 결과
     *
     * "SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033" → (schema=SDE_NGWS, table=WT_DREAM_PERMWELL_PUBLIC_21033)
     * "TM_GD112002"                              → (schema=null,     table=TM_GD112002)
     */
    public static class TableRef {
        public final String schema;  // nullable — null 이면 접속 계정의 기본 스키마
        public final String table;
        public TableRef(String schema, String table) {
            this.schema = schema;
            this.table = table;
        }
    }

    /**
     * 논리적 테이블명 파싱 — "SCHEMA.TABLE" or "TABLE"
     */
    public static TableRef parse(String logicalName) {
        if (logicalName == null) return new TableRef(null, null);
        int dot = logicalName.indexOf('.');
        if (dot > 0 && dot < logicalName.length() - 1) {
            return new TableRef(logicalName.substring(0, dot), logicalName.substring(dot + 1));
        }
        return new TableRef(null, logicalName);
    }

    /**
     * 실제 테이블명 조회 (PostgreSQL 대소문자 처리, SCHEMA.TABLE 형식 지원)
     * 메타데이터에서 대소문자 변형을 시도하여 실제 존재하는 테이블명 반환
     *
     * "SCHEMA.TABLE" 형식으로 전달하면 schema 를 분리해서 metaData.getTables(catalog, schema, table, ...) 호출.
     * schema 없으면 기존 동작(schema=null, 현재 접속 계정 스키마 조회)과 동일.
     *
     * @param dataSource     데이터소스
     * @param datasourceId   캐시 키용 datasource ID
     * @param logicalName    논리적 테이블명 (config에 설정된 이름)
     * @return 실제 테이블명 (DB에 존재하는 대소문자). SCHEMA.TABLE 입력 시 "SCHEMA.TABLE" 형태 반환
     */
    public static String resolve(DataSource dataSource, String datasourceId, String logicalName) {
        String cacheKey = datasourceId + ":" + logicalName;
        return tableNameCache.computeIfAbsent(cacheKey, key -> resolveInternal(dataSource, logicalName, true));
    }

    /**
     * 실제 테이블명 조회 (datasourceId 없이 - 캐시 미사용)
     */
    public static String resolve(DataSource dataSource, String logicalName) {
        return resolveInternal(dataSource, logicalName, false);
    }

    private static String resolveInternal(DataSource dataSource, String logicalName, boolean infoLog) {
        TableRef ref = parse(logicalName);
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();  // MySQL은 catalog(=database)를 지정해야 해당 DB 테이블만 반환

            String[] tableVariants = {ref.table, ref.table.toLowerCase(), ref.table.toUpperCase()};
            String[] schemaVariants = (ref.schema != null)
                    ? new String[]{ref.schema, ref.schema.toLowerCase(), ref.schema.toUpperCase()}
                    : new String[]{null};

            for (String schemaVar : schemaVariants) {
                for (String tableVar : tableVariants) {
                    try (ResultSet rs = metaData.getTables(catalog, schemaVar, tableVar, new String[]{"TABLE", "VIEW"})) {
                        if (rs.next()) {
                            String actualName = rs.getString("TABLE_NAME");
                            String result;
                            if (ref.schema != null) {
                                String actualSchema = rs.getString("TABLE_SCHEM");
                                result = (actualSchema != null ? actualSchema : ref.schema) + "." + actualName;
                            } else {
                                result = actualName;
                            }
                            if (infoLog) {
                                log.info("Resolved table name: '{}' -> '{}'", logicalName, result);
                            } else {
                                log.debug("Resolved table name: '{}' -> '{}'", logicalName, result);
                            }
                            return result;
                        }
                    }
                }
            }
            log.warn("Table not found in metadata: '{}'. Using as-is.", logicalName);
        } catch (Exception e) {
            log.error("Failed to resolve table name '{}': {}", logicalName, e.getMessage());
        }
        return logicalName;
    }

    /**
     * 캐시 초기화 (테스트용)
     */
    public static void clearCache() {
        tableNameCache.clear();
    }
}
