package com.infolink.agent.common.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * JPA/Hibernate용 대소문자 처리 NamingStrategy
 *
 * ============================================================
 * CaseAwareNamingStrategy vs JdbcTableNameResolver 역할 구분
 * ============================================================
 *
 * 1. CaseAwareNamingStrategy (JPA/Hibernate용) - 이 클래스
 *    - Hibernate가 Entity → SQL 생성할 때 적용
 *    - 정적 규칙 기반: camelCase → snake_case + 대/소문자 변환
 *    - @Table, @Column 어노테이션과 함께 동작
 *    - 예: Entity 필드 obsvCode → SQL의 OBSV_CODE 또는 obsv_code
 *
 * 2. JdbcTableNameResolver (JdbcTemplate용)
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
 *
 * 사용법:
 * - Entity 필드/테이블명은 camelCase로 작성
 * - 이 Strategy가 snake_case로 변환 후 대소문자 적용
 *
 * 예시:
 * - obsvCode → obsv_code (소문자) 또는 OBSV_CODE (대문자)
 * - secJewonView → sec_jewon_view 또는 SEC_JEWON_VIEW
 */
public class CaseAwareNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    private final boolean useUpperCase;

    public CaseAwareNamingStrategy() {
        this(false);  // 기본값: 소문자
    }

    public CaseAwareNamingStrategy(boolean useUpperCase) {
        this.useUpperCase = useUpperCase;
    }

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        if (name == null) return null;
        String tableName = toSnakeCase(name.getText());
        if (useUpperCase) {
            // 대문자 + 따옴표 (PostgreSQL에서 대문자 유지)
            return Identifier.toIdentifier(tableName.toUpperCase(), true);
        } else {
            // 소문자
            return Identifier.toIdentifier(tableName.toLowerCase(), false);
        }
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        if (name == null) return null;
        String columnName = toSnakeCase(name.getText());
        if (useUpperCase) {
            // 대문자 + 따옴표 (PostgreSQL에서 대문자 유지)
            return Identifier.toIdentifier(columnName.toUpperCase(), true);
        } else {
            // 소문자
            return Identifier.toIdentifier(columnName.toLowerCase(), false);
        }
    }

    /**
     * camelCase를 snake_case로 변환
     * obsvCode -> obsv_code
     */
    private String toSnakeCase(String name) {
        if (name == null) return null;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
