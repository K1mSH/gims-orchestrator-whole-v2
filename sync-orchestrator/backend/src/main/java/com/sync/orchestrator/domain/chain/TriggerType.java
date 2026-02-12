package com.sync.orchestrator.domain.chain;

public enum TriggerType {
    INDIVIDUAL,   // 각 Agent가 개별 스케줄로 실행
    SEQUENTIAL    // Chain의 첫 Agent 완료 후 순차 실행
}
