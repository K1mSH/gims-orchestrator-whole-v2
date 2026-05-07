package com.infolink.agent.bojo.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * 대소문자를 동적으로 처리하는 NamingStrategy
 * Oracle에서 온 DB는 대문자, PostgreSQL 네이티브는 소문자
 */
public class CaseAwareNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    private final boolean useUpperCase;

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
     *
     * 이미 소문자이거나 snake_case인 이름은 그대로 유지
     * (id, obsv_code 같은 경우)
     */
    private String toSnakeCase(String name) {
        if (name == null) return null;

        // 이미 모두 소문자이거나 언더스코어를 포함한 snake_case면 그대로 반환
        if (name.equals(name.toLowerCase()) || name.contains("_")) {
            return name;
        }

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
