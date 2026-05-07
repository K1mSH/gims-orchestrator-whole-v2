package com.infolink.orchestrator.scheduler;

import com.infolink.orchestrator.entity.Agent;
import com.infolink.orchestrator.entity.AgentStatus;
import com.infolink.orchestrator.repository.AgentRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.orchestrator.entity.ExecutionHistory;
import com.infolink.orchestrator.repository.ExecutionHistoryRepository;
import com.infolink.orchestrator.entity.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 서버 사이드 Agent 상태 확인 스케줄러
 * - 30초 간격으로 전체 Agent health check (병렬)
 * - Agent health 응답의 runningAgents로 실제 실행 중 여부 판단
 * - RUNNING 잔류 자동 복구: Agent가 실행 중이 아닌데 DB만 RUNNING인 경우 정리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHealthScheduler {

    private final AgentRepository agentRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final com.infolink.orchestrator.service.AgentService agentService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * health 응답 결과 (프로세스 생존 여부 + 실제 실행 중인 agentCode 목록)
     */
    private static class HealthResult {
        final boolean alive;
        final Set<String> runningAgents;

        HealthResult(boolean alive, Set<String> runningAgents) {
            this.alive = alive;
            this.runningAgents = runningAgents;
        }

        static HealthResult offline() {
            return new HealthResult(false, Collections.emptySet());
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void checkAllAgentHealth() {
        List<Agent> agents = agentRepository.findByIsActiveTrue();
        if (agents.isEmpty()) return;

        // 동일 endpointUrl끼리 그룹핑 (하나의 물리 앱에 여러 Agent)
        Map<String, List<Agent>> byEndpoint = agents.stream()
                .collect(Collectors.groupingBy(Agent::getEndpointUrl));

        // 엔드포인트별 병렬 health check
        Map<String, HealthResult> healthResults = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = byEndpoint.keySet().stream()
                .map(url -> CompletableFuture.runAsync(() ->
                        healthResults.put(url, pingHealth(url))
                ))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int changed = 0;
        int recovered = 0;

        for (Agent agent : agents) {
            HealthResult health = healthResults.get(agent.getEndpointUrl());
            if (health == null) continue;

            AgentStatus currentStatus = agent.getStatus();
            boolean actuallyRunning = health.runningAgents.contains(agent.getAgentCode());

            if (!health.alive) {
                // 프로세스 죽음
                if (currentStatus != AgentStatus.OFFLINE) {
                    log.warn("Agent {} 프로세스 응답 없음: {} → OFFLINE", agent.getAgentCode(), currentStatus);
                    agent.setStatus(AgentStatus.OFFLINE);
                    agentRepository.save(agent);
                    if (currentStatus == AgentStatus.RUNNING) {
                        recoverStuckExecutions(agent.getAgentCode());
                        recovered++;
                    }
                    changed++;
                }
            } else if (actuallyRunning) {
                // 실제 실행 중
                if (currentStatus != AgentStatus.RUNNING) {
                    log.info("Agent {} 실행 중 감지: {} → RUNNING", agent.getAgentCode(), currentStatus);
                    agent.setStatus(AgentStatus.RUNNING);
                    agentRepository.save(agent);
                    changed++;
                }
            } else {
                // 프로세스 살아있고 실행 중 아님 → ONLINE
                if (currentStatus != AgentStatus.ONLINE) {
                    if (currentStatus == AgentStatus.RUNNING) {
                        log.info("Agent {} 실행 완료 감지: RUNNING → ONLINE", agent.getAgentCode());
                        recoverStuckExecutions(agent.getAgentCode());
                        recovered++;
                    } else {
                        log.info("Agent {} 상태 변경: {} → ONLINE", agent.getAgentCode(), currentStatus);
                    }
                    agent.setStatus(AgentStatus.ONLINE);
                    agentRepository.save(agent);
                    changed++;
                    // OFFLINE→ONLINE: pipeline/info 기반 agent_table 자동 동기화
                    if (currentStatus == AgentStatus.OFFLINE) {
                        try {
                            agentService.syncAgentTables(agent.getId());
                        } catch (Exception syncEx) {
                            log.warn("Agent {} 테이블 자동 갱신 실패: {}", agent.getAgentCode(), syncEx.getMessage());
                        }
                    }
                }
            }
        }

        // 고아 이력 정리: Agent.status≠RUNNING인데 ExecutionHistory만 RUNNING인 경우
        int orphanRecovered = recoverOrphanExecutions(agents);

        long online = agents.stream().filter(a -> a.getStatus() == AgentStatus.ONLINE).count();
        long offline = agents.stream().filter(a -> a.getStatus() == AgentStatus.OFFLINE).count();
        long running = agents.stream().filter(a -> a.getStatus() == AgentStatus.RUNNING).count();

        if (changed > 0 || orphanRecovered > 0) {
            log.info("Health check: online={}, offline={}, running={}, 변경={}건{}{}",
                    online, offline, running, changed,
                    recovered > 0 ? ", RUNNING 복구=" + recovered + "건" : "",
                    orphanRecovered > 0 ? ", 고아 이력 복구=" + orphanRecovered + "건" : "");
        } else {
            log.debug("Health check: online={}, offline={}, running={}", online, offline, running);
        }
    }

    /**
     * RUNNING 잔류 ExecutionHistory를 SUCCESS로 정리
     */
    private void recoverStuckExecutions(String agentCode) {
        List<ExecutionHistory> stuckList = executionHistoryRepository
                .findByStatusOrderByStartedAtAsc(ExecutionStatus.RUNNING)
                .stream()
                .filter(h -> agentCode.equals(h.getAgentCode()))
                .collect(Collectors.toList());

        for (ExecutionHistory history : stuckList) {
            history.setStatus(ExecutionStatus.SUCCESS);
            history.setErrorMessage("콜백 유실 - 헬스체크에서 자동 복구");
            history.setFinishedAt(LocalDateTime.now());
            executionHistoryRepository.save(history);
            log.info("ExecutionHistory {} RUNNING → SUCCESS 자동 복구", history.getExecutionId());
        }
    }

    /**
     * Agent.status는 RUNNING이 아닌데 ExecutionHistory만 RUNNING인 고아 이력 정리
     */
    private int recoverOrphanExecutions(List<Agent> agents) {
        List<ExecutionHistory> runningHistories = executionHistoryRepository
                .findByStatusOrderByStartedAtAsc(ExecutionStatus.RUNNING);
        if (runningHistories.isEmpty()) return 0;

        Set<String> actuallyRunning = agents.stream()
                .filter(a -> a.getStatus() == AgentStatus.RUNNING)
                .map(Agent::getAgentCode)
                .collect(Collectors.toSet());

        int count = 0;
        for (ExecutionHistory history : runningHistories) {
            if (!actuallyRunning.contains(history.getAgentCode())) {
                history.setStatus(ExecutionStatus.SUCCESS);
                history.setErrorMessage("콜백 유실 - 헬스체크에서 자동 복구");
                history.setFinishedAt(LocalDateTime.now());
                executionHistoryRepository.save(history);
                log.info("고아 ExecutionHistory {} (agent={}) RUNNING → SUCCESS 자동 복구",
                        history.getExecutionId(), history.getAgentCode());
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private HealthResult pingHealth(String endpointUrl) {
        try {
            String healthUrl = endpointUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return HealthResult.offline();
            }

            // runningAgents 파싱
            Set<String> runningAgents = Collections.emptySet();
            if (response.getBody() != null) {
                try {
                    Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
                    Object running = body.get("runningAgents");
                    if (running instanceof Collection) {
                        runningAgents = ((Collection<String>) running).stream()
                                .collect(Collectors.toSet());
                    }
                } catch (Exception e) {
                    log.debug("Health 응답 파싱 실패 (무시): {}", e.getMessage());
                }
            }
            return new HealthResult(true, runningAgents);
        } catch (Exception e) {
            return HealthResult.offline();
        }
    }
}
