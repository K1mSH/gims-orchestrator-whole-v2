package com.infolink.provider.repository;

import com.infolink.provider.entity.ApiPrvOperationParam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiPrvOperationParamRepository extends JpaRepository<ApiPrvOperationParam, Long> {

    List<ApiPrvOperationParam> findByOperationId(Long operationId);

    void deleteByOperationId(Long operationId);
}
