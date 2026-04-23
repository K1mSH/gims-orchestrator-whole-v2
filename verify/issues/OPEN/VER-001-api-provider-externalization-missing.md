---
id: VER-001
title: gims-api-provider 외부화 누락 (credentials + URL 평문)
status: OPEN
created: 2026-04-23
parts: [P9-api-provider]
parallel_safe: true
assignee: forward
related: []
---

## 증상 요약

`gims-api-provider` 모듈만 **Jasypt 외부화 패턴을 따르지 않음**. credentials 와 DB/Proxy URL 이 평문으로 하드코딩되어 실배포 진입 블로커.

다른 모든 모듈(7 개)은 이미 Jasypt ENC + `${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}` fallback 패턴 적용 완료 상태. 이 모듈 하나만 예외.

## 재현 절차
1. `gims-api-provider/src/main/resources/application.yml` 열기
2. line 10 ~ 12, line 37 확인

## 기대 vs 실제

### 기대 (다른 모듈 표준 패턴)
```yaml
spring:
  datasource:
    url: ENC(...)
    username: ENC(...)
    password: ENC(...)
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:sync-pipeline-secret-key-2024}
# 기타 URL (있다면)
something:
  url: ${PROXY_INTERNAL_URL:http://localhost:8093}
```

### 실제
```yaml
# gims-api-provider/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:29006/api_provider   # line 10 — 평문
    username: k1m                                         # line 11 — 평문
    password: 1111                                        # line 12 — 평문
# (중략)
  url: http://localhost:8093                              # line 37 — 평문
```

## 증거

전 모듈 비교 (grep 결과):
- **7 개 모듈** (`sync-agent-bojo/bojo-int/others/provide/proxy-dmz/proxy-internal/orchestrator-backend` + `infolink-api-collector` 일부): `url: ENC(...)` / `username: ENC(...)` / `password: ENC(...)`
- **1 개 모듈** (`gims-api-provider`): 평문

## 수정 범위 제안

`gims-api-provider/src/main/resources/application.yml`:
- line 10 — DB URL → 다른 모듈처럼 `ENC(...)` 로 암호화 또는 `${DB_URL:...}` 환경변수 패턴
- line 11 — username → `ENC(...)`
- line 12 — password → `ENC(...)`
- line 37 — Proxy Internal URL → `${PROXY_INTERNAL_URL:http://localhost:8093}`
- Jasypt encryptor 설정 블록 존재 확인 / 추가 (다른 모듈 패턴 참조)

## 회귀 확인 방법
- 재기동 후 `/actuator/health` 200 응답 확인
- provide Agent 실행 → api-provider 연동 정상 확인
- Proxy Internal 경유 조회 정상 확인
- `verify/deployment/config-replacement.md` § A7, C10 항목 갱신 (치환 지점 실제 주입 가능해짐)

## 관련 문서
- `verify/_invariants/00-overview.md` § 11A (credentials 외부화 / 환경 분리)
- `verify/deployment/config-replacement.md` A7, C10
- MEMORY: `feedback_config_replacement_sync`, `feedback_config_vs_registration`
