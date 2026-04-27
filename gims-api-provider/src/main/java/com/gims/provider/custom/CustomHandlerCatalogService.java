package com.gims.provider.custom;

import com.gims.provider.entity.ApiPrvOperation;
import com.gims.provider.entity.ApiPrvOperationColumn;
import com.gims.provider.entity.ApiPrvOperationParam;
import com.gims.provider.repository.ApiPrvOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 커스텀 핸들러 카탈로그 + 등록 서비스.
 *
 * Agent 등록 패턴 (discover + 운영자 수동 등록) 을 핸들러에 적용:
 *  - 부팅 자동 등록은 X (운영자 의도 반영)
 *  - catalog 노출만 → 운영자가 신규 등록 화면에서 딸깍 선택
 *  - 등록 시 핸들러 metadata 에서 description/columns/params 등 자동 채움 + readonly + lock
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomHandlerCatalogService {

    private final CustomHandlerRegistry registry;
    private final ApiPrvOperationRepository operationRepository;

    /** 등록 가능한 핸들러 카탈로그 (이름 + 엔드포인트, 등록 여부는 handler_key 기준) */
    public List<CatalogEntry> listCatalog() {
        List<CatalogEntry> result = new ArrayList<>();
        for (CustomOperationHandler handler : registry.getAll()) {
            CustomOperationMetadata meta = handler.getMetadata();
            boolean registered = operationRepository.findByHandlerKey(meta.getOperationId()).isPresent();
            result.add(new CatalogEntry(meta.getOperationId(), meta.getOperationName(), registered));
        }
        return result;
    }

    /** 핸들러 metadata 미리보기 (등록 화면에서 readonly 채움용) */
    public CustomOperationMetadata preview(String operationId) {
        CustomOperationHandler handler = registry.get(operationId);
        if (handler == null) {
            throw new IllegalArgumentException("핸들러를 찾을 수 없습니다: " + operationId);
        }
        return handler.getMetadata();
    }

    /** 핸들러 등록 — metadata 에서 ApiPrvOperation 자동 INSERT. 운영자가 operationId/operationName 변경 가능. */
    @Transactional
    public ApiPrvOperation register(String handlerKey, String customOperationId, String customOperationName) {
        CustomOperationHandler handler = registry.get(handlerKey);
        if (handler == null) {
            throw new IllegalArgumentException("핸들러를 찾을 수 없습니다: " + handlerKey);
        }
        // 핸들러 자체가 이미 등록됐는지 (handler_key 기준)
        if (operationRepository.findByHandlerKey(handlerKey).isPresent()) {
            throw new IllegalStateException("이미 등록된 핸들러입니다: " + handlerKey);
        }
        // 운영자가 변경한 operationId 가 다른 operation 과 중복되면 차단
        String finalOpId = (customOperationId != null && !customOperationId.isBlank())
                ? customOperationId.trim()
                : handler.getMetadata().getOperationId();
        if (operationRepository.findByOperationId(finalOpId).isPresent()) {
            throw new IllegalStateException("이미 사용 중인 operationId 입니다: " + finalOpId);
        }
        ApiPrvOperation op = newOperation(handler.getMetadata());
        op.setOperationId(finalOpId);
        if (customOperationName != null && !customOperationName.isBlank()) {
            op.setOperationName(customOperationName.trim());
        }
        ApiPrvOperation saved = operationRepository.save(op);
        log.info("[CustomHandler] 운영자 등록: handlerKey={}, operationId={} (id={})",
                handlerKey, finalOpId, saved.getId());
        return saved;
    }

    private ApiPrvOperation newOperation(CustomOperationMetadata meta) {
        ApiPrvOperation op = ApiPrvOperation.builder()
                .operationId(meta.getOperationId())
                .operationName(meta.getOperationName())
                .description(meta.getDescription())
                .datasourceId(meta.getDatasourceId())
                .tableName(meta.getTableName())
                .pageSize(meta.getPageSize())
                .maxPageSize(meta.getMaxPageSize())
                .isPublished(true)
                .isActive(true)
                .operationType("CUSTOM")
                .isLocked(true)
                .handlerKey(meta.getOperationId())  // 핸들러 매칭 키 — 운영자가 operationId 변경해도 보존
                .build();
        applyColumnsParams(op, meta);
        return op;
    }

    private void applyColumnsParams(ApiPrvOperation op, CustomOperationMetadata meta) {
        if (meta.getColumns() != null) {
            int order = 1;
            for (CustomColumnSpec c : meta.getColumns()) {
                ApiPrvOperationColumn col = ApiPrvOperationColumn.builder()
                        .columnName(c.getColumnName())
                        .aliasName(c.getAliasName())
                        .displayOrder(c.getDisplayOrder() != null && c.getDisplayOrder() > 0 ? c.getDisplayOrder() : order)
                        .transformType(c.getTransformType())
                        .transformParam(c.getTransformParam())
                        .operation(op)
                        .build();
                op.getColumns().add(col);
                order++;
            }
        }
        if (meta.getParams() != null) {
            for (CustomParamSpec p : meta.getParams()) {
                ApiPrvOperationParam param = ApiPrvOperationParam.builder()
                        .paramName(p.getParamName())
                        .columnName(p.getColumnName())
                        .operator(p.getOperator())
                        .isRequired(Boolean.TRUE.equals(p.getRequired()))
                        .defaultValue(p.getDefaultValue())
                        .dataType(p.getDataType())
                        .isHidden(Boolean.TRUE.equals(p.getHidden()))
                        .operation(op)
                        .build();
                op.getParams().add(param);
            }
        }
    }

    /** 카탈로그 응답 DTO — 이름 + 엔드포인트 + 등록 여부 */
    public record CatalogEntry(String operationId, String operationName, boolean registered) {}
}
