# Auth 시스템 — Phase 4: api-collector (8084/8094) 통합

> 작성일: 2026-05-04
> 범위: `infolink-api-collector` 에 JWT 검증 통합 + MockApiController 운영 차단
> 선행: [Phase 1](auth-system-phase1.md), [Phase 2 Backend](auth-system-phase2-backend.md)
> 동반 문서: [AUTH_DESIGN.md §9.3.3](../../../docs/AUTH_DESIGN.md)

---

## 1. 목적

api-collector (DMZ 8084 / Internal 8094) 의 운영자 호출 endpoint 에 JWT 인증 적용.
**`/mock` prefix** (= `MockApiController` — NGW_0118 언론사 mock 등) 운영 차단.

## 2. Endpoint 매핑 (AUTH_DESIGN.md §9.3.3)

| Controller | Prefix | 호출자 | 정책 |
|---|---|---|:--:|
| `ApiEndpointController` | `/api/endpoints` | 운영자 | 🔒 JWT |
| `ApiHistoryController` | `/api/endpoints/{id}/history` | 운영자 | 🔒 JWT |
| `ApiScheduleController` | `/api/...` | 운영자 | 🔒 JWT |
| **`MockApiController`** | **`/mock`** ⚠️ (`/api/` 아님!) | **테스트 전용** | **운영 차단** |
| `/actuator/health` | — | — | ✅ permitAll |

## 3. 작업 범위

### 포함
- `build.gradle` 의존성 추가
- `application.yml` 에 auth 토글 + `mock.api.enabled: false`
- `SecurityConfig` 신규 — `/mock/**` denyAll + `/api/**` JWT 필수
- **`MockApiController` 에 `@ConditionalOnProperty(mock.api.enabled=true)` 추가** (이중 방어)

### 미포함
- Frontend 통합 — Phase 5

## 4. 변경 파일

```
infolink-api-collector/
├── build.gradle                                           ← 수정
├── src/main/resources/application.yml                     ← 수정 (auth 토글 + mock default false)
└── src/main/java/com/infolink/collector/
    ├── config/SecurityConfig.java                         ← 신규
    └── controller/MockApiController.java                  ← 수정 (@ConditionalOnProperty)
```

## 5. 단계별 Step

### Step 1. build.gradle 의존성 추가 (~10분)
Phase 2 동일 패턴

### Step 2. application.yml (~5분)
```yaml
auth:
  jwks-url: http://localhost:8096/.well-known/jwks.json   # DMZ 환경에서는 내부 host 로 변경
  issuer: orchestrator-auth
  audience: orchestrator

jwt:
  cookie:
    enabled: true

# MockApiController 운영 비활성
mock:
  api:
    enabled: false
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
                .antMatchers("/mock/**").denyAll()             // ⚠️ /api/ prefix 아님 — 명시 처리
                .antMatchers("/api/**").authenticated()        // 운영자 — JWT
                .anyRequest().denyAll()
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

### Step 4. MockApiController 토글 추가 (~5분)
```java
@RestController
@RequestMapping("/mock")
@ConditionalOnProperty(name = "mock.api.enabled", havingValue = "true")
public class MockApiController {
    // 기존 mock 응답 (NGW_0118 언론사 등) 유지
}
```

### Step 5. 부팅 검증 (~10분)
- [ ] `./gradlew clean build -x test` 성공
- [ ] `./gradlew bootRun` Started OK
- [ ] 8084 (또는 8094) listen
- [ ] **MockApiController 비활성** — 빈 등록 안 됨 확인

### Step 6. 운영자 호출 검증 (~15분)
- [ ] `curl http://localhost:8084/api/endpoints` (cookie 없이) → 401
- [ ] auth 8096 에서 로그인 → cookie → `/api/endpoints` → 200
- [ ] `curl http://localhost:8084/mock/anything` → **403 FORBIDDEN** (denyAll + ConditionalOnProperty 둘 다)

### Step 7. 회귀 검증 (~10분)
- [ ] api-collector → 외부 API 수집 정상 동작 (NGW_0118 언론사 mock 이 운영 비활성이라 — 운영에서는 실 endpoint 호출하므로 영향 없어야)
- [ ] api-collector 수집 스케줄 cron 정상 동작
- [ ] 등록된 endpoint 호출 → DB 적재

## 6. 검증 시나리오 (§12.6 매트릭스)
- ✅ #25 인증 없이 보호 endpoint → 401
- ✅ Mock 운영 차단 (이중 방어 — ConditionalOnProperty + SecurityConfig denyAll)

## 7. 영향 범위 / 회귀

### 영향 받는 흐름
- Frontend → `/api/endpoints/**` — JWT 필요 (Phase 5 후 정상)
- 외부 API 수집 cron — 영향 0 (운영 endpoint 별개)

### 회귀 위험
- **NGW_0118 언론사 LOOKUP** — 4/23 dev_log 의 LOOKUP 파생 컬럼 흐름 영향 검증 필요
  - LOOKUP 이 mock controller 거치는지? 아니면 실 NGW 엔드포인트?
  - mock 비활성 후에도 LOOKUP 정상 동작하는지 확인
- DMZ (8084) / Internal (8094) 양쪽 환경 적용 — yml 두 벌

## 8. 추정 시간
- Step 1~7 합계 = **~1.5시간**

## 9. 참고
- AUTH_DESIGN.md §9.3.3 — api-collector endpoint 매핑
- AUTH_DESIGN.md §9.3.5 — Mock 이중 방어
- 3/23 dev_log — NGW_0118 LOOKUP 도입
- 3/24 dev_log — API key 참조 흐름
