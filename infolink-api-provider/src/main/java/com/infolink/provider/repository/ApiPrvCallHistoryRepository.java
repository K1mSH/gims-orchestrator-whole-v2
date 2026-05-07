package com.infolink.provider.repository;

import com.infolink.provider.entity.ApiPrvCallHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiPrvCallHistoryRepository extends JpaRepository<ApiPrvCallHistory, Long> {

    Page<ApiPrvCallHistory> findByOperationIdOrderByCalledAtDesc(Long operationId, Pageable pageable);
}
