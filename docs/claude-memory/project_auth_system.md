---
name: Auth 시스템 (sync-orchestrator-auth, port 8096)
description: 운영자 로그인/인가 — Phase 1~5 모두 완료 (2026-05-06), 9개 모듈 통합 운영 가능
type: project
originSessionId: 307dc124-c3af-418f-8267-eebf68533c7f
---
orchestrator 시스템 운영자 로그인/인가. 9개 모듈 통합 완료.

**현황** (2026-05-06):
- ✅ Phase 1 + 1.5: sync-orchestrator-auth (8096) + sync-agent-common 검증자 자산
- ✅ Phase 2~5: Backend (8080) / api-provider (8095) / api-collector (8084) / Frontend (3000) 통합
- ✅ 통합 E2E 37/37 PASS — Health · JWKS · 인증 정합 · LOOKUP 회귀 · peer multi · 마지막 1명 차단 · Frontend middleware/proxy

**핵심 결정**:
- jjwt + Nimbus JOSE 자체 구현 (Spring Boot 2.7.12 호환 — OAuth2 starter 미사용)
- **폐쇄망 nexus 가용 버전: jjwt 0.12.6 / Nimbus 9.31** (5/4 박은 0.11.5/9.37 → 5/6 통일)
- jjwt 0.12 변경점 = `parserBuilder()` 만 제거 (`parser()` 로 통합), 그 외 deprecated 상태로 backward compatible. `Claims.getAudience()` 만 `Set<String>` 으로 반환 타입 변경 (RFC 7519 정합)
- Peer Multiplication = 누구나 사용자 추가, 본인만 비번변경/탈퇴 (마지막 1명 차단). **role 단일** (`user`) — admin/user 분리 X
- 최초 1명 = `UserGeneratorCli` (별도 main, BCrypt + JDBC, Spring 안 띄움)
- 검증자 모듈은 sync-agent-common AutoConfiguration 패턴으로 자동 등록 — yml 토글만 박으면 동작 (`@AutoConfiguration` + `META-INF/spring/AutoConfiguration.imports`)

**Mock 정책 (5/6 정정)**:
- dev_plan 의 "denyAll 이중방어" 폐기 — 개발 자기호출 흐름과 충돌 (api-collector LOOKUP `/mock/common/select/...`, api-provider `ApiKeyValidationService.url=:8095/api/mock/api-key/validate`)
- 통일 정책: 개발 default = mock 활성 (`@ConditionalOnProperty matchIfMissing=true`) + SecurityConfig path = `permitAll` (자기호출 보장) + 운영 차단 = yml `mock.*.enabled=false` 단일 토글 override

**RSA 키 정책**:
- 매일 자정 회전 (`@Scheduled cron 0 0 0 * * ?`)
- 8일 보관 (refresh 7일 + 1일 여유), 시스템 동시 ~8쌍
- private_pem = jasypt ENC, public_pem = JWKS endpoint 노출

**Frontend 패턴** (폐쇄망 npm 부담 회피):
- SWR / react-hot-toast 등 추가 패키지 0건. 기존 axios + useState/useEffect만 사용
- `useCurrentUser` = 모듈 단위 캐시 + subscribe pattern (자체 구현)
- ⚠️ login 페이지에서 반드시 `mutate()` 호출 — 캐시 stale 회피 (5/6 발견 fix). cached=null+fetched=true 박힌 상태에서 단순 redirect 하면 AppHeader 가 stale 상태로 머묾
- `/auth/*` rewrite (3000→8096), `/api`(8080), `/provider-api`(8095), `/collector-api`(8084) — same-origin proxy 통해 cookie 자동 전송
- middleware 1차 가드 = accessToken cookie 존재만 확인 (실 토큰 검증은 백엔드)

**참고**:
- 설계: `docs/AUTH_DESIGN.md` / 라이프사이클: `docs/AUTH_FLOW.md`
- 계획: `dev_plan/2026_05/04/auth-system-phase{1~5}*.md`
- 안전 tag: `pre-auth` (637cc90), `auth-phase1-baseline` (677505d)
- DB 테이블 (Orchestrator PG `orchestrator`): `auth_users` / `auth_refresh_tokens` / `auth_rsa_keys`
- 운영자 호출 endpoint: backend `/api/**` (callback 제외), provider `/api/manage/**`, collector `/api/**`
- 외부 사용자 endpoint (JWT 미적용): provider `/api/provide/**` (자체 API key 검증)
