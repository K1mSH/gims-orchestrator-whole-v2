package com.infolink.collector.controller;

import com.infolink.collector.repository.ApiExecutionHistoryRepository;
import com.infolink.collector.dto.ApiExecutionHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/endpoints/{endpointId}/history")
@RequiredArgsConstructor
public class ApiHistoryController {

    private final ApiExecutionHistoryRepository historyRepository;

    @GetMapping
    public Page<ApiExecutionHistoryDto.Response> getHistory(
            @PathVariable Long endpointId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        PageRequest pageRequest = PageRequest.of(page, size);

        if (startDate != null && endDate != null) {
            LocalDateTime from = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime to = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            return historyRepository
                    .findByApiEndpointIdAndStartedAtBetweenOrderByStartedAtDesc(endpointId, from, to, pageRequest)
                    .map(ApiExecutionHistoryDto.Response::from);
        }

        return historyRepository
                .findByApiEndpointIdOrderByStartedAtDesc(endpointId, pageRequest)
                .map(ApiExecutionHistoryDto.Response::from);
    }
}
