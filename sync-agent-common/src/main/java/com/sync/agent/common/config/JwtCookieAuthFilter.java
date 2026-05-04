package com.sync.agent.common.config;

import com.sync.agent.common.auth.AuthErrorCode;
import com.sync.agent.common.auth.AuthErrorResponse;
import com.sync.agent.common.auth.AuthFetchFailedException;
import com.sync.agent.common.client.JwksClient;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Key;
import java.security.PublicKey;
import java.util.Collections;

/**
 * JWT cookie 인증 필터 (검증자 모듈용 — Backend / api-provider / api-collector).
 *
 * <ol>
 *   <li>cookie `accessToken` 추출</li>
 *   <li>header.kid 추출</li>
 *   <li>{@link JwksClient} 로 매칭 공개키 lookup (JWKS fetch + 5min 캐시 + retry 2회)</li>
 *   <li>jjwt 로 서명 + iss + aud 검증</li>
 *   <li>SecurityContext 에 userId / role 박음</li>
 * </ol>
 *
 * <p>실패 시 {@link AuthErrorResponse} 로 401 (cookie 없음/위조/만료) 또는 503 (auth 다운).
 * AUTH_DESIGN.md §13.8 응답 매트릭스 정합.</p>
 *
 * <p>활성화 — `jwt.cookie.enabled=true` 설정 시 (개발 편의 토글).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jwt.cookie.enabled", havingValue = "true")
@ConditionalOnBean(JwksClient.class)
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "accessToken";

    private final JwksClient jwksClient;
    private final String issuer;
    private final String audience;

    public JwtCookieAuthFilter(JwksClient jwksClient,
                               @Value("${auth.issuer:orchestrator-auth}") String issuer,
                               @Value("${auth.audience:orchestrator}") String audience) {
        this.jwksClient = jwksClient;
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Cookie cookie = WebUtils.getCookie(req, COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            // SecurityContext 비워둠 — 보호 endpoint 라면 SecurityConfig 의 EntryPoint 가 401 처리
            chain.doFilter(req, res);
            return;
        }

        String token = cookie.getValue();
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(JwsHeader header, Claims claims) {
                        String kid = header.getKeyId();
                        PublicKey pk = jwksClient.getPublicKey(kid)
                            .orElseThrow(() -> new UnknownKidException(kid));
                        return pk;
                    }
                })
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseClaimsJws(token)
                .getBody();

            Long userId = Long.parseLong(claims.getSubject());
            String role = claims.get("role", String.class);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role.toUpperCase())))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(req, res);

        } catch (UnknownKidException e) {
            AuthErrorResponse.write(res, AuthErrorCode.UNKNOWN_KEY_ID);
        } catch (AuthFetchFailedException e) {
            log.error("[JwtCookieAuthFilter] auth service unavailable", e);
            AuthErrorResponse.write(res, AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        } catch (ExpiredJwtException e) {
            AuthErrorResponse.write(res, AuthErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException e) {
            AuthErrorResponse.write(res, AuthErrorCode.INVALID_SIGNATURE);
        } catch (JwtException | IllegalArgumentException e) {
            AuthErrorResponse.write(res, AuthErrorCode.INVALID_TOKEN);
        }
    }

    /** kid 매칭 실패 — JwksClient 에서 던진 게 아니라 SigningKeyResolver 안에서 마커 */
    private static class UnknownKidException extends RuntimeException {
        UnknownKidException(String kid) { super("Unknown kid: " + kid); }
    }
}
