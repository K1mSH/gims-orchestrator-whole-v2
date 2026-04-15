package com.gims.provider.repository;

import com.gims.provider.entity.ApiPrvOperationColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiPrvOperationColumnRepository extends JpaRepository<ApiPrvOperationColumn, Long> {

    List<ApiPrvOperationColumn> findByOperationIdOrderByDisplayOrder(Long operationId);

    void deleteByOperationId(Long operationId);
}
