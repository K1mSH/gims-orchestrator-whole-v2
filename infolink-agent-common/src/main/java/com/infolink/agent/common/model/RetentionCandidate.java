package com.infolink.agent.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent yml 의 retention-candidates 항목.
 *
 * <p>운영자가 retention 설정 시 선택 가능한 (table, dateColumn) 후보를 명시한다.
 * 빈 배열이면 해당 Agent 는 retention 비대상 (마스터 / Link / 메타 데이터).</p>
 *
 * <p>4 layer 검증의 단일 진실원 (single source of truth):
 * <ul>
 *   <li>yml 이 후보 명시</li>
 *   <li>Frontend dropdown = 후보만</li>
 *   <li>Backend PUT validation = 후보 외 거부</li>
 *   <li>Agent DataRetentionService = 실행 시점 후보 외 거부 (최후 방어)</li>
 * </ul></p>
 *
 * @see dev_plan/2026_05/08/retention-candidates-safety.md
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetentionCandidate {
    /** retention 적용 대상 테이블 명 (Agent datasource 안). */
    private String table;

    /** WHERE 절의 cutoff 비교 기준 컬럼 — 데이터 발생 시점 (관측일/측정일). 등록일/설치일 류는 부적합. */
    private String dateColumn;

    /** 운영자 화면에 표시할 설명 (예: "관측 시계열 데이터"). */
    private String description;
}
