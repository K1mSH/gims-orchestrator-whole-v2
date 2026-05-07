package com.infolink.agent.common.step;

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
                    params.add(castIfDate(c.getValue()));
                    break;
                case GTE:
                    sql.append(col).append(" >= ?");
                    params.add(castIfDate(c.getValue()));
                    break;
                case LT:
                    sql.append(col).append(" < ?");
                    params.add(castIfDate(c.getValue()));
                    break;
                case LTE:
                    sql.append(col).append(" <= ?");
                    params.add(castIfDate(c.getValue()));
                    break;
                case BETWEEN:
                    sql.append(col).append(" BETWEEN ? AND ?");
                    params.add(castIfDate(c.getValue()));
                    params.add(castIfDate(c.getValue2()));
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
     * 커스텀 Step용: IF 테이블 조건 조회 WHERE 절 생성
     *
     * - 조건실행(conditions): 사용자 조건으로 조회 (tableName 필터링 포함)
     * - 기본실행: WHERE LINK_STATUS IN ('PENDING', 'RESYNC')
     *
     * @param options    실행 옵션 (context.getExecutionOptions())
     * @param ifTable    IF 테이블명 (조건의 tableName 필터링용)
     * @param dbType     DB 타입 (ORACLE, POSTGRESQL 등)
     * @return WhereClause (toWhereSql()로 SQL에 붙이면 됨)
     */
    public static WhereClause buildIfTableQuery(ExecutionOptions options, String ifTable, String dbType) {
        boolean hasConditions = options != null && options.hasConditions();

        if (hasConditions) {
            // 조건실행: 사용자 조건에서 tableName 필터링
            List<ExecutionCondition> filtered = options.getConditions().stream()
                    .filter(c -> c.getTableName() == null || c.getTableName().isEmpty()
                            || c.getTableName().equalsIgnoreCase(ifTable))
                    .collect(java.util.stream.Collectors.toList());
            return build(filtered, dbType);
        }

        // 기본실행: PENDING/RESYNC
        Map<String, ExecutionCondition> defaults = new LinkedHashMap<>();
        defaults.put("LINK_STATUS", ExecutionCondition.in("LINK_STATUS", "PENDING,RESYNC"));
        return build(defaults, dbType);
    }

    /**
     * 조건실행 여부 판별 (커스텀 Step용)
     */
    public static boolean isResyncExecution(ExecutionOptions options) {
        return options != null && (options.hasConditions() || options.isTimeRangeExecution());
    }

    /**
     * 날짜 형식(yyyy-MM-dd)이면 java.sql.Date로 변환, 아니면 원본 반환.
     * PostgreSQL date 컬럼에 String 바인딩 시 타입 불일치 방지.
     */
    private static Object castIfDate(String value) {
        if (value == null) return null;
        if (value.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return java.sql.Date.valueOf(value);
        }
        return value;
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
        if (dbType != null && (dbType.equalsIgnoreCase("ORACLE") || dbType.equalsIgnoreCase("TIBERO"))) {
            return identifier;  // Oracle: 인용 없이 (자동 대문자)
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
