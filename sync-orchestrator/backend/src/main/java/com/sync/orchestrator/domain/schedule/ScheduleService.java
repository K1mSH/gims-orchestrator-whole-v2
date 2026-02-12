package com.sync.orchestrator.domain.schedule;

import com.sync.orchestrator.domain.agent.Agent;
import com.sync.orchestrator.domain.agent.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final AgentRepository agentRepository;
    private final ScheduleExecutor scheduleExecutor;

    public List<ScheduleDto.Response> findAll() {
        return scheduleRepository.findAll().stream()
                .map(ScheduleDto.Response::from)
                .collect(Collectors.toList());
    }

    public ScheduleDto.Response findById(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
        return ScheduleDto.Response.from(schedule);
    }

    public List<ScheduleDto.Response> findByAgentId(String agentId) {
        return scheduleRepository.findByAgentAgentId(agentId).stream()
                .map(ScheduleDto.Response::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduleDto.Response create(ScheduleDto.CreateRequest request) {
        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + request.getAgentId()));

        Schedule schedule = Schedule.builder()
                .agent(agent)
                .cronExpression(request.getCronExpression())
                .isEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true)
                .executionOptions(request.getExecutionOptions())
                .build();

        Schedule saved = scheduleRepository.save(schedule);

        // 스케줄 등록
        log.info("Registering new schedule: id={}, agent={}, cron={}",
                saved.getScheduleId(), agent.getAgentId(), saved.getCronExpression());
        scheduleExecutor.registerSchedule(saved);

        return ScheduleDto.Response.from(saved);
    }

    @Transactional
    public ScheduleDto.Response update(Long scheduleId, ScheduleDto.UpdateRequest request) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        if (request.getCronExpression() != null) {
            schedule.setCronExpression(request.getCronExpression());
        }
        if (request.getIsEnabled() != null) {
            schedule.setIsEnabled(request.getIsEnabled());
        }
        if (request.getExecutionOptions() != null) {
            schedule.setExecutionOptions(request.getExecutionOptions());
        }

        // 스케줄 갱신
        log.info("Updating schedule: id={}, cron={}, enabled={}",
                scheduleId, schedule.getCronExpression(), schedule.getIsEnabled());
        scheduleExecutor.registerSchedule(schedule);

        return ScheduleDto.Response.from(schedule);
    }

    @Transactional
    public ScheduleDto.Response toggle(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedule.setIsEnabled(!schedule.getIsEnabled());

        // 스케줄 갱신 (활성화/비활성화)
        log.info("Toggling schedule: id={}, enabled={}", scheduleId, schedule.getIsEnabled());
        scheduleExecutor.registerSchedule(schedule);

        return ScheduleDto.Response.from(schedule);
    }

    @Transactional
    public void delete(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new IllegalArgumentException("Schedule not found: " + scheduleId);
        }

        // 스케줄 취소
        log.info("Deleting schedule: id={}", scheduleId);
        scheduleExecutor.cancelSchedule(scheduleId);

        scheduleRepository.deleteById(scheduleId);
    }
}
