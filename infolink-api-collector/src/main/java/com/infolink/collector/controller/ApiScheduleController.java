package com.infolink.collector.controller;

import com.infolink.collector.dto.ApiScheduleDto.*;
import com.infolink.collector.service.ApiScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApiScheduleController {

    private final ApiScheduleService scheduleService;

    @GetMapping("/api/endpoints/{endpointId}/schedules")
    public List<Response> getSchedules(@PathVariable Long endpointId) {
        return scheduleService.getSchedules(endpointId);
    }

    @PostMapping("/api/endpoints/{endpointId}/schedules")
    public ResponseEntity<Response> create(@PathVariable Long endpointId, @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(endpointId, request));
    }

    @PutMapping("/api/schedules/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return scheduleService.update(id, request);
    }

    @PutMapping("/api/schedules/{id}/toggle")
    public Response toggle(@PathVariable Long id) {
        return scheduleService.toggle(id);
    }

    @DeleteMapping("/api/schedules/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
