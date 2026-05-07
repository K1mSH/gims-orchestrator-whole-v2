package com.infolink.agent.common.config;

import com.infolink.agent.common.step.ExecutionParamDefinition;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent의 실행 파라미터 메타데이터 설정
 *
 * relay-variables.yml 또는 loader-variables.yml에서 바인딩:
 *
 * agent:
 *   execution-params:
 *     - param-id: sido
 *       label: "시도"
 *       data-type: STRING
 *       ...
 */
@Component
@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
public class ExecutionParamsProperties {

    private List<ExecutionParamDefinition> executionParams = new ArrayList<>();
}
