package com.infolink.provider.repository;

import com.infolink.provider.entity.ApiPrvOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiPrvOperationRepository extends JpaRepository<ApiPrvOperation, Long> {

    Optional<ApiPrvOperation> findByOperationId(String operationId);

    Optional<ApiPrvOperation> findByHandlerKey(String handlerKey);

    List<ApiPrvOperation> findByIsActiveTrue();

    List<ApiPrvOperation> findByIsPublishedTrueAndIsActiveTrue();
}
