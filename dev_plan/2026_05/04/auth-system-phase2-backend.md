# Auth 시스템 — Phase 2: Backend (8080) 통합

> 작성일: 2026-05-04
> 범위: `sync-orchestrator/backend` 에 JWT 검증 통합
> 선행: [Phase 1](auth-system-phase1.md) 완료 (auth 모듈 8096 + sync-agent-common 검증자 자산)
> 후속: [Phase 3 api-provider](auth-system-phase3-api-provider.md), [Phase 4 api-collector](auth-system-phase4-api-collector.md), [Phase 5 Frontend](auth-system-phase5-frontend.md)
> 동반 문서: [AUTH_DESIGN.md §9.3.1](../../../docs/AUTH_DESIGN.md), [AUTH_FLOW.md](../../../docs/AUTH_FLOW.md)

---

## 1. 목적

Backend (sync-orchestrator/backend, 8080) 의 운영자 호출 endpoint 에 JWT 인증 적용.
**`/api/callback/**`** 만 시스템 간 콜백 (Agent → Backend) 으로 permitAll 유지.

## 2. Backend Endpoint 매핑 (AUTH_DESIGN.md §9.3.1 조사 결과)

| Controller | Prefix | 호출자 | 본 작업 정책 |
|---|---|---|:--:|
| `AgentController` | `/api/agents` | 운영자 | 🔒 JWT |
| `DatasourceController` | `/api/datasources` | 운영자 | 🔒 JWT |
| `ExecutionController` | `/api/executions` | 운영자 | 🔒 JWT |
| `ScheduleController` | `/api/schedules` | 운영자 | 🔒 JWT |
| `CallbackController` | `/api/callback/started`, `/api/callback/finished` | **Agent (시스템 간)** | ✅ permitAll |
| `/actuator/health` | — | 모든 호출 | ✅ permitAll |

## 3. 작업 범위

### 포함
- `build.gradle` 의존성 추가 (Spring Security)
- `application.yml` 에 `auth.jwks-url`, `jwt.cookie.enabled`, `auth.issuer`, `auth.audience`
- `SecurityConfig` 신규 (callback permit + 그 외 JWT 필수)
- 회귀 검증 — Agent → Backend 콜백 정상 동작 + Frontend 호출 (Phase 5 후 검증)

### 미포함
- 시스템 간 인증 강화 (Backend 에 ApiKeyFilter 도입) — 후속 작업
- Frontend 통합 — Phase 5

## 4. 변경 파일

```
sync-orchestrator/backend/
├── build.gradle                           ← 수정 (jjwt + spring-security)
├── src/main/resources/application.yml     ← 수정 (auth 토글)
└── src/main/java/com/sync/orchestrator/config/
    └── SecurityConfig.java                ← 신규
```

## 5. 단계별 Step

### Step 1. build.gradle 의존성 추가 (~10분)
```gradle
dependencies {
    // 기존 유지
    implementation files('libs/sync-agent-common-1.0.0-SNAPSHOT.jar')

    // 신규 — JWT 검증 (sync-agent-common 의 JwtCookieAuthFilter 활용)
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.11.5'
    implementation 'com.nimbusds:nimbus-jose-jwt:9.37'
}
```

### Step 2. application.yml — auth 토글 (~5분)
```yaml
auth:
  jwks-url: http://localhost:8096/.well-known/jwks.json
  issuer: orchestrator-auth
  audience: orchestrator

jwt:
  cookie:
    enabled: true   # JwtCookieAuthFilter 활성
```

### Step 3. SecurityConfig 신규 (~30분)
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtCookieAuthFilter jwtCookieAuthFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/api/callback/**").permitAll()  // Agent → Backend 콜백 (시스템 간)
                .anyRequest().authenticated()                 // 그 외 운영자 호출
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"AUTH_REQUIRED\"}");
                })
            )
            .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### Step 4. 부팅 + 컴파일 검증 (~10분)
- [ ] `./gradlew clean build -x test` 성공
- [ ] `./gradlew bootRun` Started OK
- [ ] 8080 listen + `/actuator/health` 200

### Step 5. 회귀 검증 — Agent 콜백 (~20분)
- [ ] `curl -X POST http://localhost:8080/api/callback/started -H "Content-Type: application/json" -d '{...}'` → 200/204 (인증 없이 통과)
- [ ] sync-agent-bojo 기동 + 파이프라인 1회 실행 → callback 정상 → Agent 로그에 "Notified orchestrator: execution started/finished" 보임
- [ ] DB 의 execution_log 등 callback 결과 정상 INSERT

### Step 6. 운영자 호출 검증 (~10분)
- [ ] `curl http://localhost:8080/api/agents` (cookie 없이) → **401 AUTH_REQUIRED**
- [ ] auth 모듈 (8096) 에서 admin 로그인 → cookie 받음 → 같은 cookie 로 `/api/agents` → 200
  - **주의**: cookie 의 `Path=/` 라 8080 으로 자동 전송됨 (같은 host 가정)
  - **운영 시점**: Frontend 가 same-origin proxy 통해 호출 → 자연스럽게 동작

## 6. 검증 시나리오 (§12.6 매트릭스)
- ✅ #25 인증 없이 보호 endpoint → 401 (`/api/agents` 등)
- ✅ #28 X-API-Key 흐름 회귀 (Backend → Agent 호출 영향 0)
- ✅ #20 부분 — JWKS fetch 동작 (Phase 1 검증 완료)

## 7. 영향 범위 / 회귀

### 영향 받는 흐름
- Frontend → Backend `/api/**` — JWT 필요 (Phase 5 에서 Frontend 가 cookie 첨부하면 정상)
- Agent → Backend `/api/callback/**` — permitAll, 영향 0

### 회귀 위험
- Frontend 가 아직 로그인 미구현 (Phase 5) → **Phase 2 적용 시점에 운영 화면 진입 시 401 발생 가능**
- 완화: Phase 2 + 5 같이 배포 또는 Phase 5 먼저 머지 후 Phase 2

## 8. 추정 시간
- Step 1~6 합계 = **~1.5시간**

## 9. 참고
- AUTH_DESIGN.md §9.3.1 — Backend endpoint 매핑
- AUTH_FLOW.md §3 — 검증자 모듈 부팅 + Lazy 정책
- AUTH_FLOW.md §6 — API 호출 시 검증 흐름
