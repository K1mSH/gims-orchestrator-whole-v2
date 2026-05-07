package com.infolink.provider.custom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * operationId → CustomOperationHandler 매핑.
 * Spring 이 모든 CustomOperationHandler Bean 자동 주입 → operationId 기준 인덱스.
 */
@Slf4j
@Component
public class CustomHandlerRegistry {

    private final List<CustomOperationHandler> handlers;
    private final Map<String, CustomOperationHandler> byOperationId = new HashMap<>();

    public CustomHandlerRegistry(List<CustomOperationHandler> handlers) {
        this.handlers = handlers;
    }

    @PostConstruct
    void index() {
        for (CustomOperationHandler h : handlers) {
            String opId = h.getMetadata().getOperationId();
            if (opId == null || opId.isBlank()) {
                throw new IllegalStateException("CustomOperationHandler 의 operationId 가 비어있습니다: " + h.getClass().getName());
            }
            CustomOperationHandler prev = byOperationId.put(opId, h);
            if (prev != null) {
                throw new IllegalStateException("operationId 중복: " + opId
                        + " (" + prev.getClass().getName() + " vs " + h.getClass().getName() + ")");
            }
        }
        log.info("[CustomHandler] 등록된 핸들러 {}개: {}", byOperationId.size(), byOperationId.keySet());
    }

    public CustomOperationHandler get(String operationId) {
        return byOperationId.get(operationId);
    }

    public boolean has(String operationId) {
        return byOperationId.containsKey(operationId);
    }

    public List<CustomOperationHandler> getAll() {
        return handlers;
    }
}
