# Auth — 토큰 / 키 / 검증 플로우 가이드

> 목적: 본 시스템 인증의 모든 키와 토큰이 **어떤 시점에 / 어디서 / 어떻게** 사용되고 검증되는지 시간순으로 풀어 설명.
> 동반 문서: [AUTH_DESIGN.md](AUTH_DESIGN.md) — 결정/설계 정리.
> 본 문서: 라이프사이클 시나리오 — "보안 처음 보는 사람도 따라갈 수 있도록".

---

## 0. 들어가며 — 비유

본 시스템 = **영화관 + 회원제 클럽**.

- 운영자(사용자) = 회원
- Backend / api-provider 등 운영 모듈 = 상영관 출입구
- auth 모듈 = 회원 등록/카드 발급 카운터
- Access Token = **일회용 입장권** (15분 유효, 매 출입구마다 검사)
- Refresh Token = **회원 카드** (7일 유효, 입장권 재발급용)
- RSA 개인키 = auth 모듈의 **도장** (입장권에 찍는 진위 도장 — 외부 노출 금지)
- RSA 공개키 = 출입구가 가진 **도장 진위 확인용 견본** (공개해도 됨)
- JWKS endpoint = "오늘 사용 중인 도장 견본 보세요" 라고 auth 카운터가 게시판에 붙여놓는 것
- BCrypt = 회원 비밀번호 보관소의 일방향 자물쇠 (해킹당해도 비번 원본 복원 불가)
- JASYPT = 시스템 전체 **마스터 비밀번호** — 다른 자물쇠들의 자물쇠

---

## 1. 등장 인물 — 키와 토큰 한 컷

| # | 이름 | 형태 | 어디 사는가 | 누가 발급 | 누가 검증 | 본 작업 |
|:--:|---|---|---|---|---|:--:|
| 1 | **JASYPT_PASSWORD** | 짧은 문자열 | OS 환경변수 (모듈 공통) | 사람 (배포 시 입력) | jasypt 라이브러리 자동 | 기존 |
| 2 | **DB 자격증명** | 문자열 | application.yml `ENC(...)` + Orchestrator DB | 운영자 (UI 등록) | jasypt + Agent ThreadLocal | 기존 |
| 3 | **X-API-Key** (시스템 간) | 문자열 | yml ENC | 운영자 | 모듈의 ApiKeyFilter | 기존 |
| 4 | **Provide API Key** (외부 사용자) | 문자열 | api-provider DB | 운영자 (UI 등록) | api-provider 자체 | 기존 |
| 5 | **BCrypt password hash** | 일방향 해시 | PG `auth_users.password_hash` | auth 모듈 (가입/변경 시) | auth 모듈 (로그인 시) | **신규** |
| 6 | **RSA 개인키** | PEM 텍스트 (jasypt ENC) | PG `auth_rsa_keys.private_pem_enc` | auth 모듈 (자정 회전 자동) | (검증 안 함, 서명용) | **신규** |
| 7 | **RSA 공개키** | PEM 텍스트 | PG `auth_rsa_keys.public_pem` + JWKS endpoint | auth 모듈 (개인키와 페어) | 모든 검증자 모듈 | **신규** |
| 8 | **kid (key id)** | 짧은 문자열 (UUID) | PG `auth_rsa_keys.kid` + JWT header | auth 모듈 (RSA 페어와 함께) | 검증자 (header kid → 매칭 공개키) | **신규** |
| 9 | **Access Token (JWT)** | 긴 문자열 (3토막) | 클라이언트 cookie (httpOnly) | auth 모듈 (로그인/refresh 시) | 모든 검증자 모듈 (자체) | **신규** |
| 10 | **Refresh Token + jti** | 문자열 + PG row | 클라이언트 cookie + PG `auth_refresh_tokens` | auth 모듈 | auth 모듈 | **신규** |

> **약어**:
> - **PEM** = 키를 ASCII 텍스트 로 표현한 표준 형식 (`-----BEGIN PUBLIC KEY-----` 같은 거)
> - **JWT** = JSON Web Token. `헤더.페이로드.서명` 3토막 구조
> - **jti** = JWT ID. refresh token 의 고유 ID — DB 에 저장해서 revoke 가능하게 함
> - **JWKS** = JSON Web Key Set. 공개키 묶음을 JSON 으로 노출하는 표준 endpoint

> ⚠️ **자주 헷갈리는 부분 — RSA 키는 "매일 1쌍" 이 아닌 "약 8쌍 동시 존재"**
>
> - **서명용 (active)** = 매일 1쌍만 (오늘 active 키)
> - **검증용 (만료 안 된 비활성)** = 약 7쌍 더 보관 (이전 7일치)
> - **시스템 동시 보관** = **약 8쌍** = 1 active + 7 검증용
> - 이유: 7일 전 발급된 토큰도 만료 전까지 검증돼야 함 → 그 시점 active 였던 공개키 보관 필수
> - 자세한 메커니즘 = §13.5 참조

---

## 2. 시나리오 1 — auth 모듈 부팅 (시스템 시작)

```
[ 사람 ]                                 [ auth 모듈 8096 ]
  배포 시:
  $ JASYPT_PASSWORD=xxxx ./auth.sh       ① 환경변수 JASYPT_PASSWORD 로드
                                         ② application.yml 의 ENC(...) 복호화
                                            └ DB 자격증명 평문화 → PG 연결
                                         ③ DB schema 검사 / ddl-auto=update

  사용 중인 RSA 키 있나? ─── PG SELECT * FROM auth_rsa_keys WHERE active=true
                          │
                          ▼
                       ┌─ 있음 → 개인키 jasypt ENC 복호화 → 메모리 캐시
                       └─ 없음 → 새 RSA 페어 생성 (KeyPairGenerator)
                                  ↳ kid = UUID
                                  ↳ 개인키 jasypt ENC 후 INSERT
                                  ↳ 공개키 그대로 INSERT
                                  ↳ active=true, expires_at=오늘+8일

                                         ④ JWKS endpoint 활성화 (/.well-known/jwks.json)
                                         ⑤ 스케줄러 시작 (자정 회전, cron 0 0 0 * * ?)
                                         ⑥ ApplicationReady — 8096 listen
```

**이 시점 사용된 키**: JASYPT_PASSWORD (#1) + DB 자격증명 (#2) + RSA 개인키/공개키 (#6/#7) + kid (#8)

**검증 포인트**:
- JASYPT_PASSWORD 잘못 → APPLICATION FAILED TO START (yml ENC 복호화 실패)
- DB 자격증명 잘못 → APPLICATION FAILED TO START
- RSA 개인키 jasypt ENC 복호화 실패 → 부팅 중단

---

## 3. 시나리오 2 — 검증자 모듈 부팅 (Backend / api-provider)

```
[ Backend 8080 ]
  ① yml ENC 복호화 (자체 JASYPT_PASSWORD)
  ② DB / 다른 모듈 연결 준비
  ③ JwtCookieAuthFilter Bean 등록 (Spring Security 필터 체인에 박힘)
  ④ JwksClient 준비 (auth 모듈 URL = http://auth:8096/.well-known/jwks.json)
  ⑤ JWKS 캐시 비어있음 (아직 한 번도 안 가져옴) ← Lazy 정책
  ⑥ ApplicationReady — 8080 listen
```

### 3.1 Lazy fetch 정책 (본 시스템 채택)

| 항목 | 본 시스템 (Lazy) | 대안 (Eager) |
|---|---|---|
| 부팅 시 JWKS fetch | ❌ 안 함 | ✅ 자동 fetch |
| 첫 API 호출 응답 시간 | +1 fetch 라운드트립 (~수십 ms) | 즉시 캐시 hit |
| auth 모듈 다운 시 검증자 부팅 | ✅ 가능 — 부팅 자체는 정상 | 부팅 fail 또는 캐시 비어있는 채로 |
| 부팅 순서 강제 | ❌ 자유 | auth 먼저 부팅 권장 |
| 운영 유연성 | 높음 — 검증자만 재시작 가능 | 의존 강함 |

**Lazy 채택 이유** (§AUTH_DESIGN.md §17 (n) 결정):
1. 모듈 4~5개가 각자 부팅 — 부팅 순서 강제 X
2. auth 다운 상태에서도 검증자 재시작 가능 (예: api-provider 만 hotfix 배포)
3. 첫 호출 비용 무시할 수준 (운영자 첫 진입 시 +수십ms — 사용자 체감 X)

### 3.2 Lazy fetch 흐름 — 한 컷

```
[ 검증자 부팅 ]
   JwksCache = {} (빈 Map)
        ↓
   "준비 완료" — listen 시작

[ 첫 API 호출 들어옴 (예: 10분 후 운영자 진입) ]
   GET /api/operations
   Cookie: accessToken=...
        ↓
   JwtCookieAuthFilter
   ① cookie 추출 + kid 추출 → "K-05-04-uuid"
   ② 캐시 lookup → 비어있음 ❌
   ③ JwksClient.fetch():
        - GET http://auth:8096/.well-known/jwks.json
        - 실패 시 retry (200ms / 500ms) — 옵션 C 정책
        - 그래도 실패 → 503 응답 (§14 시나리오 13)
   ④ 응답 받아 캐시 채움 (8개 키)
       cacheLoadedAt = NOW
   ⑤ kid 매칭 publicKey → 검증
   ⑥ Controller 진입

[ 그 후 5분간 ]
   매 호출마다 캐시 hit → 검증 (네트워크 0회)

[ 5분 경과 후 다음 호출 ]
   NOW - cacheLoadedAt > 5min → 만료
   다시 fetch → 캐시 갱신 (실패 시 동일 retry → 503)
```

**이 시점 사용된 키**: JASYPT_PASSWORD (#1) + DB 자격증명 (#2). RSA 공개키는 **첫 API 호출 들어올 때까지 fetch 안 함**.

---

## 4. 시나리오 3 — 첫 사용자 발급 (UserGeneratorCli, 최초 1회)

운영 배포 직후 시스템에 사용자 0명 → 로그인 자체 불가 → CLI 로 1명 미리 만들어둠.

```
[ 사람 ]                                 [ UserGeneratorCli ]
  $ ./gradlew createUser \              ① 인자 검증 (username, password, name)
      --args="admin pass1234 운영자"     ② DB 직접 connection (Spring 안 띄움)
                                         ③ BCrypt.hashpw(password, gensalt(12))
                                            └ 결과: "$2a$12$xxxx...." (60자 해시)
                                         ④ INSERT INTO auth_users (
                                              username='admin',
                                              password_hash='$2a$12$...',
                                              name='운영자',
                                              role='user',
                                              created_at=NOW()
                                            )
                                         ⑤ 성공 → stdout: "user_id=1 created"
                                         ⑥ 종료 (CLI 끝)
```

**이 시점 사용된 키**: BCrypt password hash (#5)

> **중요**: 비밀번호 평문은 **저장 안 됨**. BCrypt 해시 만 저장. 해시는 단방향 — 비번 원본 복원 불가능 (DB 가 통째로 유출돼도 안전).

> **비상 진입로**: 시스템 운영 중에 어떤 이유로든 사용자 0명이 되면 (마지막 1명 차단 정책에도 race condition 등 비상 케이스), 운영자가 다시 CLI 돌려서 진입 가능.

---

## 5. 시나리오 4 — 로그인

```
[ 사용자 ]    [ Frontend 3000 ]        [ Auth 모듈 8096 ]
  ID/PW 입력 ─►  POST /api/auth/login   ① username 으로 PG SELECT
              { "username": "admin",      └ 없으면 401 (timing safe — bcrypt 더미 1회 실행)
                "password": "pass1234"}   └ locked_until > NOW → 423 Locked

                                         ② BCrypt.matches(password, password_hash)
                                            └ 실패 → fail_count++, 5회면 locked_until = NOW+30min
                                                   → 401
                                         ③ 성공:
                                            - fail_count=0, locked_until=null UPDATE
                                            - last_login_at=NOW UPDATE

                                         ④ active RSA 개인키 + kid 메모리에서 가져옴

                                         ⑤ Access Token 만들기 (jjwt):
                                            header  = { alg: RS256, typ: JWT, kid: "K-2026-05-04-uuid" }
                                            payload = {
                                              sub: 1,            // user id
                                              role: "user",
                                              jti: <UUID>,
                                              iat: <epoch초>,
                                              exp: <iat + 15min>,
                                              iss: "orchestrator-auth",
                                              aud: "orchestrator"
                                            }
                                            signature = RSA-PSS(privateKey, header + "." + payload)
                                            => "eyJhbGciOi....헤더.페이로드.서명"

                                         ⑥ Refresh Token 만들기:
                                            새 jti = UUID
                                            INSERT auth_refresh_tokens (
                                              jti, user_id=1, expires_at=NOW+7day, revoked=false
                                            )
                                            refresh JWT = jjwt 로 sub/jti/exp/type=refresh 넣어 RSA 서명

                                         ⑦ 응답:
                                            Set-Cookie: accessToken=<JWT>; HttpOnly; SameSite=Strict;
                                                         Max-Age=900; Path=/
                                            Set-Cookie: refreshToken=<JWT>; HttpOnly; SameSite=Strict;
                                                         Max-Age=604800; Path=/api/auth
                                            Body: { user: { id, username, name, role } }
            ◄─── cookie 두 개 + user info ─────

  Frontend: 메인 화면으로 redirect (/)
```

**이 시점 사용된 키**: BCrypt (#5) + RSA 개인키 (#6) + kid (#8) + Access Token 발급 (#9) + Refresh Token 발급 (#10)

---

## 6. 시나리오 5 — 정상 API 호출 (15분 안)

```
[ 브라우저 ]                    [ Backend 8080 ]                       [ Auth 8096 ]
  GET /api/operations
  Cookie: accessToken=eyJhbGc...  ① JwtCookieAuthFilter 동작
  (자동 첨부)                        ② cookie 에서 accessToken 추출
                                    ③ JWT 헤더 디코드 → kid 추출

                                    ④ JWKS 캐시 조회
                                       └ 캐시 비어있음 / TTL 만료
                                          → GET /.well-known/jwks.json ──►  활성+유효 공개키
                                                                            JSON 응답
                                       └ 5min 캐시 저장
                                       └ kid 매칭 공개키 가져옴

                                    ⑤ jjwt 로 검증:
                                       Jwts.parserBuilder()
                                           .setSigningKey(matchedPublicKey)
                                           .build()
                                           .parseClaimsJws(token);
                                       └ 서명 위조 → 예외 → 401
                                       └ exp 지남 → ExpiredJwtException → 401
                                       └ iss/aud 불일치 → 예외 → 401

                                    ⑥ SecurityContextHolder.getContext()
                                          .setAuthentication(userId, role)

                                    ⑦ Controller 진입
                       ◄────────── 200 + 데이터
```

**이 시점 사용된 키**: Access Token (#9) + RSA 공개키 (#7, JWKS 캐시) + kid (#8)

> **stateless 검증**: Backend 가 auth 모듈/DB 안 봄. 캐시된 공개키 1번에 자체 검증.
> JWKS fetch 도 5분에 1번 (또는 회전 직후 cache miss 시).

> **중요**: 기존 X-API-Key (#3) 흐름 영향 없음. ApiKeyFilter 는 시스템 간 호출 (Backend → Agent/Proxy) 전용 — 운영자 호출 흐름과 path 분리.

---

## 7. 시나리오 6 — Access Token 만료 → 자동 갱신

```
[ 브라우저 ]                    [ Backend 8080 ]                       [ Auth 8096 ]
  GET /api/operations
  Cookie: accessToken=...        ① JwtCookieAuthFilter
  (15분 지난 만료 토큰)             ② jjwt 검증 → exp 지남 → 401
                       ◄────────  401 Unauthorized

  axios interceptor 자동:
  POST /api/auth/refresh          ──────────────────────────────────►   ① refresh JWT 검증
  Cookie: refreshToken=...                                                  - 서명 (RSA 공개키)
  (7일 안 만료 안 됨)                                                       - exp
                                                                            - type=refresh
                                                                          ② jti 추출
                                                                          ③ PG SELECT auth_refresh_tokens
                                                                              WHERE jti=? AND NOT revoked
                                                                              └ 없음 / revoked → 401
                                                                          ④ 회전 (1회용):
                                                                              - 기존 jti revoked=true UPDATE
                                                                              - 새 jti 생성
                                                                              - INSERT 새 row (expires_at=NOW+7day)
                                                                          ⑤ 새 access + 새 refresh 발급
                                                                             (시나리오 4 의 ⑤⑥)
                                  ◄──────────────────────────────────  Set-Cookie 두 개 (덮어쓰기)

  axios: 원 요청 재시도
  GET /api/operations
  Cookie: accessToken=새 토큰    ✅ 200 OK
                       ◄────────  데이터
```

**이 시점 사용된 키**: 만료된 Access (#9) + Refresh (#10) + RSA 개인키/공개키 (#6/#7) + kid (#8)

> **사용자는 모름** — 화면에선 그냥 "버튼 눌렀더니 결과 나옴" 으로 보임. 백그라운드에서 갱신 일어남.

> **회전 정책**: refresh 1회용. 이번에 쓰면 기존 jti 즉시 무효화 + 새 jti 발급. **만약 같은 refresh 가 두 번 들어오면 두 번째는 fail** → 탈취 의심 신호 (감지 가능).

---

## 8. 시나리오 7 — Refresh Token 도 만료 (7일 경과)

```
[ 브라우저 ]                                                             [ Auth 8096 ]
  POST /api/auth/refresh ──────────────────────────────────────────►    ① refresh JWT 검증
  Cookie: refreshToken=... (7일 지남)                                       └ exp 지남 → 401
                       ◄────────────────────────────────────────────  401

  Frontend interceptor:
    - refresh 도 401 → window.location = '/login'
    - "다시 로그인 해주세요" 메시지 (선택)

  사용자: 로그인 화면에서 ID/PW 다시 입력 → 시나리오 4 처음부터
```

**이 시점 사용된 키**: 만료된 Refresh (#10)

---

## 9. 시나리오 8 — 사용자 추가 (peer multiplication)

로그인된 사용자가 새 사용자 추가. 모두 동급 권한 (role='user').

```
[ 운영자 alice ]               [ Frontend ]                       [ Auth 8096 ]
  /users 화면 진입
  "새 사용자 추가" 클릭
  모달:
    ID: bob
    PW: passXYZ
    이름: 홍길동
  [등록] 클릭 ───────────────►  POST /api/auth/users
                                Cookie: accessToken=...
                                Body: {
                                  username: "bob",
                                  password: "passXYZ",
                                  name: "홍길동"
                                }                            ──►   ① JwtCookieAuthFilter — 토큰 검증 OK
                                                                    ② input 검증 (username 중복, PW 정책, name 비어있지 않음)
                                                                    ③ BCrypt.hashpw(password)
                                                                       └ 결과: "$2a$12$xxxx..." (해시)
                                                                    ④ INSERT auth_users (
                                                                         username='bob', password_hash='$2a$12$..',
                                                                         name='홍길동', role='user', ...)
                                                                       └ UNIQUE 충돌 → 409
                                                                    ⑤ 응답: { id, username, name, createdAt }

  Frontend: alert("등록 완료. ID/PW 를 별도 채널로 전달하세요")
  Alice: bob 에게 메일/메신저 로 "ID=bob / PW=passXYZ" 전달
  Bob: 받은 ID/PW 로 로그인 (시나리오 4) → 본인이 비번 변경 권장 (시나리오 9)
```

**이 시점 사용된 키**: Access Token (#9) + 새 BCrypt hash (#5)

> **note**: 등록자 정보 (alice) 저장 안 함 — `registered_by` 컬럼 없음. 새 사용자는 자기 자신의 row 만 가짐.

---

## 10. 시나리오 9 — 본인 비밀번호 변경

```
[ 사용자 bob ]                 [ Frontend /users/me ]              [ Auth 8096 ]
  현재 비번: passXYZ
  새 비번: bobSecure!23         PATCH /api/auth/users/me/password
  [변경] 클릭 ───────────────►  Cookie: accessToken=...
                                Body: {
                                  currentPassword: "passXYZ",
                                  newPassword: "bobSecure!23"
                                }                            ──►   ① JwtCookieAuthFilter — 토큰 검증 → userId=2 추출
                                                                    ② SELECT password_hash FROM auth_users WHERE id=2
                                                                    ③ BCrypt.matches(currentPassword, hash)
                                                                       └ 실패 → 400 ("현재 비번 불일치")
                                                                    ④ 새 PW 정책 검증
                                                                    ⑤ BCrypt.hashpw(newPassword) → password_hash UPDATE
                                                                    ⑥ ⚠️ UPDATE auth_refresh_tokens
                                                                          SET revoked=true WHERE user_id=2
                                                                       └ 본인의 모든 refresh 무효화
                                                                          (다른 디바이스 강제 로그아웃 효과)
                                                                    ⑦ 응답: 204 No Content
```

**이 시점 사용된 키**: Access Token (#9) + 기존 BCrypt hash (#5) + 새 BCrypt hash (#5) + 모든 Refresh Token revoke (#10)

> **왜 모든 refresh revoke?**: 비번 변경의 핵심 시나리오 = "내 cookie 가 탈취당했을지 모르니 갱신". 탈취된 cookie 의 refresh 도 같이 무효화해야 의미 있음.
>
> **현재 cookie 의 access 는 만료(15min) 까지 유효** — 다음 refresh 시도 시 401 → /login redirect (자연 종료).

---

## 11. 시나리오 10 — 본인 탈퇴

```
[ 사용자 bob ]                 [ Frontend /users/me ]              [ Auth 8096 ]
  [탈퇴] 클릭
  "정말 탈퇴하시겠어요?" 확인
  [예] ─────────────────────►   DELETE /api/auth/users/me
                                Cookie: accessToken=...
                                                              ──►   ① JwtCookieAuthFilter — userId=2 추출
                                                                    ② SELECT COUNT(*) FROM auth_users
                                                                       └ count <= 1 → 409 ("마지막 사용자는 탈퇴 불가")
                                                                    ③ UPDATE auth_refresh_tokens
                                                                          SET revoked=true WHERE user_id=2
                                                                    ④ DELETE FROM auth_users WHERE id=2
                                                                    ⑤ 응답: 204 No Content
                                                                       Set-Cookie: accessToken=; Max-Age=0
                                                                       Set-Cookie: refreshToken=; Max-Age=0; Path=/api/auth

  Frontend: cookie 만료됨 → window.location = '/login'
  bob 의 다음 호출은 cookie 없음 → 401 → /login
```

**이 시점 사용된 키**: Access Token (#9) + 모든 Refresh revoke (#10) + 사용자 row 자체 삭제

> **마지막 1명 차단**: 시스템에 사용자 0명 = 로그인 불가 상태 방지. 카운트 1개 query — 부하 0.

---

## 12. 시나리오 11 — 로그아웃

```
[ 사용자 ]                     [ Frontend ]                       [ Auth 8096 ]
  헤더의 [로그아웃] 클릭        POST /api/auth/logout
                                Cookie: refreshToken=...
                                                              ──►   ① refresh jti 추출
                                                                    ② UPDATE auth_refresh_tokens
                                                                          SET revoked=true WHERE jti=?
                                                                    ③ 응답: 204
                                                                       Set-Cookie: accessToken=; Max-Age=0
                                                                       Set-Cookie: refreshToken=; Max-Age=0

  Frontend: window.location = '/login'
```

**이 시점 사용된 키**: Refresh Token jti (#10)

> **access token 자체는 만료(15분)까지 유효** — blacklist 없음 정책.
> refresh 갱신 차단되어 사실상 종료. 이론상 15분 안에 탈취된 access 사용 가능하지만 — 매우 짧은 윈도우 + 브라우저는 cookie 지워졌으므로 같은 사용자가 다시 호출하지 못함.

---

## 13. 시나리오 12 — RSA 자정 회전 (자동)

매일 자정 auth 모듈이 자동 실행. 보안 강화.

```
[ Auth 모듈 스케줄러 ]                    [ PG auth_rsa_keys ]
  cron "0 0 0 * * ?" 발동
  ① 새 RSA 페어 생성 (KeyPairGenerator)
     - kid = 새 UUID
     - 개인키 jasypt ENC

  ② 트랜잭션:
     - UPDATE 기존 active=true 행들 → active=false
       (이전 키들은 검증용으로만 남음)
     - INSERT 새 행 (
         kid=새UUID,
         private_pem_enc=ENC(새 개인키),
         public_pem=새 공개키,
         active=true,
         expires_at=NOW+8day
       )

  ③ Cleanup:
     DELETE FROM auth_rsa_keys WHERE expires_at < NOW
     (8일 지난 키는 검증도 못 하니 폐기)

  ④ 메모리 캐시 갱신
     - 새 active 키 (서명용) 교체
     - JWKS 응답도 갱신 (새 공개키 포함, 만료 안 된 이전 공개키들 유지)

[ 다른 검증자 모듈 ]
  - 5분 캐시 TTL 후 자동 fetch — 새 kid 인식
  - 5분 갭 동안: 새 토큰의 kid 가 캐시에 없으면 401 — refresh 시도 시 새 access 받게 됨 (자동 회복)
  - 만료 안 된 이전 kid 의 토큰은 정상 검증 (이전 공개키들도 JWKS 에 포함되니까)
```

**이 시점 사용된 키**: 새 RSA 페어 (#6/#7/#8) + 이전 RSA 키들 (만료 전까지 검증용)

> **8일 보관 이유**: refresh 7일 + 1일 여유. 7일 전 발급된 토큰도 검증 가능해야 하니까.

> **회전 효과**: RSA 개인키가 어떤 식으로 노출됐다 해도 24시간 이내에 새 키로 교체됨 — 노출 영향 시간 제한.

---

## 13.5 보강 — 시스템에 동시 존재하는 RSA 키 (왜 8쌍인가)

매일 자정 회전이지만, **시스템엔 보통 8쌍이 동시 존재**. "매일 새 키 = 1쌍" 이 시스템 전체 키 수가 아님.

### 13.5.1 5/4 자정 회전 직후 PG 상태

```
SELECT * FROM auth_rsa_keys ORDER BY created_at DESC;

| kid          | 만든 날 | active | expires_at  | 용도                |
|--------------|--------|--------|-------------|---------------------|
| K-05-04-uuid | 5/4    | true   | 5/12        | 새 토큰 서명 + 검증  |  ← 오늘 만든 1쌍 (active)
| K-05-03-uuid | 5/3    | false  | 5/11        | 검증만               |
| K-05-02-uuid | 5/2    | false  | 5/10        | 검증만               |
| K-05-01-uuid | 5/1    | false  | 5/9         | 검증만               |
| K-04-30-uuid | 4/30   | false  | 5/8         | 검증만               |
| K-04-29-uuid | 4/29   | false  | 5/7         | 검증만               |
| K-04-28-uuid | 4/28   | false  | 5/6         | 검증만               |
| K-04-27-uuid | 4/27   | false  | 5/5         | 검증만               |
─────────────────────────────────────────────────────────────────────
│ K-04-26-uuid│  4/26  │  -    │  5/4 (DELETED at 자정 cleanup) ✗  │
```

**총 8쌍** = 1 active + 7 검증용. 자정 cleanup 으로 9쌍째 (8일 전 거) 가 빠지면서 균형 유지.

### 13.5.2 왜 7쌍이나 더 보관?

이전 토큰들도 검증해야 하기 때문.

**예시**: 5/3 13:00 사용자 A 가 로그인 → access (kid=`K-05-03`, exp=`5/3 13:15`) 발급.

```
시간 흐름:

5/3 13:00  로그인          → access(kid=K-05-03)         어제 active 키
5/3 13:14  refresh         → 새 access(kid=K-05-03)       (자정 전이라 그대로)
5/4 00:00  자정 회전        → active 가 K-05-04 로 교체됨
                              K-05-03 은 active=false 로 (검증용 보관)
5/4 09:00  refresh          → 새 access(kid=K-05-04)       오늘 active 키
... 사용자가 계속 시스템 사용 ...
5/10 12:55 마지막 refresh   → access(kid=K-05-10)
5/10 13:10 마지막 호출       → 검증 OK → 응답
```

이 동안 **여러 active 키가 거쳐갔지만 모두 검증 가능**. 만약 K-05-03 을 즉시 폐기했다면 5/4 09:00 refresh 시도가 401 나서 강제 로그아웃 됐을 것.

### 13.5.3 보관 기간 산식 = 7일 + 1일 = 8일

| 토큰 종류 | 최대 수명 | 보관 영향 |
|---|---|---|
| Access | 15분 | 짧아서 무시 가능 |
| **Refresh** | **7일** | **8일 보관 산식의 결정 요인** |

→ 가장 오래 살아있을 수 있는 토큰 = 7일 전 active 키로 서명된 refresh.
→ 그 토큰이 만료될 때까지 매칭 공개키 보관 필수.
→ **7일 + 1일 (안전 여유) = 8일**.

8일 지나면 그 키로 서명된 토큰은 어차피 모두 만료 (refresh 도 7일 한도 넘었으니 갱신 불가) → cleanup 안전.

### 13.5.4 kid 의 역할 — 8쌍 중 어느 거?

시스템에 공개키가 8개 동시 존재 → 검증자는 **각 토큰이 어느 키로 서명됐는지 알아야 함**. 이게 `kid` (Key ID).

```
토큰 header (jjwt 가 자동 박음):
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "K-05-03-uuid"   ← 이걸로 매칭 결정
}
                ↓
검증자 측 처리:
① header.kid 추출 = "K-05-03-uuid"
② JWKS 캐시에서 매칭 공개키 lookup
③ 없으면 → /.well-known/jwks.json fetch (5분 캐시)
④ 매칭 공개키로 서명 검증
```

→ kid 없으면 검증자는 8개 공개키 중 어느 거 써야 할지 모름. 시도해 본다 해도 8번 검증 시도 = 7번 fail + 1번 OK = 비효율.
→ **kid 가 있어 1번에 매칭 가능**.

### 13.5.5 JWKS endpoint 응답 형태 — 8쌍 모두 노출

`GET /.well-known/jwks.json` 응답 (5/4 자정 회전 직후 시점):

```json
{
  "keys": [
    { "kid": "K-05-04-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" },
    { "kid": "K-05-03-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" },
    { "kid": "K-05-02-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" },
    { "kid": "K-05-01-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" },
    { "kid": "K-04-30-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" },
    { "kid": "K-04-29-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" },
    { "kid": "K-04-28-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" },
    { "kid": "K-04-27-uuid", "kty": "RSA", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" }
  ]
}
```

**8개 모두 응답** — active 1개 + 만료 안 된 비활성 7개. 검증자는 이걸 통째 캐시 (5min TTL).

### 13.5.6 한 줄 정리

> **서명용 (auth 모듈)** = active 1쌍의 개인키 / **검증용 (모든 검증자 모듈)** = 8쌍의 공개키 (JWKS 캐시)
>
> 매일 자정 = active 1쌍 새로 추가 + 8일 지난 1쌍 폐기 → **시스템 총 8쌍 균형 유지**.

---

## 14. 시나리오 13 — auth 모듈 다운 시

### 14.1 캐시 5분 안 — 영향 없음

```
[ 검증자 모듈들 ]                       [ Auth 모듈 ]
  JWKS 캐시 보유 (5min TTL 안)            다운 (네트워크 단절 / 재시작 중 등)

  운영자 호출:
    GET /api/operations
    Cookie: accessToken=...
       ↓
    Backend ① cookie 추출
            ② kid 추출
            ③ 캐시 매칭 공개키 있음 → 검증 OK
            ④ 200 응답

  → 캐시 5분 동안은 정상 동작. auth 잠깐 다운 영향 없음.
```

**영향받는 것 (5min 안)**:
- 신규 로그인 (POST /api/auth/login) 불가 — auth 직접 호출이라 즉시 503
- access 만료 후 refresh 갱신 (POST /api/auth/refresh) 불가 — 동일

### 14.2 캐시 만료 후 + auth 여전히 다운 — JWKS fetch 실패

```
[ 검증자 모듈 ]
  GET /api/operations
  Cookie: accessToken=...
       ↓
  JwtCookieAuthFilter
  ① cookie 추출 + kid 추출
  ② 캐시 만료 (NOW - cacheLoadedAt > 5min)
       ↓
  ③ JwksClient.fetch() 호출:
       ┌── 첫 시도: GET http://auth:8096/.well-known/jwks.json
       │     ❌ 실패 (Connection refused / Timeout / 5xx)
       │
       ├── 1차 retry (200ms 대기)
       │     ❌ 실패
       │
       └── 2차 retry (500ms 대기)
             ❌ 실패
       ↓
  ④ 503 응답:
       Status: 503 Service Unavailable
       Header: Retry-After: 30
       Body: {
         "error": "AUTH_SERVICE_UNAVAILABLE",
         "message": "인증 서버 응답 없음 — 잠시 후 다시 시도하세요"
       }
       ↓
  ⑤ 운영 모니터링 후크 (자동):
     - log.error("[Auth] JWKS fetch failed after 2 retries", e)
     - meterRegistry.counter("auth.jwks.fetch.failed").increment()
     - (선택) 알람 시스템 push
```

**총 retry 시간**: ~700ms. 일시적 끊김은 자동 회복, 진짜 다운이면 빠르게 503.

### 14.3 운영 모니터링 — 3중 후크

| 신호 | 채널 | 효과 |
|---|---|---|
| **503 응답 ↑** | 검증자 모듈 access log | 다수 발생 시 운영자 즉시 인지 |
| **`auth.jwks.fetch.failed` 카운터** | Prometheus → Grafana | 임계치 초과 → Slack/이메일 알람 |
| **auth 모듈 `/actuator/health`** | 별도 health check 시스템 | 다운 즉시 감지 (검증자 503 발생 전에) |

→ 어느 채널이든 막혀도 다른 채널이 잡아내 — 운영자 알람 보장.

### 14.4 401 vs 503 응답 매트릭스

운영 측면에서 **403 (자격 문제) vs 503 (시스템 문제)** 구분이 중요. 자세한 분류는 `AUTH_DESIGN.md §13.8` 참조.

요약:

| 응답 | 의미 | 운영 대응 |
|:--:|---|---|
| **401** | "당신 자격증명에 문제 있음" | 보통 정상 (의도된 동작). 이상 빈도 시 추적 |
| **503** | "우리 시스템에 문제 있음" | **즉시 alert** — 운영자 개입 필요 |

### 14.5 완화

- actuator/health 모니터링
- auth 모듈 재기동 자동화 (예: systemd / docker restart policy)
- 다중화 (auth 모듈 2 인스턴스 + LB) — 필요 시 후속 작업

---

## 15. 시나리오 14 — 키 탈취 시나리오 / 방어

| 시나리오 | 위험 | 방어 |
|---|---|---|
| **A. XSS 로 cookie 탈취** | 악성 스크립트가 사용자 cookie 읽음 | **httpOnly cookie** — JS 가 못 읽음. XSS 자체는 발생 가능하지만 토큰은 안전 |
| **B. CSRF — 다른 사이트가 우리 도메인 자동 호출** | 사용자 cookie 자동 첨부 → 위조 요청 | **SameSite=Strict** — 다른 origin 의 cross-site 요청에 cookie 자동 차단 |
| **C. Access Token 탈취 (다른 경로)** | 위조 가능 — 15분간 | 짧은 만료 (15min) — 피해 시간 제한. 비번 변경 시 refresh 전체 revoke 로 추가 차단 |
| **D. Refresh Token 탈취** | 7일 동안 access 갱신 가능 — 위험 | 1회용 회전 — 한 번 쓰면 무효화. 정상 사용자가 갱신 시도 시 토큰 무효 → 차단 + 운영자 인지. SameSite=Strict 로 cross-site 차단 추가 |
| **E. RSA 개인키 (DB) 탈취** | 모든 토큰 위조 가능 — 매우 위험 | 1) jasypt ENC 저장 (DB 만 털려도 안 됨). 2) **자정 회전** (24시간 후 새 키로 교체). 3) DB 권한 분리 |
| **F. JASYPT_PASSWORD 노출** | 모든 yml ENC 와 RSA 개인키 ENC 복호화 가능 — 치명적 | 1) **환경변수만 사용** (yml/코드에 박지 않음). 2) 운영 secret 관리 시스템 (Vault 등). 3) 회전 시 일괄 재암호화 + 재배포 (별 작업) |
| **G. BCrypt password DB 유출** | 비번 brute force | 1) **단방향 해시** — 원본 복원 불가. 2) round 12 — 한 hash 검증 ~250ms. 3) salt 자동 (BCrypt 자체) |
| **H. 마지막 사용자 탈퇴 race** | 동시 탈퇴 시 0명 됨 | UI count 체크 + DB transaction 또는 락. 그래도 0명 시 CLI 비상 진입로 |

---

## 16. 키 라이프사이클 요약

| 키 / 토큰 | 발급 시점 | 사용 시점 | 만료/회전 | 폐기 시점 |
|---|---|---|---|---|
| JASYPT_PASSWORD | 배포 시 사람이 입력 | 모든 모듈 부팅마다 yml ENC 복호화 | 운영 정책 (회전 시 일괄 재암호화 + 재배포) | 새 PW 로 교체 시 |
| DB 자격증명 | 운영자가 UI 등록 | Agent ThreadLocal | 운영자가 변경 | row 삭제 시 |
| X-API-Key | 운영자가 yml ENC 등록 | 시스템 간 호출마다 | 수동 회전 | 새 키로 교체 시 |
| Provide API Key | 운영자가 UI 등록 | 외부 호출마다 | 운영자가 변경 | row 삭제 시 |
| **BCrypt password hash** | 사용자 가입 / 본인 변경 시 | 로그인 시 1회 | 운영자가 변경 (개별) | 사용자 탈퇴 시 |
| **RSA 개인키** | auth 부팅 / 자정 회전 시 자동 | JWT 서명 (auth 모듈만) | **24시간** (자정 회전) | 8일 후 cleanup DELETE |
| **RSA 공개키** | RSA 페어와 함께 | JWT 검증 (모든 검증자) | **24시간 + 8일 보관** | 8일 후 cleanup |
| **kid** | RSA 페어와 함께 | header → 매칭 공개키 lookup | 키와 같이 회전 | 키와 같이 폐기 |
| **Access Token** | 로그인 / refresh 시 | 매 API 호출마다 | **15분** | exp 도달 즉시 (서버 저장 X) |
| **Refresh Token + jti** | 로그인 / refresh 시 | refresh 호출 시 1회 | **7일** + 사용 시 회전 (1회용) | revoked=true UPDATE 또는 expires_at 도달 |

---

## 17. FAQ — 자주 헷갈리는 부분

### Q1. "JWT 와 일반 cookie 세션의 차이?"

| 항목 | JWT | 세션 cookie (Spring Session 등) |
|---|---|---|
| 검증 | 서명 검증만 (stateless) — DB/Redis 안 봄 | 매 요청마다 sessionId 로 DB/Redis 조회 |
| 정보 | 토큰 자체가 정보 (sub/role 등) 담음 | sessionId 만 cookie, 정보는 서버 측 저장 |
| 다중 모듈 | 모든 모듈이 자체 검증 가능 (JWKS 만 알면 됨) | 모든 모듈이 같은 세션 저장소 공유 필요 |
| 만료 후 무효화 | 어려움 (blacklist 또는 짧은 만료) | sessionId DELETE 만 하면 됨 |
| 우리 선택 | ✅ — 다중 모듈 환경에서 부하 적음 | ❌ |

### Q2. "RSA 개인키와 공개키 — 둘 다 비밀이야?"

- **개인키 = 비밀** (절대 외부 노출 X). 토큰 서명 = 도장 찍기. 노출되면 누구나 위조 가능.
- **공개키 = 공개** (JWKS endpoint 로 노출). 토큰 검증 = 도장 진위 확인. 공개해도 위조 못 함.

### Q3. "왜 자정 회전? 매시간 / 매일이 무슨 차이?"

- **자정 1회** = 운영 부담 ↓ + 보안 강화 충분 (24시간 전 노출돼도 24시간 후 교체)
- **매시간** = 보안 강화 ↑ but 운영자 감지 어려움 (이상 발생 시 추적 어려워짐)
- **매주** = 노출 후 탐지/대응 시간 길어짐

본 시스템 = **자정 1회**. 운영 환경 부담 + 충분한 보안 균형.

### Q4. "Access Token 만료를 1시간으로 늘리면 안 돼?"

- **장점**: refresh 호출 빈도 ↓ (서버 부담 ↓)
- **단점**: 탈취 시 1시간 동안 위조 가능 — **15분 → 1시간 = 4배 더 위험**
- 표준 권장 = 5~15분. 본 시스템 = **15분** (기본값).

### Q5. "JWKS 가 노출되면 위험한가?"

**아니요 — JWKS 는 공개해도 안전**. 공개키만 들어있어서 검증만 가능, 서명(위조)은 못 함. RFC 표준 endpoint.

### Q6. "마스터 비밀번호 (JASYPT) 가 가장 중요한 키 같은데 맞아?"

**맞음**. JASYPT_PASSWORD 가 모든 ENC 의 마스터 키 — 이걸 알면 yml 의 DB 자격증명 + DB 의 RSA 개인키 ENC 모두 복호화 가능. 그래서:
- **환경변수만** (yml 에 절대 박지 않음)
- **운영 secret 관리** (Vault 같은 시스템 권장)
- **회전 시 모든 ENC 일괄 재암호화** + 재배포 (큰 작업)

### Q7. "JWT payload 에 비밀번호 같은 거 넣어도 돼?"

❌ **절대 안 됨**. JWT 의 header/payload 는 **Base64URL 인코딩만** 적용 — 누구나 [jwt.io](https://jwt.io) 에 붙이면 디코드해서 평문 확인 가능. 즉 토큰 자체는 **암호화되지 않은 평문**.

RSA 가 한 일 = `signature` 부분만 만든 것. 위조 방어용 도장이지 **내용 가리기 X**.

**OK**: userId(sub), role, exp, iss 같은 식별/메타 정보
**X**: password, password_hash, API key, 개인정보 같은 민감 데이터

### Q8. "OAuth2 라이브러리 안 쓰면 보안 약하지 않아?"

**아니, 동일 강도**. 본 시스템은 Spring OAuth2 starter 미사용 (Spring Boot 2.7.12 호환 한계 — Spring Authorization Server 1.x 가 Boot 3.x 필요) 이라 **jjwt + Nimbus JOSE 자체 구현**.

| 항목 | OAuth2 starter | jjwt + Nimbus 자체 (본 시스템) |
|---|---|---|
| RS256 서명/검증 | ✅ | ✅ (jjwt 가 처리) |
| JWKS endpoint | ✅ 자동 | ✅ 자체 Controller (Nimbus 가 JSON 직렬화) |
| RSA 회전 | 자동 (Authorization Server 가 관리) | KeyRotationJob 직접 작성 (~80줄) |
| JWKS 캐시 (검증자) | 자동 (Resource Server) | JwksClient 직접 작성 (~70줄) |
| 검증된 라이브러리? | ✅ Spring | ✅ jjwt/Nimbus 모두 검증된 표준 라이브러리 |
| 코드량 | 적음 (~50줄) | 많음 (~300줄) |
| **보안 강도** | **동일** | **동일** |

**핵심**: 자체 구현은 Spring 이 자동으로 해주던 부분을 직접 짜는 것일 뿐 — 내부적으로 같은 RS256/JWKS/RSA 페어 사용. 검증된 라이브러리 (jjwt, Nimbus) 가 핵심 로직 처리하니 결함 위험 낮음.

### Q9. "외부 네트워크 필요해?"

❌ **불필요**. 본 시스템 RSA + JWT 는 **자체 IDP** 구조 — 외부 IDP (Google/Naver 등) 통합 안 함.

- auth 모듈 = 자체 토큰 발급기 (외부 호출 없음)
- 검증자 모듈 = JWKS endpoint 호출하지만 **내부망의 auth 모듈** (`http://auth:8096`)
- **운영 폐쇄망 그대로 가능**

빌드 시 maven central 의존성 다운로드만 외부 — 사내 Nexus mirror 로 해결 가능 (다른 라이브러리도 동일 패턴).

---

## 18. 한 컷 요약

```
┌─────────────── 운영 시작 ──────────────┐
│  JASYPT_PASSWORD (env)                 │
│       ↓                                 │
│  yml ENC 복호화 → DB 자격증명           │
│       ↓                                 │
│  auth 모듈 부팅 → RSA 페어 생성/로드    │
│       ↓                                 │
│  CLI 로 첫 사용자 발급 (BCrypt)        │
└─────────────────────────────────────────┘
                  ↓
┌─────────────── 운영 중 (사용자 1명+) ──┐
│  로그인: BCrypt 검증 → JWT 발급        │
│       ↓                                 │
│  cookie (httpOnly, SameSite=Strict)    │
│       ↓                                 │
│  매 호출: 검증자가 RSA 공개키로 검증    │
│       ↓                                 │
│  15분 만료 → refresh → 새 토큰          │
│       ↓                                 │
│  7일 만료 → 재로그인                    │
│       ↓                                 │
│  운영자 추가 (peer multi)              │
│  본인 비번 변경 / 탈퇴                  │
│  (모두 동급 권한, 단일 role='user')    │
└─────────────────────────────────────────┘
                  ↓
┌─────────────── 매일 자정 ───────────────┐
│  RSA 자정 회전 (24h 보안 갱신)          │
│  만료 8일 지난 키 cleanup               │
│  검증자 5min 캐시 TTL 후 새 kid 인식    │
└─────────────────────────────────────────┘
```

---

> **이 문서가 다루지 않는 영역** — 결정/스펙 디테일은 [AUTH_DESIGN.md](AUTH_DESIGN.md) 참조
>
> - DB 스키마 정확한 SQL → §7
> - API 명세 정확한 path/body → §8
> - 보안 결정 트레이드오프 → §13
> - Phase 별 구현 task → §11
> - 키 검증 시나리오 매트릭스 28개 → §12.6
