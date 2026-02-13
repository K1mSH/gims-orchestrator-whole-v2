package com.sync.orchestrator.domain.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/callback")
@RequiredArgsConstructor
public class CallbackController {

    private final CallbackService callbackService;

    @PostMapping("/started")
    public ResponseEntity<?> handleStarted(@RequestBody CallbackDto.StartedRequest request) {
        try {
            callbackService.handleStarted(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to handle started callback", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/finished")
    public ResponseEntity<?> handleFinished(@RequestBody CallbackDto.FinishedRequest request) {
        try {
            callbackService.handleFinished(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to handle finished callback", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
