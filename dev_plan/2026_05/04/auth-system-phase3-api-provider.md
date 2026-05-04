# Auth 시스템 — Phase 3: api-provider (8095) 통합

> 작성일: 2026-05-04
> 범위: `gims-api-provider` 에 JWT 검증 통합 + MockApiKeyController 운영 차단
> 선행: [Phase 1](auth-system-phase1.md), [Phase 2 Backend](auth-system-phase2-backend.md)
> 동반 문서: [AUTH_DESIGN.md §9.3.2](../../../docs/AUTH_DESIGN.md)

---

## 1. 목적

api-provider (8095) 는 **외부 사용자 + 운영자 양쪽 받음** — 가장 복잡한 검증자 모듈.

| Path | 호출자 | 인증 |
|---|---|:--:|
| `/api/provide/**` | 외부 사용자 (GIMS 본 시스템 등) | **Provide API Key** (기존, 자체 검증) |
| `/api/manage/**` | 운영자 (Frontend) | **JWT** (본 작업) |
| `/api/mock/api-key` | **테스트 전용 (MockApiKeyController)** | **운영 차단** |

## 2. 작업 범위

### 포함
- `build.gradle` 의존성 추가 (Spring Security)
- `application.yml` 에 auth 토글
- `SecurityConfig` 신규 (path 분리 정책)
- **`MockApiKeyController` 에 `@ConditionalOnProperty(mock.api-key.enabled=true)` 추가** + yml default false (이중 방어)

### 미포함
- Frontend 통합 — Phase 5

## 3. 변경 파일

```
gims-api-provider/
├── build.gradle                                           ← 수정
├── src/main/resources/application.yml                     ← 수정 (auth 토글 + mock default false)
└── src/main/java/com/gims/provider/
    ├── config/SecurityConfig.java                         ← 신규
    └── controller/MockApiKeyController.java               ← 수정 (@ConditionalOnProperty)
```

## 4. 단계별 Step

### Step 1. build.gradle 의존성 추가 (~10분)
Phase 2 Backend 와 동일 패턴 (jjwt + Spring Security 등)

### Step 2. application.yml (~5분)
```yaml
auth:
  jwks-url: http://localhost:8096/.well-known/jwks.json
  issuer: orchestrator-auth
  audience: orchestrator

jwt:
  cookie:
    enabled: true

# MockApiKeyController 운영 비활성 (이중 방어 — SecurityConfig 의 denyAll 과 중첩)
mock:
  api-key:
    enabled: false
```

### Step 3. SecurityConfig 신규 — path 분리 (~40분)
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
                .antMatchers("/.well-known/**").permitAll()
                .antMatchers("/api/provide/**").permitAll()       // 외부 사용자 — Provide API Key 자체 검증 (Spring Security 외 영역)
                .antMatchers("/api/mock/**").denyAll()            // ⚠️ 운영 차단 (이중 방어)
                .antMatchers("/api/manage/**").authenticated()    // 운영자 — JWT
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

### Step 4. MockApiKeyController 토글 추가 (~5분)
```java
@Slf4j
@RestController
@RequestMapping("/api/mock/api-key")
@ConditionalOnProperty(name = "mock.api-key.enabled", havingValue = "true")  // ← 신규
public class MockApiKeyController {
    // 기존 코드 그대로
}
```

### Step 5. 부팅 검증 (~10분)
- [ ] `./gradlew clean build -x test` 성공
- [ ] `./gradlew bootRun` Started OK
- [ ] 8095 listen + `/actuator/health` 200
- [ ] **MockApiKeyController 비활성** — 부팅 로그에서 컨트롤러 빈 등록 안 됨 확인

### Step 6. 외부 사용자 호출 회귀 (~15분) — Provide API Key
- [ ] Type A 12종 외부 호출 — 기존 apiKey 인증 흐름 영향 0
- [ ] Type B 16종 카탈로그 호출 — 동
- [ ] `curl http://localhost:8095/api/provide/...` (apiKey 헤더만, JWT cookie 없이) → 200 (정상 응답)

### Step 7. 운영자 호출 검증 (~15분) — JWT
- [ ] `curl http://localhost:8095/api/manage/operations` (cookie 없이) → 401 AUTH_REQUIRED
- [ ] auth 8096 에서 로그인 → cookie 받음 → 같은 cookie 로 `/api/manage/operations` → 200
- [ ] `curl http://localhost:8095/api/mock/api-key/validate` → **403 FORBIDDEN** (denyAll)

## 5. 검증 시나리오 (§12.6 매트릭스)
- ✅ #25 인증 없이 보호 endpoint → 401 (`/api/manage/**`)
- ✅ #26 외부 `/api/provide/**` permitAll (Provide API Key 자체 검증)
- ✅ #27 인증 없이 `/api/manage/**` → 401
- ✅ Mock 운영 차단 (이중 방어)

## 6. 영향 범위 / 회귀

### 영향 받는 흐름
- 외부 사용자 (`/api/provide/**`) — Provide API Key 그대로, 영향 0
- Frontend → `/api/manage/**` — JWT 필요 (Phase 5 후 정상)

### 회귀 위험
- **Type A 12종 / Type B 16종 외부 호출 — 영향 0 검증 필수**
- Provide API Key 흐름이 Spring Security `permitAll` 통과 후 컨트롤러 내부에서 자체 검증 — 동작 변화 없는지 확인

## 7. 추정 시간
- Step 1~7 합계 = **~1.5~2시간**

## 8. 참고
- AUTH_DESIGN.md §9.3.2 — api-provider endpoint 매핑
- AUTH_DESIGN.md §9.3.5 — Mock 이중 방어 결정
- 4/29 dev_log — Type A/B 회귀 패턴 (PoC 시 검증 패턴 활용)
