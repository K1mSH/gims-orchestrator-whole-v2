---
id: VER-002
title: api-collector Lookup / API-Key URL 환경변수 치환 지점 미비
status: OPEN
created: 2026-04-23
parts: [P8-api-collector]
parallel_safe: true
assignee: forward
related: [VER-001]
---

## 증상 요약

`infolink-api-collector/application.yml` 에서 공통코드 API URL 과 API-Key URL 이 **Mock 을 가리키는 평문 localhost** 로 하드코딩되어 있고, 환경변수 치환 패턴(`${ENV:default}`) 이 없음.

실배포 시 코드 수정 없이 실 URL 로 전환 불가능하며, Mock URL 이 그대로 잔존하면 실운영에서 Mock 이 호출될 위험.

## 재현 절차
1. `infolink-api-collector/src/main/resources/application.yml` 열기
2. line 36, 37 확인

## 기대 vs 실제

### 기대 (환경변수 치환 패턴)
```yaml
lookup:
  common-code-url: ${COMMON_CODE_URL:http://localhost:8084/mock/common/select/{groupCode}}
  api-key-url: ${API_KEY_URL:http://localhost:8084/mock/api-keys}
```

### 실제
```yaml
# infolink-api-collector/src/main/resources/application.yml
lookup:
  common-code-url: http://localhost:8084/mock/common/select/{groupCode}  # line 36 — 평문
  api-key-url: http://localhost:8084/mock/api-keys                        # line 37 — 평문 Mock
```

## 증거

같은 파일 내 DB URL(line 9) 및 lookup password(line 26) 는 이미 ENC / 환경변수 적용됨 — **이 두 줄만 예외**.

```yaml
# 같은 파일 다른 부분 — 이미 외부화됨
spring:
  datasource:
    url: ENC(SEmKJET7sHv0FyC7ffxBbGrBe3fIfRiH3tRvEANblHcUC2x...)   # line 9
    ...
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}    # line 26
```

### 추가 맥락 — Mock URL 현황 전수 (2 차 스캔 결과)

2026-04-23 오후 Java/Frontend 스캔에서 Mock 을 가리키는 URL 패턴이 본 이슈 외에도 여러 곳에 `@Value` fallback 으로 존재함 확인. 본 이슈와 별개이나 **실배포 시 Mock 호출 방지** 관점에서 한 곳에 모아 기록 (`verify/deployment/config-replacement.md § H` 와 연결).

| 위치 | 환경변수 키 | Mock fallback URL | 상태 |
|------|-----------|-------------------|------|
| `infolink-api-collector/application.yml:36` (**본 이슈**) | `lookup.common-code-url` | `http://localhost:8084/mock/common/select/{groupCode}` | **치환 지점 부재** |
| `infolink-api-collector/application.yml:37` (**본 이슈**) | `lookup.api-key-url` | `http://localhost:8084/mock/api-keys` | **치환 지점 부재** |
| `infolink-api-collector/AnyangUsageExecutor.java:39` | `anyang.api.fac-url` | `http://localhost:8084/mock/anyang/fac` | `@Value` 외부화 완료 |
| `infolink-api-collector/AnyangUsageExecutor.java:42` | `anyang.api.data-url` | `http://localhost:8084/mock/anyang/data` | `@Value` 외부화 완료 |
| `infolink-api-provider/ApiKeyValidationService.java:22` | `app.api-key-validation.url` | `http://localhost:8095/api/mock/api-key/validate` | `@Value` 외부화 완료 |

본 이슈(VER-002) 범위는 **line 36/37 의 치환 지점 부재** 에 한정. 나머지 3 건은 이미 외부화되어 환경변수 주입만 하면 실 URL 전환 가능. 다만 **Mock 호출 위험은 동일**하므로 `verify/deployment/production-checklist.md § 2.2` 에 **"Mock URL 환경변수 전부 실 값으로 주입 확인"** 체크 항목을 추가해야 함 (후속 TASK 후보).

## 수정 범위 제안

`infolink-api-collector/src/main/resources/application.yml`:
- line 36 — `common-code-url: ${COMMON_CODE_URL:http://localhost:8084/mock/common/select/{groupCode}}`
- line 37 — `api-key-url: ${API_KEY_URL:http://localhost:8084/mock/api-keys}`
- (선택) 실배포 시 Mock URL 자체 제거 여부는 `verify/deployment/config-replacement.md § D4` 에서 별도 관리

## 회귀 확인 방법
- 재기동 후 기본값(Mock) 호출 동작 유지 확인
- 환경변수 주입 시 실 URL 로 전환되는지 확인 (예: `COMMON_CODE_URL=http://real.example.com/... ./gradlew bootRun` 형태)
- Lookup 파생 컬럼 동작 확인 (`MEMORY: infolink-api-collector § LOOKUP`)

## 관련 문서
- `verify/_invariants/00-overview.md` § 11A (환경 분리 / 외부화)
- `verify/_invariants/00-overview.md` § 11B (임시값 / Mock 제거)
- `verify/deployment/config-replacement.md` D1, D4
- MEMORY: `feedback_config_replacement_sync`
