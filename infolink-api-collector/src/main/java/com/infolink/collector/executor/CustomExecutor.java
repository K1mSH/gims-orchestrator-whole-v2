package com.infolink.collector.executor;

import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.entity.ApiParam;

import java.util.List;
import java.util.Map;

/**
 * 커스텀 실행기 인터페이스
 * - 범용 매핑으로 처리 불가능한 특수 로직 대응
 * - Spring Bean으로 등록하면 CustomExecutorRegistry가 자동 수집
 */
public interface CustomExecutor {

    /** 실행기 ID (엔드포인트에 저장되는 키) */
    String getId();

    /** 표시명 (UI 드롭다운용) */
    String getDisplayName();

    /** 실행 */
    CustomExecutionResult execute(ApiEndpoint endpoint, List<ApiParam> params,
                                   Map<String, String> overrides, String triggeredBy);
}
