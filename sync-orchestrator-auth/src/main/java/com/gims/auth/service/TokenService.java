package com.gims.auth.service;

import com.gims.auth.service.KeyService.ActiveKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급 / 검증 (RS256 + jjwt).
 *
 * <ul>
 *   <li>Access Token TTL = 15min (yml `auth.access-token-ttl-minutes`)</li>
 *   <li>Refresh Token TTL = 7day (yml `auth.refresh-token-ttl-days`)</li>
 *   <li>Issuer = orchestrator-auth / Audience = orchestrator (yml)</li>
 *   <li>kid 박힌 RS256 — 검증자가 JWKS 로 매칭 공개키 lookup</li>
 *   <li>refresh token 은 `type=refresh` claim 으로 access 와 구분</li>
 * </ul>
 *
 * Refresh DB INSERT 는 AuthService 책임 — 여기서는 토큰 객체만 만들어 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_REFRESH = "refresh";

    private final KeyService keyService;

    @Value("${auth.access-token-ttl-minutes:15}")
    private int accessTtlMin;

    @Value("${auth.refresh-token-ttl-days:7}")
    private int refreshTtlDays;

    @Value("${auth.issuer:orchestrator-auth}")
    private String issuer;

    @Value("${auth.audience:orchestrator}")
    private String audience;

    // ============================================================
    // 발급
    // ============================================================

    public String createAccessToken(Long userId, String role) {
        ActiveKey active = activeKey();
        Instant now = Instant.now();
        return Jwts.builder()
            .setHeaderParam("kid", active.kid())
            .setSubject(String.valueOf(userId))
            .claim(CLAIM_ROLE, role)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plus(Duration.ofMinutes(accessTtlMin))))
            .setIssuer(issuer)
            .setAudience(audience)
            .signWith(active.privateKey(), SignatureAlgorithm.RS256)
            .compact();
    }

    public RefreshToken createRefreshToken(Long userId) {
        ActiveKey active = activeKey();
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofDays(refreshTtlDays));
        String jti = UUID.randomUUID().toString();

        String jwt = Jwts.builder()
            .setHeaderParam("kid", active.kid())
            .setSubject(String.valueOf(userId))
            .claim(CLAIM_TYPE, TYPE_REFRESH)
            .setId(jti)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(exp))
            .setIssuer(issuer)
            .setAudience(audience)
            .signWith(active.privateKey(), SignatureAlgorithm.RS256)
            .compact();

        return new RefreshToken(jti, jwt, LocalDateTime.ofInstant(exp, ZoneId.systemDefault()));
    }

    // ============================================================
    // 검증
    // ============================================================

    public Claims verifyAccessToken(String token) {
        Claims claims = parseAndVerify(token);
        if (TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new IllegalArgumentException("refresh token cannot be used as access");
        }
        return claims;
    }

    public Claims verifyRefreshToken(String token) {
        Claims claims = parseAndVerify(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new IllegalArgumentException("not a refresh token");
        }
        return claims;
    }

    /**
     * 서명 + iss + aud + exp 검증.
     * SigningKeyResolverAdapter 로 토큰 header 의 kid 보고 매칭 publicKey 동적 lookup.
     */
    private Claims parseAndVerify(String token) {
        return Jwts.parserBuilder()
            .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                @Override
                public Key resolveSigningKey(JwsHeader header, Claims claims) {
                    String kid = header.getKeyId();
                    return keyService.findPublicKeyByKid(kid)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown kid: " + kid));
                }
            })
            .requireIssuer(issuer)
            .requireAudience(audience)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private ActiveKey activeKey() {
        return keyService.findActiveKey()
            .orElseThrow(() -> new IllegalStateException("No active RSA key — auth module not initialized"));
    }

    // ============================================================
    // value object
    // ============================================================

    /** Refresh Token 발급 결과 — DB INSERT 시 jti 와 expiresAt 사용 */
    public record RefreshToken(String jti, String jwt, LocalDateTime expiresAt) {}
}
