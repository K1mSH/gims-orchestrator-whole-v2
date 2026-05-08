# 01 — 보안 (Auth + ApiKeyFilter) 기능 테스트 문서

> 작성일: 2026-05-07
> 동반: `docs/AUTH_DESIGN.md`, `docs/AUTH_FLOW.md`, `dev_plan/2026_05/06/auth-integration-matrix.md`

---

## 공통 검증 규칙

- **claude API 호출 → 1차 확인.** 그 결과만으로 "통과" 판단 X.
- **사용자가 직접 프론트(`localhost:3000`)에서 같은 흐름 시각적으로 확인** 후에만 통과 표시.
- 실행 단계에서 단계마다 사용자 OK 후 다음 단계 진입.

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [사전 준비](#2-사전-준비)
3. [JWT 발급/검증 흐름](#3-jwt-발급검증-흐름)
4. [위조/만료/거부 케이스](#4-위조만료거부-케이스)
5. [ApiKeyFilter (시스템 간 인증)](#5-apikeyfilter-시스템-간-인증)
6. [사용자 관리 (peer multi)](#6-사용자-관리-peer-multi)
7. [Frontend middleware + interceptor](#7-frontend-middleware--interceptor)
8. [검증자 모듈 회귀](#8-검증자-모듈-회귀)
9. [알려진 허점 / 주의사항](#9-알려진-허점--주의사항)

---

## 1. 시스템 구성

### 1-1. 모듈 구성
| 역할 | 모듈 | 포트 | 비고 |
|---|---|:-:|---|
| **발급자** | infolink-auth | 8096 | JWT RS256 / refresh / 사용자 관리 |
| **검증자** (운영자 endpoint) | infolink-orchestrator-backend | 8080 | `/api/agents`, `/api/operations` 등 |
| 검증자 | infolink-api-provider | 8095 | `/api/manage/**` (Provide API key 흐름은 별개) |
| 검증자 | infolink-api-collector | 8084 / 8094 | `/api/endpoints`, `/api/operations` 등 |
| 검증자 (시스템 간) | infolink-proxy-{dmz,internal} | 8083 / 8093 | X-API-Key strict |
| 검증자 (시스템 간) | infolink-agent-* | 8082 / 8085 / 8092 | X-API-Key strict |
| 운영자 UI | infolink-orchestrator-frontend | 3000 | middleware → /login redirect |

### 1-2. 인증 모델
- **운영자 자격 영역** (Frontend ↔ Backend / Provider 운영 / Collector 운영) → JWT cookie (HttpOnly)
- **시스템 간 호출** (Backend ↔ Agent / Backend ↔ Proxy / Proxy ↔ Backend connection-info) → X-API-Key
- **외부 사용자 영역** (외부 → Provider `/api/provide/{operationId}`) → Provide API Key (DB 등록)

### 1-3. DB 테이블 (Orchestrator PG 29001)
| 테이블 | 보관 |
|---|---|
| `auth_users` | id (Long IDENTITY) / **`auth_users_id`** (5/6 rename, UNIQUE) / password_hash / name / created_at |
| `auth_refresh_tokens` | jti / user_id / issued_at / expires_at / revoked_at |
| `auth_rsa_keys` | kid / public_pem / private_pem_enc (jasypt) / generated_at / expires_at |

### 1-4. JWT 토큰 모델
- **알고리즘**: RS256
- **Access**: 15분, claims = sub(authUsersId), role=user, iss, aud, jti, exp
- **Refresh**: 7일, jti 회전 (사용 시 revoke 후 새 jti 발급)
- **저장 위치 (Frontend)**: HttpOnly + sameSite=Strict cookie 2개 (`accessToken`, `refreshToken`)
- **JWKS endpoint**: `GET http://localhost:8096/.well-known/jwks.json`

---

## 2. 사전 준비

### 2-1. 서비스 기동 (의존 순서)
```
1. infolink-auth (8096)
2. infolink-orchestrator-backend (8080)
3. infolink-proxy-dmz (8083) / infolink-proxy-internal (8093)
4. infolink-api-collector (8084) / infolink-api-provider (8095)
5. infolink-agent-bojo-dmz (8082)
6. infolink-orchestrator-frontend (3000)
```

### 2-2. DB 상태 확인
- [ ] `auth_users` 에 admin 사용자 (id=27, authUsersId='admin') 존재
- [ ] 비번 = `pass1234` (5/6 시점 그대로)
- [ ] `auth_rsa_keys` 에 활성 키 1쌍 (kid=K-2026-05-04-... 또는 자동 회전 신규)
- [ ] (필요 시) `UserGeneratorCli` 로 admin 재발급:
  ```bash
  cd D:/dev/claude/GIMS/orchestrator_v2/infolink-auth
  ./gradlew createUser --args="admin pass1234 운영자"
  ```

### 2-3. 검증자 모듈 yml 점검
각 검증자의 `application.yml` 에 다음 항목 박혀있는지:
```yaml
auth:
  jwks-url: http://localhost:8096/.well-known/jwks.json
  issuer: infolink-auth
  audience: infolink-orchestrator
jwt:
  cookie:
    enabled: true
common:
  filter:
    api-key:
      enabled: true
      soft-mode: true   # backend 만 (다른 모듈은 strict default)
agent:
  api-key: ENC(...)
```

---

## 3. JWT 발급/검증 흐름

### 3-1. Health 7/7 UP
```bash
for p in 8096 8080 8083 8093 8084 8095 8082; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:$p/actuator/health
done
```
- [ ] 7/7 → HTTP 200
- [ ] frontend `curl -I http://localhost:3000/` → HTTP 307 (middleware /login redirect)

### 3-2. JWKS endpoint
```bash
curl -s http://localhost:8096/.well-known/jwks.json | jq
```
- [ ] `keys` 배열에 1개 (또는 회전 직후 2개)
- [ ] 각 key 의 `kty=RSA`, `alg=RS256`, `kid` 명시
- [ ] 사용자 검증 (프론트): 화면에서 JWT decode (jwt.io) 시 같은 kid 표시

### 3-3. 인증 없이 호출 → 401 거부
```bash
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/agents
```
- [ ] HTTP 401 + `{"error":"AUTH_REQUIRED"}`
- [ ] `http://localhost:8095/api/manage/operations` → 401
- [ ] `http://localhost:8084/api/endpoints` → 401

### 3-4. 로그인 (`POST /api/auth/login`)
```bash
curl -s -c /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8096/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"authUsersId":"admin","password":"pass1234"}'
```
- [ ] HTTP 200 + body `{"user":{"id":27,"authUsersId":"admin","name":"운영자","createdAt":"..."}}`
- [ ] cookie jar 에 `accessToken`, `refreshToken` 둘 다 박힘
- [ ] 사용자 검증: 프론트 `/login` 화면에서 같은 자격 입력 → `/` (대시보드) 자동 이동 + 헤더에 "운영자" 표시

### 3-5. `/api/auth/me` (cookie 인증 통과)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" http://localhost:8096/api/auth/me
```
- [ ] HTTP 200 + body `{"id":27,"authUsersId":"admin","name":"운영자","createdAt":"..."}`
- [ ] 사용자 검증: 프론트 헤더 → "내 정보 변경" 진입 → 같은 정보 표시

### 3-6. Refresh (`POST /api/auth/refresh`)
```bash
curl -s -b /tmp/cookies.txt -c /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8096/api/auth/refresh
```
- [ ] HTTP 204 (No Content)
- [ ] cookie jar 의 accessToken / refreshToken 모두 새 값으로 교체
- [ ] 새 cookie 로 `/api/auth/me` → 200
- [ ] **이전 cookie (refresh 사용 전)** 로 다시 refresh 시도 → 401 `REFRESH_REVOKED_OR_EXPIRED`

### 3-7. Logout (`POST /api/auth/logout`)
```bash
curl -s -b /tmp/cookies.txt -c /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8096/api/auth/logout
```
- [ ] HTTP 204
- [ ] cookie jar 비워짐 (또는 만료 cookie)
- [ ] 만료 cookie 로 `/api/auth/me` → 401
- [ ] 사용자 검증: 프론트 헤더 → "로그아웃" 클릭 → `/login` 자동 이동

---

## 4. 위조/만료/거부 케이스

### 4-1. 위조 cookie
```bash
curl -s -w "\nHTTP %{http_code}\n" \
  -H "Cookie: accessToken=eyJxxx.fake.signature" \
  http://localhost:8080/api/agents
```
- [ ] HTTP 401 + `{"error":"INVALID_TOKEN"}`
- [ ] backend / provider / collector 모두 동일 결과

### 4-2. 만료 access token (시뮬레이션)
- [ ] 로그인 후 17분 대기 (또는 yml `auth.access-ttl-minutes` 임시 단축) → `/api/agents` 호출
- [ ] HTTP 401 → axios interceptor 가 `/api/auth/refresh` 자동 호출 → 새 access 받아 재시도 → 200
- [ ] 사용자 검증: 프론트에서 17분 이상 가만 두고 어떤 페이지 클릭 → 자동 갱신 후 정상 표시 (눈에 안 띄게)

### 4-3. 폐기된 refresh
- [ ] 로그인 → refresh 1회 호출 (jti 회전)
- [ ] 옛 refresh cookie 로 다시 refresh → 401 `REFRESH_REVOKED_OR_EXPIRED`
- [ ] DB `auth_refresh_tokens` 의 옛 jti row 의 `revoked_at` not null 확인

### 4-4. 비번 변경 후 cookie revoke
- [ ] alice 로그인 → cookie 보관
- [ ] alice 가 본인 비번 변경 (`PATCH /api/auth/users/me/password`) → 200
- [ ] 보관된 옛 cookie 로 refresh → 401 `REFRESH_REVOKED_OR_EXPIRED` (모든 refresh revoke)
- [ ] alice 가 새 비번으로 재로그인 → 200

---

## 5. ApiKeyFilter (시스템 간 인증)

### 5-1. Strict 모드 (Agent / Proxy)
| 요청 | 응답 |
|---|---|
| X-API-Key 없음 | 401 |
| X-API-Key 불일치 | 401 |
| X-API-Key 일치 | 200 |
| `/health` (filter 제외) | 200 (인증 무관) |

```bash
# strict — 헤더 없음 → 401
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8083/api/datasources/dmz/connection-info
```
- [ ] HTTP 401
- [ ] X-API-Key 정상 헤더 → 200 (Agent / Proxy 둘 다)

### 5-2. Soft 모드 (Backend 만)
| 요청 | 응답 |
|---|---|
| X-API-Key 없음 → cookie 흐름 양보 | cookie 검증 결과대로 (200 또는 401) |
| X-API-Key 일치 | 200 + ROLE_SYSTEM SecurityContext |
| X-API-Key 불일치 | 401 |

- [ ] backend `/api/datasources/{id}/connection-info` 에 X-API-Key 정상 헤더 → 200
- [ ] backend 같은 endpoint 에 X-API-Key 없이 cookie 만 → 200 (운영자 흐름)
- [ ] backend `/api/agents` 에 X-API-Key 없이 cookie 만 → 200

### 5-3. Backend 의 SecurityFilterChain 등록 (5/6 보강)
- [ ] `ApiKeyFilter` 가 `SecurityContextPersistenceFilter` 다음에 등록 (servlet chain 아님)
- [ ] `FilterRegistrationBean.setEnabled(false)` 로 servlet 자동 등록 끔
- [ ] X-API-Key 매치 시 `SecurityContextHolder` 에 `ROLE_SYSTEM` 박힘 (다음 endpoint 도달까지 인증 유지)

### 5-4. proxy → backend 호출에 X-API-Key 박혀있는지
- [ ] `infolink-proxy-internal/.../ConnectionInfoController` 의 RestTemplate 인터셉터가 `agent.api-key` 자동 주입
- [ ] proxy-dmz 동일

---

## 6. 사용자 관리 (peer multi)

### 6-1. 사용자 목록 (`GET /api/auth/users`)
```bash
curl -s -b /tmp/cookies.txt http://localhost:8096/api/auth/users | jq
```
- [ ] HTTP 200
- [ ] 배열 안 각 항목 = `{id, authUsersId, name, createdAt}` (password_hash 노출 X)
- [ ] 사용자 검증: 프론트 `/users` 화면 → 같은 목록 표시

### 6-2. 사용자 추가 (`POST /api/auth/users`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8096/api/auth/users \
  -H "Content-Type: application/json" \
  -d '{"authUsersId":"alice","password":"pass1234","name":"앨리스"}'
```
- [ ] HTTP 201
- [ ] 응답 body `{id: ..., authUsersId: "alice", ...}` (password_hash 미포함)
- [ ] 사용자 검증: 프론트 `/users` → "+ 새 사용자" 모달 → 같은 정보 입력 → 목록에 alice 추가됨

### 6-3. 검증 에러 (6가지)
- [ ] 중복 authUsersId → 409 `AUTH_USERS_ID_DUPLICATE`
- [ ] 비번 8자 미만 → 400 `PASSWORD_TOO_SHORT`
- [ ] authUsersId 누락 → 400 `AUTH_USERS_ID_REQUIRED`
- [ ] authUsersId 50자 초과 → 400 `AUTH_USERS_ID_TOO_LONG` (엔티티 length=50 정합)
- [ ] name 누락 → 400 `NAME_REQUIRED`
- [ ] name 50자 초과 → 400 `NAME_TOO_LONG`

### 6-4. 본인 비번 변경 (`PATCH /api/auth/users/me/password`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X PATCH http://localhost:8096/api/auth/users/me/password \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"pass1234","newPassword":"newpass5678"}'
```
- [ ] HTTP 204
- [ ] currentPassword 불일치 → 400 `INVALID_CURRENT_PASSWORD`
- [ ] 모든 refresh token revoke (옛 cookie 로 refresh → 401)
- [ ] 사용자 검증: 프론트 "내 정보 변경" → 비번 변경 폼 → 입력 후 자동 로그아웃 → 새 비번으로 재로그인

### 6-5. 본인 탈퇴 (`DELETE /api/auth/users/me`)
- [ ] alice 로 본인 탈퇴 → 204 (count 2명 → 1명)
- [ ] alice cookie 로 `/api/auth/me` → 401
- [ ] DB `auth_users` 에서 alice 삭제됨
- [ ] **마지막 1명 차단**: admin 본인 탈퇴 시도 → 409 `LAST_USER_CANNOT_DELETE`
- [ ] 사용자 검증: 프론트 "내 정보 변경" → "탈퇴" 버튼 → 확인 모달 → 클릭 후 `/login` 이동

---

## 7. Frontend middleware + interceptor

### 7-1. middleware (1차 가드 — `middleware.ts`)
- [ ] cookie 없음 → `GET /` → 307 `/login?next=/`
- [ ] cookie 없음 → `GET /agents` → 307 `/login?next=/agents`
- [ ] cookie 없음 → `GET /login` → 200 (가드 우회)
- [ ] cookie 정상 → `GET /` → 200 (대시보드)
- [ ] cookie 정상 → `GET /login` → 307 `/` (로그인 후 재진입 차단)

### 7-2. axios interceptor (`lib/authInterceptor.ts`)
- [ ] 401 응답 → `/api/auth/refresh` 자동 호출 → 새 cookie 받아 원 요청 재시도
- [ ] refresh 도 실패 → `window.location.href = /login?next=...`
- [ ] 503 응답 → alert ("서비스 일시 중단")

### 7-3. Route group `(main)/`
- [ ] `/login` → root layout 만 (sidebar/header 없음)
- [ ] `/`, `/agents`, `/users`, `/datasources` 등 → `(main)/layout.tsx` 의 AppShell 적용 (sidebar + header)
- [ ] **5/6 stale layout 이슈 회귀**: 로그아웃 후 재로그인 시 sidebar/header 즉시 표시 (F5 없이)

### 7-4. proxy rewrite (`next.config.js`)
- [ ] `/auth/:path*` → `localhost:8096/api/auth/:path*` (frontend 에서는 짧은 path 사용)
- [ ] `/api/:path*` → `localhost:8080/api/:path*` (backend)
- [ ] `/provider-api/:path*` → `localhost:8095/api/:path*` (provider)
- [ ] `/collector-api/:path*` → `localhost:8084/api/:path*` (collector)
- [ ] cookie 자동 전송 확인 (withCredentials)

### 7-5. useCurrentUser hook
- [ ] 로그인 직후 헤더 사용자 이름 즉시 반영 (5/6 stale 이슈 fix — `mutate()` 호출)
- [ ] 로그아웃 후 헤더 → "로그인" 버튼

---

## 8. 검증자 모듈 회귀

### 8-1. backend (8080)
- [ ] cookie 정상 → `GET /api/agents` → 200 + agent 목록
- [ ] cookie 정상 → `GET /api/datasources` → 200
- [ ] cookie 정상 → `GET /api/executions` → 200

### 8-2. provider (8095)
- [ ] cookie 정상 → `GET /api/manage/operations` → 200 + operation 목록 (B4 id=36 포함)
- [ ] **B4 핸들러 회귀**:
  ```bash
  curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
    -X POST http://localhost:8095/api/manage/operations/36/test \
    -H "Content-Type: application/json" \
    -d '{"params":{"rel_trans_cgg_code":"3030000"}}'
  ```
  - HTTP 200 + 13 컬럼 정합 (permNtFormCode/addr/uwaterSrvCode/digDph 등)
- [ ] 외부 사용자 흐름 (Provide API Key) 도 정상 — `?apiKey=...&rel_trans_cgg_code=...`

### 8-3. collector (8084)
- [ ] cookie 정상 → `GET /api/endpoints` → 200 + endpoint 목록
- [ ] cookie 정상 → `GET /api/operations` → 200
- [ ] Mock LOOKUP 자기호출: `GET /mock/common/select/NGW_0001` → 200 (인증 무관, permitAll)

### 8-4. callback path (Backend 의 `/api/callback/**`)
- [ ] permitAll 정합: `GET /api/callback/started` → 405 (method not allowed, 인증 통과)
- [ ] **별 사이클 보강 예정** (5/6 auth-integration-matrix §4.2): callback 도 X-API-Key 강제 검토

---

## 9. 알려진 허점 / 주의사항

### 9-1. 운영자 흐름 vs 시스템 흐름의 endpoint 분리 (5/6 §9.3.1)
- Backend `/api/datasources/*/connection-info` 가 처음부터 무인증 — 5/6 ApiKeyFilter soft-mode + SecurityFilterChain 등록으로 봉합. 본 테스트로 회귀 점검.

### 9-2. callback path X-API-Key 강제 (별 사이클)
- 현재 `/api/callback/**` permitAll. Agent → Backend 콜백이 인증 없이 들어옴. 외부 노출 X 라 운영 영향 적지만 별 보강 필요.

### 9-3. UserServiceTest 환경 의존 (5/6 발견)
- `deleteMe_blocked_when_only_one_user` 테스트가 실 DB admin 살아있을 때 count=2 로 보여 마지막 1명 차단 검증 안 걸림. `@Sql` 또는 `@DirtiesContext` 격리 필요. **단위 테스트 별 사이클**, 본 통합 테스트 영역 X.

### 9-4. 폐쇄망 운영 yml override 패턴 (별 사이클)
- `mock.api.enabled=false`, `mock.api-key.enabled=false`, JWKS URL 운영 호스트 등. 본 dev 환경 검증 후 운영 yml 점검 필요.

### 9-5. 첫 로그인 후 헤더 stale (5/6 fix 회귀)
- `useCurrentUser` 의 `cached=null, fetched=true` 박힘 → 로그인 성공 후 mutate() 미호출 시 stale. 5/6 `app/login/page.tsx` 의 `await mutate(); router.replace(); router.refresh();` 패턴 보존.

### 9-6. middleware 가 통제 못하는 영역
- middleware = path 기반 1차 가드. 실제 인증 검증은 backend Filter. middleware 우회 시 (cookie 위조 등) 결국 backend 에서 401.

