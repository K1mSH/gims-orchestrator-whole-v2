# 인증 통합 모델 — X-API-Key + JWT 흐름 매트릭스

> 작성일: 2026-05-06
> 목적: 5/6 Phase 2 fix 회귀 발견 후 전체 인증 흐름 정리. 결정 기준 자료.
> 동반: `docs/AUTH_DESIGN.md` §9.3.1 "이슈 1: 시스템 간 인증 강화 = 별 후속 작업"

---

## 1. 두 인증의 책임 분리

| 인증 | 용도 | 발급/관리 | 토글 |
|---|---|---|---|
| **X-API-Key** | 시스템 간 호출 보호 (Backend ↔ Agent ↔ Proxy ↔ api-provider) | `JASYPT_PASSWORD` Docker env → jasypt → 모든 모듈 동일 평문 | `common.filter.api-key.enabled` (sync-agent-common ApiKeyFilter) |
| **JWT cookie** | 운영자 (Frontend) 인증 — 사용자/세션 식별 | sync-orchestrator-auth (8096) — RSA 서명, 매일 회전 | `jwt.cookie.enabled` (sync-agent-common JwtCookieAuthFilter, AutoConfig) |
| **Provide API Key** | 외부 사용자 — api-provider `/api/provide/**` | `MockApiKeyController` (개발) / 외부 인증서버 (운영) | `mock.api-key.enabled` (default true 개발) |

핵심:
- X-API-Key 와 JWT 는 **완전 분리된 흐름**. 한 호출은 둘 중 하나 사용.
- Backend 만 양쪽 다 받음 (Frontend = JWT, 시스템 간 = X-API-Key).
- 그 외 모듈은 한 종류만 받음.

---

## 2. 호출 매트릭스 (Inbound 보호)

### 2.1 모듈별 inbound 인증 토글 현황

| 모듈 | port | ApiKeyFilter | JwtCookieAuthFilter | 비고 |
|---|:--:|:--:|:--:|---|
| sync-orchestrator-auth | 8096 | ❌ (자체 SecurityConfig — 401/204 흐름) | — | 자체 토큰 발급/검증 |
| **sync-orchestrator/backend** | 8080 | **❌ ⚠️ 누락** | ✅ (5/6 Phase 2) | Frontend(cookie) + Proxy/Agent(X-API-Key) 둘 다 받아야 |
| sync-agent-bojo (DMZ Agent) | 8082 | ✅ | ❌ | 시스템 간만 받음 |
| sync-proxy-dmz | 8083 | ✅ | ❌ | 동 |
| infolink-api-collector | 8084 (DMZ) / 8094 (Internal) | ❌ | ✅ (5/6 Phase 4) | 운영자만 — 외부 호출 없음 |
| sync-agent-others | 8085 | ✅ | ❌ | 시스템 간만 |
| sync-agent-bojo-int (Internal Agent) | 8092 | ✅ | ❌ | 동 |
| sync-proxy-internal | 8093 | ✅ | ❌ | 동 |
| sync-agent-provide | (port 별도) | ✅ | ❌ | 동 |
| gims-api-provider | 8095 | ❌ (외부=Provide Key, 운영=JWT) | ✅ (5/6 Phase 3) | 외부 사용자 + 운영자 |

### 2.2 호출 흐름 매트릭스

| # | 호출자 → 수신자 | 인증 | 현 상태 |
|:-:|---|---|:--:|
| 1 | Frontend (3000) → Backend `/api/**` | JWT cookie | ✅ |
| 2 | Frontend → api-provider `/api/manage/**` | JWT cookie | ✅ |
| 3 | Frontend → api-collector `/api/**` | JWT cookie | ✅ |
| 4 | Frontend → auth `/api/auth/**` | (login = 무인증, me/refresh = cookie) | ✅ |
| 5 | Backend → Agent (실행 trigger) | X-API-Key (`proxy.api-key`, WebConfig RestTemplate 인터셉터) | ✅ |
| 6 | Backend → Proxy (실행 trigger 등) | X-API-Key (동) | ✅ |
| 7 | Agent → Proxy `/api/datasources/*/connection-info` | X-API-Key | ✅ |
| 8 | Agent → Backend `/api/callback/**` (실행 알림) | (현 permitAll, 시스템 간 강화 미진행) | ⚠️ 5/4 §9.3.1 "별 후속" |
| 9 | **Proxy → Backend `/api/datasources/*/connection-info`** | **X-API-Key (자격증명 응답)** | **❌ 5/6 발견 회귀, fix 진행 중** |
| 10 | api-provider → Internal Proxy `/api/datasources/*/connection-info` | X-API-Key (`ProviderDataSourceService:67`) | ✅ |
| 11 | 외부 사용자 → api-provider `/api/provide/**` | Provide API Key (자체 검증, ApiKeyValidationService → mock 또는 외부 인증서버) | ✅ |
| 12 | api-provider → Mock 자체 `/api/mock/api-key/validate` | (자기 호출, 개발 의존성) | ✅ permitAll |
| 13 | api-collector → Mock 자체 `/mock/common/select/...` (LOOKUP) | (자기 호출) | ✅ permitAll |

### 2.3 비대칭 발견

| 흐름 | 문제 |
|---|---|
| #9 Proxy → Backend connection-info | Backend 가 ApiKeyFilter 활성 X — 자격증명 endpoint 처음부터 무인증 (5/4 §9.3.1 "별 후속" 미완) |
| #8 Agent → Backend callback | 시스템 간 X-API-Key 강화 미진행 (5/4 메모) |
| Backend WebConfig | outbound 만 X-API-Key 박음 (Backend → 다른 모듈), inbound 검증 X — **비대칭** |

---

## 3. 통합 인증 모델 (목표 상태)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 운영자 흐름 (JWT cookie)                                                  │
│   Frontend → Backend / api-provider / api-collector                     │
│     - 모든 운영자 endpoint 는 JwtCookieAuthFilter 통과                     │
│     - Frontend 는 X-API-Key 박지 X (브라우저 노출 위험)                    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ 시스템 간 흐름 (X-API-Key, JASYPT_PASSWORD 로 풀린 동일 평문)                │
│   Backend ↔ Agent ↔ Proxy ↔ api-provider                                 │
│     - 모든 inbound 에 ApiKeyFilter 활성                                   │
│     - 모든 outbound 에 RestTemplate 인터셉터로 헤더 박음                    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ Backend 의 mixed 케이스                                                  │
│   - 시스템 간 X-API-Key 호출: ApiKeyFilter 가 검증 → 통과                  │
│   - 운영자 cookie 호출: X-API-Key 없음 → ApiKeyFilter "통과"               │
│                          → JwtCookieAuthFilter 가 cookie 검증              │
│   ⚠️ 둘 중 어느 쪽도 통과 못 하면 401                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Backend 의 두 인증 OR 처리 — `soft-mode` 의 의미

```
ApiKeyFilter (path = /api/**):
   ├ X-API-Key 있음 + 매치:
   │     strict mode → 통과 (Spring Security 무관, Agent/Proxy 는 컨트롤러 직행)
   │     soft mode   → 통과 + SecurityContext 에 ROLE_SYSTEM 박음 (Backend 의 SecurityFilterChain 통과 위해)
   ├ X-API-Key 있음 + 불일치 → 401 (가짜 키)
   └ X-API-Key 없음:
       ├ strict mode (default, Agent/Proxy) → 401
       └ soft mode (Backend) → 통과 (다음 filter = JwtCookieAuthFilter 가 cookie 검증)
```

**모드별 차이 (3 케이스)**:

| X-API-Key | strict (default) | soft (Backend) |
|---|---|---|
| 있음 + 매치 | ✅ 통과 (서블릿 chain 그대로) | ✅ 통과 + `SecurityContext.ROLE_SYSTEM` 박음 |
| 있음 + 불일치 | ❌ 401 | ❌ 401 |
| 없음 | ❌ 401 | ✅ 통과 (cookie 흐름 양보) |

X-API-Key 검증 자체 (매치/불일치 판정) 는 모든 모듈에서 동일. Backend 만 두 가지 분기 추가:
- 키 매치 시 SecurityContext 박기 — Spring Security `anyRequest().authenticated()` 통과
- 키 없을 때 통과 — cookie 흐름 양보

> ⚠️ Spring Security 의존성 — `SecurityContextHolder` 호출은 메서드 안에서만 (`if (softMode)` 분기 안). JVM lazy class resolution 덕에 strict 모듈 (Spring Security 미포함) 에서는 호출 안 되어 NoClassDefFoundError 0.

### 3.2 다른 모듈은 왜 strict 그대로?

- Agent / Proxy / sync-agent-* = cookie 흐름 자체 없음 (Frontend 가 직접 호출 안 함) + Spring Security 자체 안 씀
- 따라서 X-API-Key 가 유일한 inbound 인증 → strict 가 옳음 + SecurityContext 박을 필요도 없음
- soft mode 로 바꾸면 무인증 호출 통과 = 보안 약화

→ **toggle 로 분리하지 않으면 다른 모듈 보안 약화 + Spring Security 의존성 강제 위험.** 토글 추가가 정공법.

---

## 4. fix 작업 단위 (제안)

### 4.1 즉시 (본 작업 흐름 안)

| 단위 | 변경 | 영향 |
|---|---|---|
| **A** | sync-agent-common `ApiKeyFilter` 수정:<br>1. `common.filter.api-key.soft-mode` yml 토글 추가 (default false=strict)<br>2. softMode=true + 키 매치 시 `SecurityContext.ROLE_SYSTEM` 박는 분기 추가<br>3. softMode=true + 키 없음 → chain.doFilter 통과 (cookie 양보) | common 1 파일, 다른 모듈 동작 0 (lazy class resolution) |
| **B** | Backend yml + 메인 — ApiKeyFilter 활성 + soft-mode 활성 | 5/6 작업 누락 보완 |
| **B-1** | Backend SecurityConfig 의 `/api/datasources/*/connection-info` permitAll 줄 제거 (이전 임시 fix) — soft-mode 로 통합 처리 | 정정 |
| **C** | Proxy `ConnectionInfoController` — RestTemplate 헤더 X-API-Key 박기 (Internal+DMZ) | 이미 적용 |
| **D** | sync-agent-common JAR 재빌드 + 9개 모듈 복사 | 다른 모듈 동작 0 (토글 default 미설정) |

### 4.2 별 사이클 (5/4 §9.3.1 후속)

- `/api/callback/**` X-API-Key 강화 — Agent → Backend callback path 도 검증
- api-collector / api-provider 가 inbound X-API-Key 받는 흐름이 있는지 점검 (현 X)

---

## 5. 결정 항목

1. ✅ **두 인증 OR 처리 모델** 채택 — Backend 만 soft mode, 그 외 strict
2. ✅ **ApiKeyFilter 의 path = `/api/**` 모든 inbound** (Backend 도 통일)
3. ✅ **공유 키 = `agent.api-key` ENC, JASYPT_PASSWORD 로 풀린 동일 평문** (다른 모듈과 동일)
4. ⚠️ **callback path 강화는 별 사이클** (본 작업 범위 밖)
5. ⚠️ **api-collector / api-provider 의 ApiKeyFilter 활성 여부**: 현 비활성. 외부에서 직접 호출 받지 않으니 (Frontend 만 호출, 또는 Provide API Key) 유지 OK. 별 사이클에서 점검.

---

## 6. 본 작업 구체

§4.1 A~D 진행. 5/6 Phase 2 누락 보완 + 5/4 "별 후속" 의 자격증명 path 한정 적용.
