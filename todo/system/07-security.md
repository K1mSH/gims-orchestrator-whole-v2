# 7. 보안

> **요구사항**: 다수의 DB 자격증명과 서비스 간 통신을 안전하게 보호하고,
> 관리 화면 접근을 인가된 사용자로 제한한다.

## 상태: 로그인 Phase 1 + 1.5 완료 (Phase 2~5 별 세션 대기)

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
- [ ] Phase 2 backend 검증자 적용 (별 세션 — `/api/callback/**` permitAll 정책 포함)
- [ ] Phase 3 api-provider 검증자 적용 (별 세션 — provide/manage/mock 3-way path 분리)
- [ ] Phase 4 api-collector 검증자 적용 (별 세션 — DMZ/Internal 양쪽 yml + NGW_0118 LOOKUP 영향 검증)
- [ ] Phase 5 frontend 로그인 화면 + 사용자 관리 (별 세션 — 가장 큰 작업 5~6h)
