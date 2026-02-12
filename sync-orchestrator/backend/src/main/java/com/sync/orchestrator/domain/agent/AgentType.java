package com.sync.orchestrator.domain.agent;

/**
 * Agent 유형
 *
 * [RELAY 계열] - 데이터 추출 및 IF 테이블로 전달
 * - RELAY: source → IF-RSV, target → IF-SND (별도 프로세스로 기동)
 *
 * [LOADER 계열] - IF 테이블에서 target으로 적재
 * - LOADER_STANDARD: 표준 데이터 미러링 (1:N 컬럼 매핑)
 * - LOADER_CUSTOM: 커스텀 로직이 포함된 Loader
 */
public enum AgentType {
    // Relay 계열
    RELAY,

    // Loader 계열 (기존 STANDARD, CUSTOM 유지)
    LOADER_STANDARD,
    LOADER_CUSTOM
}
