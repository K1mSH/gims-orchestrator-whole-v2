# 7. 보안

> **요구사항**: 다수의 DB 자격증명과 서비스 간 통신을 안전하게 보호하고,
> 관리 화면 접근을 인가된 사용자로 제한한다.

## 상태: 로그인 Phase 1~5 모두 완료 (5/6, 9개 모듈 통합 운영 가능)

---

## DB 자격증명 암호화 [Common]
- [x] Jasypt PBEWithHMACSHA512AndAES_256 + RandomIV
- [x] PasswordEncryptor 클래스 (common 모듈, 전 서비스 공유)
- [x] 암호문 형식: ENC(base64) — decrypt 시 래핑 자동 제거
- [x] 암호화 키 외부화 (JASYPT_PASSWORD 환경변수, Docker 전달)
- [x] application.yml 내 민감정보 ENC() 래핑 (5개 모듈)
- [x] CLI 암복호화 도구 (scripts/encrypt.java)

## 자격증명 전달 흐름 [Common]
- [x] Orchestrator에서 암호화 후 DB 저장
- [x] 실행 요청에 ID만 전달 (평문 자격증명 미포함)
- [x] Agent → Proxy: datasourceId로 연결정보 요청 (X-API-Key 인증)
- [x] Proxy → Agent: 암호문 그대로 반환 (패스스루)
- [x] Agent: 자체 비밀키로 복호화 → ThreadLocal에 평문 보관 → DB 직접 접속
- [x] 실행 완료 시 ThreadLocal 정리 (clearCurrentDatasources)
- [x] 연결 테스트: Orchestrator 직접(zone 없음) 또는 Proxy 경유(zone 있음), 내부망 통신+API Key 인증, HTTPS 적용 시 전송 구간 보호 가능

## API Key 인증 [Common]
- [x] ApiKeyFilter (common 모듈, @ConditionalOnProperty)
- [x] 대상: /api/** (예외: /health, /api/pipeline/info)
- [x] 헤더: X-API-Key — Orchestrator RestTemplate에 자동 추가
- [x] 빈 키면 필터 비활성화 (개발 편의)

## 엔드포인트 보안 [Common]
- [x] @ConditionalOnProperty — 모듈별 엔드포인트 선택적 활성화
- [x] Agent: 전부 활성 / Proxy: execution-data, datasource만 활성
- [x] 새 컨트롤러 추가 시 명시적으로 켜지 않으면 비활성 (구조적 방어)
- [x] /debug/datasources 제거 (호스트/포트/DB명 노출 방지)

## HikariCP 풀 하드닝 [Common]
- [x] Agent/Proxy 통일 적용 (maxPool=10, timeout=10s, leak=60s)
- [ ] api-collector 하드닝 미적용 (maxPool=5, timeout/leak 미설정)

## 로그인/인가 [Orchestrator]
- [x] 사용자 로그인 기능 (ID/PW 인증) — sync-orchestrator-auth:8096 모듈 신설 (5/4)
- [x] 세션 또는 JWT 기반 인증 유지 — JWT RS256 + JWKS + Cookie HttpOnly+SameSite=Strict
- [x] 권한 체계 — peer multiplication 동급 1롤 결정 (역할 분리 없음)
- [ ] 미인증 접근 차단 (프론트 + 백엔드) — Phase 2~5 한 묶음 배포 영역
- [x] auth 모듈 신설 (Spring Boot 2.7.12 + jjwt 0.11.5 + Nimbus 9.37 + jasypt + JPA)
- [x] AuthUser / AuthRefreshToken / AuthRsaKey 엔티티 + Repository (ddl-auto 자동 생성)
- [x] KeyService — RSA 2048 페어 자동 생성 + 회전 + JWKS 응답 + InitialKeyLoader
- [x] TokenService — RS256 발급/검증 + SigningKeyResolverAdapter (kid 자동 lookup)
- [x] UserService — peer multiplication CRUD + BCrypt(round 12) + 마지막 1명 차단
- [x] AuthService — 로그인/refresh/로그아웃 + 5회 잠금(30min) + 1회용 회전 + timing-safe
- [x] AuthController/UserController/JwksController + SecurityConfig (401 AUTH_REQUIRED EntryPoint)
- [x] AuthCookieFilter — accessToken cookie → SecurityContext (auth 자체 검증)
- [x] KeyRotationJob (자정 cron `0 0 0 * * ?`)
- [x] UserGeneratorCli (초기 1명 발급, Spring context 미기동 단독 실행)
- [x] 단위/통합 테스트 31/31 PASS (TokenService 7 + UserService 10 + AuthService 10 + KeyRotation 4)
- [x] Phase 1.5 sync-agent-common 검증자 자산 — JwksClient + JwtCookieAuthFilter + 헬퍼 + 단위 5/5 PASS + JAR 8 모듈 복사 + 회귀 OK
- [x] Step 11 통합 검증 — 17 시나리오 모두 PASS (auth 모듈 E2E)
- [x] 첫 사용자 발급 (admin) — UserGeneratorCli + UNIQUE 충돌 검증 OK
- [x] Phase 2 backend 검증자 적용 (5/6 — `/api/callback/**` permitAll, 그 외 JWT)
- [x] Phase 3 api-provider 검증자 적용 (5/6 — `/api/provide` permit / `/api/manage` JWT / `/api/mock` permit. Mock 정책: 개발 자기호출 흐름 의존 발견 → matchIfMissing=true + permitAll, 운영 차단은 yml 단일 토글)
- [x] Phase 4 api-collector 검증자 적용 (5/6 — `/api/**` JWT, `/mock/**` permit. LOOKUP 자기호출 흐름 살아있음 검증 OK)
- [x] Phase 5 frontend 로그인 + 사용자 관리 (5/6 — login/users/users-me 페이지 + AppHeader/AppShell + middleware + axios 401·503 interceptor + next.config /auth/* rewrite. 폐쇄망 npm 부담 회피로 SWR/toast 등 추가 패키지 0건)
- [x] sync-agent-common AutoConfiguration 통합 (5/6 — 검증자 모듈 yml 토글만으로 자동 등록)
- [x] 첫 로그인 후 헤더 stale fix (5/6 — useCurrentUser mutate() 호출, 모듈 캐시 stale 회피)
- [x] 통합 E2E 검증 37/37 PASS (5/6 — Health · JWKS · 인증 정합 · LOOKUP 회귀 회피 · peer multi · 마지막 1명 차단 · Frontend middleware/proxy)
- [ ] **후속 1**: 단위 테스트 환경 격리 — `UserServiceTest.deleteMe_blocked_when_only_one_user` 가 실 DB admin 살아있을 때 count=2 가 되어 차단 안 걸림. `@Sql` cleanup 또는 transactional isolation 강제로 fix 필요. (현재 영향: 테스트 1건 실패. curl E2E 17 로 동등 검증 됨)
- [ ] **후속 3**: 폐쇄망 운영 yml override 패턴 정립 — `mock.api.enabled=false` (api-collector), `mock.api-key.enabled=false` (api-provider), JWKS URL (외부 host), `auth.issuer/audience` 등. profile 분리 또는 환경변수 가이드 작성
- [x] **5/4 §9.3.1 이슈 1 보완 — Backend 시스템 간 인증** (5/6 오후 완료) — Backend `/api/datasources/*/connection-info` 자격증명 endpoint 가 처음부터 무인증이었던 비대칭 발견 + 보완. ApiKeyFilter soft-mode 토글 도입 + Backend SecurityFilterChain 통합 + Proxy ConnectionInfoController X-API-Key 헤더 박음. `dev_plan/2026_05/06/auth-integration-matrix.md` 매트릭스 정립
- [ ] **5/4 §9.3.1 이슈 1 — `/api/callback/**` X-API-Key 강화** (별 사이클) — Agent → Backend callback path 도 시스템 간 인증 적용 (현재 permitAll, body 만 = 자격증명 X 라 우선순위 낮음)
- [ ] 인증 매트릭스 정식화 — `dev_plan/2026_05/06/auth-integration-matrix.md` 결정 후 `docs/AUTH_DESIGN.md` §섹션 반영
