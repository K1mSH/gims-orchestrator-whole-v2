package com.sync.agent.common.step;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동적 실행 조건 (WHERE 절 구성 요소)
 *
 * 프론트 → Orchestrator → Agent로 전달되어 SQL WHERE 절을 동적 생성.
 * 같은 column의 디폴트 조건을 대체하거나, 새 column이면 AND로 추가.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionCondition {

    /** 조건 대상 테이블명 (select-tables 기준). null이면 모든 Step에 적용 (하위호환) */
    private String tableName;

    /** 대상 컬럼명 (DatasourceColumn에 등록된 컬럼만 허용) */
    private String column;

    /** 연산자 */
    private ConditionOperator operator;

    /** 값 (EQ, NEQ, GT, GTE, LT, LTE, LIKE, IN에서 사용) */
    private String value;

    /** 두 번째 값 (BETWEEN에서 사용) */
    private String value2;

    // === 팩토리 메서드 (디폴트 조건 정의용) ===

    public static ExecutionCondition eq(String column, String value) {
        return new ExecutionCondition(null, column, ConditionOperator.EQ, value, null);
    }

    public static ExecutionCondition neq(String column, String value) {
        return new ExecutionCondition(null, column, ConditionOperator.NEQ, value, null);
    }

    public static ExecutionCondition gt(String column, String value) {
        return new ExecutionCondition(null, column, ConditionOperator.GT, value, null);
    }

    public static ExecutionCondition gte(String column, String value) {
        return new ExecutionCondition(null, column, ConditionOperator.GTE, value, null);
    }

    public static ExecutionCondition lt(String column, String value) {
        return new ExecutionCondition(null, column, ConditionOperator.LT, value, null);
    }

    public static ExecutionCondition lte(String column, String value) {
        return new ExecutionCondition(null, column, ConditionOperator.LTE, value, null);
    }

    public static ExecutionCondition between(String column, String from, String to) {
        return new ExecutionCondition(null, column, ConditionOperator.BETWEEN, from, to);
    }

    public static ExecutionCondition in(String column, String commaSeparatedValues) {
        return new ExecutionCondition(null, column, ConditionOperator.IN, commaSeparatedValues, null);
    }

    public static ExecutionCondition like(String column, String pattern) {
        return new ExecutionCondition(null, column, ConditionOperator.LIKE, pattern, null);
    }

    public static ExecutionCondition isNull(String column) {
        return new ExecutionCondition(null, column, ConditionOperator.IS_NULL, null, null);
    }

    public static ExecutionCondition isNotNull(String column) {
        return new ExecutionCondition(null, column, ConditionOperator.IS_NOT_NULL, null, null);
    }
}
