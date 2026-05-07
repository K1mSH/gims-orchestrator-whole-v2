package com.infolink.auth.bootstrap;

import com.infolink.auth.service.KeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * auth 모듈 첫 부팅 시 — active RSA 키 0건이면 새 페어 자동 생성.
 *
 * 사용자 row 는 자동 생성하지 않음 (UserGeneratorCli 로 별도 발급).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialKeyLoader {

    private final KeyService keyService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        keyService.findActiveKey().ifPresentOrElse(
            active -> log.info("[InitialKeyLoader] active RSA key already exists: kid={}", active.kid()),
            () -> {
                log.info("[InitialKeyLoader] no active RSA key — generating new one");
                keyService.generateAndSave(true);
            }
        );
    }
}
