package com.infolink.agent.common.step;

import lombok.*;

/**
 * Step 메타데이터 POJO
 * Agent가 Orchestrator에 알려주는 Step 정보
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepDefinition {

    private String stepId;          // "jewon-extract"
    private String stepName;        // "제원 데이터 추출"
    private String description;     // "IF_SND → IF_RSV 제원 복사"
    private int displayOrder;       // UI 표시 순서

    @Builder.Default
    private boolean enabledByDefault = true;  // 기본 선택 여부
}
