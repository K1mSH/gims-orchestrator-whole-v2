package com.sync.agent.common.client;

import com.sync.agent.common.auth.AuthFetchFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JwksClient 검증 — RestTemplate mock 으로 retry / kid 매칭 / 캐시 동작 검증.
 *
 * AUTH_DESIGN.md §13.8 정합 — fetch 실패 retry 2회 후 AuthFetchFailedException.
 */
class JwksClientTest {

    private static final String JWKS_URL = "http://test:8096/.well-known/jwks.json";

    // ============================================================
    // 캐시 hit / miss
    // ============================================================

    @Test
    @DisplayName("정상 fetch — kid 매칭 공개키 반환")
    void fetch_and_match_kid() throws Exception {
        KeyPair pair = newRsaPair();
        Map<String, Object> jwksResponse = singleKeyJwks("K-test-1", (RSAPublicKey) pair.getPublic());

        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForObject(eq(JWKS_URL), eq(Map.class))).thenReturn(jwksResponse);

        JwksClient client = new JwksClient(JWKS_URL, rt);

        Optional<PublicKey> pk = client.getPublicKey("K-test-1");
        assertTrue(pk.isPresent());
        assertEquals(pair.getPublic(), pk.get());
    }

    @Test
    @DisplayName("kid 매칭 실패 → empty (회전 직후 한 번 더 fetch 한 후에도 없음)")
    void unknown_kid_returns_empty() throws Exception {
        KeyPair pair = newRsaPair();
        Map<String, Object> jwksResponse = singleKeyJwks("K-test-1", (RSAPublicKey) pair.getPublic());

        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForObject(eq(JWKS_URL), eq(Map.class))).thenReturn(jwksResponse);

        JwksClient client = new JwksClient(JWKS_URL, rt);

        Optional<PublicKey> pk = client.getPublicKey("K-NOT-EXIST");
        assertTrue(pk.isEmpty());
    }

    // ============================================================
    // Retry 정책 (§13.8 옵션 C)
    // ============================================================

    @Test
    @DisplayName("retry 후 성공 — 1차 fail / 2차 success")
    void retry_succeeds_on_second_attempt() throws Exception {
        KeyPair pair = newRsaPair();
        Map<String, Object> jwksResponse = singleKeyJwks("K-retry", (RSAPublicKey) pair.getPublic());

        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForObject(eq(JWKS_URL), eq(Map.class)))
            .thenThrow(new ResourceAccessException("connection refused"))   // 1차 fail
            .thenReturn(jwksResponse);                                       // retry 1차 success

        JwksClient client = new JwksClient(JWKS_URL, rt);

        Optional<PublicKey> pk = client.getPublicKey("K-retry");
        assertTrue(pk.isPresent());
    }

    @Test
    @DisplayName("retry 2회 모두 실패 → AuthFetchFailedException (503 신호)")
    void retry_exhausted_throws() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForObject(eq(JWKS_URL), eq(Map.class)))
            .thenThrow(new ResourceAccessException("auth down 1"))
            .thenThrow(new ResourceAccessException("auth down 2"))
            .thenThrow(new ResourceAccessException("auth down 3"));

        JwksClient client = new JwksClient(JWKS_URL, rt);

        assertThrows(AuthFetchFailedException.class,
            () -> client.getPublicKey("K-any"));
    }

    // ============================================================
    // 캐시 동작
    // ============================================================

    @Test
    @DisplayName("캐시 hit — 두 번째 호출은 RestTemplate 안 부름")
    void cache_hit_avoids_refetch() throws Exception {
        KeyPair pair = newRsaPair();
        Map<String, Object> jwksResponse = singleKeyJwks("K-cache", (RSAPublicKey) pair.getPublic());

        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForObject(eq(JWKS_URL), eq(Map.class))).thenReturn(jwksResponse);

        JwksClient client = new JwksClient(JWKS_URL, rt);

        Optional<PublicKey> first = client.getPublicKey("K-cache");
        assertTrue(first.isPresent());

        Optional<PublicKey> second = client.getPublicKey("K-cache");
        assertTrue(second.isPresent());

        // RestTemplate 호출 횟수 검증 — 1번 (첫 호출만)
        org.mockito.Mockito.verify(rt, org.mockito.Mockito.times(1))
            .getForObject(eq(JWKS_URL), eq(Map.class));
    }

    // ============================================================
    // 헬퍼
    // ============================================================

    private static KeyPair newRsaPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static Map<String, Object> singleKeyJwks(String kid, RSAPublicKey pub) {
        // JWKS JSON 형식 — Nimbus RSAKey.Builder 의 toJSONObject() 형식과 호환
        return Map.of("keys", java.util.List.of(Map.of(
            "kty", "RSA",
            "kid", kid,
            "use", "sig",
            "alg", "RS256",
            "n", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray()),
            "e", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray())
        )));
    }
}
