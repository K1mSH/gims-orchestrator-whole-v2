package com.infolink.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auth 모듈 — 운영자 인증/인가 마이크로서비스 (port 8096)
 *
 * 책임:
 * - 로그인 / refresh / 로그아웃 (JWT RS256)
 * - 사용자 CRUD (peer multiplication — 누구나 추가, 본인만 비번변경/탈퇴)
 * - JWKS endpoint (검증자 모듈에 RSA 공개키 노출)
 * - RSA 키 자정 회전 (매일)
 *
 * 검증자 모듈 (Backend / api-provider / api-collector) 은 sync-agent-common 의
 * JwtCookieAuthFilter + JwksClient 로 자체 검증.
 */
@SpringBootApplication
@EnableScheduling
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
