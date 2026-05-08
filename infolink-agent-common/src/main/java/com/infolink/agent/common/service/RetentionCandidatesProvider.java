package com.infolink.agent.common.service;

import com.infolink.agent.common.model.RetentionCandidate;

import java.util.List;

/**
 * Agent yml 의 retention-candidates 를 외부에 노출하는 인터페이스.
 *
 * <p>각 Agent 모듈 (bojo-dmz, bojo-internal, others-dmz, provide-dmz) 에서
 * 자기 모듈의 AgentConfigLoader 기반으로 빈 등록한다. DataRetentionController 가
 * cleanup 호출 시 이 Provider 로 후보 조회 + (table, dateColumn) 검증.</p>
 *
 * <p>4 layer 검증의 마지막 layer (D — defense-in-depth):
 * yml → Frontend → Backend PUT → Agent 자체 검증.</p>
 *
 * <p>빈으로 등록되지 않으면 (Optional null) DataRetentionController 가 검증 skip
 * (backward-compat — 기존 Agent 영향 없음).</p>
 *
 * @see dev_plan/2026_05/08/retention-candidates-safety.md §3-3 layer D
 */
public interface RetentionCandidatesProvider {
    /**
     * Agent 의 retention 후보 반환.
     * @param agentCode Agent 식별자
     * @return retention-candidates list. 빈 List = retention 비대상 Agent. agentCode 미등록 시 빈 List.
     */
    List<RetentionCandidate> getCandidates(String agentCode);
}
