package com.infolink.auth.bootstrap;

import com.infolink.auth.entity.AuthRsaKey;
import com.infolink.auth.service.KeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * auth 모듈 부팅 시 — 유효한(만료 안 된) active RSA 키가 있도록 보장.
 * <ul>
 *   <li>active 키 0건 → 새 페어 자동 생성 (첫 부팅)</li>
 *   <li>active 키 있지만 만료됨 → 회전 (모듈이 만료 시점 넘겨서 다운돼 있던 경우 자가 복구)</li>
 *   <li>유효한 active 키 있음 → 그대로 사용</li>
 * </ul>
 * 사용자 row 는 자동 생성하지 않음 (UserGeneratorCli 로 별도 발급).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialKeyLoader {

    private final KeyService keyService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        AuthRsaKey active = keyService.ensureValidActiveKey();
        log.info("[InitialKeyLoader] active RSA key ready: kid={}, expiresAt={}",
            active.getKid(), active.getExpiresAt());
    }
}
