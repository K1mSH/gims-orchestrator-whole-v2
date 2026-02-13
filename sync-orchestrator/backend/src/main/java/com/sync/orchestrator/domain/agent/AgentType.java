package com.sync.orchestrator.domain.agent;

/**
 * Agent 유형
 *
 * - RCV: 외부 Source DB → IF-RSV 테이블 추출
 * - SND: 내부 Target DB → IF-SND 테이블 추출
 * - LOADER: IF 테이블 → Target DB 적재
 */
public enum AgentType {
    RCV,
    SND,
    LOADER
}
