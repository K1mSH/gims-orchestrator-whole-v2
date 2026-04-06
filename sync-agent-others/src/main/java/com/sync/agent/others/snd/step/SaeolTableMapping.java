package com.sync.agent.others.snd.step;

import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 새올 테이블 매핑 정보
 *
 * YAML의 각 step에서 파싱된 테이블별 설정.
 * LINK_PLAN 컬럼명 → 소스 PK 컬럼명 매핑을 포함.
 */
@Getter
@Builder
public class SaeolTableMapping {

    /** 소스 테이블 (Oracle, 읽기 전용) */
    private final String sourceTable;

    /** IF_SND 타겟 테이블 (Oracle, 쓰기) */
    private final String targetTable;

    /** 소스 PK 컬럼 (쉼표 구분) */
    private final String primaryKey;

    /**
     * LINK_PLAN 컬럼 → 소스 WHERE 컬럼 매핑
     * 예: { "sf_team_code": "REL_TRANS_CGG_CODE", "perm_nt_no": "PERM_NT_NO" }
     * LinkedHashMap으로 순서 보장
     */
    @Builder.Default
    private final Map<String, String> linkPlanKeys = new LinkedHashMap<>();
}
