package com.sync.orchestrator.domain.schedule;

import com.sync.orchestrator.domain.execution.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 스케줄 실행기
 * - cron 표현식에 따라 Agent 파이프라인 실행
 * - 동적으로 스케줄 추가/수정/삭제 지원
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleExecutor {

    private final TaskScheduler taskScheduler;
    private final ScheduleRepository scheduleRepository;
    private final ExecutionService executionService;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 애플리케이션 시작 시 활성화된 스케줄 로드
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStart() {
        List<Schedule> enabledSchedules = scheduleRepository.findEnabledSchedulesWithAgent();
        log.info("스케줄 {} 건 등록 시작", enabledSchedules.size());

        for (Schedule schedule : enabledSchedules) {
            registerSchedule(schedule);
        }
    }

    /**
     * 스케줄 등록
     */
    public void registerSchedule(Schedule schedule) {
        unregisterSchedule(schedule.getScheduleId());

        if (!Boolean.TRUE.equals(schedule.getIsEnabled())) {
            return;
        }

        try {
            Long agentId = schedule.getAgent().getId();
            String agentCode = schedule.getAgent().getAgentCode();
            String cronExpression = schedule.getCronExpression();

            Runnable task = () -> executeAgent(schedule.getScheduleId(), agentId, agentCode);
            ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cronExpression));
            scheduledTasks.put(schedule.getScheduleId(), future);

            log.info("스케줄 등록: id={}, agent={}, cron={}", schedule.getScheduleId(), agentCode, cronExpression);
        } catch (Exception e) {
            log.error("스케줄 등록 실패: id={}, error={}", schedule.getScheduleId(), e.getMessage(), e);
        }
    }

    /**
     * 스케줄 해제
     */
    public void unregisterSchedule(Long scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
            log.info("스케줄 해제: id={}", scheduleId);
        }
    }

    /**
     * Agent 실행
     * executionOptions가 있으면 필터 포함 실행
     */
    @SuppressWarnings("unchecked")
    private void executeAgent(Long scheduleId, Long agentId, String agentCode) {
        log.info("스케줄 실행: scheduleId={}, agent={}", scheduleId, agentCode);

        try {
            Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
            if (schedule != null && schedule.getExecutionOptions() != null
                    && !schedule.getExecutionOptions().isBlank()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map<String, Object> options = mapper.readValue(
                            schedule.getExecutionOptions(), java.util.Map.class);
                    Object filtersObj = options.get("filters");
                    if (filtersObj instanceof java.util.List) {
                        executionService.triggerExecution(agentId, null, null,
                                (java.util.List<java.util.Map<String, Object>>) filtersObj, "SCHEDULE");
                        log.info("스케줄 실행 시작 (필터 포함): agent={}", agentCode);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("스케줄 실행 옵션 파싱 실패: {}", e.getMessage());
                }
            }

            executionService.triggerExecution(agentId, "SCHEDULE");
            log.info("스케줄 실행 시작: agent={}", agentCode);
        } catch (Exception e) {
            log.error("스케줄 실행 실패: agent={}, error={}", agentCode, e.getMessage(), e);
        }
    }

    /**
     * 현재 등록된 스케줄 수
     */
    public int getActiveScheduleCount() {
        return scheduledTasks.size();
    }

    /**
     * 특정 스케줄이 등록되어 있는지 확인
     */
    public boolean isScheduled(Long scheduleId) {
        return scheduledTasks.containsKey(scheduleId);
    }
}
