# Auth — JWT 기반 통합 로그인 시스템 구축 계획

> 작성일: 2026-04-28
> 범위: orchestrator 시스템 전체 (Backend / Frontend / api-provider / api-collector / agents) 에 통합 인증 적용
> 분류: 보안 — 운영자 인증 누락 (현재 누구나 접근 가능)
> 결과물: 신규 마이크로서비스 `sync-orchestrator-auth` (port 8096) + 기존 모든 모듈 보호

---

## 1. 배경 / 목적

### 1.1 문제
- orchestrator 시스템 전체가 운영자 인증 없이 모든 운영 endpoint 접근 가능
- 운영자 화면 (Frontend 3000) 도 로그인 없이 진입
- 기존 API key 는 호출자 식별/추적용 보안 레이어 — **운영자 사용자 인증은 별개 레이어**, 본 작업으로 추가 (2중 보안)
- 중요도 높은 시스템 (DB 등록/Agent 제어/Operation 관리/외부 API 호출 이력) — 보호 필수

### 1.2 정책
> orchestrator 시스템의 **운영자가 직접 호출하는 endpoint** 는 로그인 전제.
> 시스템 간 통신 (Backend↔Agent, Backend↔Proxy) 은 기존 **API key 그대로 유지** — Agent / Proxy 는 JWT 인증 미적용.
>
> 즉 운영자가 Frontend → Backend → (Agent/Proxy) 를 거치는 흐름에서, JWT 는 사용자 자격 영역 (Frontend/Backend/api-provider 운영 endpoint/api-collector 운영 endpoint) 까지만 검증. 그 너머의 시스템 간 호출은 API key.

### 1.3 보안 요소 다원화 + endpoint 별 분리
- 두 보안 레이어가 **다른 저장소에 분산** — 보안 다원화
  - **API key** — 호출자 식별 / yml ENC 등 시스템 메타에 저장
  - **사용자 자격증명 + JWT** — PG `auth_users` 등 별도 저장소 + Spring Security 처리
- endpoint 별 적용은 **분리** (둘이 동시에 검증되는 endpoint 없음):
  - `api-provider` 의 `/api/provide/{operationId}` 같은 **외부 사용자 endpoint = API key** (v3 호환)
  - 운영자가 호출하는 `/api/manage/**` 등 = **JWT** (본 작업)

---

## 2. 결정 정리 (전체)

| 항목 | 결정 |
|---|---|
| **인증 모듈 위치** | 별도 마이크로서비스 `sync-orchestrator-auth` |
| **포트** | 8096 (api-provider 8095 옆) |
| **알고리즘** | RS256 (RSA 비대칭) |
| **키 회전** | 매일 자정 (자동) |
| **이전 키 보관** | 8일치 (refresh 7일 + 1일 여유) |
| **토큰 저장 (Frontend)** | httpOnly cookie (**sameSite=Strict**, secure 운영) |
| **Refresh Token** | 사용 + 회전 (1회용) |
| **사용자 DB** | Orchestrator PG (`orchestrator` DB) 같은 곳에 **새 테이블 3종** (`auth_users` / `auth_refresh_tokens` / `auth_rsa_keys`) |
| **권한 모델** | **단일 role = `user`** (구분 없음, 누구든 로그인하면 동일 권한) |
| **최초 1명 발급** | **Java CLI 생성기** (UserGeneratorCli) — 운영 배포 직후 개발자가 DB 직접 INSERT (1회) + 비상 진입로 |
| **이후 사용자 증식 (Peer Multiplication)** | **로그인 사용자 누구나 운영 화면에서 동급 계정 추가** — 등록자가 ID/PW/이름 모두 입력해 INSERT (`POST /api/auth/users`) → 별도 채널로 전달 → 받는 사람이 본인 비번 변경 |
| **사용자 목록** | 로그인 사용자 누구나 조회 (`GET /api/auth/users` — id/username/name/createdAt) |
| **사용자 정보 필드** | `username`(=ID) / `password_hash` / **`name`** (담당자 이름) / `created_at` |
| **등록자 추적** | ❌ 미저장 (`registered_by` 컬럼 없음) |
| **본인 비밀번호 변경** | 본인만 (`PATCH /api/auth/users/me/password` — current 검증 + 모든 refresh revoke) |
| **본인 탈퇴** | 본인만 (`DELETE /api/auth/users/me`) |
| **마지막 1명 탈퇴 차단** | 사용자 count >= 2 일 때만 본인 탈퇴 허용 (운영자 0명 방지) |
| **타인 정보 수정** | ❌ 불가 — endpoint 자체 없음 |
| **Redis** | 미사용 — PG 만 |
| **외부 IDP 통합** | ❌ — Google/Naver/카카오 등 외부 OAuth IDP 미사용 (Spring OAuth2 Client 라이브러리 미사용) |
| **JWT 인프라 라이브러리** | **Spring Security 5 + jjwt 0.11.5 + Nimbus JOSE 9.x + BCrypt** — Spring OAuth2 starter (Authorization Server / Resource Server) **미사용** (Spring Boot 2.7.12 호환 위해 자체 구현) |
| **Spring Boot 버전** | **2.7.12 고정** — Spring Authorization Server 1.x (Boot 3.x 필요) 사용 불가, jjwt + Nimbus 자체 조합으로 대체 |
| **외부 네트워크 의존성** | 0 — 폐쇄망 운영 가능 (JWKS 도 내부망) |
| **CSRF** | **미사용 — SameSite=Strict 로 대체** (외부 링크 진입 시 자연스럽게 재로그인) |

---

## 3. 영향 범위 (모듈별)

| 모듈 | 포트 | 인증 적용 |
|---|:--:|---|
| **`sync-orchestrator-auth` (신규)** | **8096** | 로그인 / refresh / 로그아웃 / JWKS endpoint 제공 |
| **`sync-orchestrator/backend`** | 8080 | 모든 운영 REST API JWT 필수 (resource server) |
| **`sync-orchestrator/frontend`** | 3000 | 로그인 페이지 + 미인증 redirect + axios cookie 자동 전송 |
| **`gims-api-provider`** | 8095 | `/api/manage/**` JWT 필수 / `/api/provide/**` API key 유지 |
| **`infolink-api-collector`** | 8084/8094 | 운영 endpoint JWT 필수 |
| **`sync-agent-bojo` / `bojo-int` / `others`** | 8082/8092/8085 | **JWT 미적용** — 시스템 간 통신, 기존 API key 그대로 |
| **`sync-proxy-dmz` / `internal`** | 8083/8093 | **JWT 미적용** — 기존 X-API-Key 그대로 |

---

## 4. 관리 대상 14가지 매트릭스

| # | 대상 | 형태 | 저장 위치 | 누가 보유 |
|:--:|---|---|---|---|
| 1 | 사용자 자격증명 (username, password_hash) | row | PG `auth_users` | auth 모듈만 |
| 2 | 사용자 메타 (role, last_login, fail_count, locked_until) | row | PG `auth_users` 컬럼 | auth 모듈만 |
| 3 | RSA 개인키 (kid 별 PEM, jasypt ENC) | row | PG `auth_rsa_keys.private_pem_enc` | auth 모듈만 |
| 4 | RSA 공개키 (kid 별 PEM) | row | PG `auth_rsa_keys.public_pem` | auth 보유 + 다른 모듈은 JWKS fetch |
| 5 | kid + 키 메타 (active, expires_at) | row | PG `auth_rsa_keys` 컬럼 | auth 모듈만 |
| 6 | Access Token (JWT) | JWT 문자열 | 클라이언트 httpOnly cookie | 검증 모듈 stateless 검증 |
| 7 | Refresh Token jti + 메타 | row | PG `auth_refresh_tokens` | auth 모듈만 |
| 8 | Refresh Token 자체 | jti 또는 짧은 JWT | 클라이언트 httpOnly cookie (path=/api/auth) | auth 모듈만 검증 |
| 9 | JWT Claim (sub/iat/exp/jti/role/iss/aud) | JSON | 토큰 자체 (서버 저장 X) | 모두 검증 가능 |
| 10 | JWKS (공개키 묶음) | JSON | auth `/.well-known/jwks.json` + 다른 모듈 메모리 캐시 (5분 TTL) | 발급=auth, 캐시=검증자 |
| 11 | CSRF token | **미사용 — SameSite=Strict 로 대체** | cookie 속성만 | — |
| 12 | (blacklist) | — | **미사용** | — |
| 13 | 인프라 시크릿 (DB pw, jasypt pw) | string | application.yml ENC + 환경변수 | 다른 모듈과 동일 패턴 |
| 14 | 첫 admin 초기 비번 | string | 환경변수 → 부팅 시 BCrypt → PG | 1회성 |
| 15 | 사용자 추가 입력 (username/PW/name) | form | UI → POST /api/auth/users → PG | 등록자가 별도 채널로 받는 사람에게 전달 |

---

## 5. 라이프사이클 — 단계별 상세

### 5.1 부팅 / 초기화 (auth 모듈)

```
[Auth 부팅]
  ① jasypt password 로드 (env JASYPT_PASSWORD)
  ② DB 연결 (PG ENC 복호화)
  ③ schema 검사 + ddl-auto=update 또는 별도 migration
  ④ active RSA 키 row 조회 (PG auth_rsa_keys)
     - 있음: PEM 로드 + 캐시
     - 없음: 새 페어 생성 + INSERT (kid=UUID, expires_at=오늘+8일, active=true)
  ⑤ 사용자 row 부팅 시 자동 생성 안 함
     - **최초 1명** = Java CLI 생성기 (§6.4) 로 개발자가 수동 INSERT (운영 배포 직후 1회)
     - **이후 증식** = 로그인 사용자가 운영 화면에서 새 사용자 추가 (§5.7)
     - 첫 사용자가 없으면 로그인 자체가 불가 — 운영 시 사전 발급 필수
  ⑥ 회전 스케줄러 시작 (Spring @Scheduled, cron "0 0 0 * * ?")
  ⑦ JWKS endpoint 활성화
```

### 5.2 로그인 — `POST /api/auth/login`

```
요청: { "username": "admin", "password": "..." }

[Auth]
  ① PG SELECT auth_users WHERE username=?
     not found → 401 (timing safe — bcrypt 더미 1회 실행)
     locked_until > NOW() → 423 Locked
  ② BCrypt.matches(password, hash)
     실패 → fail_count++, fail_count >= 5 → locked_until = NOW + 30min
            → 401
  ③ 성공:
     - fail_count = 0, locked_until = null, last_login_at = NOW UPDATE
     - active RSA 개인키 (PG) + kid 로드
     - access JWT 생성 (15min):
         header: { alg: RS256, typ: JWT, kid: <K-2026-04-28> }
         claim:  { sub: userId, role: user,
                   jti: <UUID>, iat: <epoch>, exp: <iat+15min>,
                   iss: orchestrator-auth, aud: orchestrator }
         signature: RSA-PSS sign with private key
     - refresh jti = UUID
       PG INSERT auth_refresh_tokens (jti, user_id, expires_at=7day, revoked=false)
     - refresh JWT 생성 (7day, sub=userId, jti=jti, type=refresh)
       또는 opaque uuid 그대로 cookie (구현 단순성에 따라)
  ④ 응답:
     Set-Cookie: accessToken=<JWT>; HttpOnly; Secure; SameSite=Strict; Max-Age=900;            Path=/
     Set-Cookie: refreshToken=<JWT>; HttpOnly; Secure; SameSite=Strict; Max-Age=604800;        Path=/api/auth
     Body: { "user": { "id": ..., "username": ..., "role": "admin" } }
```

### 5.3 인증된 API 호출 (검증자 = Backend / api-provider 등)

```
[Browser]
  GET /api/operations
  Cookie: accessToken=eyJhbGc... (자동 전송)

[Backend Filter (Spring Security OAuth2 Resource Server)]
  ① cookie 에서 accessToken 추출 (BearerTokenResolver 커스텀 — header 대신 cookie)
  ② 토큰 헤더 파싱 → kid 추출
  ③ JWKS 캐시에서 kid 매칭 공개키 조회
     - cache miss → auth /.well-known/jwks.json fetch (5min TTL)
  ④ 공개키로 서명 검증
  ⑤ claim 검증:
     - exp > NOW
     - iss = "orchestrator-auth"
     - aud = "orchestrator"
  ⑥ SecurityContext.setAuthentication(user, role)

[Controller] @PreAuthorize("hasRole('ADMIN')") 또는 SecurityConfig 의 antMatchers 로 보호
```

> **모든 검증은 stateless** (PG/Redis 안 보고 키 + claim 만으로). 빠르고 단순.

### 5.4 Access Token 만료 → Refresh — `POST /api/auth/refresh`

```
[Browser]
  axios interceptor: 응답 401 받으면 자동 호출
  POST /api/auth/refresh
  Cookie: refreshToken=... (path=/api/auth 자동 전송)

[Auth]
  ① cookie 에서 refreshToken 추출
  ② JWT 검증 (서명 + exp + type=refresh) — RSA 검증
  ③ jti 추출 → PG SELECT auth_refresh_tokens WHERE jti=? AND NOT revoked
     not found / revoked / expired → 401
  ④ 회전:
     - 기존 jti revoked=true UPDATE
     - 새 jti UUID 생성 + INSERT (expires_at = 원본의 expires_at, 또는 새로 7일 — 정책 결정)
       → 정책: "rolling" 모드 (새 만료 = NOW+7day) 권장 (운영자 활동 시 무한 갱신)
  ⑤ 새 access token + 새 refresh token 발급 (5.2 ③ 동일)
  ⑥ 응답: 새 cookie 2개 (덮어쓰기)
```

### 5.5 로그아웃 — `POST /api/auth/logout`

```
[Browser]
  POST /api/auth/logout
  Cookie: accessToken + refreshToken (자동 전송)

[Auth]
  ① cookie 에서 refresh jti 추출
  ② PG UPDATE auth_refresh_tokens SET revoked=true WHERE jti=?
  ③ 응답: Set-Cookie: accessToken=; Max-Age=0
              Set-Cookie: refreshToken=; Max-Age=0; Path=/api/auth

> access token 자체는 만료(15min)까지 유효 — blacklist 없음 정책
> refresh 차단되어 갱신 불가 → 사실상 종료
```

### 5.6 RSA 키 회전 — 자정 스케줄러 (auth 모듈)

```
[Auth Scheduler] cron "0 0 0 * * ?"
  ① 새 RSA 페어 생성 (kid=UUID, RSA 2048bit)
  ② PG:
     - 기존 active 키들 → active=false UPDATE (검증만 가능)
     - 새 키 INSERT (active=true, expires_at=오늘+8일)
  ③ Cleanup: DELETE WHERE expires_at < NOW() (8일 지난 거)
  ④ 메모리 캐시 갱신 (auth 자체 + JWKS 응답 갱신)

[다른 모듈 JWKS 캐시] 5min TTL 후 자동 fetch → 새 kid 인식 (지연 허용)
```

### 5.7 사용자 추가 (증식) — `POST /api/auth/users`

```
요청 (인증된 사용자가 호출):
  Cookie: accessToken=...
  Body: { "username": "alice", "password": "...", "name": "홍길동" }

[Auth]
  ① JWT 검증 (인증된 호출자 확인)
  ② input 검증 (username 중복, password 정책, name 비어있지 않음)
  ③ BCrypt.hashpw(password, gensalt(12))
  ④ INSERT INTO auth_users (username, password_hash, name, role='user', ...)
     UNIQUE 충돌 → 409
  ⑤ 응답: { "id": ..., "username": ..., "name": ..., "createdAt": ... }
     (등록자 정보는 저장 안 함 — registered_by 컬럼 없음)
```

> 등록자가 받는 사람에게 **별도 채널** (메일/메신저 등) 로 username/password/name 전달.
> 받는 사람은 로그인 후 본인이 비번 변경 (§5.9) 가능.

### 5.8 본인 탈퇴 — `DELETE /api/auth/users/me`

```
요청: Cookie: accessToken=... (본인 식별 = JWT sub)

[Auth]
  ① JWT 검증 → userId 추출
  ② SELECT COUNT(*) FROM auth_users
     count <= 1 → 409 Conflict ("마지막 사용자는 탈퇴 불가, 운영자 0명 방지")
  ③ 본인의 모든 refresh_token revoked=true UPDATE (다른 디바이스 강제 로그아웃)
  ④ DELETE FROM auth_users WHERE id=?
  ⑤ 응답: 204 No Content + Set-Cookie: accessToken/refreshToken Max-Age=0 (cookie 만료)
```

### 5.9 본인 비밀번호 변경 — `PATCH /api/auth/users/me/password`

```
요청:
  Cookie: accessToken=...
  Body: { "currentPassword": "...", "newPassword": "..." }

[Auth]
  ① JWT 검증 → userId 추출
  ② SELECT password_hash FROM auth_users WHERE id=?
  ③ BCrypt.matches(currentPassword, hash)
     실패 → 400 ("현재 비밀번호 불일치")
  ④ password 정책 검증 (newPassword)
  ⑤ BCrypt.hashpw(newPassword) → password_hash UPDATE
  ⑥ 본인의 모든 refresh_token revoked=true UPDATE (탈취 cookie 무력화 + 다른 디바이스 강제 로그아웃)
  ⑦ 응답: 204 (현재 cookie 는 만료까지 유효 — refresh 시도 시 401)
```

### 5.10 사용자 목록 조회 — `GET /api/auth/users`

```
요청: Cookie: accessToken=...

[Auth]
  ① JWT 검증
  ② SELECT id, username, name, created_at FROM auth_users ORDER BY created_at
  ③ 응답: [{ id, username, name, createdAt }, ...]
```

> password_hash, fail_count, locked_until 등 민감/내부 메타는 응답 제외.

### 5.11 JWKS endpoint — `GET /.well-known/jwks.json` (auth 모듈, 인증 불필요)

#### 5.11.1 JWKS 가 무엇인가
- **JWKS (JSON Web Key Set)** = RFC 7517 표준 — 공개키 묶음을 JSON 으로 노출하는 endpoint
- **`/.well-known/`** = RFC 8615 의 "잘 알려진 URI" 표준 path — OAuth/OpenID 등 표준 기능이 약속된 위치에 있게 하는 관행

#### 5.11.2 왜 필요한가 — 정적 배포 vs JWKS 비교
| 대안 | 회전 대응 |
|---|---|
| 공개키를 yml 에 정적 박음 | 회전 시 모든 모듈 재배포 |
| 공개키를 파일로 공유 | 회전 시 모든 모듈에 파일 복사 |
| **JWKS endpoint** | **검증자가 동적 fetch + TTL 캐시 → 회전 자동 반영** |

→ 우리는 **자정 회전** 결정했으므로 JWKS 가 사실상 필수.

#### 5.11.3 흐름
```
[auth 모듈]                    [검증자 (Backend / api-provider 등)]
 개인키로 토큰 서명               GET /.well-known/jwks.json (5min 캐시)
   토큰 헤더에 kid 박음                ↓
       ↓                          공개키 묶음 응답
       └────── 토큰 ─────────→  토큰 헤더 kid → 매칭 공개키 → 서명 검증
```

#### 5.11.4 응답 형태
```
응답:
{
  "keys": [
    {
      "kid": "K-2026-04-28-uuid",  // 토큰 헤더 kid 와 매칭
      "kty": "RSA",                 // 키 타입
      "use": "sig",                 // 서명용
      "alg": "RS256",               // 알고리즘
      "n": "<modulus base64url>",   // RSA modulus
      "e": "AQAB"                   // RSA exponent
    },
    {
      "kid": "K-2026-04-27-uuid",   // 만료 안 된 이전 키들도 포함
      ...
    }
  ]
}
```

#### 5.11.5 노출 정책
- **active=true** (현재 발급용) — 1개 row + 응답에 포함
- **active=false 면서 expires_at > NOW** (이전 키들) — 응답에 포함 (검증용)
- **expires_at < NOW** — 응답 제외 (cleanup 대상)

#### 5.11.6 Spring 검증자 자동 처리
검증자 모듈은 yml 한 줄로 자동 fetch + 캐시 + 서명 검증:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8096/.well-known/jwks.json
```
→ 코드 추가 없이 회전 자동 반영 (캐시 TTL 5분).

---

## 6. 신규 모듈 구조 — `sync-orchestrator-auth`

### 6.1 패키지 구조

```
sync-orchestrator-auth/
├── build.gradle
└── src/main/
    ├── java/com/gims/auth/
    │   ├── AuthApplication.java
    │   ├── config/
    │   │   ├── SecurityConfig.java         (Spring Security 5 — login endpoint permit, 나머지 자체 처리)
    │   │   ├── JasyptConfig.java
    │   │   └── SchedulerConfig.java        (@EnableScheduling)
    │   ├── controller/
    │   │   ├── AuthController.java         (login/refresh/logout/me)
    │   │   ├── UserController.java         (사용자 CRUD — POST /users / GET /users / DELETE /users/me / PATCH /users/me/password)
    │   │   └── JwksController.java         (.well-known/jwks.json — Nimbus 로 JWK JSON 직렬화)
    │   ├── service/
    │   │   ├── AuthService.java            (로그인/refresh 비즈니스)
    │   │   ├── KeyService.java             (RSA 페어 로드/생성/회전 — KeyPairGenerator 직접 사용)
    │   │   ├── TokenService.java           (JWT 발급/검증 — jjwt 직접 사용)
    │   │   └── UserService.java            (사용자 CRUD — addUser / listUsers / deleteMe (count 체크) / changeMyPassword (refresh revoke))
    │   ├── entity/
    │   │   ├── AuthUser.java               (auth_users — name 컬럼 포함)
    │   │   ├── AuthRefreshToken.java       (auth_refresh_tokens)
    │   │   └── AuthRsaKey.java             (auth_rsa_keys)
    │   ├── repository/
    │   │   ├── AuthUserRepository.java
    │   │   ├── AuthRefreshTokenRepository.java
    │   │   └── AuthRsaKeyRepository.java
    │   ├── dto/
    │   │   ├── LoginRequest.java
    │   │   ├── LoginResponse.java
    │   │   ├── AddUserRequest.java
    │   │   ├── ChangePasswordRequest.java
    │   │   └── UserDto.java
    │   ├── scheduler/
    │   │   ├── KeyRotationJob.java         (자정 회전 — 새 페어 생성 + INSERT + 만료 cleanup)
    │   │   └── KeyCleanupJob.java          (만료 키 폐기, 회전 시 통합도 가능)
    │   ├── bootstrap/
    │   │   └── InitialKeyLoader.java       (첫 RSA 키 생성 — 사용자는 CLI 별도)
    │   └── tools/
    │       └── UserGeneratorCli.java       (별도 main — BCrypt + JDBC 직접 INSERT, 최초 1명/비상 진입로)
    └── resources/
        └── application.yml
```

> 참고 — 검증자 측 (`sync-agent-common` 안 신규):
> ```
> sync-agent-common/.../config/
> ├── ApiKeyFilter.java                (기존 — 시스템 간 X-API-Key 인증, 그대로)
> └── JwtCookieAuthFilter.java         (신규 — 운영자 JWT 인증, OncePerRequestFilter)
>
> sync-agent-common/.../client/
> └── JwksClient.java                  (신규 — JWKS fetch + 5min 캐시 + retry 2회)
> ```
> 검증자 모듈 (Backend, api-provider, api-collector) 은 기존 `sync-agent-common` 의존 추가하고 SecurityConfig 작성.

### 6.4 UserGeneratorCli — **최초 1명** 발급 전용 도구 (별도 main)

> 운영 배포 직후 **최초 1명** 발급 전용. 그 이후 운영자 추가는 운영 화면(§5.7) 으로 진행.
> CLI 는 사용자 0명 상황(시스템 부팅 직후 / 마지막 사용자가 어떤 이유로 사라짐) 의 비상 진입로 역할도 겸함.

```java
// 실행: ./gradlew createUser --args="<username> <password> <name>"
// 또는: java -cp <fat-jar> com.gims.auth.tools.UserGeneratorCli <username> <password> <name>

public class UserGeneratorCli {
    public static void main(String[] args) {
        // 1. 인자 검증 (username, password, name)
        // 2. PG 직접 connection (env JDBC_URL/USER/PW 또는 yml 의 DB 정보 jasypt 복호화)
        // 3. BCrypt.hashpw(password, BCrypt.gensalt(12))
        // 4. INSERT INTO auth_users (username, password_hash, name, role, created_at) VALUES (?, ?, ?, 'user', NOW())
        // 5. 중복 시 ERROR / 성공 시 stdout (id 반환)
    }
}
```

특징:
- Spring context 안 띄움 — 단독 실행 (가볍고 빠름)
- BCrypt + Postgres JDBC + jasypt-core 만 사용
- gradle 별도 task 또는 fat jar
- 운영 환경에서도 동일 도구로 발급
- 비밀번호는 인자 평문 → 운영 시 환경변수 prompt 모드 옵션 (`--prompt`) 도 검토


### 6.2 build.gradle 의존성

```gradle
// Spring Boot 2.7.12 (다른 모듈과 통일)
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'   // Spring Security 5 (필터/BCrypt/SecurityContext)
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // ⚠️ Spring OAuth2 starter 사용 안 함 (Spring Boot 2.7.12 호환 한계)
    // - oauth2-authorization-server 1.x = Boot 3.x 필요
    // - oauth2-resource-server = 가능하지만 일관성 위해 미사용

    // JWT — 자체 구현
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'           // JWT 발급/검증
    runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.11.5'
    implementation 'com.nimbusds:nimbus-jose-jwt:9.37'         // JWKS 응답 변환 헬퍼

    // jasypt
    implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'

    // PG
    runtimeOnly 'org.postgresql:postgresql'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### 6.3 application.yml (jasypt ENC 적용)

```yaml
server:
  port: 8096

spring:
  application:
    name: sync-orchestrator-auth
  datasource:
    url: ENC(...)        # jdbc:postgresql://localhost:29001/orchestrator
    username: ENC(...)
    password: ENC(...)
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL95Dialect

jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
    algorithm: PBEWithHMACSHA512AndAES_256
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator

auth:
  access-token-ttl-minutes: 15
  refresh-token-ttl-days: 7
  rsa:
    key-size: 2048
    rotation-cron: "0 0 0 * * ?"      # 자정
    retention-days: 8                 # 만료 후 폐기 시점
  # 사용자 발급은 부팅 시 자동 생성 X — UserGeneratorCli (§6.4) 로 수동 발급
  cookie:
    same-site: Strict
    secure: ${AUTH_COOKIE_SECURE:false}           # 운영=true (HTTPS)
```

---

## 7. DB 스키마 (Orchestrator PG `orchestrator` DB)

> **생성 메커니즘**: JPA `ddl-auto: update` 가 entity 기반 자동 생성 / 변경. **별도 SQL DDL 실행 X**.
> 아래 SQL 은 entity 와 동등한 **참고용 표현** (DBA 검토 / 마이그 논의 시 활용).

### 7.1 새 테이블 3종

```sql
CREATE TABLE auth_users (
  id              BIGSERIAL PRIMARY KEY,
  username        VARCHAR(50)  UNIQUE NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,
  name            VARCHAR(50)  NOT NULL,            -- 담당자 이름 (UI 노출용)
  role            VARCHAR(20)  NOT NULL DEFAULT 'user',
  last_login_at   TIMESTAMP,
  fail_count      INT          NOT NULL DEFAULT 0,
  locked_until    TIMESTAMP,
  created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP
);

CREATE TABLE auth_refresh_tokens (
  jti             VARCHAR(64)  PRIMARY KEY,
  user_id         BIGINT       NOT NULL REFERENCES auth_users(id),
  expires_at      TIMESTAMP    NOT NULL,
  revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_auth_refresh_user ON auth_refresh_tokens(user_id);
CREATE INDEX idx_auth_refresh_expires ON auth_refresh_tokens(expires_at);

CREATE TABLE auth_rsa_keys (
  kid              VARCHAR(64)  PRIMARY KEY,
  public_pem       TEXT         NOT NULL,
  private_pem_enc  TEXT         NOT NULL,    -- jasypt ENC(...)
  active           BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
  expires_at       TIMESTAMP    NOT NULL
);
CREATE INDEX idx_auth_rsa_active ON auth_rsa_keys(active) WHERE active = TRUE;
CREATE INDEX idx_auth_rsa_expires ON auth_rsa_keys(expires_at);

-- (옵션) — 사용 안 함 결정. 향후 즉시 로그아웃 강제 필요 시
-- CREATE TABLE auth_blacklist (...)
```

### 7.2 인덱스 / 제약

- `auth_users.username` UNIQUE (로그인 lookup)
- `auth_refresh_tokens(user_id)` (사용자별 토큰 조회)
- `auth_refresh_tokens(expires_at)` (cleanup job)
- `auth_rsa_keys(active=true)` partial — 활성 키 빠른 조회
- `auth_rsa_keys(expires_at)` (cleanup)

---

## 8. API 명세 (auth 모듈)

| Method | Path | 인증 | 역할 |
|---|---|---|---|
| `POST` | `/api/auth/login` | 불필요 | 로그인 |
| `POST` | `/api/auth/refresh` | refresh cookie | access 갱신 |
| `POST` | `/api/auth/logout` | refresh cookie | 로그아웃 (revoke) |
| `GET`  | `/api/auth/me` | access cookie | 현재 사용자 조회 |
| `POST` | `/api/auth/users` | access cookie | **새 사용자 추가** (증식, ID/PW/이름 입력) |
| `GET`  | `/api/auth/users` | access cookie | **사용자 목록** (id/username/name/createdAt) |
| `DELETE` | `/api/auth/users/me` | access cookie | **본인 탈퇴** (마지막 1명이면 409) |
| `PATCH` | `/api/auth/users/me/password` | access cookie | **본인 비번 변경** (current+new) |
| `GET`  | `/.well-known/jwks.json` | 불필요 | JWKS 공개키 조회 |
| `GET`  | `/actuator/health` | 불필요 | 헬스체크 |

### 8.1 요청/응답 예시

**POST /api/auth/login**
```json
요청: { "username": "admin", "password": "..." }
응답: { "user": { "id": 1, "username": "admin", "role": "admin" } }
Set-Cookie: accessToken=...; HttpOnly; ...
Set-Cookie: refreshToken=...; HttpOnly; Path=/api/auth; ...
```

**POST /api/auth/refresh**
```
요청: (body 없음, refresh cookie 자동 전송)
응답: 200 OK + 새 cookie 2개 / 또는 401
```

**GET /api/auth/me**
```json
응답: { "id": 1, "username": "admin", "name": "운영자", "role": "user" }
```

**POST /api/auth/users** — 새 사용자 추가 (증식)
```json
요청: { "username": "alice", "password": "...", "name": "홍길동" }
응답: { "id": 2, "username": "alice", "name": "홍길동", "createdAt": "2026-05-04T..." }
       409 — username 중복
       400 — password 정책 위반 / name 빈 값
```

**GET /api/auth/users** — 사용자 목록
```json
응답: [
  { "id": 1, "username": "admin", "name": "운영자", "createdAt": "..." },
  { "id": 2, "username": "alice", "name": "홍길동", "createdAt": "..." }
]
```

**DELETE /api/auth/users/me** — 본인 탈퇴
```
요청: (body 없음, accessToken cookie)
응답: 204 No Content + cookie 만료
       409 — 마지막 1명 (count <= 1)
```

**PATCH /api/auth/users/me/password** — 본인 비번 변경
```json
요청: { "currentPassword": "...", "newPassword": "..." }
응답: 204 No Content (refresh token 전체 revoke — 다른 디바이스 강제 로그아웃)
       400 — currentPassword 불일치 / newPassword 정책 위반
```

---

## 9. 다른 모듈 통합 (검증자)

### 9.1 공통 변경사항

각 모듈의 `application.yml` 에 OAuth2 Resource Server 설정 추가:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8096/.well-known/jwks.json
          issuer-uri: orchestrator-auth          # iss 검증
```

`build.gradle` (검증자 측 — Backend / api-provider / api-collector 모두 동일):
```gradle
// Spring Boot 2.7.12 — Spring Security 5 만 사용 (oauth2-resource-server 미사용)
implementation 'org.springframework.boot:spring-boot-starter-security'

// JWT 검증 (auth 모듈과 같은 jjwt 사용)
implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.11.5'
runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.11.5'

// sync-agent-common 의 JwtCookieAuthFilter / JwksClient 사용
implementation project(':sync-agent-common')   // 이미 의존하고 있을 수 있음
```

`SecurityConfig.java` (검증자 측 — 자체 필터 등록):
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtCookieAuthFilter jwtCookieAuthFilter;   // sync-agent-common 의 신규 필터

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/provide/**").permitAll()       // api-provider 만 — 외부 사용자
                .requestMatchers("/api/callback/**").permitAll()      // Backend 만 — Agent 콜백
                .requestMatchers("/api/mock/**", "/mock/**").denyAll() // mock — 운영 차단 (이중 방어)
                .anyRequest().authenticated()
            )
            // ⚠️ oauth2ResourceServer() 미사용 (Spring Boot 2.7 + Authorization Server 1.x 호환 불가)
            // 자체 OncePerRequestFilter 로 cookie 추출 + jjwt 검증
            .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .csrf(csrf -> csrf.disable())   // SameSite=Strict 로 대체 (§13.5)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }
}
```

> **모듈별 path 차이**:
> - **Backend**: `/api/callback/**` permitAll 라인만 의미 있음 (다른 모듈엔 없음)
> - **api-provider**: `/api/provide/**` permitAll 라인만 의미 있음
> - **api-collector**: 위 라인들 모두 무관 (`/api/**` 인증 + `/mock/**` denyAll)
>
> 동일 SecurityConfig 골격에 모듈별 path 만 활성화. 자세한 매트릭스는 §9.3 참조.

### 9.2 모듈별 보호 endpoint

| 모듈 | 보호 (JWT 필수) | 미보호 (현재 그대로) |
|---|---|---|
| Backend (8080) | 모든 `/api/**` | `/actuator/health` |
| api-provider (8095) | `/api/manage/**` | `/api/provide/**`, `/.well-known/...` |
| api-collector (8084/8094) | 모든 운영 endpoint | `/actuator/health` |
| Agent bojo / bojo-int / others | (Orchestrator 가 호출하는 internal endpoint — JWT 불필요?) | 운영자 호출 endpoint 만 보호 |

> Agent endpoint 는 대부분 Orchestrator-internal — Orchestrator 자체가 인증되면 충분. 별도 검토 필요.

### 9.3 검증자 3개 모듈 — 실제 endpoint 매핑 (2026-05-04 조사)

#### 9.3.1 sync-orchestrator/backend (8080) — 단순

전부 `/api/**` prefix. 운영자(Frontend) 호출 받음.

| Controller | Prefix | 호출자 | JWT |
|---|---|---|:--:|
| `AgentController` | `/api/agents` | 운영자 | ✅ |
| `DatasourceController` | `/api/datasources` | 운영자 | ✅ |
| `ExecutionController` | `/api/executions` | 운영자 | ✅ |
| `ScheduleController` | `/api/schedules` | 운영자 | ✅ |
| **`CallbackController`** | **`/api/callback`** | **Agent → Backend (시스템 간!)** | **❓ 검토 필수** |
| `/actuator/health` | — | 모든 호출 | permit |

✅ **이슈 1 결정 (2026-05-04)** — `/api/callback/**` = 시스템 간 호출 (Agent → Backend), JWT 적용 제외.

**확인된 흐름**:
- 호출자: **Agent (`sync-agent-common/OrchestratorClient`)**
- endpoint: `POST /api/callback/started`, `POST /api/callback/finished` (파이프라인 실행 시작/완료 알림)
- 본질: 운영자 호출 아닌 **시스템 간 콜백**

**본 작업 처리**: Spring Security 에서 `permitAll` (JWT 보호 제외)

**시스템 간 인증 강화 (Backend 에 ApiKeyFilter 도입 등) = 본 작업 범위 외 / 별 후속 작업으로 분리**.

```java
// Backend 의 SecurityConfig
http
  .authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/api/callback/**").permitAll()    // ← 시스템 간 콜백, 본 작업 JWT 적용 제외
    .anyRequest().authenticated()                       // ← 그 외 모든 /api/** 운영자 호출 = JWT 필수
  )
  .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

#### 9.3.2 gims-api-provider (8095) — 복잡 (path 분리 필수)

| Controller | Prefix | 호출자 | 인증 |
|---|---|---|:--:|
| `ApiGatewayController` | `/api/provide/**` | **외부 사용자** (GIMS 본 시스템 등) | **Provide API Key** |
| `ApiPrvManageController` | `/api/manage/**` | 운영자 (Frontend) | ✅ JWT |
| `CustomHandlerCatalogController` | `/api/manage/custom-handlers/**` | 운영자 | ✅ JWT |
| **`MockApiKeyController`** | **`/api/mock/api-key`** | **테스트 전용** | **❓ 운영 비활성?** |
| `/actuator/health` | — | — | permit |

✅ **이슈 2 결정 (2026-05-04)** — `MockApiKeyController` = 테스트 전용. **현재 토글 없음 (운영 노출)** 확인됨.

**본 작업 처리 (이중 방어)**:
- (a) 컨트롤러에 `@ConditionalOnProperty(name="mock.api-key.enabled", havingValue="true")` 추가 — yml 토글 default false (운영 비활성)
- (b) Spring Security 의 SecurityConfig 에서 `/api/mock/**` `denyAll` — 코드 토글 빠져도 path 자체 차단

```java
// api-provider 의 SecurityConfig
http
  .authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/.well-known/**").permitAll()      // JWKS 등 표준
    .requestMatchers("/api/provide/**").permitAll()      // ← Provide API Key 는 자체 검증 (Spring Security 외 영역)
    .requestMatchers("/api/mock/**").denyAll()           // ← 이슈 2 — 운영 차단 (또는 토글)
    .requestMatchers("/api/manage/**").authenticated()   // ← JWT 필수
    .anyRequest().denyAll()
  )
  .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

> **`/api/provide/**` 의 Provide API Key 인증** = Spring Security `permitAll()` 로 통과시킨 후, 컨트롤러 내부 또는 별도 필터에서 자체 검증 (현재 `ApiGatewayController` 의 `apiKey` 쿼리 파라미터 처리). Spring Security 와 분리된 영역.

#### 9.3.3 infolink-api-collector (8084/8094) — 단순 (mock 만 주의)

| Controller | Prefix | 호출자 | JWT |
|---|---|---|:--:|
| `ApiEndpointController` | `/api/endpoints` | 운영자 | ✅ |
| `ApiHistoryController` | `/api/endpoints/{id}/history` | 운영자 | ✅ |
| `ApiScheduleController` | (확인 필요) | 운영자 | ✅ |
| **`MockApiController`** | **`/mock`** | **테스트 전용** | **❓ 운영 비활성?** |
| `/actuator/health` | — | — | permit |

✅ **이슈 3 결정 (2026-05-04)** — `MockApiController` (`/mock` prefix) = 테스트 전용. **현재 토글 없음 (운영 노출)** 확인됨. NGW_0118 언론사 등 mock 응답 컨트롤러 (3/23 dev_log).

**본 작업 처리 (이중 방어)**:
- (a) 컨트롤러에 `@ConditionalOnProperty(name="mock.api.enabled", havingValue="true")` 추가 — yml 토글 default false (운영 비활성)
- (b) Spring Security 의 SecurityConfig 에서 `/mock/**` `denyAll` — `/api/` prefix 아니므로 명시 처리

```java
// api-collector 의 SecurityConfig
http
  .authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/mock/**").denyAll()              // ← 이슈 3 — 운영 차단 (또는 토글)
    .requestMatchers("/api/**").authenticated()         // ← 운영자 endpoint 모두 JWT 필수
    .anyRequest().denyAll()
  )
  .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

#### 9.3.4 정리 — 모듈별 작업 비교

| 모듈 | SecurityConfig 복잡도 | 본 작업 추가 task |
|---|:--:|---|
| **Backend** | 단순 + `/api/callback/**` permit | — (callback 시스템 간 호출 그대로) |
| **api-provider** | path 분리 (provide permit / manage 인증 / mock denyAll) | MockApiKeyController 에 `@ConditionalOnProperty` 추가 |
| **api-collector** | 단순 + `/mock` denyAll | MockApiController 에 `@ConditionalOnProperty` 추가 |

#### 9.3.5 결정 완료 항목 (2026-05-04)

| 이슈 | 결정 |
|---|---|
| **`/api/callback/**` 인증** | ✅ permitAll — 시스템 간 콜백 (Agent → Backend), JWT 적용 제외. 시스템 간 인증 강화는 별 후속 작업 |
| **`MockApiKeyController`** | ✅ 컨트롤러에 `@ConditionalOnProperty(mock.api-key.enabled=true)` 추가 (yml default false) + SecurityConfig 의 `/api/mock/**` denyAll (이중 방어) |
| **`MockApiController`** | ✅ 컨트롤러에 `@ConditionalOnProperty(mock.api.enabled=true)` 추가 (yml default false) + SecurityConfig 의 `/mock/**` denyAll (이중 방어) |
| **시스템 간 인증 강화** | 후속 작업 — Backend 에 ApiKeyFilter 도입 / 또는 path 분리 (`/api/internal/**`) 후속 plan 으로 분리 |

#### 9.3.6 본 작업에 추가되는 코드 변경 (Phase 1~5 외)

Phase 1~5 와 별개로 검증자 모듈에 박힐 변경:

| 모듈 | 파일 | 변경 |
|---|---|---|
| `gims-api-provider` | `MockApiKeyController.java` | `@ConditionalOnProperty(name="mock.api-key.enabled", havingValue="true")` 추가 |
| `gims-api-provider` | `application.yml` | `mock.api-key.enabled: false` (default) |
| `infolink-api-collector` | `MockApiController.java` | `@ConditionalOnProperty(name="mock.api.enabled", havingValue="true")` 추가 |
| `infolink-api-collector` | `application.yml` | `mock.api.enabled: false` (default) |

> 이중 방어 패턴: 컨트롤러 토글로 1차 차단 + SecurityConfig denyAll 로 2차 차단. 둘 중 하나만 동작해도 운영 안전.

---

## 10. Frontend (Next.js) 통합

### 10.1 로그인 화면
- 신규 페이지: `app/login/page.tsx`
- POST `http://localhost:8096/api/auth/login` (또는 next.config proxy 통해)
- 성공 시 redirect to `/`

### 10.2 미인증 가드 — Next.js middleware (1차 가드)

**위치**: `middleware.ts` (프로젝트 루트, app router 의 `middleware`)

**역할**: 페이지 라우팅 진입 전 cookie 존재 확인 → 미인증 사용자 차단 (UX 빠름, 백엔드 호출 없이 처리)

**동작 매트릭스**:
| 사용자 상태 | 접근 경로 | 동작 |
|---|---|---|
| 미인증 (cookie 없음) | `/login` | 통과 (로그인 화면 표시) |
| 미인증 (cookie 없음) | `/login` 외 모든 경로 | **`/login` 으로 redirect** |
| 인증 (cookie 있음) | `/login` | **`/` (홈) 으로 redirect** (이미 로그인됨) |
| 인증 (cookie 있음) | 다른 경로 | 통과 |

**구현 예시 (middleware.ts)**:
```typescript
import { NextRequest, NextResponse } from 'next/server';

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;

  // proxy / 정적 자원은 middleware 제외 (matcher 에서 제외)
  // (matcher 설정으로 처리 — 아래)

  const accessCookie = req.cookies.get('accessToken');     // 또는 refresh cookie 도 검사
  const isLoginPath = pathname === '/login';

  // 미인증 + 로그인 외 경로 → /login
  if (!accessCookie && !isLoginPath) {
    const url = req.nextUrl.clone();
    url.pathname = '/login';
    url.searchParams.set('next', pathname);   // 로그인 후 원래 경로 복귀
    return NextResponse.redirect(url);
  }

  // 인증 + /login 접근 → /
  if (accessCookie && isLoginPath) {
    const url = req.nextUrl.clone();
    url.pathname = '/';
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    // 정적 자원 / API 프록시 / Next.js internal 제외
    '/((?!_next/static|_next/image|favicon.ico|auth/|orch-api/|provider-api/|collector-api/).*)',
  ],
};
```

**주의**:
- middleware 는 **cookie 존재 여부만 확인** — 토큰 자체 유효성 검증 X (서명 검증은 backend)
- 만료된 토큰이라도 cookie 가 있으면 통과 → 실제 페이지 로드 시 first API 호출 401 → interceptor 가 refresh 시도 → 실패 시 `/login` (10.3)
- httpOnly cookie 라 JS 에서 직접 못 읽음 — middleware 만 가능 (server-side)

### 10.3 axios interceptor — 2차 가드 (런타임)

```typescript
// lib/axios.ts
import axios from 'axios';

const client = axios.create({
  withCredentials: true,    // cookie 자동 전송 (httpOnly)
});

let refreshing: Promise<void> | null = null;

client.interceptors.response.use(
  res => res,
  async err => {
    const original = err.config;
    if (err.response?.status === 401 && !original._retry) {
      original._retry = true;

      // 동시 호출 시 refresh 1회만 (race 방지)
      if (!refreshing) {
        refreshing = axios.post('/auth/refresh', null, { withCredentials: true })
          .then(() => { refreshing = null; })
          .catch(() => {
            refreshing = null;
            window.location.href = '/login';   // refresh 실패 → 강제 로그인 화면
            throw err;
          });
      }
      await refreshing;
      return client(original);    // 원 요청 재시도
    }
    return Promise.reject(err);
  }
);

export default client;
```

**역할**:
- middleware 가 못 잡는 토큰 만료/위조 케이스 잡음
- 401 받으면 refresh 자동 → 성공 시 사용자가 모르게 갱신
- refresh 도 실패하면 `/login` redirect (강제)

### 10.4 두 층 가드 정리

| 층 | 위치 | 시점 | 검증 항목 | 미인증 처리 |
|---|---|:--:|---|---|
| 1차 | middleware.ts (server) | 페이지 라우팅 진입 시 | cookie 존재 여부 | `/login` redirect |
| 2차 | axios interceptor (client) | API 호출 응답 401 | 토큰 서명/만료 (backend 검증) | refresh 시도 → 실패 시 `/login` |

→ middleware = 빠른 가드 (cookie 없으면 화면 진입 차단) / interceptor = 정확한 가드 (실제 토큰 검증 결과 처리)

### 10.5 next.config.mjs proxy
```js
async rewrites() {
  return [
    { source: '/auth/:path*', destination: 'http://localhost:8096/api/auth/:path*' },
    { source: '/orch-api/:path*', destination: 'http://localhost:8080/api/:path*' },
    { source: '/provider-api/:path*', destination: 'http://localhost:8095/api/:path*' },
    { source: '/collector-api/:path*', destination: 'http://localhost:8084/api/:path*' },
  ]
}
```
> cookie 가 동일 origin 으로 자동 전송되도록 proxy 통일

### 10.6 글로벌 슬림 헤더 (전체 layout)

**위치**: `app/layout.tsx` 의 최상단 (모든 페이지 공통)

**디자인**: 얇은 헤더 (height ~40px) — 운영 콘텐츠 영역 압박 최소화.

**상태별 표시**:

| 사용자 상태 | 좌측 | 우측 |
|---|---|---|
| 미로그인 | 시스템명 (link to `/`) | **로그인** 버튼 |
| 로그인 | 시스템명 (link to `/`) | 사용자 이름 (`name`) + **사용자 목록 아이콘** (→ `/users`) + **로그아웃** 버튼 |

**구현**:
```tsx
// components/AppHeader.tsx
export default function AppHeader() {
  const { user } = useCurrentUser();   // SWR /api/auth/me

  return (
    <header style={{ height: 40, display: 'flex', justifyContent: 'space-between',
                      alignItems: 'center', padding: '0 1rem', borderBottom: '1px solid #ddd' }}>
      <Link href="/">GIMS Orchestrator</Link>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        {user ? (
          <>
            <span>{user.name}</span>
            <Link href="/users" title="사용자 목록"><UsersIcon /></Link>
            <button onClick={handleLogout}>로그아웃</button>
          </>
        ) : (
          <Link href="/login">로그인</Link>
        )}
      </div>
    </header>
  );
}

// app/layout.tsx
export default function RootLayout({ children }) {
  return (
    <html><body>
      <AppHeader />
      <main>{children}</main>
    </body></html>
  );
}
```

**예외 처리**: 로그인 페이지(`/login`) 에서는 헤더 숨김 또는 "로그인" 버튼 만 표시 (선호에 따라).

### 10.7 사용자 관리 화면 — `/users`

**역할**: 로그인 사용자가 사용자 목록 조회 + 새 사용자 추가 (peer multiplication) + 본인 정보 변경.

**구성**:
```
/users                    — 사용자 목록 + 새 사용자 추가 버튼
/users/me                 — 본인 비번 변경 / 본인 탈퇴 (또는 모달)
```

**`/users` (목록 + 추가)**:
- GET /api/auth/users → 테이블 표시 (`id`, `username`, `name`, `createdAt`)
- 본인 row 옆 "내 정보 변경" 버튼 → `/users/me` 진입 (또는 모달)
- 우측 상단 **새 사용자 추가** 버튼 → 모달 (username, password, name 입력)
  - 등록 성공 시 alert ("ID/PW 를 별도 채널로 전달하세요")
  - 409 (중복) — alert "이미 사용 중인 ID 입니다"

**`/users/me` (본인 정보 변경)**:
- 비밀번호 변경 폼 (currentPassword, newPassword, confirmPassword)
  - PATCH /api/auth/users/me/password
  - 성공 시 — 모든 refresh token revoke 됨 (백엔드 처리), `/login` 으로 자동 redirect 권장
- 탈퇴 버튼 (확인 모달)
  - DELETE /api/auth/users/me
  - 성공 시 cookie 만료 + `/login` redirect
  - 409 (마지막 1명) — alert "마지막 사용자는 탈퇴할 수 없습니다"

**미인증 진입**: middleware (10.2) 가 `/login` 으로 redirect.

---

## 11. 단계별 작업 (Phase 1~6)

### Phase 1 — Auth 모듈 신규 (`sync-orchestrator-auth` 8096)
- [ ] 모듈 생성 + build.gradle (Spring Security 5, **jjwt 0.11.5 + Nimbus JOSE 9.x**, JPA, jasypt) — Spring OAuth2 starter 미사용 (Boot 2.7 호환)
- [ ] application.yml + jasypt ENC
- [ ] DB 스키마 3 테이블 ddl-auto=update (auth_users 에 `name` 컬럼 포함, auth_refresh_tokens, auth_rsa_keys)
- [ ] entity + repository
- [ ] KeyService (RSA 페어 생성/로드/회전)
- [ ] TokenService (JWT 발급/검증)
- [ ] AuthService (로그인/refresh/로그아웃)
- [ ] **UserService** (사용자 CRUD: addUser / listUsers / deleteMe / changeMyPassword)
- [ ] AuthController + JwksController + **UserController** (`/api/auth/users`, `/api/auth/users/me`, `/api/auth/users/me/password`)
- [ ] InitialKeyLoader (첫 RSA 키만 자동 생성, 사용자는 별도 CLI)
- [ ] **UserGeneratorCli** (별도 main, BCrypt + JDBC 직접 INSERT, 최초 1명/비상 진입로 전용)
- [ ] KeyRotationJob (자정 cron)
- [ ] **마지막 1명 탈퇴 차단 로직** (UserService.deleteMe 안 count 체크)
- [ ] **비번 변경 시 refresh token 전체 revoke 로직**
- [ ] **단위 테스트** (KeyService / TokenService / UserService / AuthService) — §12.4 단위 레이어
- [ ] **통합 테스트** (auth 모듈 endpoint, PG 실제 연결) — §12.6 매트릭스 #3~#7, #13, #15~#16, #18~#19, #21~#24
- [ ] **검증자 통합 테스트** (Backend 또는 별 mock 으로 OAuth2 Resource Server 가 JWKS fetch 후 토큰 검증) — §12.6 #14, #20

### Phase 1.5 — sync-agent-common 검증자 자산 추가 (Phase 2~4 의 공통 의존)
- [ ] `JwtCookieAuthFilter` (OncePerRequestFilter) — cookie 추출 + jjwt 검증 + SecurityContext set
- [ ] `JwksClient` — JWKS fetch + 5min 캐시 + retry 2회 (200ms/500ms) + 503 응답 처리
- [ ] `JwtAuthenticationToken` (SecurityContext 객체)
- [ ] 401/503 응답 헬퍼 (ApiKeyFilter 와 일관 JSON 형식)
- [ ] `@ConditionalOnProperty(jwt.cookie.enabled=true)` 토글 (개발 편의)
- [ ] 단위 테스트 (jjwt 검증 / kid 매칭 / JWKS retry)

### Phase 2 — Backend (8080) 통합
- [ ] build.gradle: jjwt + spring-security 의존성 추가 (sync-agent-common 이미 의존)
- [ ] application.yml: `auth.jwks-url`, `jwt.cookie.enabled=true`
- [ ] SecurityConfig 신규 — `/actuator/health`/`/api/callback/**` permit + 그 외 `/api/**` JWT 필수
- [ ] 회귀 테스트 — Agent 콜백 (`/api/callback/started`/`/finished`) 정상 동작 확인

### Phase 3 — api-provider (8095) 통합
- [ ] build.gradle / application.yml 동일 패턴
- [ ] SecurityConfig 신규 — `/api/provide/**` permit (외부) + `/api/manage/**` JWT 필수 + `/api/mock/**` denyAll
- [ ] **`MockApiKeyController` 에 `@ConditionalOnProperty(mock.api-key.enabled=true)` 추가** + yml default false (이중 방어, §9.3.5)
- [ ] 카탈로그/등록/test 호출 — 토큰 전송 검증

### Phase 4 — api-collector (8084/8094) 통합
- [ ] build.gradle / application.yml 동일 패턴
- [ ] SecurityConfig 신규 — 모든 `/api/**` JWT 필수 + `/mock/**` denyAll
- [ ] **`MockApiController` 에 `@ConditionalOnProperty(mock.api.enabled=true)` 추가** + yml default false (이중 방어, §9.3.5)

### Phase 5 — Frontend (3000)
- [ ] 로그인 페이지 (`app/login/page.tsx`)
- [ ] middleware (가드)
- [ ] axios interceptor (refresh 자동)
- [ ] next.config rewrites (cookie 동일 origin)
- [ ] **글로벌 슬림 헤더** (`components/AppHeader.tsx` + `app/layout.tsx`)
  - 미로그인: 로그인 버튼 / 로그인: 사용자 이름 + 사용자 목록 아이콘 + 로그아웃 버튼
- [ ] **사용자 관리 화면** (`app/users/page.tsx`)
  - 목록 (id/username/name/createdAt) + 새 사용자 추가 모달 (ID/PW/이름)
- [ ] **본인 정보 변경 화면** (`app/users/me/page.tsx`)
  - 비번 변경 (current+new) / 탈퇴 (마지막 1명 차단 메시지)
- [ ] useCurrentUser SWR hook (GET /api/auth/me)

### Phase 6 — Agent / Proxy (본 작업 범위 밖)
- Agent (bojo/bojo-int/others) / Proxy (dmz/internal) = 시스템 간 통신 — **JWT 미적용**
- 기존 API key 그대로 유지
- 운영자가 직접 호출 안 함 (Backend 통해 간접 호출)

---

## 12. 검증 / 회귀

### 12.1 기능 검증
- 로그인 성공 / 실패 (잘못된 비번, 잠금)
- access 만료 → refresh 자동 동작
- refresh 만료 → 401 → 로그인 페이지로
- 로그아웃 → refresh revoke → 다시 갱신 불가
- 키 회전 후에도 기존 토큰 검증 정상 (이전 키 보관)
- JWKS endpoint 응답 (활성 + 유효 키)
- 다른 모듈 JWKS 캐시 갱신
- **사용자 추가 (peer multiplication)** — 등록 후 신규 사용자 로그인 가능
- **사용자 목록 조회** — 등록된 사용자 정보 표시 (password_hash 미노출)
- **본인 비번 변경** — current 필수 검증 + 변경 후 모든 refresh revoke 됨
- **본인 탈퇴** — 마지막 1명 시 409 / 2명 이상이면 정상 삭제 + cookie 만료
- **타인 정보 수정 시도 차단** — `/users/{otherId}` endpoint 자체 없음 (404)

### 12.2 회귀
- Type A 12종 외부 API key 호출 → 영향 없어야 (`/api/provide/**` permit)
- Type B 16종 카탈로그 호출 → JWT 필수
- 운영자 화면 모든 진입 점검
- **기존 X-API-Key 흐름** (Backend↔Agent / Backend↔Proxy) 영향 없어야

### 12.3 보안 검증
- 토큰 위조 시도 → 검증 실패
- 만료 토큰 → 401
- 잘못된 issuer/audience → 401
- BCrypt round 12 (default) 검증
- secure cookie 운영 환경에서 HTTPS 만 전송

### 12.4 테스트 전략 (레이어별)

| 레이어 | 도구 | 대상 | 비고 |
|---|---|---|---|
| **단위** | JUnit5 + Mockito | KeyService / TokenService / UserService / AuthService 의 메소드 단위 | 의존성 mock, 빠른 피드백 |
| **통합** | Spring Boot Test + TestContainers (PG) 또는 H2 in-memory | auth 모듈 endpoint (login/refresh/logout/users CRUD/JWKS) | 실제 PG, 실제 BCrypt, 실제 RSA 서명 — 핵심 흐름 검증 |
| **검증자 통합** | Spring Boot Test (Backend 등) | OAuth2 Resource Server 가 JWKS fetch 후 토큰 검증 | auth 모듈 띄워놓고 실호출 또는 WireMock |
| **E2E (수동/curl)** | curl + 브라우저 | Frontend 로그인 → 백엔드 호출 → refresh / 로그아웃 흐름 | 4/29 PoC S1~S5 패턴 재활용 |
| **회귀 (수동)** | curl + 기존 화면 | Type A/B 외부 호출 / X-API-Key 흐름 / 운영자 화면 진입 | JWT 도입 후 기존 흐름 영향 0 검증 |

### 12.5 시스템 키 매트릭스 (전체 — 기존 + 신규)

> 본 작업으로 새로 도입되는 키 = #5~#10. 기존 키 (#1~#4) 와의 충돌/혼동 방지는 §12.7.

| # | 키 종류 | 형태 | 발급/생성 | 저장 위치 | 사용처 | 회전 | 본 작업 |
|:--:|---|---|---|---|---|---|:--:|
| 1 | **JASYPT_PASSWORD** | env string | 사람 직접 (배포 시) | OS 환경변수 (모듈 공통) | yml ENC() 복호화 / DB 의 jasypt ENC 컬럼 복호화 | 수동 (재배포 + 일괄 재암호화) | 기존 |
| 2 | **X-API-Key (시스템 간)** | string | yml ENC | application.yml ENC + Orchestrator DB datasource_keys | Backend↔Agent / Backend↔Proxy 인증 | 수동 | 기존 |
| 3 | **Provide API Key (외부 사용자)** | string | UI 등록 + DB | api-provider DB (또는 별 테이블) | 외부 클라이언트 → `/api/provide/**` (apiKey 쿼리 또는 헤더) | 수동 | 기존 |
| 4 | **DB 자격증명** | string | UI 등록 (Orchestrator) | Orchestrator DB jasypt ENC | Agent 가 datasourceId 로 조회 후 복호화 → ThreadLocal | 수동 | 기존 |
| 5 | **BCrypt password hash** | hash | 가입 / 본인 변경 시 | PG `auth_users.password_hash` | 로그인 시 검증 | 수동 (개인) | **신규** |
| 6 | **RSA private key** | PEM (jasypt ENC) | auth 부팅 / 회전 시 자동 | PG `auth_rsa_keys.private_pem_enc` | JWT 서명 (auth 모듈만) | 자동 (자정 cron) | **신규** |
| 7 | **RSA public key** | PEM | auth 회전 시 자동 (개인키와 페어) | PG `auth_rsa_keys.public_pem` + JWKS endpoint | JWT 검증 (모든 검증자 모듈) | 자동 (자정 cron) | **신규** |
| 8 | **JWT access token** | JWT (RS256) | 로그인 / refresh 시 | 클라이언트 cookie (httpOnly/Strict) | Backend / api-provider 등 검증 | 15min 만료 → refresh | **신규** |
| 9 | **JWT refresh token + jti** | JWT 또는 opaque + PG row | 로그인 / refresh 시 | 클라이언트 cookie + PG `auth_refresh_tokens` | refresh endpoint 만 | 7day + 사용 시 회전 | **신규** |
| 10 | **kid (key id)** | string | RSA 회전 시 | PG `auth_rsa_keys.kid` + JWT header | header kid → 매칭 공개키 조회 | 키와 함께 회전 | **신규** |

### 12.6 키 검증 시나리오 매트릭스

| # | 단계 | 시나리오 | 대상 키 | 기대 결과 | 테스트 레이어 |
|:--:|---|---|---|---|---|
| 1 | 부팅 | yml ENC 복호화 (정상 PW) | JASYPT_PASSWORD | 정상 기동 | E2E |
| 2 | 부팅 | yml ENC 복호화 (잘못 PW) | JASYPT_PASSWORD | APPLICATION FAILED TO START | E2E (4/29 PoC S3) |
| 3 | 부팅 | DB 의 RSA 개인키 ENC 복호화 (재부팅) | JASYPT_PASSWORD + RSA private | SELECT + 복호화 OK + 캐시 적재 | 통합 |
| 4 | 부팅 | active RSA 키 0건 | RSA pair | 새 페어 자동 생성 + INSERT (active=true, expires_at=오늘+8일) | 통합 |
| 5 | 부팅 | UserGeneratorCli 사용자 0명 → 1명 | BCrypt | INSERT 성공, 재실행 시 UNIQUE 충돌 → ERROR | 통합 |
| 6 | 사용 | 로그인 BCrypt 검증 (정확) | password_hash | 200 + 토큰 발급 + fail_count=0 | 단위 + 통합 |
| 7 | 사용 | 로그인 BCrypt 검증 (틀림) | password_hash | 401 + fail_count++ + 5회시 423 잠금 | 통합 |
| 8 | 사용 | JWT 서명 검증 (정상) | RSA public + kid | SecurityContext set, 200 응답 | 단위 + 검증자 통합 |
| 9 | 사용 | JWT 서명 검증 (위조 — 다른 키로 sign) | RSA public | 401 | 단위 |
| 10 | 사용 | JWT header kid 매칭 실패 | RSA public | 401 (해당 kid 가 JWKS 에 없음) | 단위 |
| 11 | 사용 | JWT exp 지남 | JWT exp | 401 → axios interceptor refresh 시도 | E2E |
| 12 | 사용 | JWT iss/aud 불일치 | JWT claim | 401 | 단위 |
| 13 | 사용 | JWKS 응답 (활성 + 만료안된 비활성 키) | RSA public | active=true 1건 + active=false but exp>NOW 키들 포함 / exp<NOW 제외 | 통합 |
| 14 | 사용 | 검증자 모듈 JWKS 캐시 (첫 호출) | RSA public | fetch + 5min TTL 캐시 | 검증자 통합 |
| 15 | 사용 | refresh token jti 검증 (정상) | jti + PG row | 새 access + 새 refresh + 기존 jti revoked | 통합 |
| 16 | 사용 | refresh token revoked 인 jti 사용 시도 | jti | 401 | 통합 |
| 17 | 사용 | refresh token expired | JWT exp + PG | 401 → /login redirect | E2E |
| 18 | 회전 | RSA 자정 회전 | RSA pair | 새 페어 INSERT + 기존 active=false / 만료 키 cleanup | 통합 (스케줄러 강제 트리거) |
| 19 | 회전 | 회전 후 기존 토큰 검증 (이전 kid) | RSA public 이전 kid | 만료 안 됐으면 검증 OK (8일 보관) | 통합 |
| 20 | 회전 | 회전 직후 다른 모듈 캐시 | JWKS | 5min 이내 401 가능 / 캐시 갱신 후 OK | 검증자 통합 |
| 21 | 탈취 | 비번 변경 → refresh 전체 revoke | refresh row | 모든 refresh.revoked=true → 다른 디바이스 401 | 통합 |
| 22 | 탈취 | 로그아웃 → refresh revoke | refresh row | 해당 jti revoked=true → 갱신 불가 | 통합 |
| 23 | 차단 | 마지막 1명 본인 탈퇴 | user count | 409 ("마지막 사용자는 탈퇴 불가") | 통합 |
| 24 | 차단 | 잠긴 사용자 로그인 시도 | locked_until | 423 Locked | 통합 |
| 25 | 차단 | 인증 없이 보호 endpoint 호출 | — | 401 | 검증자 통합 |
| 26 | 차단 | 인증 없이 `/api/provide/**` 호출 (api-provider) | — | 200 (외부 사용자용 permit) | 검증자 통합 + 회귀 |
| 27 | 차단 | 인증 없이 `/api/manage/**` 호출 (api-provider) | — | 401 | 검증자 통합 |
| 28 | 회귀 | 인증 없이 Backend↔Agent X-API-Key 호출 | X-API-Key | 200 (기존 흐름 영향 없음) | 회귀 |

### 12.7 시스템 간 키 충돌 / 혼동 방지

> 새 JWT 인증과 기존 키들이 같은 모듈/path 에서 공존할 때의 우려와 방어.

| 케이스 | 우려 | 방어 |
|---|---|---|
| api-provider — `/api/provide/**` (Provide API Key) vs `/api/manage/**` (JWT) | 한 모듈에 두 인증 공존 | endpoint path 분리 + Spring Security `requestMatchers` 명확 분기 (§9.1 SecurityConfig) — `/api/provide/**` permitAll + `/api/manage/**` authenticated |
| Backend ↔ Agent (X-API-Key) | Backend 의 OAuth2 Resource Server 활성 후 X-API-Key 흐름 영향? | **Backend 의 ApiKeyFilter 는 시스템 간 호출 (Backend → Agent/Proxy) 의 발신 측에서 자동 헤더 추가 — 수신 측 (Agent/Proxy) 의 ApiKeyFilter 가 검증.** Backend 자체는 **JWT 만 수신** (Frontend 에서 cookie 로). Backend 가 JWT + X-API-Key 둘 다 받는 path 는 없음. |
| Frontend → Backend cookie + 동시 X-API-Key | cookie 와 X-API-Key 둘 다 헤더에 있을 수 있나? | Frontend 에서 X-API-Key 안 쏨 (운영자 호출은 cookie 만). Backend 의 SecurityConfig 가 cookie JWT 만 인식. |
| auth 모듈 다운 시 다른 모듈 영향 | 새 토큰 발급 불가 | JWKS 캐시 5min — 이미 발급된 토큰은 만료까지 동작 / 신규 로그인만 차단. actuator/health 모니터링. |
| RSA 회전 시 클러스터(미래)에서 일관성 | 다른 모듈 JWKS 캐시가 모듈마다 다른 시점에 갱신 | 5min TTL 안에서 401 가능 — refresh 가 발급해주면 새 kid 토큰 받게 되므로 자연 해소. |
| jasypt password 변경 (회전) | 기존 yml ENC 복호화 불가 | 본 작업 범위 외 — 일괄 재암호화 + 재배포 패턴 (4/29 PoC 운영 정책 영역). RSA 개인키 ENC 컬럼도 같이 영향. |
| BCrypt round 변경 (보안 강화) | 기존 hash 검증 불가? | BCrypt 자체가 hash 안에 round 정보 포함 → 검증 자동 호환. 변경은 새 가입/변경부터만 적용. |

---

## 13. 보안 고려사항

### 13.1 cookie 설정
- `HttpOnly` — JavaScript 접근 차단 (XSS 방어)
- `Secure` — HTTPS 만 전송 (운영)
- `SameSite=Strict` — CSRF 일정 부분 방어 + 일반 navigation 허용
- `Path=/api/auth` for refresh — 다른 endpoint 에 안 보냄

### 13.2 BCrypt
- round 12 default
- 비밀번호 정책 — 최소 8자, 영문/숫자/특수문자 (서비스 정책)

### 13.3 brute force
- fail_count >= 5 → 30분 잠금
- 잠금 해제는 자동 (locked_until 경과)

### 13.4 RSA 키
- 2048bit (RSA 표준)
- 개인키 jasypt ENC 저장
- 회전 8일치 보관

### 13.5 CSRF — SameSite=Strict 로 대체 (CSRF token 미사용)

**공격**: 다른 사이트가 자동 fetch 로 우리 도메인 호출 시 브라우저가 httpOnly cookie 자동 첨부 → 우리 서버가 인증된 요청으로 처리 → 위조

**우리 방어**: cookie `SameSite=Strict` 만으로 충분
- 다른 origin 에서 우리 도메인으로의 모든 cross-site 요청에 cookie 자동 차단 (브라우저 강제)
- 외부 링크로 진입 시 → cookie 안 감 → middleware 가 `/login` 으로 redirect → 자연스러운 재로그인
- Spring CSRF token 미사용 (코드 추가 0, 운영자 도구 특성상 외부 링크 진입 빈도 낮음)

**Trade-off**: 외부 사이트 링크 → 우리 시스템 진입 시 항상 재로그인. 운영자가 직접 URL/즐겨찾기 진입은 영향 없음.

**운영 컨텍스트 (Strict 합리화)**:
- 본 시스템 사용자 = **담당자가 모니터링 PC 에서 사용** — 외부 링크 진입 시나리오가 거의 없음
- 일반 진입 동선 = 즐겨찾기 / 주소창 직접 입력 → cookie 정상 전송 → 영향 0
- 외부 알림(Slack/메일 등) 링크 클릭 시에는 재로그인 필요하나 빈도 낮음
- 향후 외부 링크 진입 빈번해지면 (B) `Lax + Spring CSRF token` 으로 마이그레이션 가능

**Spring Security 설정**:
```java
http.csrf(csrf -> csrf.disable())   // CSRF token 미사용
```
**Cookie**:
```
Set-Cookie: accessToken=...; HttpOnly; Secure; SameSite=Strict; ...
Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; ...
```

### 13.6 마지막 1명 탈퇴 차단 (운영자 0명 방지)

**문제**: 사용자가 모두 탈퇴 시 시스템 진입 불가 → CLI 다시 돌려야 하는 비상 상황.

**방어**:
- `DELETE /api/auth/users/me` 진입 시 **사용자 count 체크** (count <= 1 이면 409 응답)
- 카운트 쿼리는 단일 row scan — 부하 없음

**비상 진입로**: 그래도 0명이 되는 비상 케이스 (예: DB 직접 조작, 동시 탈퇴 race) 대비 — UserGeneratorCli 가 비상 진입로 역할 (§6.4)

### 13.7 비밀번호 변경 시 refresh token 전체 revoke

**이유**: 비밀번호 변경의 핵심 시나리오 = "내 cookie 가 탈취됐을지도 모르니 갱신" → 변경 후 기존 모든 디바이스의 refresh 무효화 필요.

**동작**:
- `PATCH /api/auth/users/me/password` 성공 시 `auth_refresh_tokens SET revoked=true WHERE user_id=?` 일괄 UPDATE
- 현재 cookie 의 access token 은 만료(15min)까지 유효 — refresh 시도 시 401 → 로그인 화면

### 13.8 인증 실패 응답 정책 (401 vs 503)

**원칙**: 응답 코드로 **운영 측 책임 vs 사용자 측 책임** 구분 → 모니터링/알람 후크 명확화.

**응답 매트릭스**:

| 상황 | 응답 코드 | Body / Header | Frontend 처리 |
|---|:--:|---|---|
| 토큰 없음 (cookie 미첨부) | **401** | `{"error":"AUTH_REQUIRED","message":"인증이 필요합니다"}` | `/login` redirect |
| 토큰 위조 (서명 검증 실패) | **401** | `{"error":"INVALID_SIGNATURE","message":"토큰 서명이 유효하지 않습니다"}` | `/login` redirect |
| 토큰 만료 (exp 지남) | **401** | `{"error":"TOKEN_EXPIRED","message":"토큰이 만료되었습니다"}` | axios interceptor → /refresh 자동 → 실패 시 `/login` |
| iss/aud 불일치 | **401** | `{"error":"INVALID_TOKEN","message":"유효하지 않은 토큰입니다"}` | `/login` redirect |
| kid 미매칭 (JWKS 에 그 키 없음) | **401** | `{"error":"UNKNOWN_KEY_ID","message":"알 수 없는 키 ID — 회전 직후일 수 있음. 다시 시도하세요"}` | 1회 자동 retry (회전 5min 갭 대비) → 실패 시 `/login` |
| **JWKS fetch 실패 (auth 다운)** | **503** | `{"error":"AUTH_SERVICE_UNAVAILABLE","message":"인증 서버 응답 없음 — 잠시 후 다시 시도하세요"}` + `Retry-After: 30` | 사용자에게 toast/모달 표시. 자동 retry 안 함 |

**JWKS fetch retry 정책 (옵션 C — 검증자 측)**:

```
첫 fetch 시도 → 실패
       ↓
1차 retry (200ms 대기) → 실패
       ↓
2차 retry (500ms 대기) → 실패
       ↓
503 응답 + Retry-After: 30 + 명확 메시지
```

총 retry 시간 ~700ms. 일시적 네트워크 끊김은 자동 회복, 진짜 다운이면 빠르게 503.

**운영 모니터링 후크**:

```
[ 검증자 모듈 ] 503 응답 시 자동:
  ① 로그 기록 (ERROR level)
     log.error("[Auth] JWKS fetch failed after 2 retries", e);
  ② 메트릭 카운터 증가
     meterRegistry.counter("auth.jwks.fetch.failed").increment();

[ Prometheus / Grafana ] 메트릭 임계치 초과 → 알람 → 운영자 즉시 인지

[ auth 모듈 ] /actuator/health endpoint 별도 모니터링 (다운 즉시 감지)
```

→ **3중 후크** (503 응답 / 메트릭 카운터 / actuator/health) 로 auth 다운 즉시 인지.

**401 vs 503 운영 의미**:

| 측면 | 401 | 503 |
|---|---|---|
| 의미 | "당신 자격증명에 문제 있음" | "우리 시스템에 문제 있음" |
| 사용자 동작 | 다시 로그인 | 잠시 후 재시도 |
| 운영 대응 | 보통 정상 (의도된 동작) | **즉시 alert** — 운영자 개입 필요 |
| 모니터링 | 추적 (이상 빈도 시 의심) | **임계치 → 페이지** |

---

## 14. 배포 / 운영

### 14.1 환경변수
| 변수 | 필수 | 용도 |
|---|---|---|
| `JASYPT_PASSWORD` | ✅ | yml ENC 복호화 (모든 모듈) |
| `AUTH_COOKIE_SECURE` | 운영 | true (HTTPS 만 cookie) |

### 14.2 배포 순서
1. PG 스키마 migration (4 테이블)
2. auth 모듈 배포 (8096) — 첫 부팅 시 RSA 키만 자동 생성, 사용자 row 0
3. **UserGeneratorCli 로 첫 사용자 발급** (개발자가 username/password 입력)
4. 다른 모듈 oauth2 resource server 설정 + 재배포
5. Frontend 로그인 페이지 + middleware 배포
6. 운영자에게 발급한 ID/비번 전달

### 14.3 사용자 발급 방법
- **CLI 도구 사용** (§6.4 UserGeneratorCli)
- 예: `./gradlew :sync-orchestrator-auth:createUser --args="alice strongPass!23"`
- 결과: PG `auth_users` row INSERT (BCrypt 해시), id 출력
- 운영자에게 username/password 별도 채널로 전달
- 변경 시 동일 도구로 update (또는 사용자 본인이 비번 변경 endpoint — 별도 작업)

---

## 15. 리스크 / 주의사항

| # | 리스크 | 완화 |
|---|---|---|
| 1 | auth 모듈 다운 시 모든 시스템 인증 불가 | actuator/health 모니터링, 다른 모듈은 JWKS 캐시 5min 으로 일시적 동작 |
| 2 | 키 회전 시 다른 모듈 JWKS 캐시 미갱신 | TTL 5min 보장 — 운영자 5분 이내 401 가능. 더 짧게 줄일 수도 |
| 3 | refresh token 탈취 (XSS 등) | httpOnly + Secure + SameSite + 회전 (1회용) — 탈취 즉시 회전 시도 충돌 → 모두 revoke 정책 검토 |
| 4 | RSA 개인키 노출 | jasypt ENC + DB 권한 제한 + 회전 |
| 5 | jasypt password 노출 | env 만 (yml 에 박지 않음) + 운영 secret 관리 |
| 6 | DB 마이그레이션 시 기존 모듈 영향 | 새 테이블만 추가 — 기존 테이블 변경 없음 |
| 7 | ~~OAuth2 Authorization Server 의존성 충돌~~ | **해소** — Spring OAuth2 starter 미사용, jjwt + Nimbus 자체 구현 (Boot 2.7 호환) |
| 8 | jjwt 라이브러리 보안 결함 | 0.11.5 = 현행 안정 버전 (검증된 라이브러리). 보안 패치 추적 필요 |
| 9 | JWKS fetch 실패 시 5min 캐시 미보유 모듈 | retry 2회 후 503 + 명확 메시지. 모니터링 알람 후크 (§13 후속) |

---

## 16. 후속 작업 (본 작업 외)

- **CSRF 결정** — sameSite=Strict vs Spring CSRF 토큰
- **MFA (2FA)** — TOTP 등
- **비밀번호 정책 강화** — 만료 / 재사용 금지 / 복잡도 (현재는 최소 길이 정도만)
- **OneLogin SSO 통합** — GIMS 본 시스템과 연동 검토 (선택)
- **감사 로그** — 로그인 성공/실패 / 사용자 추가/탈퇴 / IP / User-Agent 기록 테이블

> **명시적 비포함**: RBAC (단일 role 정책 확정 — 모든 사용자 동급)
> **본 작업 포함으로 변경**: 사용자 관리 화면 (peer multiplication) / 본인 비밀번호 변경 endpoint / 본인 탈퇴

---

## 17. 승인 요청

본 계획서 검토 후 진행 여부 결정:

1. **(a)** 정책 / 결정 (§2) OK? — **2026-05-04 정정 반영**: peer multiplication / `name` 필드 / 마지막 1명 탈퇴 차단
2. **(b)** 영향 범위 (§3) 적정?
3. **(c)** 매트릭스 (§4) 누락 없는지?
4. **(d)** 라이프사이클 시나리오 (§5) 정확? — §5.7~§5.10 신규 (사용자 추가/탈퇴/비번변경/목록). §2 표의 "첫 admin 환경변수 자동 생성" 행 제거됨 — 모순 해소 (사용자 발급 모델 = 최초 1명 CLI 만)
5. **(e)** DB 스키마 (§7) 4 테이블 OK? — `auth_users.name` 컬럼 추가
6. **(f)** API 명세 (§8) endpoint 적정? — `/users`, `/users/me`, `/users/me/password` 추가
7. **(g)** 다른 모듈 통합 (§9) 방식 OK?
8. **(h)** Frontend (§10) 흐름 OK? — §10.6 글로벌 헤더 / §10.7 사용자 관리 신규
9. **(i)** Phase 1~6 단계 (§11) — 본 세션 = Phase 1만 / Phase 2~5 별 세션
10. **(j)** CSRF (§13.5) 별도 결정 시점?
11. **(k)** 마지막 1명 탈퇴 차단 (§13.6) + 비번 변경 시 refresh revoke (§13.7) OK?
12. **(l)** 테스트 전략 (§12.4) + 키 매트릭스 (§12.5) + 키 검증 시나리오 (§12.6) + 키 충돌 방지 (§12.7) — 누락 없는지?
13. **(m)** 라이브러리 결정 — Spring Boot 2.7.12 고정 + Spring OAuth2 starter 미사용 + jjwt 0.11.5 + Nimbus JOSE 9.x 자체 구현 (§2 라이브러리 행, §6.2) OK?
14. **(n)** §9.3 검증자 모듈별 endpoint 매핑 + Mock 운영 차단 (이중 방어) + callback permitAll OK?
15. **(o)** §13.8 인증 실패 응답 정책 (401 vs 503 구분) + 검증자 측 JWKS retry 2회 + 운영 모니터링 3중 후크 OK?

승인 받으면 다음 순서로 진행:
1. Phase 1 (auth 모듈 신규 + UserController + UserGeneratorCli) — 본 세션
2. Phase 1.5 (sync-agent-common 검증자 자산: JwtCookieAuthFilter / JwksClient) — Phase 1 끝나면 이어서
3. Phase 2~4 (Backend / api-provider / api-collector 통합) — 별 세션
4. Phase 5 (Frontend) — 별 세션
