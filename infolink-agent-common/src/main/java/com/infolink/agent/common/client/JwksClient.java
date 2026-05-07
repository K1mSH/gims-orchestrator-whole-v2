package com.infolink.agent.common.client;

import com.infolink.agent.common.auth.AuthFetchFailedException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.PublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JWKS endpoint 호출 + 5min 캐시 + retry 2회.
 *
 * <p>검증자 모듈 (Backend / api-provider / api-collector) 이 사용.
 * Lazy 정책 — 부팅 시 fetch X, 첫 호출 시 또는 캐시 만료 시 fetch.
 *
 * <p>Retry: 200ms → 500ms 후 실패 시 {@link AuthFetchFailedException}.
 *
 * <p>활성화 = `auth.jwks-url` property 설정.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.jwks-url")
public class JwksClient {

    private static final long[] RETRY_DELAYS_MS = {0L, 200L, 500L};   // 첫 시도 + retry 2회
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final String jwksUrl;
    private final RestTemplate restTemplate;

    private final ConcurrentMap<String, PublicKey> cache = new ConcurrentHashMap<>();
    private volatile Instant cacheLoadedAt = Instant.EPOCH;

    public JwksClient(@Value("${auth.jwks-url}") String jwksUrl) {
        this(jwksUrl, new RestTemplate());
    }

    /** 테스트용 — RestTemplate mock 주입 */
    public JwksClient(String jwksUrl, RestTemplate restTemplate) {
        this.jwksUrl = jwksUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * kid 매칭 공개키 조회.
     *
     * <ol>
     *   <li>캐시 살아있고 매칭 키 있음 → 즉시 반환</li>
     *   <li>캐시 만료 / 매칭 실패 → JWKS fetch (retry 2회) → 캐시 갱신</li>
     *   <li>여전히 매칭 실패 → empty (호출자가 401 처리)</li>
     *   <li>fetch 자체 실패 → AuthFetchFailedException (503)</li>
     * </ol>
     */
    public Optional<PublicKey> getPublicKey(String kid) {
        if (kid == null) return Optional.empty();

        if (isCacheStale()) {
            refresh();
        }

        PublicKey hit = cache.get(kid);
        if (hit == null) {
            // 캐시 안 살아있더라도, 회전 직후 새 kid 일 수 있어 한 번 더 fetch
            log.debug("[JwksClient] kid not in cache — refreshing once: {}", kid);
            refresh();
            hit = cache.get(kid);
        }
        return Optional.ofNullable(hit);
    }

    private boolean isCacheStale() {
        if (cache.isEmpty()) return true;
        return Duration.between(cacheLoadedAt, Instant.now()).compareTo(CACHE_TTL) > 0;
    }

    /** JWKS fetch + retry 2회 + 캐시 atomic 갱신 */
    private synchronized void refresh() {
        // race 방어 — 다른 스레드가 이미 갱신했으면 skip
        if (!isCacheStale() && !cache.isEmpty()) return;

        Exception last = null;
        for (long delayMs : RETRY_DELAYS_MS) {
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            try {
                Map<String, Object> response = restTemplate.getForObject(jwksUrl, Map.class);
                if (response == null) {
                    throw new IllegalStateException("JWKS response null");
                }

                ConcurrentMap<String, PublicKey> next = new ConcurrentHashMap<>();
                JWKSet jwkSet = JWKSet.parse(new HashMap<>(response));
                List<JWK> keys = jwkSet.getKeys();
                for (JWK jwk : keys) {
                    if (jwk instanceof RSAKey rsaKey) {
                        next.put(rsaKey.getKeyID(), rsaKey.toPublicKey());
                    }
                }

                if (next.isEmpty()) {
                    throw new IllegalStateException("JWKS response has no usable RSA keys");
                }

                this.cache.clear();
                this.cache.putAll(next);
                this.cacheLoadedAt = Instant.now();
                log.debug("[JwksClient] cache refreshed: {} keys", next.size());
                return;

            } catch (Exception e) {
                last = e;
                log.warn("[JwksClient] JWKS fetch attempt failed (delay={}ms): {}", delayMs, e.getMessage());
            }
        }

        throw new AuthFetchFailedException("JWKS fetch failed after " + (RETRY_DELAYS_MS.length - 1) + " retries", last);
    }

    // 테스트용 헬퍼
    void clearCacheForTest() {
        this.cache.clear();
        this.cacheLoadedAt = Instant.EPOCH;
    }

    int cacheSizeForTest() {
        return cache.size();
    }

    /** ParseException 잡혀서 RuntimeException 으로 보내기 (refresh 안에서 처리되긴 함) */
    @SuppressWarnings("unused")
    private static class JwkParseException extends RuntimeException {
        JwkParseException(ParseException cause) { super(cause); }
    }
}
