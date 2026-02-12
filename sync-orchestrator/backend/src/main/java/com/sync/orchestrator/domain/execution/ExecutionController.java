package com.sync.orchestrator.domain.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 실행 API 컨트롤러
 * - 실행 데이터는 Agent DB에 저장됨
 * - Orchestrator는 Agent API 프록시 역할
 */
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    /**
     * Agent별 실행 이력 조회 (Agent DB에서)
     */
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<List<Map<String, Object>>> getExecutionsByAgent(@PathVariable String agentId) {
        return ResponseEntity.ok(executionService.findByAgentIdFromAgent(agentId));
    }

    /**
     * 전체 Agent 상태 조회 (대시보드용)
     */
    @GetMapping("/status")
    public ResponseEntity<List<ExecutionDto.AgentExecutionSummary>> getAgentStatuses() {
        return ResponseEntity.ok(executionService.getAgentStatuses());
    }

    /**
     * 실행 트리거 - Agent에 실행 요청
     * - body 없이 호출: 기본 lookback 사용 (증분 동기화)
     * - body에 startTime/endTime 지정: 해당 범위 재동기화
     */
    @PostMapping("/{agentId}/run")
    public ResponseEntity<ExecutionDto.TriggerResponse> triggerExecution(
            @PathVariable String agentId,
            @RequestBody(required = false) ExecutionDto.TriggerRequest request) {
        if (request != null && (request.getStartTime() != null || request.getEndTime() != null || request.getFilters() != null)) {
            return ResponseEntity.ok(executionService.triggerExecution(
                    agentId, request.getStartTime(), request.getEndTime(),
                    request.getFilters(), "MANUAL"));
        }
        return ResponseEntity.ok(executionService.triggerExecution(agentId));
    }

    /**
     * 실행 상세 정보 조회 (Agent DB에서)
     */
    @GetMapping("/{executionId}/detail")
    public ResponseEntity<Map<String, Object>> getExecutionDetail(@PathVariable String executionId) {
        return ResponseEntity.ok(executionService.getExecutionDetail(executionId));
    }

    /**
     * 실행 데이터 조회 (Agent 프록시) - 페이징/검색 지원
     */
    @GetMapping("/{executionId}/data/{dataType}")
    public ResponseEntity<Map<String, Object>> getExecutionData(
            @PathVariable String executionId,
            @PathVariable String dataType,
            ExecutionDto.TableDataSearchParams searchParams) {
        return ResponseEntity.ok(executionService.getExecutionData(executionId, dataType, searchParams));
    }

    /**
     * Step 로그 조회 (Agent DB에서)
     */
    @GetMapping("/{executionId}/steps")
    public ResponseEntity<List<Map<String, Object>>> getStepLogs(@PathVariable String executionId) {
        return ResponseEntity.ok(executionService.getStepLogs(executionId));
    }

    /**
     * 테이블별 통계 조회 (Agent DB에서)
     */
    @GetMapping("/{executionId}/tables")
    public ResponseEntity<List<Map<String, Object>>> getTableStats(@PathVariable String executionId) {
        return ResponseEntity.ok(executionService.getTableStats(executionId));
    }

    /**
     * 특정 테이블의 레코드 조회 (Agent DB에서)
     */
    @GetMapping("/{executionId}/tables/{tableName}")
    public ResponseEntity<List<Map<String, Object>>> getTableRecords(
            @PathVariable String executionId,
            @PathVariable String tableName) {
        return ResponseEntity.ok(executionService.getTableRecords(executionId, tableName));
    }

    /**
     * 특정 테이블의 실패 레코드 조회 (Agent DB에서)
     */
    @GetMapping("/{executionId}/tables/{tableName}/failed")
    public ResponseEntity<List<Map<String, Object>>> getTableFailedRecords(
            @PathVariable String executionId,
            @PathVariable String tableName) {
        return ResponseEntity.ok(executionService.getTableFailedRecords(executionId, tableName));
    }

    /**
     * Source PK로 데이터 추적 (Source → Target)
     */
    @GetMapping("/{executionId}/trace")
    public ResponseEntity<Map<String, Object>> traceBySourcePk(
            @PathVariable String executionId,
            @RequestParam String pkValue,
            @RequestParam(defaultValue = "id") String pkColumn,
            @RequestParam String sourceTable,
            @RequestParam(required = false) String ifTableName,
            @RequestParam(required = false) String targetTableName) {
        return ResponseEntity.ok(executionService.traceBySourcePk(executionId, pkValue, pkColumn, sourceTable, ifTableName, targetTableName));
    }

    /**
     * Target에서 Source로 역추적 (sourceRefs 기반)
     */
    @GetMapping("/{executionId}/trace-source")
    public ResponseEntity<Map<String, Object>> traceToSource(
            @PathVariable String executionId,
            @RequestParam String sourceRefs,
            @RequestParam(required = false) String sourceTable) {
        return ResponseEntity.ok(executionService.traceToSource(executionId, sourceRefs, sourceTable));
    }

    /**
     * 레코드 처리 이력 조회 (Agent 프록시)
     */
    @GetMapping("/{executionId}/record-history")
    public ResponseEntity<Map<String, Object>> getRecordHistory(
            @PathVariable String executionId,
            @RequestParam String tableName,
            @RequestParam String recordKey) {
        return ResponseEntity.ok(executionService.getRecordHistory(executionId, tableName, recordKey));
    }

    /**
     * 실행 ID별 처리 이력 조회 (Agent 프록시)
     * 이전 실행에서 처리한 레코드 이력을 조회 (UPSERT로 IF테이블 데이터가 덮어씌워진 경우에도 확인 가능)
     */
    @GetMapping("/{executionId}/record-history/by-execution")
    public ResponseEntity<Map<String, Object>> getRecordHistoryByExecution(
            @PathVariable String executionId) {
        return ResponseEntity.ok(executionService.getRecordHistoryByExecution(executionId));
    }

    // ==================== ExecutionHistory 관련 API ====================

    /**
     * 최근 실행 이력 조회 (대시보드용 - 최신 50건)
     */
    @GetMapping("/history")
    public ResponseEntity<List<ExecutionDto.HistoryResponse>> getRecentHistory() {
        return ResponseEntity.ok(executionService.getRecentHistory());
    }

    /**
     * 현재 실행 중인 이력 조회
     */
    @GetMapping("/history/running")
    public ResponseEntity<List<ExecutionDto.HistoryResponse>> getRunningHistory() {
        return ResponseEntity.ok(executionService.getRunningHistory());
    }

    /**
     * Agent별 실행 이력 조회 (Orchestrator DB)
     */
    @GetMapping("/history/agent/{agentId}")
    public ResponseEntity<List<ExecutionDto.HistoryResponse>> getHistoryByAgent(@PathVariable String agentId) {
        return ResponseEntity.ok(executionService.getHistoryByAgent(agentId));
    }

    /**
     * 실행 이력 페이징 조회
     */
    @GetMapping("/history/paged")
    public ResponseEntity<Page<ExecutionDto.HistoryResponse>> getHistoryPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(executionService.getHistoryPaged(pageable));
    }

    /**
     * 대시보드 통계 조회
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<ExecutionDto.DashboardStats> getDashboardStats() {
        return ResponseEntity.ok(executionService.getDashboardStats());
    }
}
