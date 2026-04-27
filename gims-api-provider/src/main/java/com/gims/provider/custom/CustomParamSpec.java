package com.gims.provider.custom;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomParamSpec {

    /** 요청 파라미터명 */
    private String paramName;

    /** WHERE 대상 컬럼명 — 핸들러 안에서 자체 처리하므로 표시용 */
    private String columnName;

    /** 연산자 — 핸들러 안에서 자체 처리, 표시용 */
    @Builder.Default
    private String operator = "EQ";

    /** 필수 여부 */
    @Builder.Default
    private Boolean required = false;

    /** 기본값 */
    private String defaultValue;

    /** 데이터 타입 */
    @Builder.Default
    private String dataType = "STRING";

    /** 숨김 여부 (외부 미노출, 기본값 고정) */
    @Builder.Default
    private Boolean hidden = false;
}
