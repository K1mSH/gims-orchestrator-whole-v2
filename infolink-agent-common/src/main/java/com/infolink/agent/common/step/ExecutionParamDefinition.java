package com.infolink.agent.common.step;

import lombok.*;

/**
 * Agent가 지원하는 실행 파라미터 메타데이터 정의
 *
 * Agent가 어떤 파라미터를 받을 수 있는지 프론트엔드에 알려주는 용도.
 * 실제 쿼리 로직은 각 Agent의 Step/Fetcher에서 자체 처리 (프리셋 방식).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionParamDefinition {

    private String paramId;           // "sido", "obsv-code"
    private String label;             // "시도", "관측소 코드"
    private String description;       // 설명

    @Builder.Default
    private String dataType = "STRING";   // STRING, NUMBER, DATE, BOOLEAN
    private String defaultValue;          // 기본값

    @Builder.Default
    private boolean required = false;

    @Builder.Default
    private int displayOrder = 0;
}
