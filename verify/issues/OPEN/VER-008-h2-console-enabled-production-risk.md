---
id: VER-008
title: H2 console 실배포 활성화 위험 (보안)
status: OPEN
created: 2026-04-23
parts: [P4-bojo, P8-api-collector]
parallel_safe: true
assignee: forward
related: []
---

## 증상

`spring.h2.console.enabled: true` 가 `application.yml` 에 하드코딩되어 있음. 프로파일 분리 없이 **실배포 시 그대로 켜짐** → H2 콘솔이 외부 접근 가능하면 관리자 DB 노출 **보안 취약점**.

## 증거

```yaml
# infolink-agent-bojo-dmz/src/main/resources/application.yml:93-96
spring:
  h2:
    console:
      enabled: true
      path: /h2-console

# infolink-api-collector/src/main/resources/application.yml:63-66
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

두 모듈 모두 `application-prod.yml` / 조건부 활성화 없음 → 프로파일 무관 상시 on.

## 영향

- **보안**: 운영 환경에서 `/h2-console` 경로로 접근 가능 시 DB 스키마/데이터 열람 및 쿼리 실행
- H2 는 개발용 임시 DB 인데 프로덕션에 잔존 → invariant 11B (임시값 제거) 의 확장 위반

## 수정 범위

**방향 A — 프로파일별 설정 (권장)**
```yaml
# application.yml (공통)
spring:
  h2:
    console:
      enabled: ${H2_CONSOLE_ENABLED:false}
      path: /h2-console

# application-dev.yml
spring:
  h2:
    console:
      enabled: true
```

**방향 B — 즉시 false, 개발 시 수동 활성화**
```yaml
spring:
  h2:
    console:
      enabled: false   # 기본 false, 개발 시에만 로컬에서 true 로 변경
```

환경변수 기반이 config-replacement 규약과 일치 (§ F4 와 맞물림).

## 회귀 확인

- dev 환경: 환경변수 주입 또는 프로파일 활성화로 콘솔 사용 가능 유지
- prod 가정 (환경변수 미주입): 콘솔 404 또는 비활성화 확인
- `verify/deployment/config-replacement.md § F / G` 에 H2 콘솔 제어 항목 추가 필요

## 추가 — `infolink-agent-bojo-dmz` H2 컨텍스트

`infolink-agent-bojo-dmz/application.yml:88` 에 H2 인메모리 DB 도 있음:
```yaml
url: jdbc:h2:mem:agent_bojo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```
실배포 시 H2 자체가 필요 없을 가능성 — 이 DB 가 무엇을 위한 것인지 검토 후 제거 고려 (H2 콘솔 비활성화와 별개).

## 관련 문서
- `verify/_invariants/00-overview.md § 11A` (환경 분리) + `§ 11B` (임시값 / 개발용 자산 제거)
- `verify/deployment/config-replacement.md § F4, § G` (추가 대상)
- MEMORY: `feedback_config_replacement_sync`
