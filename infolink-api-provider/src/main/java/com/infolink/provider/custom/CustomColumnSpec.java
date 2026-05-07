package com.infolink.provider.custom;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomColumnSpec {

    /** DB 컬럼명 (또는 응답 키) */
    private String columnName;

    /** 응답필드명(별칭) — 보통 대문자 */
    private String aliasName;

    /** 표시순서 */
    @Builder.Default
    private Integer displayOrder = 0;

    /** 가공타입 — 핸들러 안에서 처리하므로 NONE 권장 */
    @Builder.Default
    private String transformType = "NONE";

    /** 가공 파라미터 */
    private String transformParam;
}
