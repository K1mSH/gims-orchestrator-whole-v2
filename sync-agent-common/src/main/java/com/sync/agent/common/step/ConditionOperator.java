package com.sync.agent.common.step;

/**
 * 동적 WHERE 조건 연산자
 */
public enum ConditionOperator {
    EQ,           // = ?
    NEQ,          // != ?
    GT,           // > ?
    GTE,          // >= ?
    LT,           // < ?
    LTE,          // <= ?
    BETWEEN,      // BETWEEN ? AND ?
    IN,           // IN (?, ?, ...)
    LIKE,         // LIKE ?  (사용자가 % 직접 포함)
    IS_NULL,      // IS NULL
    IS_NOT_NULL   // IS NOT NULL
}
