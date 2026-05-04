# Auth 시스템 — Phase 1 + Phase 1.5 작업 계획

> 작성일: 2026-05-04
> 범위: **Phase 1 (auth 모듈 신규)** + **Phase 1.5 (sync-agent-common 검증자 자산)**
> Phase 2~5 (Backend / api-provider / api-collector / Frontend) = 별 세션
> 동반 문서: [AUTH_DESIGN.md](../../../docs/AUTH_DESIGN.md), [AUTH_FLOW.md](../../../docs/AUTH_FLOW.md)

---

## 1. 목적 / 배경

- orchestrator 시스템에 **운영자 로그인** 시스템 부재 — 누구나 운영 endpoint 접근 가능 (보안 결함)
- todo/system/07-security.md 의 "로그인/인가" 4 항목 미구현
- AUTH_DESIGN.md (4/28 작성, 5/4 정정) + AUTH_FLOW.md 결정 사항 따라 구현
- 데이터 삭제/수정 endpoint 보호 + peer multiplication 사용자 관리 시스템 도입

## 2. 사전 결정 사항 (요약)

### 2.1 인프라 / 라이브러리

| 항목 | 결정 |
|---|---|
| 신규 모듈 | `sync-orchestrator-auth` (port **8096**) |
| Java | **17** (다른 모듈과 통일) |
| Spring Boot | **2.7.12 고정** |
| 라이브러리 | Spring Security 5 + **jjwt 0.11.5 + Nimbus JOSE 9.x** + BCrypt |
| OAuth2 starter | **미사용** (Boot 2.7 호환 한계) |
| 외부 IDP | **미사용** (자체 ID/PW) |
| 검증자 자산 위치 | 기존 `sync-agent-common` 에 추가 (JwtCookieAuthFilter / JwksClient) |

### 2.2 토큰 / RSA 키

| 항목 | 결정 |
|---|---|
| 알고리즘 | RS256 (RSA 비대칭) |
| RSA 키 길이 | 2048bit |
| 키 회전 | 매일 자정 자동 |
| 보관 기간 | 8일 (refresh 7일 + 1일 여유) |
| 시스템 동시 보관 | active 1쌍 + 비활성 7쌍 = **약 8쌍** |
| Access Token TTL | 15분 |
| Refresh Token TTL | 7일 + 사용 시 회전 (1회용) |
| 토큰 저장 | httpOnly cookie + SameSite=Strict |
| JWKS fetch (검증자) | Lazy + retry 2회 (200ms/500ms) → 503 |
| 응답 정책 | 401 (자격 문제) vs 503 (시스템 문제) — `Retry-After: 30` 헤더 |

### 2.3 사용자 관리 모델 (Peer Multiplication = 동급 계정 증식)

| 항목 | 결정 |
|---|---|
| 권한 모델 | **단일 role = `user`** — 모두 동급 권한, admin 구분 없음 |
| **사용자 정보 필드** | `username` (=ID) / `password_hash` / **`name`** (담당자 이름) / `created_at` |
| **최초 1명 발급** | **Java CLI** (UserGeneratorCli) — 운영 배포 직후 1회 + 비상 진입로 |
| **이후 사용자 추가** | **로그인 사용자 누구나** 운영 화면(Phase 5)/`POST /api/auth/users` 로 추가 (등록자가 ID/PW/이름 모두 입력 → 받는 사람에게 별도 채널로 전달) |
| **사용자 목록** | 로그인 사용자 누구나 조회 (`GET /api/auth/users` — id/username/name/createdAt) |
| **등록자 추적** | ❌ 미저장 (`registered_by` 컬럼 없음) |
| **비밀번호 변경** | **본인만** (`PATCH /api/auth/users/me/password` — current 필수 + 모든 refresh revoke) |
| **탈퇴** | **본인만** (`DELETE /api/auth/users/me` — 마지막 1명 차단 = count >= 2 체크) |
| **타인 정보 수정** | ❌ 불가 — endpoint 자체 없음 |

### 2.4 본 작업의 endpoint 4개 (사용자 관리 — 본 세션에 모두 포함)

| Method | Path | 인증 | 역할 |
|---|---|:--:|---|
| `POST` | `/api/auth/users` | ✅ | **새 사용자 추가** (peer multiplication) |
| `GET` | `/api/auth/users` | ✅ | 사용자 목록 |
| `DELETE` | `/api/auth/users/me` | ✅ | 본인 탈퇴 |
| `PATCH` | `/api/auth/users/me/password` | ✅ | 본인 비번 변경 |

> Phase 5 (Frontend) 에서 운영 화면 (`/users` / `/users/me`) 으로 이 endpoint 들 호출. **본 세션은 backend endpoint 까지만**.

## 3. 작업 범위 — 본 세션 (Phase 1 + 1.5)

### 포함

#### 인프라
- ✅ `sync-orchestrator-auth` 신규 모듈 — 포트 8096
- ✅ `sync-agent-common` 검증자 자산 (JwtCookieAuthFilter / JwksClient / 헬퍼)
- ✅ DB 스키마 3 테이블 (Orchestrator PG `orchestrator` DB)
- ✅ JASYPT ENC 적용 (다른 모듈 패턴 따라)

#### 인증 endpoint
- ✅ `POST /api/auth/login` — 로그인
- ✅ `POST /api/auth/refresh` — access 갱신
- ✅ `POST /api/auth/logout` — 로그아웃
- ✅ `GET /api/auth/me` — 현재 사용자 조회
- ✅ `GET /.well-known/jwks.json` — JWKS endpoint

#### 사용자 관리 endpoint (Peer Multiplication 백엔드)
- ✅ `POST /api/auth/users` — **새 사용자 추가** (등록자가 ID/PW/이름 입력)
- ✅ `GET /api/auth/users` — 사용자 목록 조회
- ✅ `DELETE /api/auth/users/me` — 본인 탈퇴 (마지막 1명 차단)
- ✅ `PATCH /api/auth/users/me/password` — 본인 비번 변경 (current 검증 + refresh revoke)

#### 자동화 / 도구
- ✅ KeyRotationJob (자정 cron — RSA 페어 회전 + 만료 키 cleanup)
- ✅ InitialKeyLoader (auth 모듈 첫 부팅 시 RSA 페어 자동 생성)
- ✅ UserGeneratorCli — **최초 1명 발급 도구** (BCrypt + JDBC, Spring 안 띄움)

#### 검증
- ✅ 단위 테스트 (KeyService / TokenService / UserService / AuthService)
- ✅ 통합 테스트 (PG 실연결 — 로그인/refresh/사용자 CRUD/회전)
- ✅ curl 시나리오 10가지 수동 검증 (Step 11)

### 미포함 (별 세션)
- ❌ Phase 2 — Backend (8080) 통합 (SecurityConfig + 의존성 추가 + callback permit)
- ❌ Phase 3 — api-provider (8095) 통합 + MockApiKeyController `@ConditionalOnProperty` + denyAll
- ❌ Phase 4 — api-collector (8084/8094) 통합 + MockApiController 동
- ❌ Phase 5 — Frontend (3000):
  - 로그인 화면 (`app/login/page.tsx`)
  - 글로벌 슬림 헤더 (로그인/로그아웃 버튼 + 사용자 목록 아이콘)
  - **사용자 관리 화면** (`app/users/page.tsx` — 목록 + 새 사용자 추가 모달) ← Peer multiplication 의 운영자 UI
  - 본인 정보 변경 화면 (`app/users/me/page.tsx` — 비번 변경 + 탈퇴)

## 4. 신규 모듈 — `sync-orchestrator-auth`

### 4.1 디렉토리 구조

```
sync-orchestrator-auth/
├── build.gradle
├── settings.gradle (또는 root settings.gradle 에 등록)
└── src/main/
    ├── java/com/gims/auth/
    │   ├── AuthApplication.java
    │   ├── config/
    │   │   ├── SecurityConfig.java          ← Spring Security 5 (login permit, 나머지 자체 처리)
    │   │   ├── JasyptConfig.java
    │   │   └── SchedulerConfig.java         ← @EnableScheduling
    │   ├── controller/
    │   │   ├── AuthController.java          ← /api/auth/{login,refresh,logout,me}
    │   │   ├── UserController.java          ← /api/auth/users(/me)(/password)
    │   │   └── JwksController.java          ← /.well-known/jwks.json
    │   ├── service/
    │   │   ├── AuthService.java             ← 로그인/refresh/로그아웃 비즈니스
    │   │   ├── KeyService.java              ← RSA 페어 로드/생성/회전 (KeyPairGenerator)
    │   │   ├── TokenService.java            ← JWT 발급/검증 (jjwt)
    │   │   └── UserService.java             ← 사용자 CRUD (마지막 1명 차단 / refresh revoke 포함)
    │   ├── entity/
    │   │   ├── AuthUser.java                ← auth_users (name 컬럼 포함)
    │   │   ├── AuthRefreshToken.java
    │   │   └── AuthRsaKey.java
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
    │   │   └── KeyRotationJob.java          ← 자정 cron (회전 + cleanup 통합)
    │   ├── bootstrap/
    │   │   └── InitialKeyLoader.java        ← 첫 RSA 키 자동 생성 (사용자 0명은 그대로)
    │   └── tools/
    │       └── UserGeneratorCli.java        ← 별도 main (BCrypt + JDBC, 최초 1명/비상)
    └── resources/
        └── application.yml
```

### 4.2 build.gradle

```gradle
plugins {
    id 'org.springframework.boot' version '2.7.12'
    id 'io.spring.dependency-management' version '1.1.4'
    id 'java'
}

group = 'com.gims'
version = '1.0.0'
sourceCompatibility = '17'   // 또는 '11' — 다른 모듈과 통일

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // ⚠️ Spring OAuth2 starter 사용 안 함 (Boot 2.7 호환 한계)

    // JWT — 자체 구현
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.11.5'
    implementation 'com.nimbusds:nimbus-jose-jwt:9.37'

    // jasypt
    implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'

    // PG
    runtimeOnly 'org.postgresql:postgresql'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}

// CLI fat jar (선택)
task generatorJar(type: Jar) {
    archiveClassifier = 'cli'
    from sourceSets.main.output
    manifest { attributes 'Main-Class': 'com.gims.auth.tools.UserGeneratorCli' }
    with jar
}
```

### 4.3 application.yml

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
        format_sql: true
    show-sql: false

jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD}                    # 환경변수만 — yml default 없음
    algorithm: PBEWithHMACSHA512AndAES_256
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator

auth:
  access-token-ttl-minutes: 15
  refresh-token-ttl-days: 7
  rsa:
    key-size: 2048
    rotation-cron: "0 0 0 * * ?"
    retention-days: 8
  cookie:
    same-site: Strict
    secure: ${AUTH_COOKIE_SECURE:false}             # 운영=true (HTTPS)
  bcrypt:
    rounds: 12

management:
  endpoints:
    web:
      exposure:
        include: health
```

### 4.4 DB 스키마 (3 테이블)

```sql
-- Orchestrator PG `orchestrator` DB

CREATE TABLE auth_users (
  id              BIGSERIAL PRIMARY KEY,
  username        VARCHAR(50)  UNIQUE NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,
  name            VARCHAR(50)  NOT NULL,            -- 담당자 이름
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
```

> ddl-auto=update 가 entity 기반 자동 생성. **별도 SQL DDL 실행 / 파일 관리 X** (entity 가 진실원).

## 5. sync-agent-common 검증자 자산 (Phase 1.5)

### 5.1 추가 파일

```
sync-agent-common/src/main/java/com/sync/agent/common/
├── config/
│   ├── ApiKeyFilter.java                ← 기존, 변경 없음
│   └── JwtCookieAuthFilter.java         ← 신규 (OncePerRequestFilter)
├── client/
│   └── JwksClient.java                  ← 신규 (JWKS fetch + 5min 캐시 + retry)
└── auth/                                 ← 신규 패키지
    ├── JwtAuthenticationToken.java      ← SecurityContext 객체
    ├── AuthErrorResponse.java           ← 401/503 JSON 응답 헬퍼
    └── AuthErrorCode.java               ← AUTH_REQUIRED / INVALID_SIGNATURE / ... enum
```

### 5.2 build.gradle 추가 의존성

```gradle
// sync-agent-common/build.gradle 에 추가
dependencies {
    // 기존 의존성 유지
    // ...

    // JWT 검증
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.11.5'
    implementation 'com.nimbusds:nimbus-jose-jwt:9.37'   // PEM/JWKS 파싱 헬퍼

    // Spring Security (검증자 모듈에서 사용)
    compileOnly 'org.springframework.boot:spring-boot-starter-security'
    // compileOnly — 검증자 모듈이 자체 의존성 추가
}
```

### 5.3 JwksClient 동작 (Lazy + retry 2회)

```java
// sync-agent-common/.../client/JwksClient.java
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.jwks-url")
public class JwksClient {
    private final String jwksUrl;
    private final ConcurrentMap<String, PublicKey> cache = new ConcurrentHashMap<>();
    private volatile Instant cacheLoadedAt;
    private static final Duration TTL = Duration.ofMinutes(5);
    private final RestTemplate restTemplate;

    public PublicKey getPublicKey(String kid) throws AuthFetchFailedException {
        if (isCacheStale()) {
            refresh();   // ← retry 2회 포함
        }
        PublicKey key = cache.get(kid);
        if (key == null) {
            // kid 미매칭 → 회전 직후일 수 있음 → 한 번 더 fetch (최신 키 갱신)
            refresh();
            key = cache.get(kid);
        }
        return key;   // null 이면 호출자가 401 처리
    }

    private void refresh() throws AuthFetchFailedException {
        long[] retryDelaysMs = {0L, 200L, 500L};   // 첫 시도 + retry 2회
        Exception lastException = null;
        for (long delay : retryDelaysMs) {
            if (delay > 0) Thread.sleep(delay);
            try {
                Map<String, Object> response = restTemplate.getForObject(jwksUrl, Map.class);
                List<Map<String, String>> keys = (List<Map<String, String>>) response.get("keys");
                ConcurrentMap<String, PublicKey> newCache = new ConcurrentHashMap<>();
                for (Map<String, String> jwk : keys) {
                    String kid = jwk.get("kid");
                    PublicKey pk = JwkParser.parse(jwk);   // Nimbus 사용
                    newCache.put(kid, pk);
                }
                this.cache.clear();
                this.cache.putAll(newCache);
                this.cacheLoadedAt = Instant.now();
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("[Auth] JWKS fetch attempt failed (delay={}ms)", delay, e);
            }
        }
        throw new AuthFetchFailedException("JWKS fetch failed after 2 retries", lastException);
    }
}
```

### 5.4 JwtCookieAuthFilter 동작

```java
// sync-agent-common/.../config/JwtCookieAuthFilter.java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jwt.cookie.enabled", havingValue = "true")
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private final JwksClient jwksClient;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Cookie cookie = WebUtils.getCookie(req, "accessToken");
        if (cookie == null) {
            chain.doFilter(req, res);   // 토큰 없음 → SecurityConfig 가 401 처리
            return;
        }

        String token = cookie.getValue();
        try {
            // ① header 디코드 → kid 추출
            String kid = JwtUtil.extractKid(token);

            // ② JWKS 캐시에서 매칭 공개키
            PublicKey publicKey = jwksClient.getPublicKey(kid);
            if (publicKey == null) {
                AuthErrorResponse.write(res, 401, AuthErrorCode.UNKNOWN_KEY_ID);
                return;
            }

            // ③ jjwt 검증 (서명 + exp + iss + aud)
            Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .requireIssuer("orchestrator-auth")
                .requireAudience("orchestrator")
                .build()
                .parseClaimsJws(token);

            // ④ SecurityContext set
            Long userId = Long.valueOf(jws.getBody().getSubject());
            String role = jws.getBody().get("role", String.class);
            JwtAuthenticationToken auth = new JwtAuthenticationToken(userId, role);
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(req, res);
        } catch (ExpiredJwtException e) {
            AuthErrorResponse.write(res, 401, AuthErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException e) {
            AuthErrorResponse.write(res, 401, AuthErrorCode.INVALID_SIGNATURE);
        } catch (JwtException e) {
            AuthErrorResponse.write(res, 401, AuthErrorCode.INVALID_TOKEN);
        } catch (AuthFetchFailedException e) {
            res.setHeader("Retry-After", "30");
            AuthErrorResponse.write(res, 503, AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }
}
```

## 6. 작업 순서 — 단계별 step

### Step 1. 모듈 신설 + 기본 설정 (~30분)
- [ ] `sync-orchestrator-auth/` 디렉토리 생성
- [ ] `build.gradle` 작성 (§4.2)
- [ ] root `settings.gradle` 에 `include 'sync-orchestrator-auth'` 추가
- [ ] `application.yml` 작성 (§4.3, ENC 값은 placeholder)
- [ ] `AuthApplication.java` (Spring Boot main)
- [ ] `JasyptConfig.java`
- [ ] **검증**: `./gradlew :sync-orchestrator-auth:bootRun` — 8096 listen 확인 (DB 연결까지)

### Step 2. Entity + Repository (~30분)
- [ ] `entity/AuthUser.java` — name 컬럼 포함, 단일 role='user'
- [ ] `entity/AuthRefreshToken.java` — jti PK, revoked 플래그
- [ ] `entity/AuthRsaKey.java` — kid PK, public/private(ENC)/active/expires
- [ ] `repository/AuthUserRepository.java` (findByUsername, count)
- [ ] `repository/AuthRefreshTokenRepository.java` (findByJtiAndRevokedFalse, revokeAllByUserId)
- [ ] `repository/AuthRsaKeyRepository.java` (findActiveTrue, findByExpiresAtAfter)
- [ ] **검증**: 부팅 시 **ddl-auto=update 가 엔티티 기반 3 테이블 자동 생성** (별도 SQL 실행 X)

> **테이블 생성 정책**: JPA `ddl-auto: update` 가 모든 스키마 변경 담당. SQL DDL 파일 별도 관리 X. 운영 배포 시에도 entity 가 진실원.

### Step 3. KeyService — RSA 페어 관리 (~1시간)
- [ ] `service/KeyService.java`
  - `getActiveKeyPair()` — active=true 캐시 또는 DB 조회
  - `loadAllValidKeys()` — JWKS 응답용 (active + 만료 안 된 비활성)
  - `generateNewKeyPair()` — KeyPairGenerator (RSA 2048bit)
  - `rotateKeys()` — 새 페어 INSERT + 기존 active=false + cleanup
  - `decryptPrivatePem(encPem)` — jasypt 복호화
- [ ] `bootstrap/InitialKeyLoader.java` — `@PostConstruct` 또는 `ApplicationReadyEvent`
  - active 키 0건이면 새 페어 생성 + INSERT
- [ ] **단위 테스트**: KeyService.generateNewKeyPair / rotateKeys / decryptPrivatePem
- [ ] **통합 테스트**: 첫 부팅 시 keys 0건 → 1건 자동 생성 (§12.6 매트릭스 #4)

### Step 4. TokenService — JWT 발급/검증 (~1시간)
- [ ] `service/TokenService.java`
  - `createAccessToken(userId, role)` — header.kid + payload(sub/role/jti/iat/exp/iss/aud) + signature
  - `createRefreshToken(userId)` — 동, type=refresh, 7일 exp + DB INSERT (jti)
  - `verifyAccessToken(token)` — kid → PublicKey → jjwt parseClaimsJws
  - `extractKid(token)` — header 디코드
- [ ] **단위 테스트**: 발급 → 검증 정상 / 위조 검증 실패 / exp 검증 / iss/aud 검증

### Step 5. UserService — 사용자 CRUD (~1시간)
- [ ] `service/UserService.java`
  - `addUser(username, password, name)` — BCrypt 해시 + INSERT (UNIQUE 충돌 → 409)
  - `listUsers()` — id/username/name/createdAt 만 (password_hash 제외)
  - `deleteMe(userId)` — count >= 2 체크 + refresh revoke + DELETE
  - `changeMyPassword(userId, current, newPw)` — current BCrypt 검증 + 새 hash UPDATE + refresh 전체 revoke
- [ ] **단위 테스트**: 마지막 1명 삭제 시도 → 409 / 비번 변경 시 모든 refresh revoke 확인 (§12.6 #21, #23)

### Step 6. AuthService — 로그인/refresh/로그아웃 (~1.5시간)
- [ ] `service/AuthService.java`
  - `login(username, password)` — BCrypt 검증 + locked_until 체크 + fail_count 처리 + 토큰 발급
  - `refresh(refreshTokenJwt)` — JWT 검증 + DB jti 조회 + 회전 (1회용) + 새 토큰 2개
  - `logout(refreshTokenJwt)` — jti revoked=true UPDATE
- [ ] **통합 테스트** (PG 연결): 로그인 성공 / 잘못된 비번 5회 → 423 / refresh 회전 / 로그아웃 후 갱신 불가 (§12.6 #6, #7, #15, #16, #22)

### Step 7. Controller — Auth/User/Jwks (~1시간)
- [ ] `controller/AuthController.java`
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
- [ ] `controller/UserController.java`
  - `POST /api/auth/users`
  - `GET /api/auth/users`
  - `DELETE /api/auth/users/me`
  - `PATCH /api/auth/users/me/password`
- [ ] `controller/JwksController.java`
  - `GET /.well-known/jwks.json` — Nimbus JWKSet 직렬화
- [ ] `config/SecurityConfig.java` — Spring Security 5
  - `/api/auth/login` permitAll
  - `/api/auth/refresh` permitAll (cookie 자체 검증으로 처리)
  - `/.well-known/**` permitAll
  - `/actuator/health` permitAll
  - 그 외 인증 필요 (자체 JWT cookie 인증 — auth 모듈 내부에서도 동일 패턴)
- [ ] **수동 검증** (curl): 로그인 → cookie 받기 → /me 호출 → 200 / refresh → 새 cookie / logout → /me 401

### Step 8. KeyRotationJob — 자정 회전 (~30분)
- [ ] `scheduler/KeyRotationJob.java` — `@Scheduled(cron = "${auth.rsa.rotation-cron}")`
  - 회전: 새 페어 생성 + INSERT + 기존 active=false UPDATE
  - cleanup: expires_at < NOW DELETE
- [ ] **테스트** (수동 트리거): 메서드 직접 호출 → DB 상태 확인 (active 1건 → 2건 + 기존 false / 만료 cleanup) (§12.6 #18, #19)

### Step 9. UserGeneratorCli — 별도 main (~1시간)
- [ ] `tools/UserGeneratorCli.java`
  - args 검증 (username, password, name)
  - PG 직접 connection (env JDBC_URL/USER/PW 또는 같은 yml jasypt 복호화)
  - BCrypt.hashpw + INSERT
  - 중복 시 stderr + exit 1 / 성공 시 stdout (id 반환)
- [ ] gradle task `createUser` 등록 (build.gradle §4.2)
- [ ] **수동 검증**: `./gradlew :sync-orchestrator-auth:createUser --args="admin pass1234 운영자"` → DB row 확인 + 두 번째 실행 시 UNIQUE 충돌 (§12.6 #5)

### Step 10. sync-agent-common 검증자 자산 (~2시간)
- [ ] `sync-agent-common/build.gradle` jjwt + Nimbus + Spring Security compileOnly 추가
- [ ] `auth/JwtAuthenticationToken.java`
- [ ] `auth/AuthErrorCode.java` (enum)
- [ ] `auth/AuthErrorResponse.java` (write 헬퍼)
- [ ] `client/JwksClient.java` (§5.3)
- [ ] `config/JwtCookieAuthFilter.java` (§5.4)
- [ ] **단위 테스트**: JwksClient retry 정책 (mock RestTemplate, 2회 fail → 503) / kid 미매칭 → 한 번 더 fetch
- [ ] **검증**: `./gradlew :sync-agent-common:build -x test` 성공
- [ ] **JAR 복사** — 메모리 룰: `sync-agent-bojo/libs/`, `sync-agent-others/libs/` 등 의존 모듈에 복사 (Phase 2~4 에서 본격 사용)

### Step 11. 통합 검증 — auth ↔ sync-agent-common (수동 ~30분)
- [ ] auth 모듈 기동 (8096)
- [ ] curl 시나리오:
  ```
  # 1. CLI 첫 사용자 발급
  ./gradlew :sync-orchestrator-auth:createUser --args="admin pass1234 운영자"

  # 2. 로그인
  curl -i -X POST http://localhost:8096/api/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username":"admin","password":"pass1234"}' \
       --cookie-jar cookies.txt

  # 3. /me 호출 (cookie 자동 첨부)
  curl -i http://localhost:8096/api/auth/me --cookie cookies.txt

  # 4. JWKS endpoint 조회
  curl http://localhost:8096/.well-known/jwks.json | jq

  # 5. 새 사용자 추가 (peer multiplication)
  curl -i -X POST http://localhost:8096/api/auth/users \
       -H "Content-Type: application/json" \
       -d '{"username":"alice","password":"alicePw!","name":"홍길동"}' \
       --cookie cookies.txt

  # 6. 사용자 목록
  curl http://localhost:8096/api/auth/users --cookie cookies.txt

  # 7. 본인 비번 변경
  curl -i -X PATCH http://localhost:8096/api/auth/users/me/password \
       -H "Content-Type: application/json" \
       -d '{"currentPassword":"pass1234","newPassword":"newPw!23"}' \
       --cookie cookies.txt

  # 8. 마지막 1명 탈퇴 시도 (alice 삭제 후 admin 시도)
  # alice 로그인 → DELETE /me → 정상
  # admin 다시 로그인 → DELETE /me → 409 Conflict

  # 9. 로그아웃
  curl -i -X POST http://localhost:8096/api/auth/logout --cookie cookies.txt

  # 10. 회전 강제 트리거 (테스트용 endpoint or 직접 메서드 호출)
  # 회전 후 /jwks 응답에 키 2개 (어제 + 오늘) 확인
  ```
- [ ] **회귀**: auth 모듈만 기동 — 다른 모듈/서비스 영향 없음 (검증자 통합은 Phase 2~4)

## 7. 검증 시나리오 매핑 (AUTH_DESIGN.md §12.6)

본 작업 (Phase 1 + 1.5) 에서 커버되는 시나리오:

| # | 시나리오 | Step |
|:--:|---|:--:|
| 1, 2 | yml ENC 복호화 (정상 / 잘못 PW) | Step 1 |
| 3 | 재부팅 시 RSA 개인키 ENC 복호화 | Step 3 |
| 4 | active RSA 키 0건 → 새 생성 | Step 3 (InitialKeyLoader) |
| 5 | UserGeneratorCli — 사용자 0명 → 1명 | Step 9 |
| 6, 7 | 로그인 BCrypt 검증 (정확/틀림+잠금) | Step 6 |
| 8, 9, 10 | JWT 서명 검증 (정상/위조/kid 미매칭) | Step 4 단위 + Step 10 통합 |
| 11 | JWT exp 만료 | Step 6 통합 |
| 13 | JWKS 응답 (활성 + 만료 안 된 비활성) | Step 7 (JwksController) |
| 14 | 검증자 모듈 JWKS 캐시 (첫 호출 fetch) | Step 10 단위 + Step 11 |
| 15, 16, 17 | refresh jti 검증 (정상/revoked/expired) | Step 6 통합 |
| 18, 19 | RSA 자정 회전 | Step 8 (수동 트리거) |
| 21 | 비번 변경 → refresh 전체 revoke | Step 5 통합 |
| 22 | 로그아웃 → refresh revoke | Step 6 통합 |
| 23, 24 | 마지막 1명 탈퇴 차단 / 잠긴 사용자 로그인 | Step 5, Step 6 통합 |

미커버 (Phase 2~4 또는 별 검증):
- #12 (iss/aud 불일치 — Step 4 에서 단위로는 가능)
- #20 (회전 후 검증자 5min 갭 — Phase 2 이후 통합 검증)
- #25, #26, #27 (검증자 path 정책 — Phase 2~4)
- #28 (X-API-Key 흐름 회귀 — Phase 2 이후)

## 8. 영향 범위 / 회귀

### 신규 (영향 0)
- `sync-orchestrator-auth/` — 신규 모듈, 다른 모듈 의존 없음
- ~~SQL DDL 파일~~ — entity 가 진실원, ddl-auto=update 자동 생성
- Orchestrator PG 의 `orchestrator` DB — 새 테이블 3개 추가 (기존 테이블 변경 없음)

### sync-agent-common 변경 (의존 모듈 영향 검토 필요)
- `build.gradle` 의존성 추가 (jjwt, Nimbus, Spring Security compileOnly)
- 새 파일 5개 (JwtCookieAuthFilter, JwksClient, AuthErrorResponse, AuthErrorCode, JwtAuthenticationToken)
- **기존 ApiKeyFilter / 다른 파일 변경 0**
- **회귀 검증**:
  - `./gradlew :sync-agent-common:build -x test` 성공
  - `sync-agent-bojo/libs/` 등에 JAR 복사 후 sync-agent-bojo 빌드 성공
  - sync-agent-bojo 기동 시 ApiKeyFilter 동작 변화 0 (JwtCookieAuthFilter 는 `@ConditionalOnProperty` 로 비활성)

### Frontend / 운영 화면 — 영향 0 (Phase 5 에서 처리)

## 9. 미진행 / 다음 단계

| Phase | 내용 | 다음 세션 |
|:--:|---|---|
| Phase 2 | Backend (8080) 통합 — SecurityConfig + 의존성 추가 + callback permit | 별 세션 |
| Phase 3 | api-provider (8095) 통합 + MockApiKeyController `@ConditionalOnProperty` + denyAll | 별 세션 |
| Phase 4 | api-collector (8084/8094) 통합 + MockApiController 동 | 별 세션 |
| Phase 5 | Frontend — 로그인 화면 / 글로벌 헤더 / 사용자 관리 화면 | 별 세션 |

## 10. 추정 시간

| Phase | Step | 예상 시간 |
|:--:|---|:--:|
| 1 | Step 1~9 (auth 모듈) | ~6.5h |
| 1.5 | Step 10 (sync-agent-common) | ~2h |
| 1+1.5 | Step 11 (통합 검증) | ~30min |
| | **총합** | **~9h (1~1.5일)** |

## 11. 작업 시작 전 확인 사항

- [ ] AUTH_DESIGN.md §17 승인 항목 (a)~(o) 사용자 OK 받음
- [ ] AUTH_FLOW.md 라이프사이클/Lazy 정책/응답 매트릭스 사용자 검토 완료
- [ ] Spring Boot 2.7.12 / Java 17 (또는 11) 환경 확인 — 다른 모듈과 통일
- [ ] jasypt password (`JASYPT_PASSWORD=sync-pipeline-secret-key-2024`) — 다른 모듈과 동일하게 사용 (4/29 PoC 패턴)
- [ ] 사용자 "진행하라" 명령 받음

## 12. 참고

- [docs/AUTH_DESIGN.md](../../../docs/AUTH_DESIGN.md) — 결정/설계 정리
- [docs/AUTH_FLOW.md](../../../docs/AUTH_FLOW.md) — 라이프사이클 가이드
- [docs/AUTH_DESIGN.md §9.3](../../../docs/AUTH_DESIGN.md) — 검증자 모듈별 endpoint 매핑
- [docs/AUTH_DESIGN.md §12.6](../../../docs/AUTH_DESIGN.md) — 키 검증 시나리오 28개 매트릭스
- [docs/AUTH_DESIGN.md §13.8](../../../docs/AUTH_DESIGN.md) — 인증 실패 응답 정책
- [todo/system/07-security.md](../../../todo/system/07-security.md) — 7-security 의 "로그인/인가" 4 항목
