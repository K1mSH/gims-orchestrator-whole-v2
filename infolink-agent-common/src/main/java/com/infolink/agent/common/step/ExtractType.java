package com.infolink.agent.common.step;

/**
 * Source → IF 추출 유형
 */
public enum ExtractType {

    /**
     * 단순 복제: Source 1개 → IF (1:1)
     * - Source 테이블을 그대로 IF에 복제
     * - Source → IF → Target 전체 추적 가능
     */
    SIMPLE_COPY,

    /**
     * 커스텀 스테이징: Source N개 → IF (N:M)
     * - 여러 Source를 조합/가공해서 IF에 적재
     * - IF → Target만 추적 가능 (Source 추적은 로직 레벨)
     */
    CUSTOM_STAGING
}
