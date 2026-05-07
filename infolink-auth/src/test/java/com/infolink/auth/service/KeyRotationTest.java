package com.infolink.auth.service;

import com.infolink.auth.entity.AuthRsaKey;
import com.infolink.auth.repository.AuthRsaKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RSA 회전 로직 검증.
 * - 새 페어 INSERT (active=true)
 * - 기존 active 키들 → active=false
 * - 만료된 키 cleanup
 *
 * 검증 시나리오 매트릭스 §12.6 #18, #19, #4 일부.
 */
@SpringBootTest
@Transactional
class KeyRotationTest {

    @Autowired KeyService keyService;
    @Autowired AuthRsaKeyRepository keyRepository;

    @BeforeEach
    void ensureActiveKey() {
        if (keyRepository.findByActiveTrue().isEmpty()) {
            keyService.generateAndSave(true);
        }
    }

    // ============================================================
    // §12.6 #18 — 자정 회전 동작
    // ============================================================

    @Test
    @DisplayName("회전 시 새 active 키 생성 + 기존 active 비활성화")
    void rotation_creates_new_active_and_deactivates_old() {
        AuthRsaKey before = keyRepository.findByActiveTrue().orElseThrow();
        long beforeCount = keyRepository.count();

        AuthRsaKey rotated = keyService.rotate();

        // 새 키 active=true
        assertTrue(rotated.isActive());
        assertNotEquals(before.getKid(), rotated.getKid());

        // 기존 키 active=false (검증용 보관)
        AuthRsaKey old = keyRepository.findById(before.getKid()).orElseThrow();
        assertFalse(old.isActive());

        // active=true 인 키는 정확히 1개
        assertEquals(rotated.getKid(),
            keyRepository.findByActiveTrue().orElseThrow().getKid());

        // 총 키 수 = 회전 전 + 1 (만료 키 없으므로 cleanup 0)
        assertEquals(beforeCount + 1, keyRepository.count());
    }

    // ============================================================
    // §12.6 #18 일부 — cleanup 동작
    // ============================================================

    @Test
    @DisplayName("회전 시 만료된 키 cleanup DELETE")
    void rotation_cleans_up_expired_keys() {
        // 만료된 키 1개 직접 생성 (active=false, expires_at 과거)
        AuthRsaKey expired = keyService.generateAndSave(false);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        keyRepository.saveAndFlush(expired);

        String expiredKid = expired.getKid();
        assertTrue(keyRepository.existsById(expiredKid));

        keyService.rotate();

        // 만료 키 사라짐
        assertFalse(keyRepository.existsById(expiredKid));
    }

    // ============================================================
    // §12.6 #19 — 회전 후 이전 kid 토큰 검증 가능
    // ============================================================

    @Test
    @DisplayName("회전 후 이전 active 키도 (만료 안 됐으면) JWKS 응답에 포함 — 이전 토큰 검증 가능")
    void rotation_preserves_previous_keys_for_verification() {
        AuthRsaKey before = keyRepository.findByActiveTrue().orElseThrow();

        keyService.rotate();

        // 이전 active 키도 만료 안 됐으면 findAllValidPublicKeys 에 포함
        boolean previousIncluded = keyService.findAllValidPublicKeys().stream()
            .anyMatch(info -> info.kid().equals(before.getKid()));
        assertTrue(previousIncluded, "이전 active 키가 JWKS 응답에 포함돼야 (검증 가능 상태)");

        // 새 active 키도 포함
        Optional<AuthRsaKey> newActive = keyRepository.findByActiveTrue();
        assertTrue(newActive.isPresent());
        boolean newActiveIncluded = keyService.findAllValidPublicKeys().stream()
            .anyMatch(info -> info.kid().equals(newActive.get().getKid()));
        assertTrue(newActiveIncluded);
    }

    @Test
    @DisplayName("회전 직후 findActiveKey() = 새 kid")
    void after_rotation_active_key_is_new_one() {
        AuthRsaKey before = keyRepository.findByActiveTrue().orElseThrow();
        AuthRsaKey rotated = keyService.rotate();

        assertEquals(rotated.getKid(),
            keyService.findActiveKey().orElseThrow().kid());
        assertNotEquals(before.getKid(),
            keyService.findActiveKey().orElseThrow().kid());
    }
}
