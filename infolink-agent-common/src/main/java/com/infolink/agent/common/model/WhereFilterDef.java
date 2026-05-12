package com.infolink.agent.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent yml 의 where-filters 항목.
 *
 * <p>수동 실행(WHERE conditions) 시 운영자에게 노출/허용할 필터를 명시한다.
 * 로더처럼 내부 로직에 따라 "어느 테이블의 어느 컬럼에 조건을 거는 게 의미 있는지"가
 * 정해진 단계는 이걸로 큐레이션하고, 단순 카피 단계는 {@code column: "*"} 한 줄로
 * 그 테이블 전체 컬럼을 허용한다.</p>
 *
 * <p>{@code where-filters} 키가 없는 Agent 는 기존 동작 유지
 * ({@code select-tables} / steps 자동수집 기반 범용 드롭다운). 있으면 이 목록만 허용.</p>
 *
 * <p>{@code select-tables} / {@code retention-candidates} 와 동일한 흐름:
 * yml → AgentDefinition → agent {@code GET /api/pipeline/{code}/where-filters}
 * → Orchestrator 중계 → frontend conditions UI.</p>
 *
 * @see dev_plan/2026_05/12/yml-declared-where-filters.md
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhereFilterDef {

    /** 와일드카드 인식자 — {@code column} 이 이 값이면 "테이블 전체 컬럼 허용 (범용 모드)". */
    public static final String ALL_COLUMNS = "*";

    /** 프론트에서 다룰 식별 키 (예: "region", "period"). 없으면 column 으로 대체 가능. */
    private String key;

    /** 운영자 화면에 표시할 라벨 (예: "지역(관측코드)"). 범용 항목이면 생략 가능. */
    private String label;

    /** 조건 대상 테이블 (Agent 의 source/IF 테이블 중 하나). {@code "*"} 면 "아무 소스 테이블이나" 단축형. */
    private String table;

    /** 조건 대상 컬럼. {@link #ALL_COLUMNS}("*") 면 이 테이블 전체 컬럼 허용 (프론트가 메타에서 드롭다운). */
    private String column;

    /** 허용 연산자 목록 (예: ["LIKE","IN"], ["BETWEEN"]). null/빈 값이면 전체 허용. ExecutionCondition.Operator 이름. */
    private List<String> operators;

    /** 값 입력 타입 힌트 — "STRING" | "DATE" | "DATETIME" | "NUMBER". 프론트 입력 위젯 선택용. null 이면 STRING 취급. */
    private String valueType;

    /** 입력 placeholder/도움말 (예: "예: GN-SAC-G1%  /  GN-%  /  코드목록(쉼표)"). */
    private String hint;

    /** 이 항목이 "테이블 전체 컬럼 허용" 범용 항목인지. */
    public boolean isAllColumns() {
        return ALL_COLUMNS.equals(column);
    }

    /** 이 항목이 "아무 소스 테이블이나" 범용 항목인지. */
    public boolean isAllTables() {
        return ALL_COLUMNS.equals(table);
    }
}
