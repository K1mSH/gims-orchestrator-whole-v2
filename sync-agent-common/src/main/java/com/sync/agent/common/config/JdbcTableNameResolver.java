package com.sync.agent.common.config;

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
     * 실제 테이블명 조회 (PostgreSQL 대소문자 처리)
     * 메타데이터에서 대소문자 변형을 시도하여 실제 존재하는 테이블명 반환
     *
     * @param dataSource     데이터소스
     * @param datasourceId   캐시 키용 datasource ID
     * @param logicalName    논리적 테이블명 (config에 설정된 이름)
     * @return 실제 테이블명 (DB에 존재하는 대소문자)
     */
    public static String resolve(DataSource dataSource, String datasourceId, String logicalName) {
        String cacheKey = datasourceId + ":" + logicalName;

        return tableNameCache.computeIfAbsent(cacheKey, key -> {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                // MySQL은 catalog(=database)를 지정해야 해당 DB 테이블만 반환
                String catalog = conn.getCatalog();
                String[] variants = {logicalName, logicalName.toLowerCase(), logicalName.toUpperCase()};

                for (String variant : variants) {
                    try (ResultSet rs = metaData.getTables(catalog, null, variant, new String[]{"TABLE", "VIEW"})) {
                        if (rs.next()) {
                            String actualName = rs.getString("TABLE_NAME");
                            log.info("Resolved table name: '{}' -> '{}'", logicalName, actualName);
                            return actualName;
                        }
                    }
                }

                log.warn("Table not found in metadata: '{}'. Using as-is.", logicalName);
            } catch (Exception e) {
                log.error("Failed to resolve table name '{}': {}", logicalName, e.getMessage());
            }
            return logicalName;
        });
    }

    /**
     * 실제 테이블명 조회 (datasourceId 없이 - 캐시 미사용)
     *
     * @param dataSource  데이터소스
     * @param logicalName 논리적 테이블명
     * @return 실제 테이블명
     */
    public static String resolve(DataSource dataSource, String logicalName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String[] variants = {logicalName, logicalName.toLowerCase(), logicalName.toUpperCase()};

            for (String variant : variants) {
                try (ResultSet rs = metaData.getTables(catalog, null, variant, new String[]{"TABLE", "VIEW"})) {
                    if (rs.next()) {
                        String actualName = rs.getString("TABLE_NAME");
                        log.debug("Resolved table name: '{}' -> '{}'", logicalName, actualName);
                        return actualName;
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
