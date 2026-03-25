package com.infolink.collector.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 커스텀 실행기 레지스트리
 * - Spring이 모든 CustomExecutor Bean을 자동 주입
 * - ID로 조회 + 전체 목록 제공 (UI 드롭다운용)
 */
@Component
@Slf4j
public class CustomExecutorRegistry {

    private final Map<String, CustomExecutor> executors;

    public CustomExecutorRegistry(List<CustomExecutor> executorList) {
        this.executors = new LinkedHashMap<>();
        for (CustomExecutor executor : executorList) {
            executors.put(executor.getId(), executor);
            log.info("커스텀 실행기 등록: {} ({})", executor.getId(), executor.getDisplayName());
        }
        log.info("커스텀 실행기 총 {}건 등록", executors.size());
    }

    public Optional<CustomExecutor> get(String id) {
        return Optional.ofNullable(executors.get(id));
    }

    public List<Map<String, String>> getAll() {
        return executors.values().stream()
                .map(e -> Map.of("id", e.getId(), "displayName", e.getDisplayName()))
                .collect(Collectors.toList());
    }
}
