package com.gims.provider.service;

import com.gims.provider.dto.DynamicQueryResult;
import com.gims.provider.entity.ApiPrvOperation;
import com.gims.provider.entity.ApiPrvOperationColumn;
import com.gims.provider.entity.ApiPrvOperationParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 동적 SELECT 쿼리 생성 + 실행 엔진
 * 오퍼레이션 설정 기반으로 SQL을 동적 생성하고 Proxy 경유 JdbcTemplate으로 실행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private final ProviderDataSourceService dataSourceService;

    // 테이블명/컬럼명 화이트리스트: 영문, 숫자, 밑줄, 점(스키마.테이블)만 허용
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_.]+$");

    // v3 레거시 호환 — 응답 첫 키로 RNUM(결과 row 위치 1부터) 부착이 필요한 op
    // 근거: v3 sql_megokrapi.xml 외부 SELECT 의 ROWNUM AS RNUM 흉내
    private static final Set<String> ROWNUM_PREPEND_OPS = Set.of(
            "megokrApi/ngw04_01",   // A7
            "megokrApi/ngw03_01"    // B2
    );

    /**
     * 동적 쿼리 실행
     */
    public DynamicQueryResult execute(ApiPrvOperation operation, Map<String, String> requestParams,
                                       int page, int pageSize) {
        long startTime = System.currentTimeMillis();

        // 1. SQL 빌드
        SqlBuildResult sqlResult = buildSql(operation, requestParams, page, pageSize);

        // 2. JdbcTemplate 획득
        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(operation.getDatasourceId());

        // 3. 데이터 쿼리 실행
        List<Map<String, Object>> data = jdbc.queryForList(
                sqlResult.dataSql, sqlResult.dataParams.toArray());

        // 3.1 v3 호환 — RNUM 첫 키로 부착 (해당 op 만)
        if (ROWNUM_PREPEND_OPS.contains(operation.getOperationId())) {
            data = prependRownum(data);
        }

        // 4. COUNT 쿼리 실행
        Long totalCount = jdbc.queryForObject(
                sqlResult.countSql, Long.class, sqlResult.countParams.toArray());
        if (totalCount == null) totalCount = 0L;

        long durationMs = System.currentTimeMillis() - startTime;
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        log.info("[Provider] 동적 쿼리 실행: {} — {}건 / 총 {}건 ({}ms)",
                operation.getOperationId(), data.size(), totalCount, durationMs);

        return DynamicQueryResult.builder()
                .data(data)
                .pagination(DynamicQueryResult.PaginationInfo.builder()
                        .page(page)
                        .pageSize(pageSize)
                        .totalCount(totalCount)
                        .totalPages(totalPages)
                        .build())
                .executedSql(sqlResult.dataSql)
                .durationMs(durationMs)
                .build();
    }

    /**
     * SQL 미리보기 (실행 안 함)
     */
    public String buildSqlPreview(ApiPrvOperation operation, Map<String, String> requestParams) {
        SqlBuildResult result = buildSql(operation, requestParams, 1, 10);
        return result.dataSql + "\n-- params: " + result.dataParams;
    }

    // ========== SQL 빌드 로직 ==========

    private SqlBuildResult buildSql(ApiPrvOperation operation, Map<String, String> requestParams,
                                     int page, int pageSize) {
        validateIdentifier(operation.getTableName(), "테이블명");

        // SELECT
        String selectClause = buildSelectClause(operation.getColumns());

        // WHERE
        WhereResult where = buildWhereClause(operation.getParams(), requestParams);

        // ORDER BY
        String orderByClause = buildOrderByClause(operation.getOrderByColumn(), operation.getOrderByDirection());

        // 페이징 (PG 표준)
        int offset = (page - 1) * pageSize;

        // 데이터 SQL
        String dataSql = "SELECT " + selectClause
                + " FROM " + operation.getTableName()
                + where.clause
                + orderByClause
                + " LIMIT " + pageSize + " OFFSET " + offset;

        // COUNT SQL
        String countSql = "SELECT COUNT(*) FROM " + operation.getTableName() + where.clause;

        // 데이터 쿼리 파라미터 = WHERE 파라미터
        List<Object> dataParams = new ArrayList<>(where.params);
        List<Object> countParams = new ArrayList<>(where.params);

        return new SqlBuildResult(dataSql, dataParams, countSql, countParams);
    }

    private String buildSelectClause(List<ApiPrvOperationColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return "*";
        }

        return columns.stream()
                .sorted(Comparator.comparingInt(c -> c.getDisplayOrder() != null ? c.getDisplayOrder() : 0))
                .map(col -> {
                    validateIdentifier(col.getColumnName(), "컬럼명");
                    String expr = applyTransform(col);
                    String alias = col.getAliasName();
                    if (alias != null && !alias.isEmpty()) {
                        validateIdentifier(alias, "별칭");
                        // 쌍따옴표로 감싸서 대소문자 보존 (PG가 unquoted identifier를 lowercase로 정규화하는 것을 방지)
                        return expr + " AS \"" + alias + "\"";
                    }
                    // 가공 적용 시 원본 컬럼명을 alias로 유지
                    if (!expr.equals(col.getColumnName())) {
                        return expr + " AS " + col.getColumnName();
                    }
                    return expr;
                })
                .collect(Collectors.joining(", "));
    }

    private String applyTransform(ApiPrvOperationColumn col) {
        String colName = col.getColumnName();
        String type = col.getTransformType();
        String param = col.getTransformParam();

        if (type == null || "NONE".equals(type) || type.isEmpty()) {
            return colName;
        }

        switch (type.toUpperCase()) {
            case "ROUND":
                int scale = param != null ? Integer.parseInt(param) : 0;
                return "ROUND(" + colName + "::numeric, " + scale + ")";
            case "DATE_FORMAT":
                String format = param != null ? param : "YYYY-MM-DD";
                validateDateFormat(format);
                return "TO_CHAR(" + colName + ", '" + format + "')";
            case "COALESCE":
                String defaultVal = param != null ? param : "";
                return "COALESCE(" + colName + "::text, '" + defaultVal.replace("'", "''") + "')";
            case "SUBSTRING":
                int length = param != null ? Integer.parseInt(param) : 100;
                return "SUBSTRING(" + colName + "::text, 1, " + length + ")";
            default:
                log.warn("[Provider] 알 수 없는 가공 타입: {} (컬럼: {})", type, colName);
                return colName;
        }
    }

    private void validateDateFormat(String format) {
        // PG TO_CHAR 포맷에 허용되는 문자만
        if (!Pattern.matches("^[A-Za-z0-9\\-/:. ]+$", format)) {
            throw new IllegalArgumentException("유효하지 않은 날짜 포맷: " + format);
        }
    }

    private WhereResult buildWhereClause(List<ApiPrvOperationParam> params, Map<String, String> requestParams) {
        if (params == null || params.isEmpty()) {
            return new WhereResult("", Collections.emptyList());
        }

        if (requestParams == null) {
            requestParams = Collections.emptyMap();
        }

        List<String> conditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (ApiPrvOperationParam param : params) {
            String paramValue = requestParams.get(param.getParamName());

            // 값이 없으면 기본값 사용
            if ((paramValue == null || paramValue.isEmpty()) && param.getDefaultValue() != null) {
                paramValue = param.getDefaultValue();
            }

            // 필수 파라미터 검증
            if (param.getIsRequired() && (paramValue == null || paramValue.isEmpty())) {
                throw new IllegalArgumentException("필수 파라미터 누락: " + param.getParamName());
            }

            // 값이 없는 선택 파라미터는 건너뜀
            if (paramValue == null || paramValue.isEmpty()) {
                continue;
            }

            validateIdentifier(param.getColumnName(), "조건 컬럼명");

            String condition = buildCondition(param, paramValue, values);
            conditions.add(condition);
        }

        if (conditions.isEmpty()) {
            return new WhereResult("", Collections.emptyList());
        }

        return new WhereResult(" WHERE " + String.join(" AND ", conditions), values);
    }

    private String buildCondition(ApiPrvOperationParam param, String value, List<Object> values) {
        String column = param.getColumnName();
        String operator = param.getOperator().toUpperCase();
        String dataType = param.getDataType() != null ? param.getDataType().toUpperCase() : "STRING";

        switch (operator) {
            case "EQ":
                values.add(convertValue(value, dataType));
                return column + " = ?";

            case "LIKE":
                values.add("%" + value + "%");
                return column + " LIKE ?";

            case "LIKE_START":
                values.add(value + "%");
                return column + " LIKE ?";

            case "LIKE_END":
                values.add("%" + value);
                return column + " LIKE ?";

            case "GT":
                values.add(convertValue(value, dataType));
                return column + " > ?";

            case "GTE":
                values.add(convertValue(value, dataType));
                return column + " >= ?";

            case "LT":
                values.add(convertValue(value, dataType));
                return column + " < ?";

            case "LTE":
                values.add(convertValue(value, dataType));
                return column + " <= ?";

            case "IN":
                String[] inValues = value.split(",");
                String placeholders = Arrays.stream(inValues)
                        .map(v -> {
                            values.add(convertValue(v.trim(), dataType));
                            return "?";
                        })
                        .collect(Collectors.joining(", "));
                return column + " IN (" + placeholders + ")";

            case "BETWEEN":
                String[] betweenValues = value.split(",");
                if (betweenValues.length != 2) {
                    throw new IllegalArgumentException("BETWEEN 연산자는 콤마로 구분된 2개 값이 필요합니다: " + param.getParamName());
                }
                values.add(convertValue(betweenValues[0].trim(), dataType));
                values.add(convertValue(betweenValues[1].trim(), dataType));
                return column + " BETWEEN ? AND ?";

            default:
                throw new IllegalArgumentException("지원하지 않는 연산자: " + operator);
        }
    }

    private Object convertValue(String value, String dataType) {
        switch (dataType) {
            case "NUMBER":
                if (value.contains(".")) return Double.parseDouble(value);
                return Long.parseLong(value);
            case "DATE":
                // PG는 문자열을 자동 캐스팅하므로 그대로 전달
                return value;
            default:
                return value;
        }
    }

    private String buildOrderByClause(String column, String direction) {
        if (column == null || column.isEmpty()) return "";
        validateIdentifier(column, "정렬 컬럼명");

        String dir = "ASC";
        if (direction != null && "DESC".equalsIgnoreCase(direction)) {
            dir = "DESC";
        }
        return " ORDER BY " + column + " " + dir;
    }

    private void validateIdentifier(String identifier, String label) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("유효하지 않은 " + label + ": " + identifier);
        }
    }

    /**
     * v3 레거시 호환 — 응답 row 에 RNUM 첫 키로 부착 (1부터 결과셋 위치 순번)
     * v3 SQL의 SELECT ROWNUM AS RNUM, TB1.* 흉내. 컬럼 값 의존 없이 List 인덱스만 사용.
     */
    private List<Map<String, Object>> prependRownum(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            ordered.put("RNUM", i + 1);
            ordered.putAll(rows.get(i));
            result.add(ordered);
        }
        return result;
    }

    // ========== 내부 DTO ==========

    private static class SqlBuildResult {
        final String dataSql;
        final List<Object> dataParams;
        final String countSql;
        final List<Object> countParams;

        SqlBuildResult(String dataSql, List<Object> dataParams, String countSql, List<Object> countParams) {
            this.dataSql = dataSql;
            this.dataParams = dataParams;
            this.countSql = countSql;
            this.countParams = countParams;
        }
    }

    private static class WhereResult {
        final String clause;
        final List<Object> params;

        WhereResult(String clause, List<Object> params) {
            this.clause = clause;
            this.params = params;
        }
    }
}
