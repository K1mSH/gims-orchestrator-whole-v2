package com.sync.orchestrator.domain.schedule;

import com.sync.orchestrator.domain.execution.ExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
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
public class ScheduleExecutor {

    private final TaskScheduler taskScheduler;
    private final ScheduleRepository scheduleRepository;
    private final ExecutionService executionService;

    // 스케줄 ID -> ScheduledFuture 매핑 (취소용)
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public ScheduleExecutor(
            TaskScheduler taskScheduler,
            ScheduleRepository scheduleRepository,
            @Lazy ExecutionService executionService) {
        this.taskScheduler = taskScheduler;
        this.scheduleRepository = scheduleRepository;
        this.executionService = executionService;
    }

    /**
     * 애플리케이션 시작 시 활성화된 스케줄 로드
     */
    @PostConstruct
    public void init() {
        log.info("Initializing schedule executor...");
        List<Schedule> enabledSchedules = scheduleRepository.findEnabledSchedulesWithAgent();
        log.info("Found {} enabled schedules", enabledSchedules.size());

        for (Schedule schedule : enabledSchedules) {
            registerSchedule(schedule);
        }
    }

    /**
     * 애플리케이션 종료 시 모든 스케줄 취소
     */
    @PreDestroy
    public void destroy() {
        log.info("Shutting down schedule executor...");
        scheduledTasks.values().forEach(future -> future.cancel(false));
        scheduledTasks.clear();
    }

    /**
     * 스케줄 등록
     */
    public void registerSchedule(Schedule schedule) {
        if (schedule.getScheduleId() == null) {
            log.warn("Cannot register schedule without ID");
            return;
        }

        // 기존 스케줄이 있으면 먼저 취소
        cancelSchedule(schedule.getScheduleId());

        if (!Boolean.TRUE.equals(schedule.getIsEnabled())) {
            log.info("Schedule {} is disabled, not registering", schedule.getScheduleId());
            return;
        }

        try {
            String agentId = schedule.getAgent().getAgentId();
            String cronExpression = schedule.getCronExpression();

            log.info("Registering schedule: id={}, agent={}, cron={}",
                    schedule.getScheduleId(), agentId, cronExpression);

            Runnable task = () -> executeAgent(schedule.getScheduleId(), agentId);
            CronTrigger trigger = new CronTrigger(cronExpression);

            ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
            scheduledTasks.put(schedule.getScheduleId(), future);

            log.info("Schedule {} registered successfully", schedule.getScheduleId());
        } catch (Exception e) {
            log.error("Failed to register schedule {}: {}", schedule.getScheduleId(), e.getMessage(), e);
        }
    }

    /**
     * 스케줄 취소
     */
    public void cancelSchedule(Long scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
            log.info("Schedule {} cancelled", scheduleId);
        }
    }

    /**
     * 스케줄 갱신 (취소 후 재등록)
     */
    public void refreshSchedule(Long scheduleId) {
        scheduleRepository.findById(scheduleId).ifPresent(this::registerSchedule);
    }

    /**
     * Agent 실행
     * Schedule에 executionOptions가 있으면 필터를 포함하여 실행
     * 필터 JSON 형식: {"filters":[{"paramId":"obsv-code","value":"GPM-123"}]}
     */
    @SuppressWarnings("unchecked")
    private void executeAgent(Long scheduleId, String agentId) {
        log.info("Scheduled execution triggered: scheduleId={}, agentId={}", scheduleId, agentId);

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
                        log.info("Scheduled execution started with filters: agentId={}", agentId);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse schedule execution options: {}", e.getMessage());
                }
            }

            executionService.triggerExecution(agentId, "SCHEDULE");
            log.info("Scheduled execution started successfully: agentId={}", agentId);
        } catch (Exception e) {
            log.error("Scheduled execution failed: agentId={}, error={}", agentId, e.getMessage(), e);
        }
    }

    /**
     * 현재 등록된 스케줄 수 조회
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
