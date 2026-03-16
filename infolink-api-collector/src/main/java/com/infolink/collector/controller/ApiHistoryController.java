package com.infolink.collector.controller;

import com.infolink.collector.domain.ApiExecutionHistoryRepository;
import com.infolink.collector.dto.ApiExecutionHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/endpoints/{endpointId}/history")
@RequiredArgsConstructor
public class ApiHistoryController {

    private final ApiExecutionHistoryRepository historyRepository;

    @GetMapping
    public Page<ApiExecutionHistoryDto.Response> getHistory(
            @PathVariable Long endpointId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return historyRepository
                .findByApiEndpointIdOrderByStartedAtDesc(endpointId, PageRequest.of(page, size))
                .map(ApiExecutionHistoryDto.Response::from);
    }
}
