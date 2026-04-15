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
        if (operationRepository.findByOperationId(operation.getOperationId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 오퍼레이션 ID입니다: " + operation.getOperationId());
        }
        log.info("오퍼레이션 생성: {}", operation.getOperationId());
        return operationRepository.save(operation);
    }

    @Transactional
    public ApiPrvOperation update(Long id, ApiPrvOperation updated) {
        ApiPrvOperation existing = findById(id);
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
        log.info("오퍼레이션 삭제: {}", operation.getOperationId());
        operationRepository.delete(operation);
    }

    @Transactional
    public ApiPrvOperation togglePublish(Long id) {
        ApiPrvOperation operation = findById(id);
        operation.setIsPublished(!operation.getIsPublished());
        log.info("오퍼레이션 게시 상태 변경: {} → {}", operation.getOperationId(), operation.getIsPublished());
        return operationRepository.save(operation);
    }

    @Transactional
    public void saveColumns(Long operationId, List<ApiPrvOperationColumn> columns) {
        ApiPrvOperation operation = findById(operationId);
        operation.getColumns().clear();
        for (ApiPrvOperationColumn col : columns) {
            col.setOperation(operation);
            operation.getColumns().add(col);
        }
        operationRepository.save(operation);
        log.info("오퍼레이션 컬럼 저장: {} ({}개)", operation.getOperationId(), columns.size());
    }

    @Transactional
    public void saveParams(Long operationId, List<ApiPrvOperationParam> params) {
        ApiPrvOperation operation = findById(operationId);
        operation.getParams().clear();
        for (ApiPrvOperationParam param : params) {
            param.setOperation(operation);
            operation.getParams().add(param);
        }
        operationRepository.save(operation);
        log.info("오퍼레이션 파라미터 저장: {} ({}개)", operation.getOperationId(), params.size());
    }
}
