---
name: Auth 시스템 (sync-orchestrator-auth, port 8096)
description: 운영자 로그인/인가 — Phase 1+1.5 완료 (2026-05-04), Phase 2~5 미진행
type: project
---

orchestrator 시스템에 운영자 로그인/인가 신규 도입.

**현황** (2026-05-04):
- ✅ Phase 1 + 1.5: sync-orchestrator-auth (8096) + sync-agent-common 검증자 자산 (`JwtCookieAuthFilter` / `JwksClient`) — 8 모듈 libs/ 복사
- ⏳ Phase 2~5 미진행 (별 세션) — Backend / api-provider / api-collector / Frontend 통합

**핵심 결정**:
- B 옵션 = jjwt 0.11.5 + Nimbus JOSE 9.x 자체 구현 (Spring Boot 2.7.12 호환 — OAuth2 starter 미사용)
- 토글 default 비활성 (`jwt.cookie.enabled` / `auth.jwks-url` 미설정) = 검증자 모듈 영향 0
- Peer Multiplication = 누구나 사용자 추가, 본인만 비번변경/탈퇴 (마지막 1명 차단)
- 최초 1명 = `UserGeneratorCli` (별도 main, BCrypt + JDBC, Spring 안 띄움)
- Phase 2~5 = **한 묶음 배포** (단독 적용 시 운영 화면 401 즉시 발생)

**RSA 키 정책**:
- 매일 자정 회전 (`@Scheduled cron 0 0 0 * * ?`)
- 8일 보관 (refresh 7일 + 1일 여유), 시스템 동시 ~8쌍
- private_pem = jasypt ENC, public_pem = JWKS endpoint 노출

**참고**:
- 설계: `docs/AUTH_DESIGN.md` / 라이프사이클: `docs/AUTH_FLOW.md`
- 계획: `dev_plan/2026_05/04/auth-system-phase{1~5}*.md`
- 안전 tag: `pre-auth` (637cc90), `auth-phase1-baseline` (677505d)
- DB 테이블 (Orchestrator PG `orchestrator`): `auth_users` / `auth_refresh_tokens` / `auth_rsa_keys`
