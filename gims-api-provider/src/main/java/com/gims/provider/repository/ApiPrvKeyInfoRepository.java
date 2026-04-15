package com.gims.provider.repository;

import com.gims.provider.entity.ApiPrvKeyInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiPrvKeyInfoRepository extends JpaRepository<ApiPrvKeyInfo, Long> {

    Optional<ApiPrvKeyInfo> findByApiKey(String apiKey);
}
