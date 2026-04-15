package com.gims.provider.repository;

import com.gims.provider.entity.ApiPrvOperationParam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiPrvOperationParamRepository extends JpaRepository<ApiPrvOperationParam, Long> {

    List<ApiPrvOperationParam> findByOperationId(Long operationId);

    void deleteByOperationId(Long operationId);
}
