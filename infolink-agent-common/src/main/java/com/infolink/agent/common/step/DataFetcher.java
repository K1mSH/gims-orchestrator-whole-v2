package com.infolink.agent.common.step;

import java.util.List;
import java.util.Map;

/**
 * 데이터 조회 인터페이스
 *
 * SIMPLE_COPY: 기본 구현 사용 (시간 범위 기반 단순 SELECT)
 * CUSTOM_STAGING: Agent에서 이 인터페이스 구현하여 주입
 */
@FunctionalInterface
public interface DataFetcher {

    /**
     * Source에서 데이터 조회
     *
     * @param context Step 실행 컨텍스트 (executionId, 파라미터 등)
     * @return 조회된 데이터 목록 (각 Map은 한 레코드, key=컬럼명)
     */
    List<Map<String, Object>> fetch(StepContext context);
}
