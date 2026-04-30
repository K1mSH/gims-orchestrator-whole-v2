---
id: TASK-002
title: 환경변수 dev 값 default 제거 + invariant 11A 강화
status: OPEN
created: 2026-04-28
decided: 2026-04-28
parts: [P0-meta, P1-common, P4, P5, P6, P7, P8, P9, P10]
parallel_safe: false
depends_on: []
blocks: [VER-001, VER-002]
assignee: forward
---

## 목적

`@Value("${KEY:dev-default}")` / yml `${KEY:dev-default}` 의 **dev 값 default 를 전면 제거** 하여 환경변수 미주입 시 즉시 기동 실패하도록 강제한다.
invariant 11A 본문에 정책 명시.

## 결정 (2026-04-28 사용자 확정)

**옵션 B 채택 — dev 값 default 금지**

> 사용자 발언: "저런식의 default를 피해야한다는 말이긴해"
> ("저런식" = `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}`, `${PROXY_INTERNAL_URL:http://localhost:8093}` 두 예시)

근거:
- dev 값 default 는 환경변수 미주입 시 **dev DB/서비스에 silently 연결** → 사일런트 장애 트리거
- 기동 실패가 즉시 식별 가능한 정상 동작 ("fail fast")

### 결정에서 모호한 부분 (forward 추가 결정 영역)
- "안전 default" (포트 0 / 빈 문자열 / `false` 같은 production-safe 값) 허용 여부 — 본 결정은 dev 값 default 만 명시적으로 금지. 안전 default 허용/금지는 invariant 11A 강화 시 forward 가 dev_plan 으로 정의.

### 배경

현재 invariant 11A 정의:
- "credentials 외부화 (yml 하드코딩 금지)"
- "허용: 명시적 치환 지점 (`${...}`)"

까지만 명시. **default 값 가부는 미정의**. 실제 4-23 run 은 default 패턴을 양호 사례로 처리:
- `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` ✅
- `${PROXY_INTERNAL_URL:http://localhost:8093}` ✅ (VER-001 권장 수정 예시에도 동일 패턴 사용)

### 우려

default 가 dev 값(localhost, 29006, k1m, 1111, sync-pipeline-secret-key-2024)일 때:
- 실배포에서 환경변수 주입 누락하면 **dev DB/서비스에 silently 연결**
- "안전망" 이 아니라 "사일런트 장애 트리거"
- 기동 실패가 아닌 부정확한 동작 → 발견 지연

## 발견된 위반 지점 (2026-04-28 verifier 사전 grep)

> 검색 패턴
> - yml: `\$\{[A-Z_][A-Z0-9_]*:[^}]+\}`
> - Java: `@Value\("\$\{[^:}]+:[^}]+\}"\)`
> 합계 20건 — 전수 스캔 시 추가 발견 가능 (yml 키 형식이 다른 케이스 등). 본 목록은 표본.

### yml — 9건 (전 모듈 JASYPT 전수)

```
gims-api-provider/src/main/resources/application.yml:43      password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
sync-agent-bojo/src/main/resources/application.yml:61        password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
sync-agent-bojo-int/src/main/resources/application.yml:49    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
sync-agent-others/src/main/resources/application.yml:49      password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
sync-agent-provide/src/main/resources/application.yml:49     password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
sync-orchestrator/backend/src/main/resources/application.yml:39  password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
sync-proxy-dmz/src/main/resources/application.yml:42         password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
sync-proxy-internal/src/main/resources/application.yml:42    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
infolink-api-collector/src/main/resources/application.yml:26 password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
```

### Java @Value — 11건

```
sync-agent-bojo/.../config/SyncDataSourceService.java:43            ${agent.orchestrator-url:http://localhost:8080}
sync-agent-bojo-int/.../config/SyncDataSourceService.java:33        ${agent.orchestrator-url:http://localhost:8080}
sync-agent-others/.../config/SyncDataSourceService.java:43          ${agent.orchestrator-url:http://localhost:8080}
sync-agent-provide/.../config/SyncDataSourceService.java:33         ${agent.orchestrator-url:http://localhost:8080}
sync-proxy-dmz/.../config/ProxyDataSourceService.java:35            ${agent.orchestrator-url:http://localhost:8080}
sync-proxy-dmz/.../controller/ConnectionInfoController.java:33      ${agent.orchestrator-url:http://localhost:8080}
sync-proxy-internal/.../config/ProxyDataSourceService.java:35       ${agent.orchestrator-url:http://localhost:8080}
gims-api-provider/.../service/ApiKeyValidationService.java:22       ${app.api-key-validation.url:http://localhost:8095/api/mock/api-key/validate}
infolink-api-collector/.../config/OrchestratorClient.java:24        ${orchestrator.url:http://localhost:8080}
infolink-api-collector/.../executor/AnyangUsageExecutor.java:39     ${anyang.api.fac-url:http://localhost:8084/mock/anyang/fac}
infolink-api-collector/.../executor/AnyangUsageExecutor.java:42     ${anyang.api.data-url:http://localhost:8084/mock/anyang/data}
```

### 추가 잠재 영향
- VER-001 의 `application.yml:37` 평문 `http://localhost:8093` — 4-23 run 권장 수정 패턴이 default 사용 형태. **본 결정에 따라 권장 패턴 수정 필요** (이미 VER-001 본문 갱신 예정).
- 4-23 run 의 양호 처리 사례 (`§ 양호 확인된 사항` 항목) — 본 결정에 따라 위반으로 재분류. runs/2026-04-28.md 에 명시.

## 수정 범위

### 1. invariant 강화 (forward dev_plan 선행)
- [ ] `verify/_invariants/00-overview.md` § 11A 갱신 — "dev 값 default 금지" 명시
- [ ] `verify/_invariants/no-hardcoded-env-values.md` 신설 (4-23 run 후속 항목과 통합)
  - dev 값 정의 (localhost / 29xxx 포트 / dev 계정 / dev secret 등)
  - "안전 default" 의 허용 여부 + 정의 (forward 결정 — 본 task 연장 또는 별도 task)

### 2. 위반 일괄 제거
- [ ] yml 9건: `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` → `${JASYPT_PASSWORD}` (전 모듈 동시 — JAR 재배포 + 환경변수 주입 절차 보강 동반)
- [ ] Java 11건: 위 표 패턴들 → `@Value("${KEY}")` (default 제거)
- [ ] 추가 grep — yml 키가 lowercase/dotted 인 경우 (`${proxy.url:...}` 등) 누락 없는지 전수 확인

### 3. 배포 절차 보강
- [ ] `verify/deployment/config-replacement.md` — 환경변수 주입 누락 시 기동 실패 명시 + 주입 검증 단계 추가
- [ ] `verify/deployment/credentials-rotation.md` — JASYPT_PASSWORD 배포 시 주입 의무화 강조

### 4. 관련 이슈 본문 갱신
- [ ] VER-001 권장 수정 패턴 — `${PROXY_INTERNAL_URL:http://localhost:8093}` → `${PROXY_INTERNAL_URL}` (verifier 가 사전 갱신함)
- [ ] VER-002 권장 패턴 동일 갱신

## 완료 조건 (Definition of Done)

- [ ] invariant 11A 본문 갱신 + `no-hardcoded-env-values.md` 신설
- [ ] yml 9건 + Java 11건 default 제거
- [ ] 전 모듈 빌드 통과 (`./gradlew clean build -x test`)
- [ ] 환경변수 주입 후 정상 기동 (모든 모듈 `/actuator/health` 200)
- [ ] 환경변수 주입 누락 시 기동 실패 확인 (회귀 테스트 — 1 모듈 샘플)
- [ ] 배포 절차 문서 갱신
- [ ] VER-001 / VER-002 권장 패턴 갱신

## 금지 사항 (scope creep 방지)

- "안전 default" 정의 합의 없이 임의 default 잔존 금지
- profile 분리 (application-prod.yml) 신설은 별도 TASK
- 본 task 범위 밖 yml 리팩토링 금지

## 파트 겹침 / 병렬 가능성

- **9 모듈 yml + 7 모듈 Java 동시 수정** = 광범위 회귀 영향 → `parallel_safe: false`
- 한 세션에서 일괄 처리 후 전 모듈 회귀 테스트 권장
- VER-001 / VER-002 (P9 / P8 단일 모듈) 와 묶어서 처리 가능

## 배경 / 발견 경위

- 2026-04-28 verifier 세션에서 사용자 우려 제기 — "default 두지 말라는 거 스캔 범위인가?"
- 현재 invariant 11A 에 명시 안 되어 있음 확인
- 4-23 run 은 default = 양호 처리, VER-001 권장 패턴도 default 사용 → 정책 충돌 잠재
- 사용자 즉시 결정: "저런식의 default를 피해야한다는 말이긴해" — **옵션 B 채택, 동일 세션 내 task 본문 갱신**

## 관련 문서

- `verify/_invariants/00-overview.md` § 11A
- `verify/runs/2026-04-23.md` (default 패턴 양호 처리 사례)
- `verify/runs/2026-04-28.md` (본 발견 기록)
- VER-001 (api-provider 외부화 — 권장 패턴이 default 사용)
- VER-002 (api-collector Lookup URL — 동일 이슈)
- MEMORY: `feedback_config_replacement_sync`
