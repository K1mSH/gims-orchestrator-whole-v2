package com.infolink.collector.executor;

/**
 * 커스텀 실행기 결과
 */
public record CustomExecutionResult(
        int httpStatusCode,
        int responseCount,
        int insertCount,
        int updateCount,
        int skipCount,
        String errorMessage
) {
    public boolean isSuccess() {
        return errorMessage == null;
    }
}
