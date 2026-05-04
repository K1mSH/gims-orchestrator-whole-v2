package com.gims.auth.controller;

import com.gims.auth.service.KeyService;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

/**
 * JWKS endpoint (RFC 7517 표준).
 *
 * 검증자 모듈 (Backend / api-provider / api-collector) 이 5min 마다 fetch 하여 캐시.
 * 응답에는 active 1쌍 + 만료 안 된 비활성 ~7쌍 = 약 8쌍 공개키 포함.
 * 회전 직후 5min 갭 동안 검증자가 새 kid 인식 못 할 수 있음 — 자연 회복.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final KeyService keyService;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        List<JWK> keys = keyService.findAllValidPublicKeys().stream()
            .map(this::toJwk)
            .map(jwk -> (JWK) jwk)
            .toList();
        return new JWKSet(keys).toJSONObject();
    }

    private RSAKey toJwk(KeyService.PublicKeyInfo info) {
        return new RSAKey.Builder((RSAPublicKey) info.publicKey())
            .keyID(info.kid())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    }
}
