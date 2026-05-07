package com.infolink.agent.bojo.rcv.factory;

import com.infolink.agent.bojo.rcv.step.LinkTableUpdateStep;
import com.infolink.agent.common.controller.DataSourceProvider;
import com.infolink.agent.common.pipeline.StepFactory;
import com.infolink.agent.common.repository.SyncLogRepository;
import com.infolink.agent.common.step.StepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * link-update Factory (bojo 전용)
 *
 * IF 테이블의 동기화 결과로 Link 테이블을 갱신하는 Step 생성.
 * 세부 설정(if-table, link-table)은 내부에서 처리.
 */
@Component
@RequiredArgsConstructor
public class LinkUpdateStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @Override
    public String getFactoryKey() {
        return "link-update";
    }

    @Override
    public StepExecutor create(Map<String, Object> config) {
        return new LinkTableUpdateStep(
                dataSourceProvider,
                (String) config.get("if-table"),
                (String) config.get("link-table"),
                syncLogRepository
        );
    }
}
