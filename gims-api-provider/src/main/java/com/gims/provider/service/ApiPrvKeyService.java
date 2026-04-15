package com.gims.provider.service;

import com.gims.provider.entity.ApiPrvKeyInfo;
import com.gims.provider.repository.ApiPrvKeyInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPrvKeyService {

    private final ApiPrvKeyInfoRepository keyInfoRepository;

    public List<ApiPrvKeyInfo> findAll() {
        return keyInfoRepository.findAll();
    }

    @Transactional
    public ApiPrvKeyInfo create(ApiPrvKeyInfo keyInfo) {
        if (keyInfo.getApiKey() == null || keyInfo.getApiKey().isBlank()) {
            keyInfo.setApiKey(UUID.randomUUID().toString().replace("-", ""));
        }
        log.info("API Key 발급: {} ({})", keyInfo.getClientName(), keyInfo.getApiKey());
        return keyInfoRepository.save(keyInfo);
    }

    @Transactional
    public void delete(Long id) {
        keyInfoRepository.deleteById(id);
        log.info("API Key 삭제: {}", id);
    }
}
