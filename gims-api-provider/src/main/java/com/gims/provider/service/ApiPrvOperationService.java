package com.gims.provider.service;

import com.gims.provider.entity.ApiPrvOperation;
import com.gims.provider.entity.ApiPrvOperationColumn;
import com.gims.provider.entity.ApiPrvOperationParam;
import com.gims.provider.repository.ApiPrvOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPrvOperationService {

    private final ApiPrvOperationRepository operationRepository;

    public List<ApiPrvOperation> findAll() {
        return operationRepository.findAll();
    }

    public List<ApiPrvOperation> findActive() {
        return operationRepository.findByIsActiveTrue();
    }

    public ApiPrvOperation findById(Long id) {
        return operationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("오퍼레이션을 찾을 수 없습니다: " + id));
    }

    public ApiPrvOperation findByOperationId(String operationId) {
        return operationRepository.findByOperationId(operationId)
                .orElseThrow(() -> new IllegalArgumentException("오퍼레이션을 찾을 수 없습니다: " + operationId));
    }

    @Transactional
    public ApiPrvOperation create(ApiPrvOperation operation) {
        trimOperation(operation);
        if (operationRepository.findByOperationId(operation.getOperationId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 오퍼레이션 ID입니다: " + operation.getOperationId());
        }
        // 운영자가 직접 등록하는 신규는 무조건 META + 잠금해제 (CUSTOM 은 부팅 시 핸들러로만 등록됨)
        operation.setOperationType("META");
        operation.setIsLocked(false);
        log.info("오퍼레이션 생성: {}", operation.getOperationId());
        return operationRepository.save(operation);
    }

    @Transactional
    public ApiPrvOperation update(Long id, ApiPrvOperation updated) {
        trimOperation(updated);
        ApiPrvOperation existing = findById(id);
        boolean locked = Boolean.TRUE.equals(existing.getIsLocked());

        // 잠금 (CUSTOM 핸들러) 이면 운영자 직관성 위해 operationId/operationName 만 변경 허용
        if (locked) {
            if (updated.getOperationId() != null && !updated.getOperationId().equals(existing.getOperationId())) {
                operationRepository.findByOperationId(updated.getOperationId()).ifPresent(other -> {
                    if (!other.getId().equals(existing.getId())) {
                        throw new IllegalStateException("이미 사용 중인 operationId 입니다: " + updated.getOperationId());
                    }
                });
                existing.setOperationId(updated.getOperationId());
            }
            if (updated.getOperationName() != null) {
                existing.setOperationName(updated.getOperationName());
            }
            log.info("오퍼레이션 부분 수정 (잠금): {} (operationId/이름)", existing.getOperationId());
            return operationRepository.save(existing);
        }

        existing.setOperationName(updated.getOperationName());
        existing.setDescription(updated.getDescription());
        existing.setDatasourceId(updated.getDatasourceId());
        existing.setTableName(updated.getTableName());
        existing.setResponseFormat(updated.getResponseFormat());
        existing.setPageSize(updated.getPageSize());
        existing.setMaxPageSize(updated.getMaxPageSize());
        existing.setOrderByColumn(updated.getOrderByColumn());
        existing.setOrderByDirection(updated.getOrderByDirection());
        existing.setIsActive(updated.getIsActive());
        log.info("오퍼레이션 수정: {}", existing.getOperationId());
        return operationRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        ApiPrvOperation operation = findById(id);
        requireUnlocked(operation, "삭제");
        log.info("오퍼레이션 삭제: {}", operation.getOperationId());
        operationRepository.delete(operation);
    }

    @Transactional
    public ApiPrvOperation togglePublish(Long id) {
        ApiPrvOperation operation = findById(id);
        operation.setIsPublished(!operation.getIsPublished());
        log.info("오퍼레이션 활성 상태 변경: {} → {}", operation.getOperationId(), operation.getIsPublished());
        return operationRepository.save(operation);
    }

    @Transactional
    public void saveColumns(Long operationId, List<ApiPrvOperationColumn> columns) {
        ApiPrvOperation operation = findById(operationId);
        requireUnlocked(operation, "컬럼 수정");
        operation.getColumns().clear();
        for (ApiPrvOperationColumn col : columns) {
            trimColumn(col);
            col.setOperation(operation);
            operation.getColumns().add(col);
        }
        operationRepository.save(operation);
        log.info("오퍼레이션 컬럼 저장: {} ({}개)", operation.getOperationId(), columns.size());
    }

    @Transactional
    public void saveParams(Long operationId, List<ApiPrvOperationParam> params) {
        ApiPrvOperation operation = findById(operationId);
        requireUnlocked(operation, "파라미터 수정");
        operation.getParams().clear();
        for (ApiPrvOperationParam param : params) {
            trimParam(param);
            param.setOperation(operation);
            operation.getParams().add(param);
        }
        operationRepository.save(operation);
        log.info("오퍼레이션 파라미터 저장: {} ({}개)", operation.getOperationId(), params.size());
    }

    // ========== 잠금 보호 ==========

    private void requireUnlocked(ApiPrvOperation op, String action) {
        if (Boolean.TRUE.equals(op.getIsLocked())) {
            throw new IllegalStateException(
                    "잠금된 오퍼레이션입니다. 시스템 내장 핸들러는 " + action + "할 수 없습니다: " + op.getOperationId());
        }
    }

    // ========== trim 처리 ==========

    private void trimOperation(ApiPrvOperation op) {
        op.setOperationId(trim(op.getOperationId()));
        op.setOperationName(trim(op.getOperationName()));
        op.setDescription(trim(op.getDescription()));
        op.setDatasourceId(trim(op.getDatasourceId()));
        op.setTableName(trim(op.getTableName()));
        op.setResponseFormat(trim(op.getResponseFormat()));
        op.setOrderByColumn(trim(op.getOrderByColumn()));
        op.setOrderByDirection(trim(op.getOrderByDirection()));
    }

    private void trimColumn(ApiPrvOperationColumn col) {
        col.setColumnName(trim(col.getColumnName()));
        col.setAliasName(trim(col.getAliasName()));
        col.setTransformType(trim(col.getTransformType()));
        col.setTransformParam(trim(col.getTransformParam()));
    }

    private void trimParam(ApiPrvOperationParam param) {
        param.setParamName(trim(param.getParamName()));
        param.setColumnName(trim(param.getColumnName()));
        param.setOperator(trim(param.getOperator()));
        param.setDefaultValue(trim(param.getDefaultValue()));
        param.setDataType(trim(param.getDataType()));
    }

    private String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
