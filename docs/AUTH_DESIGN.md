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
| **사용자 DB** | Orchestrator PG (`orchestrator` DB) 같은 곳에 새 테이블 4종 |
| **권한 모델** | **단일 role = `user`** (구분 없음, 누구든 로그인하면 동일 권한) |
| **사용자 발급 모델** | 자체 가입 없음 + 운영 화면 없음 — **Java CLI 생성기**로 개발자가 직접 DB INSERT 후 사용자에게 ID/비번 전달 |
| **Redis** | 미사용 — PG 만 |
| **IDP** | 자체 (OAuth 제외) |
| **첫 admin** | 환경변수 → 부팅 시 BCrypt 자동 생성 |
| **라이브러리** | Spring Security + Spring OAuth2 (Authorization Server / Resource Server / JOSE) + BCrypt |
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
     - 발급은 별도 Java CLI 생성기 (§6.4) 로 개발자가 수동 INSERT
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

### 5.7 JWKS endpoint — `GET /.well-known/jwks.json` (auth 모듈, 인증 불필요)

#### 5.7.1 JWKS 가 무엇인가
- **JWKS (JSON Web Key Set)** = RFC 7517 표준 — 공개키 묶음을 JSON 으로 노출하는 endpoint
- **`/.well-known/`** = RFC 8615 의 "잘 알려진 URI" 표준 path — OAuth/OpenID 등 표준 기능이 약속된 위치에 있게 하는 관행

#### 5.7.2 왜 필요한가 — 정적 배포 vs JWKS 비교
| 대안 | 회전 대응 |
|---|---|
| 공개키를 yml 에 정적 박음 | 회전 시 모든 모듈 재배포 |
| 공개키를 파일로 공유 | 회전 시 모든 모듈에 파일 복사 |
| **JWKS endpoint** | **검증자가 동적 fetch + TTL 캐시 → 회전 자동 반영** |

→ 우리는 **자정 회전** 결정했으므로 JWKS 가 사실상 필수.

#### 5.7.3 흐름
```
[auth 모듈]                    [검증자 (Backend / api-provider 등)]
 개인키로 토큰 서명               GET /.well-known/jwks.json (5min 캐시)
   토큰 헤더에 kid 박음                ↓
       ↓                          공개키 묶음 응답
       └────── 토큰 ─────────→  토큰 헤더 kid → 매칭 공개키 → 서명 검증
```

#### 5.7.4 응답 형태
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

#### 5.7.5 노출 정책
- **active=true** (현재 발급용) — 1개 row + 응답에 포함
- **active=false 면서 expires_at > NOW** (이전 키들) — 응답에 포함 (검증용)
- **expires_at < NOW** — 응답 제외 (cleanup 대상)

#### 5.7.6 Spring 검증자 자동 처리
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
    │   │   ├── SecurityConfig.java         (Spring Security)
    │   │   ├── JasyptConfig.java
    │   │   └── SchedulerConfig.java        (@EnableScheduling)
    │   ├── controller/
    │   │   ├── AuthController.java         (login/refresh/logout)
    │   │   └── JwksController.java         (.well-known/jwks.json)
    │   ├── service/
    │   │   ├── AuthService.java            (로그인/refresh 비즈니스)
    │   │   ├── KeyService.java             (RSA 페어 로드/생성/회전)
    │   │   ├── TokenService.java           (JWT 발급/검증)
    │   │   └── UserService.java            (사용자 CRUD)
    │   ├── entity/
    │   │   ├── AuthUser.java               (auth_users)
    │   │   ├── AuthRefreshToken.java       (auth_refresh_tokens)
    │   │   └── AuthRsaKey.java             (auth_rsa_keys)
    │   ├── repository/
    │   │   ├── AuthUserRepository.java
    │   │   ├── AuthRefreshTokenRepository.java
    │   │   └── AuthRsaKeyRepository.java
    │   ├── dto/
    │   │   ├── LoginRequest.java
    │   │   ├── LoginResponse.java
    │   │   └── ...
    │   ├── scheduler/
    │   │   ├── KeyRotationJob.java         (자정 회전)
    │   │   └── KeyCleanupJob.java          (만료 키 폐기)
    │   ├── bootstrap/
    │   │   └── InitialKeyLoader.java       (첫 RSA 키 생성 — 사용자는 CLI 별도)
    │   └── tools/
    │       └── UserGeneratorCli.java       (별도 main — DB 직접 INSERT)
    └── resources/
        └── application.yml
```

### 6.4 UserGeneratorCli — 사용자 발급 도구 (별도 main)

```java
// 실행: ./gradlew createUser --args="<username> <password>"
// 또는: java -cp <fat-jar> com.gims.auth.tools.UserGeneratorCli <username> <password>

public class UserGeneratorCli {
    public static void main(String[] args) {
        // 1. 인자 검증 (username, password)
        // 2. PG 직접 connection (env JDBC_URL/USER/PW 또는 yml 의 DB 정보 jasypt 복호화)
        // 3. BCrypt.hashpw(password, BCrypt.gensalt(12))
        // 4. INSERT INTO auth_users (username, password_hash, role, created_at) VALUES (?, ?, 'user', NOW())
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
dependencies {
    // Spring Boot 3.x 기준
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-authorization-server'  // JOSE 포함
    implementation 'org.springframework.boot:spring-boot-starter-validation'

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

### 7.1 새 테이블 4종

```sql
CREATE TABLE auth_users (
  id              BIGSERIAL PRIMARY KEY,
  username        VARCHAR(50)  UNIQUE NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,
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
응답: { "id": 1, "username": "admin", "role": "admin" }
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

`build.gradle`:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```

`SecurityConfig.java`:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/provide/**").permitAll()    // api-provider 만 — 외부 API
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwkSetUri("http://localhost:8096/.well-known/jwks.json"))
                .bearerTokenResolver(new CookieBearerTokenResolver("accessToken"))
            )
            .csrf(csrf -> csrf.disable())   // 일단 disable, CSRF 결정 후 조정
            .build();
    }
}
```

### 9.2 모듈별 보호 endpoint

| 모듈 | 보호 (JWT 필수) | 미보호 (현재 그대로) |
|---|---|---|
| Backend (8080) | 모든 `/api/**` | `/actuator/health` |
| api-provider (8095) | `/api/manage/**` | `/api/provide/**`, `/.well-known/...` |
| api-collector (8084/8094) | 모든 운영 endpoint | `/actuator/health` |
| Agent bojo / bojo-int / others | (Orchestrator 가 호출하는 internal endpoint — JWT 불필요?) | 운영자 호출 endpoint 만 보호 |

> Agent endpoint 는 대부분 Orchestrator-internal — Orchestrator 자체가 인증되면 충분. 별도 검토 필요.

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

### 10.4 next.config.mjs proxy
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

---

## 11. 단계별 작업 (Phase 1~6)

### Phase 1 — Auth 모듈 신규 (`sync-orchestrator-auth` 8096)
- [ ] 모듈 생성 + build.gradle (Spring Security, OAuth2 Authorization Server, JPA, jasypt)
- [ ] application.yml + jasypt ENC
- [ ] DB 스키마 4 테이블 ddl-auto=update
- [ ] entity + repository
- [ ] KeyService (RSA 페어 생성/로드/회전)
- [ ] TokenService (JWT 발급/검증)
- [ ] AuthService (로그인/refresh/로그아웃)
- [ ] AuthController + JwksController
- [ ] InitialKeyLoader (첫 RSA 키만 자동 생성, 사용자는 별도 CLI)
- [ ] **UserGeneratorCli** (별도 main, BCrypt + JDBC 직접 INSERT)
- [ ] KeyRotationJob (자정 cron)
- [ ] 로그인/refresh/로그아웃 단위 테스트

### Phase 2 — Backend (8080) 통합
- [ ] OAuth2 Resource Server 의존성 + SecurityConfig
- [ ] CookieBearerTokenResolver 구현 (cookie 에서 토큰 추출)
- [ ] 모든 endpoint 보호 / 헬스체크는 permit
- [ ] 회귀 테스트

### Phase 3 — api-provider (8095) 통합
- [ ] OAuth2 Resource Server + SecurityConfig
- [ ] `/api/provide/**` permit (외부) / `/api/manage/**` 인증 필수
- [ ] 카탈로그/등록/test 호출 — 토큰 전송 검증

### Phase 4 — api-collector (8084/8094) 통합
- [ ] 동일 패턴 적용

### Phase 5 — Frontend (3000)
- [ ] 로그인 페이지
- [ ] middleware (가드)
- [ ] axios interceptor (refresh 자동)
- [ ] next.config rewrites (cookie 동일 origin)
- [ ] 로그아웃 메뉴
- [ ] 사용자 정보 표시

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

### 12.2 회귀
- Type A 12종 외부 API key 호출 → 영향 없어야 (`/api/provide/**` permit)
- Type B 16종 카탈로그 호출 → JWT 필수
- 운영자 화면 모든 진입 점검

### 12.3 보안 검증
- 토큰 위조 시도 → 검증 실패
- 만료 토큰 → 401
- 잘못된 issuer/audience → 401
- BCrypt round 12 (default) 검증
- secure cookie 운영 환경에서 HTTPS 만 전송

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
| 7 | OAuth2 Authorization Server 의존성 충돌 | Spring Boot 3.x 기준 필요 — 다른 모듈 버전 확인 |

---

## 16. 후속 작업 (본 작업 외)

- **CSRF 결정** — sameSite=Strict vs Spring CSRF 토큰
- **MFA (2FA)** — TOTP 등
- **비밀번호 변경 endpoint** — 사용자가 본인 비번 변경 (개발자 거치지 않고)
- **비밀번호 정책** — 만료 / 재사용 금지 / 복잡도
- **OneLogin SSO 통합** — GIMS 본 시스템과 연동 검토 (선택)
- **감사 로그** — 로그인 성공/실패 / IP / User-Agent 기록 테이블

> **명시적 비포함**: RBAC (단일 role 정책 확정) / 사용자 관리 화면 (CLI 발급 정책 확정)

---

## 17. 승인 요청

본 계획서 검토 후 진행 여부 결정:

1. **(a)** 정책 / 결정 (§2) OK?
2. **(b)** 영향 범위 (§3) 적정?
3. **(c)** 매트릭스 (§4) 누락 없는지?
4. **(d)** 라이프사이클 시나리오 (§5) 정확?
5. **(e)** DB 스키마 (§7) 4 테이블 OK?
6. **(f)** API 명세 (§8) endpoint 적정?
7. **(g)** 다른 모듈 통합 (§9) 방식 OK?
8. **(h)** Frontend (§10) 흐름 OK?
9. **(i)** Phase 1~6 단계 (§11) — 전부 진행 / 단계별 분할?
10. **(j)** CSRF (§13.5) 별도 결정 시점?

승인 받으면 Phase 1 (auth 모듈 신규) 부터 진행.
