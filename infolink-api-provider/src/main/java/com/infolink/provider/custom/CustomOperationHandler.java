package com.infolink.provider.custom;

import com.infolink.provider.dto.DynamicQueryResult;

import java.util.Map;

/**
 * 커스텀 오퍼레이션 핸들러 (Type B — 외부 GIMS Oracle 직접 쿼리)
 *
 * 메타등록형(Type A, DynamicQueryService) 으로 표현 불가능한
 * 동적 PIVOT / CTE / UNION / JOIN 등 복잡 SQL 을 Java 로 박는다.
 *
 * - operationId 1개 = 핸들러 1개 (1:1)
 * - 부팅 시 {@link CustomHandlerBootstrap} 가 metadata 를 ApiPrvOperation 으로 변환·등록
 * - is_locked=true 로 운영자 수정/삭제 차단
 * - ApiGatewayController 가 operationType=CUSTOM 분기 시 호출
 */
public interface CustomOperationHandler {

    /** 부팅 시 ApiPrvOperation 으로 변환되는 메타 (operationId/이름/설명/컬럼/파라미터) */
    CustomOperationMetadata getMetadata();

    /**
     * 실제 호출. 외부 Oracle 쿼리 → 응답 List
     * @param params  page/pageSize/apiKey 제외한 요청 파라미터 (대소문자 그대로)
     * @param page    1-based 페이지
     * @param pageSize 페이지 크기 (operation.maxPageSize 로 이미 clamp 됨)
     */
    DynamicQueryResult handle(Map<String, String> params, int page, int pageSize);
}
