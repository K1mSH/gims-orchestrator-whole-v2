package com.sync.agent.bojoint.config;

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
            return Identifier.toIdentifier(tableName.toUpperCase(), true);
        } else {
            return Identifier.toIdentifier(tableName.toLowerCase(), false);
        }
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        if (name == null) return null;
        String columnName = toSnakeCase(name.getText());
        if (useUpperCase) {
            return Identifier.toIdentifier(columnName.toUpperCase(), true);
        } else {
            return Identifier.toIdentifier(columnName.toLowerCase(), false);
        }
    }

    private String toSnakeCase(String name) {
        if (name == null) return null;
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
