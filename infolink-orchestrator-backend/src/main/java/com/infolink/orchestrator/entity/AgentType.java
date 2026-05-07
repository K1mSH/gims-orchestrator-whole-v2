package com.infolink.orchestrator.entity;

/**
 * Agent 유형
 *
 * - RCV: 외부 Source DB → IF-RSV 테이블 추출
 * - SND: 내부 Target DB → IF-SND 테이블 추출
 * - LOADER: IF 테이블 → Target DB 적재
 * - DB_CON_PROXY: DB 연결 프록시 (조회 전용, 파이프라인 실행 없음)
 */
public enum AgentType {
    RCV,
    SND,
    LOADER,
    DB_CON_PROXY
}
