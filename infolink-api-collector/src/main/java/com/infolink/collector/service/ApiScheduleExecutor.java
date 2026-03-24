package com.infolink.collector.service;

import com.infolink.collector.entity.ApiExecutionHistory;
import com.infolink.collector.entity.ApiSchedule;
import com.infolink.collector.repository.ApiScheduleRepository;
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
 * - 앱 시작 시 enabled 스케줄 등록
 * - CRUD 시 동적 갱신 (register/unregister)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiScheduleExecutor {

    private final TaskScheduler taskScheduler;
    private final ApiScheduleRepository scheduleRepository;
    private final ApiExecutionService executionService;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationStart() {
        List<ApiSchedule> enabledSchedules = scheduleRepository.findByIsEnabledTrue();
        log.info("스케줄 {} 건 등록 시작", enabledSchedules.size());

        for (ApiSchedule schedule : enabledSchedules) {
            registerSchedule(schedule);
        }
    }

    /**
     * 스케줄 등록 (생성/활성화 시 호출)
     */
    public void registerSchedule(ApiSchedule schedule) {
        unregisterSchedule(schedule.getId());

        if (!Boolean.TRUE.equals(schedule.getIsEnabled())) {
            return;
        }

        try {
            Long endpointId = schedule.getApiEndpoint().getId();
            String cron = schedule.getCronExpression();

            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeScheduled(schedule.getId(), endpointId),
                    new CronTrigger(cron)
            );

            scheduledTasks.put(schedule.getId(), future);
            log.info("스케줄 등록: id={}, endpointId={}, cron={}", schedule.getId(), endpointId, cron);
        } catch (Exception e) {
            log.error("스케줄 등록 실패: id={}, error={}", schedule.getId(), e.getMessage());
        }
    }

    /**
     * 스케줄 해제 (삭제/비활성화 시 호출)
     */
    public void unregisterSchedule(Long scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
            log.info("스케줄 해제: id={}", scheduleId);
        }
    }

    /**
     * 스케줄에 의한 실행
     */
    private void executeScheduled(Long scheduleId, Long endpointId) {
        log.info("스케줄 실행: scheduleId={}, endpointId={}", scheduleId, endpointId);
        try {
            executionService.run(endpointId, ApiExecutionHistory.TriggeredBy.SCHEDULE);
            log.info("스케줄 실행 시작: endpointId={}", endpointId);
        } catch (Exception e) {
            log.error("스케줄 실행 실패: scheduleId={}, error={}", scheduleId, e.getMessage(), e);
        }
    }

    /**
     * 현재 등록된 스케줄 수
     */
    public int getActiveScheduleCount() {
        return scheduledTasks.size();
    }
}
