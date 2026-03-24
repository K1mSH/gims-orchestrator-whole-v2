package com.infolink.collector.service;

import com.infolink.collector.entity.*;
import com.infolink.collector.repository.*;
import com.infolink.collector.dto.ApiScheduleDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiScheduleService {

    private final ApiScheduleRepository scheduleRepository;
    private final ApiEndpointRepository endpointRepository;
    private final ApiScheduleExecutor scheduleExecutor;

    @Transactional(readOnly = true)
    public List<Response> getSchedules(Long endpointId) {
        return scheduleRepository.findByApiEndpointId(endpointId).stream()
                .map(Response::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public Response create(Long endpointId, CreateRequest request) {
        ApiEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + endpointId));

        ApiSchedule schedule = ApiSchedule.builder()
                .apiEndpoint(endpoint)
                .cronExpression(request.getCronExpression())
                .build();

        schedule = scheduleRepository.save(schedule);
        scheduleExecutor.registerSchedule(schedule);
        return Response.from(schedule);
    }

    @Transactional
    public Response update(Long scheduleId, UpdateRequest request) {
        ApiSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("ApiSchedule not found: " + scheduleId));

        schedule.setCronExpression(request.getCronExpression());
        schedule = scheduleRepository.save(schedule);
        scheduleExecutor.registerSchedule(schedule);
        return Response.from(schedule);
    }

    @Transactional
    public Response toggle(Long scheduleId) {
        ApiSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("ApiSchedule not found: " + scheduleId));

        schedule.setIsEnabled(!schedule.getIsEnabled());
        schedule = scheduleRepository.save(schedule);
        scheduleExecutor.registerSchedule(schedule); // 내부에서 enabled 여부 판단
        return Response.from(schedule);
    }

    @Transactional
    public void delete(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new IllegalArgumentException("ApiSchedule not found: " + scheduleId);
        }
        scheduleRepository.deleteById(scheduleId);
        scheduleExecutor.unregisterSchedule(scheduleId);
    }
}
