---
id: VER-009
title: 로그 설정 파일 부재 — Spring Boot 기본값만 사용
status: OPEN
created: 2026-04-23
parts: [P1-common, P4-bojo, P5-others, P6-bojo-int, P7-provide, P8-api-collector, P9-api-provider, P10-orchestrator, P2-proxy-dmz, P3-proxy-internal]
parallel_safe: true
assignee: forward
related: []
---

## 증상

프로젝트 전체에 `logback-spring.xml` / `logback.xml` **0 건**. 즉 **모든 모듈이 Spring Boot 기본 로그 설정만 사용**.

실배포 시 다음이 전부 기본값:
- 로그 레벨 (기본 `INFO`, 운영 조정 불가)
- 로그 경로 (기본 stdout only, 파일 저장 없음)
- 롤링 정책 (없음 — 파일 누적 또는 없음)
- 포맷 (Spring Boot 기본 콘솔 포맷)
- Appender (콘솔만)

## 증거

```
find D:/dev/claude/GIMS/orchestrator_v2 -name "logback-spring.xml" -o -name "logback.xml"
→ 결과 없음
```

## 영향

- **관측성 부족** — 파일 로그 없어 사후 분석 불가
- **로그 레벨 조정 불가** — 디버그 필요 시 코드/yml 수정 재배포 필요
- **디스크 보호 없음** — 롤링 없으면 디스크 고갈 위험 (특히 stdout → systemd journal 누적)
- **모듈별 로그 구분 어려움** — 통일 포맷 부재 시 로그 수집 도구(ELK/Loki) 매칭 규칙 복잡해짐
- invariant 11A (운영 환경 적합성) + `monitoring-setup.md § 1.2 (로그 경로 환경변수화)` 미충족
- 본 결함은 **실배포 이후 문제 발견 시 복구 비용 큼** (검증 세션 관점 중요도 ↑)

## 수정 범위

### 방향 A — 모듈별 `logback-spring.xml` 생성 (권장)

각 모듈 `src/main/resources/logback-spring.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty name="LOG_PATH" source="logging.file.path" defaultValue="./logs"/>
    <springProperty name="APP_NAME" source="spring.application.name" defaultValue="app"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APP_NAME}.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>5GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>

    <!-- profile 미지정 시 fallback -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

각 모듈의 `application.yml` 에 `spring.application.name` 설정 추가 (없다면).

### 방향 B — 공용 logback XML 을 common 에 두고 각 모듈이 import

모듈 수가 10+ 이라 복붙 유지보수 부담 있음. common 에 기본 템플릿 + 모듈별 name 만 override.

## 회귀 확인

- dev 로컬 기동 시 로그 경로 / 포맷 / 레벨 유지 (기본 동작 변화 없음 확인)
- `prod` 프로파일 가정 시 파일 로그 생성 + 롤링 동작
- 운영자가 `LOG_PATH` / `spring.application.name` / 로그 레벨 환경변수로 제어 가능한지 확인

## 관련 문서
- `verify/_invariants/00-overview.md § 11A`
- `verify/deployment/monitoring-setup.md § 1 (로그)`
- `verify/deployment/config-replacement.md § E3` (로그 경로 치환 대상) + § F (환경 전환 시 로그 레벨 조정)
- 모듈 일관성: 10+ 모듈 균일 적용 필요 (큰 변경)
