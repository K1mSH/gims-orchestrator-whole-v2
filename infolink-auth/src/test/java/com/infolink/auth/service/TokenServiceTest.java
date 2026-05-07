package com.infolink.auth.service;

import com.infolink.auth.repository.AuthRsaKeyRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional   // 각 테스트 후 DB 롤백 (실 DB 영향 0)
class TokenServiceTest {

    @Autowired KeyService keyService;
    @Autowired TokenService tokenService;
    @Autowired AuthRsaKeyRepository keyRepository;

    @BeforeEach
    void ensureActiveKey() {
        if (keyRepository.findByActiveTrue().isEmpty()) {
            keyService.generateAndSave(true);
        }
    }

    // ============================================================
    // 정상 흐름
    // ============================================================

    @Test
    @DisplayName("Access 발급 → 검증 정상 (sub/role/iss/aud/jti)")
    void access_issue_and_verify() {
        String token = tokenService.createAccessToken(42L, "user");
        Claims claims = tokenService.verifyAccessToken(token);

        assertEquals("42", claims.getSubject());
        assertEquals("user", claims.get("role"));
        assertEquals("orchestrator-auth", claims.getIssuer());
        // jjwt 0.12+ : getAudience() returns Set<String> (RFC 7519 정합)
        assertTrue(claims.getAudience().contains("orchestrator"));
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Refresh 발급 → 검증 정상 (jti / type=refresh)")
    void refresh_issue_and_verify() {
        TokenService.RefreshToken refresh = tokenService.createRefreshToken(42L);
        assertNotNull(refresh.jti());
        assertNotNull(refresh.jwt());
        assertNotNull(refresh.expiresAt());

        Claims claims = tokenService.verifyRefreshToken(refresh.jwt());
        assertEquals("42", claims.getSubject());
        assertEquals(refresh.jti(), claims.getId());
        assertEquals("refresh", claims.get("type"));
    }

    // ============================================================
    // 타입 혼용 차단 (§12.6 #8 변형)
    // ============================================================

    @Test
    @DisplayName("Access 토큰을 refresh 검증에 쓰면 거부")
    void access_cannot_be_used_as_refresh() {
        String access = tokenService.createAccessToken(42L, "user");
        assertThrows(IllegalArgumentException.class,
            () -> tokenService.verifyRefreshToken(access));
    }

    @Test
    @DisplayName("Refresh 토큰을 access 검증에 쓰면 거부")
    void refresh_cannot_be_used_as_access() {
        TokenService.RefreshToken refresh = tokenService.createRefreshToken(42L);
        assertThrows(IllegalArgumentException.class,
            () -> tokenService.verifyAccessToken(refresh.jwt()));
    }

    // ============================================================
    // 위조 / 변조 (§12.6 #9 — 서명 검증 실패)
    // ============================================================

    @Test
    @DisplayName("payload 변조 시 거부 (서명 검증 또는 base64/JSON 파싱 실패)")
    void tampered_payload_fails() {
        String token = tokenService.createAccessToken(42L, "user");
        String[] parts = token.split("\\.");
        // base64 alphabet 안에서 첫 글자 swap
        char first = parts[1].charAt(0);
        char swap = (Character.toLowerCase(first) == 'a') ? 'b' : 'a';
        String tampered = parts[0] + "." + swap + parts[1].substring(1) + "." + parts[2];

        // 변조 형태에 따라 SignatureException / MalformedJwtException / IllegalArgumentException 등 다양
        // 핵심 = "거부됨" — RuntimeException 으로 넓게 잡음
        assertThrows(RuntimeException.class,
            () -> tokenService.verifyAccessToken(tampered));
    }

    @Test
    @DisplayName("signature 변조 시 서명 검증 실패")
    void tampered_signature_fails() {
        String token = tokenService.createAccessToken(42L, "user");
        String[] parts = token.split("\\.");
        // signature 첫 글자 바꿈
        char first = parts[2].charAt(0);
        char swap = (first == 'A') ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + swap + parts[2].substring(1);

        assertThrows(SignatureException.class,
            () -> tokenService.verifyAccessToken(tampered));
    }

    @Test
    @DisplayName("kid 매칭 실패 시 거부 (§12.6 #10)")
    void unknown_kid_fails() {
        // kid 변조 — JWKS 에 매칭 키 없음
        String token = tokenService.createAccessToken(42L, "user");
        String[] parts = token.split("\\.");
        // header 디코드 → kid 변경 → 재인코드는 복잡, 그냥 header 변조로 깨뜨림
        String malformedHeader = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"RS256\",\"kid\":\"K-FAKE-NOT-EXISTS\"}".getBytes());
        String tampered = malformedHeader + "." + parts[1] + "." + parts[2];

        assertThrows(Exception.class,
            () -> tokenService.verifyAccessToken(tampered));
    }
}
