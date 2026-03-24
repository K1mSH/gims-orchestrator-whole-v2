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
                .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));
        return ScheduleDto.Response.from(schedule);
    }

    public List<ScheduleDto.Response> findByAgentId(Long agentId) {
        return scheduleRepository.findByAgentId(agentId).stream()
                .map(ScheduleDto.Response::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduleDto.Response create(ScheduleDto.CreateRequest request) {
        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + request.getAgentId()));

        Schedule schedule = Schedule.builder()
                .agent(agent)
                .cronExpression(request.getCronExpression())
                .isEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true)
                .executionOptions(request.getExecutionOptions())
                .build();

        Schedule saved = scheduleRepository.save(schedule);

        scheduleExecutor.registerSchedule(saved);

        return ScheduleDto.Response.from(saved);
    }

    @Transactional
    public ScheduleDto.Response update(Long scheduleId, ScheduleDto.UpdateRequest request) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));

        if (request.getCronExpression() != null) {
            schedule.setCronExpression(request.getCronExpression());
        }
        if (request.getIsEnabled() != null) {
            schedule.setIsEnabled(request.getIsEnabled());
        }
        if (request.getExecutionOptions() != null) {
            schedule.setExecutionOptions(request.getExecutionOptions());
        }

        scheduleExecutor.registerSchedule(schedule);

        return ScheduleDto.Response.from(schedule);
    }

    @Transactional
    public ScheduleDto.Response toggle(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));

        schedule.setIsEnabled(!schedule.getIsEnabled());

        scheduleExecutor.registerSchedule(schedule);

        return ScheduleDto.Response.from(schedule);
    }

    @Transactional
    public void delete(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId);
        }

        scheduleExecutor.unregisterSchedule(scheduleId);

        scheduleRepository.deleteById(scheduleId);
    }
}
