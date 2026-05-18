# auth 포트 8096 → 9096 이전 (provide-dmz 와 충돌 해소)

> 2026-05-18 — 계획서 (코드 변경 X)
> 사이클 배경: api-collector Phase 1 검증 통과 후 provide 흐름 살피기 진입 → port 8096 충돌 발견
> 사용자 결정: auth 측 양보 (운영 등록 데이터 protect), 새 port = **9096**

---

## 1. 배경 / 발견

### 1-1. 충돌 fact

| 모듈 | port | 비고 |
|------|:----:|------|
| `infolink-auth` | **8096** | 5/4 신규 (project_auth_system) — `application.yml:2 server.port: 8096` |
| `infolink-agent-provide-dmz` | **8096** | 자체 `application.yml:2` + agent 테이블 endpoint_url 등록 |

→ 두 모듈 동시 기동 불가. 현재는 사용자 환경에서 auth 만 떠있음 (8096), provide-dmz 미가동.

### 1-2. 등록 무게 비교

| 항목 | auth | provide-dmz |
|------|:----:|:-----------:|
| orchestrator DB agent.endpoint_url 등록 | 0 (auth 자체는 agent 아님) | **6 row** (agent id 37/38/39/40/41/42) |
| yml 참조처 (`localhost:8096` 박힌 곳) | 4 (자체 + backend + api-collector + api-provider jwks-url) + frontend next.config.js | 1 (자체 yml) |
| 운영 데이터 손상 위험 | 없음 (config) | 큼 (등록 url) |

사용자 판단: "provide 는 등록된 거 (운영 데이터)니까 auth 가 양보" — 운영 데이터 무손상 우선.

### 1-3. 추가 발견 (별 트랙)

- yml 8개 중 agent 테이블 등록은 6개만. **provide-tm-gd110301, provide-tm-gd110302 미등록** — 별 점검 안건.

## 2. 변경 범위

### 2-1. yml / 설정 (5개 파일)

| # | 파일 | 변경 |
|---|------|------|
| 1 | `infolink-auth/src/main/resources/application.yml` | `server.port: 8096` → **`9096`** |
| 2 | `infolink-orchestrator-backend/src/main/resources/application.yml` | `auth.jwks-url: http://localhost:8096/...` → **`9096`** |
| 3 | `infolink-api-collector/src/main/resources/application.yml` | `auth.jwks-url: http://localhost:8096/...` → **`9096`** |
| 4 | `infolink-api-provider/src/main/resources/application.yml` | `auth.jwks-url: http://localhost:8096/...` → **`9096`** |
| 5 | `infolink-orchestrator-frontend/next.config.js` | `/auth/:path*` rewrite destination `http://localhost:8096` → **`9096`** |

### 2-2. docs / 메모리 (4개)

| # | 파일 | 변경 |
|---|------|------|
| 6 | `docs/AUTH_DESIGN.md` | 본문 / 다이어그램 / yml 예시의 `8096` → `9096` (다수) |
| 7 | `docs/AUTH_FLOW.md` | 다이어그램 / 시퀀스의 `8096` → `9096` (다수) |
| 8 | `docs/claude-memory/project_auth_system.md` | name / 본문 `8096` → `9096` |
| 9 | `C:/Users/podo/.claude/projects/.../memory/project_auth_system.md` | 양쪽 동기화 ([[feedback_memory_git_sync]]) |

### 2-3. MEMORY.md 서버 포트표 갱신

```diff
- | **Auth 모듈 (infolink-auth)** | **8096** |
+ | **Auth 모듈 (infolink-auth)** | **9096** |
```

### 2-4. DB 변경 (없음)

provide-dmz 의 agent.endpoint_url 6 row 는 **그대로 유지** (8096). auth 가 9096 으로 이동.

## 3. 검증 계획

### Phase 1 — yml 변경 후 재기동
- auth 재기동 → port 9096 listen 확인 (`netstat -ano | findstr 9096`)
- backend / api-collector / api-provider 재기동 → 시작 로그에서 JWKS endpoint 9096 호출 정상
- frontend `npm run dev` 재기동 → next.config rewrite 갱신

### Phase 2 — 회귀 검증

| # | 시나리오 | 기대 |
|---|---------|------|
| 1 | frontend `localhost:3000` 진입 → 로그인 | cookie 발급 정상 (auth 9096 통해) |
| 2 | backend `/api/manage/agents` 호출 (JWT cookie) | 200 (jwks-url 9096 검증) |
| 3 | api-collector `/api/endpoints` 호출 (JWT cookie) | 200 |
| 4 | api-collector endpoint 수동 실행 (예: 약수터-제원 26) | 본 fix 그대로 동작 |
| 5 | api-provider `/api/manage/operations` 호출 (JWT cookie) | 200 |

### Phase 3 — docs / 메모리 동기화 확인
- AUTH_DESIGN.md / AUTH_FLOW.md `8096` → `9096` 전수 변환 검증 (grep)
- MEMORY.md 서버 포트표 9096
- 메모리 파일 양쪽 동기화

## 4. 영향 범위 / 회귀 리스크

| 영역 | 영향 | 비고 |
|------|:----:|------|
| auth 자체 | 재기동 + port listen | 단순 |
| backend / api-collector / api-provider | yml 변경 + 재기동 | JWKS endpoint 호출 변경, 재빌드 불요 (yml resource 만) |
| frontend | next.config.js + 재기동 | rewrite path 갱신 |
| provide-dmz | 변경 없음 | 8096 그대로 — 본 사이클 보호 대상 |
| **회귀 시나리오** | 모든 JWT cookie 흐름 | 로그인 / 인증 흐름 다 영향 — 5 시나리오 검증 필수 |
| common JAR | 변경 없음 | 9 모듈 복사 불요 |

## 5. 리스크 (6건)

| # | 리스크 | 완화 |
|---|--------|------|
| R1 | next.config.js 변경 후 frontend hot reload 안 잡힘 | `npm run dev` 재기동 강제 |
| R2 | docs 8096 → 9096 전수 변환 누락 시 운영 가이드 혼란 | grep 으로 잔재 확인 후 마무리 |
| R3 | 운영자 사용자가 직접 8096 박힌 다른 데이터 가지고 있을 수 있음 | 본 사이클은 코드 그래서만 변경, 운영자 별도 가이드 |
| R4 | provide-tm-gd110301/302 yml 등록 미완 — 별 트랙 | 본 사이클 외, fact 기록만 |
| R5 | 5/4 도입 시점 dev_log/jira-mapping.json 등 옛 기록의 8096 표기 | 옛 기록은 보존 (당시 fact). 현 docs 만 갱신 |
| R6 | auth 재기동 중 frontend 로그인 끊김 | 사용자 인지 — 재로그인 |

## 6. 산출물 예상 (실행 시점)

- **yml/설정 (5)**: auth + backend + api-collector + api-provider + frontend next.config.js
- **docs (3)**: AUTH_DESIGN.md / AUTH_FLOW.md / claude-memory/project_auth_system.md
- **메모리 (1)**: `~/.claude/projects/.../memory/project_auth_system.md` (양쪽 동기화)
- **MEMORY.md** 서버 포트표 갱신
- **dev_log**: `dev_logs/2026_05/2026-05-18.md` 또는 다음 실행일 dev_log 에 append
- **빌드**: 없음 (yml resource 만 변경, common JAR 무변경)
- **재기동**: auth + backend + api-collector + api-provider + frontend = 5 서비스

## 7. 커밋 메시지 (예정)

```
refactor(auth): port 8096 → 9096 (provide-dmz 와 충돌 해소)

- infolink-auth/application.yml: server.port 9096
- backend/api-collector/api-provider jwks-url: 9096
- frontend next.config.js: /auth rewrite 9096
- docs (AUTH_DESIGN/AUTH_FLOW/메모리) + MEMORY.md 서버 포트표 갱신
- provide-dmz(8096) 운영 등록 데이터 보호 (agent.endpoint_url 6 row 무변경)

원인: infolink-agent-provide-dmz 도 port 8096 박혀있어 동시 기동 불가.
사용자 결정: 등록 무게 적은 auth 가 양보. 새 port = 9096 (옛 8096 자리 매칭).
```

## 8. 결정 사항 (사용자 확인 완료, 2026-05-18)

| Q | 결정 |
|---|------|
| 새 port 값 | **9096** |
| 변경 타이밍 | **계획서만 (코드 변경 X)** — 본 세션 진입 안 함 |
| provide-dmz | **보호 (변경 없음)** |

## 9. 다음 사이클 (별 세션)

본 계획서 검토 후 사용자 승인 시:
1. yml 5개 변경 + docs 3개 + 메모리 + MEMORY.md
2. 5 서비스 재기동
3. 5 시나리오 회귀 검증 (frontend 로그인 / backend / api-collector / api-provider / endpoint 수동 실행)
4. 커밋

본 계획서는 코드 변경 없음. 실행 사이클 진입 시 본 문서 기반 작업.

## 10. 별 트랙 (본 사이클 외)

- provide-tm-gd110301, provide-tm-gd110302 yml 만 있고 agent 테이블 미등록 — 운영 등록 누락 점검
- provide-dmz 모듈명 `*-dmz` 인데 실제 zone INTERNAL — 모듈명 정정 (큰 회귀, dad8a1b 같은 rename 사이클)
- frontend hardcoded 8096 더 있는지 (next.config 외 — public 리소스는 svg 라 무관) 잔재 확인
