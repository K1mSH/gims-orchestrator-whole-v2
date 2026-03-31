package com.sync.orchestrator.controller;

import com.sync.orchestrator.dto.ScheduleDto;
import com.sync.orchestrator.service.ScheduleService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<List<ScheduleDto.Response>> getSchedules() {
        return ResponseEntity.ok(scheduleService.findAll());
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ScheduleDto.Response> getSchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(scheduleService.findById(scheduleId));
    }

    @PostMapping
    public ResponseEntity<ScheduleDto.Response> createSchedule(@Valid @RequestBody ScheduleDto.CreateRequest request) {
        ScheduleDto.Response response = scheduleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<ScheduleDto.Response> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody ScheduleDto.UpdateRequest request) {
        return ResponseEntity.ok(scheduleService.update(scheduleId, request));
    }

    @PutMapping("/{scheduleId}/toggle")
    public ResponseEntity<ScheduleDto.Response> toggleSchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(scheduleService.toggle(scheduleId));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.delete(scheduleId);
        return ResponseEntity.noContent().build();
    }
}
