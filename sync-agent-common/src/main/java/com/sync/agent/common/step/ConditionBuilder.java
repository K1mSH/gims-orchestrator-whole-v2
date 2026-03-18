package com.sync.agent.common.step;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 동적 WHERE 조건 빌더
 *
 * 디폴트 조건과 실행 시 conditions를 merge하여 SQL WHERE 절 생성.
 * - 같은 컬럼: 실행 조건이 디폴트를 대체
 * - 다른 컬럼: AND로 추가
 * - conditions 없으면: 디폴트 그대로
 */
public class ConditionBuilder {

    /**
     * 디폴트 조건과 실행 조건을 merge
     *
     * @param defaults   Step에서 정의한 디폴트 조건 (column → Condition)
     * @param executions 실행 시 전달된 동적 조건 (null이면 디폴트만 사용)
     * @return merge된 조건 맵 (column → Condition)
     */
    public static Map<String, ExecutionCondition> merge(
            Map<String, ExecutionCondition> defaults,
            List<ExecutionCondition> executions) {

        Map<String, ExecutionCondition> merged = new LinkedHashMap<>();

        // 디폴트 조건 먼저
        if (defaults != null) {
            merged.putAll(defaults);
        }

        // 실행 조건으로 대체/추가
        if (executions != null) {
            for (ExecutionCondition c : executions) {
                merged.put(c.getColumn(), c); // 같은 컬럼이면 대체
            }
        }

        return merged;
    }

    /**
     * merge된 조건들로 WhereClause 생성
     *
     * @param conditions merge된 조건 맵
     * @param dbType     DB 타입 ("POSTGRESQL", "MYSQL" 등)
     * @return WhereClause (sql + params)
     */
    public static WhereClause build(Map<String, ExecutionCondition> conditions, String dbType) {
        if (conditions == null || conditions.isEmpty()) {
            return WhereClause.EMPTY;
        }
        return build(conditions.values(), dbType);
    }

    /**
     * 조건 컬렉션으로 WhereClause 생성
     */
    public static WhereClause build(Collection<ExecutionCondition> conditions, String dbType) {
        if (conditions == null || conditions.isEmpty()) {
            return WhereClause.EMPTY;
        }

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (ExecutionCondition c : conditions) {
            if (sql.length() > 0) {
                sql.append(" AND ");
            }

            String col = quoteIdentifier(c.getColumn(), dbType);

            switch (c.getOperator()) {
                case EQ:
                    sql.append(col).append(" = ?");
                    params.add(c.getValue());
                    break;
                case NEQ:
                    sql.append(col).append(" != ?");
                    params.add(c.getValue());
                    break;
                case GT:
                    sql.append(col).append(" > ?");
                    params.add(c.getValue());
                    break;
                case GTE:
                    sql.append(col).append(" >= ?");
                    params.add(c.getValue());
                    break;
                case LT:
                    sql.append(col).append(" < ?");
                    params.add(c.getValue());
                    break;
                case LTE:
                    sql.append(col).append(" <= ?");
                    params.add(c.getValue());
                    break;
                case BETWEEN:
                    sql.append(col).append(" BETWEEN ? AND ?");
                    params.add(c.getValue());
                    params.add(c.getValue2());
                    break;
                case IN:
                    String[] vals = c.getValue().split(",");
                    String placeholders = Arrays.stream(vals)
                            .map(v -> "?")
                            .collect(Collectors.joining(", "));
                    sql.append(col).append(" IN (").append(placeholders).append(")");
                    for (String v : vals) {
                        params.add(v.trim());
                    }
                    break;
                case LIKE:
                    sql.append(col).append(" LIKE ?");
                    params.add(c.getValue());
                    break;
                case IS_NULL:
                    sql.append(col).append(" IS NULL");
                    break;
                case IS_NOT_NULL:
                    sql.append(col).append(" IS NOT NULL");
                    break;
            }
        }

        return new WhereClause(sql.toString(), params);
    }

    /**
     * 디폴트 조건 + 실행 조건을 merge하여 바로 WhereClause 생성 (편의 메서드)
     */
    public static WhereClause buildMerged(
            Map<String, ExecutionCondition> defaults,
            List<ExecutionCondition> executions,
            String dbType) {
        return build(merge(defaults, executions), dbType);
    }

    /**
     * DB 타입별 식별자 인용
     */
    private static String quoteIdentifier(String identifier, String dbType) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be null or blank");
        }
        // SQL injection 방어: 알파벳, 숫자, 밑줄만 허용
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid column name: " + identifier);
        }

        if (dbType != null && dbType.toUpperCase().contains("MYSQL")) {
            return "`" + identifier + "`";
        }
        return "\"" + identifier + "\"";
    }

    /**
     * WHERE 절 결과
     */
    public static class WhereClause {
        public static final WhereClause EMPTY = new WhereClause("", Collections.emptyList());

        private final String sql;
        private final List<Object> params;

        public WhereClause(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params != null ? params : Collections.emptyList();
        }

        public String getSql() {
            return sql;
        }

        public List<Object> getParams() {
            return params;
        }

        public Object[] getParamsArray() {
            return params.toArray();
        }

        public boolean isEmpty() {
            return sql == null || sql.isBlank();
        }

        /**
         * " WHERE ..." 형태로 반환 (빈 경우 빈 문자열)
         */
        public String toWhereSql() {
            return isEmpty() ? "" : " WHERE " + sql;
        }

        /**
         * 기존 SQL에 AND로 추가 (기존 WHERE가 이미 있을 때)
         */
        public String toAndSql() {
            return isEmpty() ? "" : " AND " + sql;
        }
    }
}
