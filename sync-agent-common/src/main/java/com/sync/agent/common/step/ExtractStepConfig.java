package com.sync.agent.common.step;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Source → IF 테이블 추출 Step 설정
 *
 * IF 테이블 메타 컬럼:
 * - source_refs: 출처 정보 JSON 배열 ["zone:dsId:tbId:pk", ...]
 * - link_status: 연계 상태 (PENDING → SUCCESS/FAILED)
 * - extracted_at: 최초 추출 시간
 * - updated_at: 마지막 수정 시간
 * - execution_id: 실행 ID
 */
@Getter
@Builder
public class ExtractStepConfig {

    /**
     * Step ID (예: "order-extract")
     */
    private final String stepId;

    /**
     * Step 이름 (예: "주문 데이터 추출")
     */
    private final String stepName;

    /**
     * 추출 유형
     * - SIMPLE_COPY: 단순 복제 (Source 1:1 IF), Source→IF→Target 전체 추적 가능
     * - CUSTOM_STAGING: 커스텀 로직 (Source N:M IF), IF→Target만 추적
     */
    @Builder.Default
    private final ExtractType extractType = ExtractType.SIMPLE_COPY;

    /**
     * 커스텀 데이터 조회 로직 (CUSTOM_STAGING일 때 사용)
     * null이면 기본 조회 로직 사용
     */
    private final DataFetcher customDataFetcher;

    /**
     * Source 테이블 이름 (SIMPLE_COPY일 때 사용)
     */
    private final String sourceTable;

    /**
     * Target IF 테이블 이름
     */
    private final String targetIfTable;

    /**
     * IF 테이블 컬럼 목록 (예: ["order_id", "customer_id", "amount"])
     * null이면 SIMPLE_COPY 모드에서 source 테이블에서 자동 감지
     */
    private final List<String> columns;

    /**
     * Primary Key 컬럼 이름 (예: "order_id")
     */
    private final String primaryKeyColumn;

    /**
     * Primary Key 컬럼 목록 (복합 PK 지원)
     * 예: ["obsv_code", "obsv_date", "obsv_time"]
     */
    private final List<String> primaryKeyColumns;

    /**
     * UPSERT 충돌 기준 컬럼 (선택적)
     * 설정 시 ON CONFLICT에 primaryKey 대신 이 컬럼을 사용
     * 예: "source_refs" - 외부 DB에 PK 중복이 있는 경우 source_refs로 충돌 판단
     * null이면 기존 primaryKey 사용 (기본 동작)
     */
    private final String conflictKey;

    /**
     * 전체 복사 모드 (시간 조건 없이 전체 조회)
     * true면 시간 필터링 없이 모든 레코드 조회 후 UPSERT
     * 마스터 테이블(제원 등)에 적합
     */
    @Builder.Default
    private final boolean fullCopy = false;

    /**
     * Source 테이블 link_status 업데이트 건너뛰기
     * true: Source 업데이트 안함 (RSV 등 외부 DB - VIEW라서 업데이트 불가)
     * false: Source 업데이트 함 (SND 등 내부 DB - 정상 업데이트)
     */
    @Builder.Default
    private final boolean skipSourceStatusUpdate = false;

    /**
     * 시간 기준 컬럼 (예: "obsr_dt") - SIMPLE_COPY일 때 사용
     * TIMESTAMP 타입 컬럼이면 이것만 설정
     * DATE + TIME 분리된 경우 dateColumn과 함께 사용
     * fullCopy=true면 무시됨
     */
    private final String timeColumn;

    /**
     * 날짜 컬럼 (예: "obsv_date") - DATE + TIME 분리된 테이블용
     * timeColumn과 함께 사용하면 (dateColumn + timeColumn)으로 조합
     * PostgreSQL: "obsv_date" + "obsv_time" → timestamp
     */
    private final String dateColumn;

    /**
     * 커스텀 시간 표현식 (예: "obsv_date + obsv_time")
     * 설정 시 timeColumn, dateColumn 대신 이 표현식 사용
     * DB 함수 사용 가능: "COALESCE(updated_at, created_at)"
     */
    private final String timeExpression;

    // ==================== 기간(Period) 설정 ====================

    /**
     * 기본 조회 범위 (시간, 기본값 3시간) - SIMPLE_COPY일 때 사용
     */
    @Builder.Default
    private final int defaultLookbackHours = 3;

    /**
     * 조회 범위 단위 (HOURS, DAYS, MINUTES)
     * defaultLookbackHours와 함께 사용
     */
    @Builder.Default
    private final LookbackUnit lookbackUnit = LookbackUnit.HOURS;

    /**
     * 조회 범위 값 (lookbackUnit과 함께 사용)
     * 0이면 defaultLookbackHours 사용 (하위 호환)
     */
    @Builder.Default
    private final int lookbackValue = 0;

    /**
     * Primary Key 컬럼 목록 반환 (호환성 유지)
     */
    public List<String> getPrimaryKeyColumnList() {
        if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
            return primaryKeyColumns;
        }
        if (primaryKeyColumn != null && !primaryKeyColumn.isEmpty()) {
            // 콤마 구분 복합키 지원: "obsv_code,obsv_date,obsv_time" → ["obsv_code","obsv_date","obsv_time"]
            if (primaryKeyColumn.contains(",")) {
                return java.util.Arrays.stream(primaryKeyColumn.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
            return List.of(primaryKeyColumn);
        }
        return List.of();
    }

    /**
     * SIMPLE_COPY인지 확인
     */
    public boolean isSimpleCopy() {
        return extractType == ExtractType.SIMPLE_COPY;
    }

    /**
     * CUSTOM_STAGING인지 확인
     */
    public boolean isCustomStaging() {
        return extractType == ExtractType.CUSTOM_STAGING;
    }

    /**
     * 시간 표현식 반환 (SQL WHERE 절에 사용)
     * 우선순위: timeExpression > (dateColumn + timeColumn) > timeColumn
     *
     * @param actualDateCol 실제 날짜 컬럼명 (대소문자 처리된)
     * @param actualTimeCol 실제 시간 컬럼명 (대소문자 처리된)
     * @return SQL 시간 표현식
     */
    public String getTimeExpressionSql(String actualDateCol, String actualTimeCol) {
        // 1. 커스텀 표현식이 있으면 그대로 사용
        if (timeExpression != null && !timeExpression.isBlank()) {
            return timeExpression;
        }

        // 2. dateColumn + timeColumn 조합
        if (dateColumn != null && !dateColumn.isBlank()
                && timeColumn != null && !timeColumn.isBlank()) {
            // PostgreSQL: DATE + TIME = TIMESTAMP
            return "(\"" + actualDateCol + "\" + \"" + actualTimeCol + "\")";
        }

        // 3. timeColumn만 사용
        if (timeColumn != null && !timeColumn.isBlank()) {
            return "\"" + actualTimeCol + "\"";
        }

        throw new IllegalStateException("No time column configured. Set timeColumn, dateColumn+timeColumn, or timeExpression.");
    }

    /**
     * 조회 범위(Lookback) 시간 계산 (시간 단위로 반환)
     */
    public long getLookbackHoursEffective() {
        if (lookbackValue > 0) {
            return switch (lookbackUnit) {
                case MINUTES -> Math.max(1, lookbackValue / 60);
                case HOURS -> lookbackValue;
                case DAYS -> lookbackValue * 24L;
            };
        }
        return defaultLookbackHours;
    }

    /**
     * 조회 범위(Lookback) 분 단위로 계산
     */
    public long getLookbackMinutes() {
        if (lookbackValue > 0) {
            return switch (lookbackUnit) {
                case MINUTES -> lookbackValue;
                case HOURS -> lookbackValue * 60L;
                case DAYS -> lookbackValue * 24L * 60L;
            };
        }
        return defaultLookbackHours * 60L;
    }

    /**
     * 조회 범위 단위
     */
    public enum LookbackUnit {
        MINUTES, HOURS, DAYS
    }
}
