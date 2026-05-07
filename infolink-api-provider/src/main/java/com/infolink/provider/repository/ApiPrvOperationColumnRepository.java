package com.infolink.provider.repository;

import com.infolink.provider.entity.ApiPrvOperationColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiPrvOperationColumnRepository extends JpaRepository<ApiPrvOperationColumn, Long> {

    List<ApiPrvOperationColumn> findByOperationIdOrderByDisplayOrder(Long operationId);

    void deleteByOperationId(Long operationId);
}
