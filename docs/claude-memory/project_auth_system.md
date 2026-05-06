---
name: Auth 시스템 (sync-orchestrator-auth, port 8096) + 시스템 간 인증 통합 모델
description: 운영자 JWT + 시스템 간 X-API-Key 양 인증 모델. Phase 1~5 완료 + Backend 비대칭 보완 (2026-05-06 오후)
type: project
originSessionId: 307dc124-c3af-418f-8267-eebf68533c7f
---

orchestrator 시스템 인증 — 운영자(JWT cookie) + 시스템 간(X-API-Key) 두 흐름 통합 모델.

## 인증 매트릭스 (호출자 → 수신자)

- **운영자 → Backend/api-provider/api-collector** = JWT cookie (sync-orchestrator-auth 8096 발급, RS256 + JWKS)
- **Frontend → 외부 모든 호출** = JWT cookie. Frontend 는 X-API-Key 박지 X (브라우저 노출 위험)
- **시스템 간 (Backend↔Agent↔Proxy↔api-provider)** = X-API-Key (`JASYPT_PASSWORD` Docker env → jasypt → 모든 모듈 동일 평문)
- **외부 사용자 → api-provider `/api/provide/**`** = Provide API Key (자체 ApiKeyValidationService — `?apiKey=...` 쿼리 파라미터)

## 모듈별 ApiKeyFilter 활성

| 모듈 | enabled | soft-mode | 사유 |
|---|:--:|:--:|---|
| Backend (8080) | ✅ | **✅** | mixed (운영자 cookie + 시스템 X-API-Key 둘 다 받음) |
| Agent (bojo, others, provide, bojo-int) | ✅ | ❌ strict | 시스템 간만 받음 |
| Proxy (internal, dmz) | ✅ | ❌ strict | 동 |
| api-provider (8095) | ❌ | — | inbound 시스템 간 호출 받지 않음 (외부=Provide Key, 운영=JWT). outbound 만 X-API-Key (Proxy 호출용) |
| api-collector (8084) | ❌ | — | 운영자만 — 외부 API 직접 호출, Proxy 안 거침 |

## ApiKeyFilter soft-mode (5/6 추가)

X-API-Key 헤더 상태별:
- 있음 + 매치: strict→통과, **soft→통과 + SecurityContext.ROLE_SYSTEM 박음**
- 있음 + 불일치: 401 (양쪽 동일)
- 없음: strict→401, **soft→통과 (cookie 흐름 양보)**

검증 동작 통일. Backend 만 cookie 양보 + SecurityContext 박기 분기.
- `SystemAuthenticationSetter` 별 클래스로 SecurityContextHolder 호출 분리 — strict 모듈 (Spring Security 미포함) lazy class resolution 으로 NoClassDefFoundError 회피
- Backend SecurityConfig 가 ApiKeyFilter 를 `addFilterAfter(SecurityContextPersistenceFilter)` 로 SecurityFilterChain 안에 등록 (servlet 자동 등록은 `FilterRegistrationBean.setEnabled(false)` 로 끔). 안 그러면 SecurityContextPersistenceFilter 가 ApiKeyFilter 가 박은 SecurityContext 를 매 요청 시작에 clear

## 자격증명 endpoint 흐름

```
Agent → Proxy → Backend
       ↑         ↑
       │ X-API-Key (Proxy 의 ConnectionInfoController 가 RestTemplate 헤더 박음, 5/6 추가)
       │
       X-API-Key (기존)
```

5/6 이전: Backend 가 `/api/datasources/*/connection-info` 무인증 노출 (5/4 §9.3.1 "이슈 1: 별 후속" 미진행). 5/6 보완.

## RSA 키 정책

- 매일 자정 회전 (`@Scheduled cron 0 0 0 * * ?`)
- 8일 보관 (refresh 7일 + 1일 여유), 시스템 동시 ~8쌍
- private_pem = jasypt ENC, public_pem = JWKS endpoint 노출
- 폐쇄망 nexus 가용 버전 = jjwt 0.12.6 / Nimbus 9.31 (5/4 박은 0.11.5/9.37 → 5/6 마이그레이션)

## Frontend 패턴 (폐쇄망 npm 부담 회피)

- SWR / react-hot-toast 등 추가 패키지 0건. 기존 axios + useState/useEffect만
- `useCurrentUser` = 모듈 단위 캐시 + subscribe pattern (자체 구현)
- ⚠️ login 페이지에서 반드시 `mutate()` 호출 — cached=null+fetched=true 박힌 상태 stale 회피
- `/auth/*` rewrite (3000→8096), `/api`(8080), `/provider-api`(8095), `/collector-api`(8084) — same-origin proxy 통해 cookie 자동 전송
- middleware 1차 가드 = accessToken cookie 존재만 확인

## Mock 정책 (5/6 정정)

- 개발 default = mock 활성 (`@ConditionalOnProperty matchIfMissing=true`)
- SecurityConfig path = `permitAll` (자기호출 보장 — api-collector LOOKUP, api-provider ApiKeyValidationService)
- 운영 차단 = yml `mock.*.enabled=false` 단일 토글

## 참고

- 설계: `docs/AUTH_DESIGN.md` (1424줄, §9.3.1 의 "별 후속" 미완 항목들)
- 라이프사이클: `docs/AUTH_FLOW.md` (856줄)
- 인증 매트릭스: `dev_plan/2026_05/06/auth-integration-matrix.md` (5/6 정립)
- DB 테이블 (Orchestrator PG): `auth_users` / `auth_refresh_tokens` / `auth_rsa_keys`
- 안전 tag: `pre-auth` (637cc90), `auth-phase1-baseline` (677505d)
- 후속 (별 사이클): `/api/callback/**` X-API-Key 강화, callback body 자체 검증
