package com.gims.auth.scheduler;

import com.gims.auth.entity.AuthRsaKey;
import com.gims.auth.service.KeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RSA 키 자정 회전 Job.
 *
 * <ul>
 *   <li>매일 자정 (`auth.rsa.rotation-cron` 기본 `0 0 0 * * ?`) 실행</li>
 *   <li>새 페어 생성 + INSERT (active=true)</li>
 *   <li>기존 active 키들 → active=false (검증용 보관)</li>
 *   <li>expires_at 지난 키 cleanup DELETE</li>
 * </ul>
 *
 * 실제 회전 로직 = {@link KeyService#rotate()}.
 * 본 Job 은 cron 트리거 wrapper — @EnableScheduling 은 AuthApplication 에서 활성.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyRotationJob {

    private final KeyService keyService;

    @Scheduled(cron = "${auth.rsa.rotation-cron:0 0 0 * * ?}")
    public void rotate() {
        log.info("[KeyRotationJob] starting RSA key rotation");
        AuthRsaKey newKey = keyService.rotate();
        log.info("[KeyRotationJob] rotation completed: newKid={}, expiresAt={}",
            newKey.getKid(), newKey.getExpiresAt());
    }
}
