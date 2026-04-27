package com.gims.provider.custom;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * 커스텀 핸들러 메타데이터.
 * 부팅 시 ApiPrvOperation/Column/Param 으로 변환되어 DB 등록.
 *
 * description 형식 가이드 (다중 테이블 표시):
 *   1줄: "관련 테이블: TM_GD110301, TM_GD110302, TM_GD120001, TC_GD00002"
 *   2줄: "변환: 동적 PIVOT + 3JOIN + 스칼라"
 */
@Data
@Builder
public class CustomOperationMetadata {

    /** operationId (URL 경로). 슬래시 포함 가능. */
    private String operationId;

    /** 표시명 */
    private String operationName;

    /** 설명 — 관련 테이블 목록 + 변환 패턴 (위 가이드 참조) */
    private String description;

    /** datasource_id — default "internal" (Orchestrator datasource 메타 등록명) */
    @Builder.Default
    private String datasourceId = "internal";

    /** 메인 테이블 1개 — Type A 호환 (DynamicQueryService 는 안 쓰지만 NOT NULL 컬럼) */
    private String tableName;

    /** 응답 컬럼 정의 (동적 PIVOT 핸들러는 알 수 있는 컬럼만 박아도 됨 — UI 안내용) */
    @Singular
    private List<CustomColumnSpec> columns;

    /** 요청 파라미터 정의 */
    @Singular
    private List<CustomParamSpec> params;

    /** 기본 페이지 크기 */
    @Builder.Default
    private Integer pageSize = 100;

    /** 최대 페이지 크기 */
    @Builder.Default
    private Integer maxPageSize = 1000;
}
