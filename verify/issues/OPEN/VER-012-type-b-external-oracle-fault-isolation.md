---
id: VER-012
title: Type B 핸들러 외부 GIMS Oracle 다이렉트 의존 — 장애 격리/재시도 미흡
status: OPEN
created: 2026-04-28
parts: [P9-api-provider]
parallel_safe: true
assignee: forward
related: [VER-011]
---

## 증상 요약

Type B 커스텀 핸들러 17종 (현재 7종 구현, 10종 예정) 이 모두 **외부 GIMS Oracle 직접 쿼리** (`ProviderDataSourceService.getJdbcTemplate("internal")`).

외부 Oracle 다운/지연 시 17종 동시 다운 — 단일 외부 의존 = 카스케이드 장애.

`dev_plan/2026_04/27/type-b-builtin-handler.md` 에 의도된 트레이드오프(실시간성 vs PVD 적재)는 명시되어 있으나, **운영 측면 방어 (타임아웃/재시도/회로차단/모니터링) 가 미흡**.

## 재현 절차

1. Type B 핸들러 7종 모두 `ProviderDataSourceService.getJdbcTemplate(DATASOURCE_ID)` 직접 호출
2. `ProviderDataSourceService.createDataSource()` 의 HikariConfig 검토:
   ```
   connectionTimeout = 10_000      # 10초
   maxLifetime       = 600_000     # 10분
   leakDetectionThreshold = 60_000 # 1분
   # statement / query / read timeout 미설정
   # circuit breaker / retry policy 없음
   ```
3. 외부 Oracle 인위적으로 차단(방화벽/컨테이너 stop) 후 Type B endpoint 호출
4. `ApiGatewayController` 의 catch (Exception) 가 500 응답 + 호출이력 기록 — 부분 격리는 됨

## 기대 vs 실제

### 기대 (invariant 11C — 외부 의존 장애 시 graceful degradation)
- HikariConfig 에 query/socket timeout 설정
- 외부 호출에 retry (지수 백오프)
- 회로 차단기 (Resilience4j 또는 수동) — 일정 실패율 도달 시 단기 차단
- 외부 Oracle 헬스체크 + 알람 (별도)

### 실제
- HikariCP connectionTimeout 만 10초 — 쿼리 자체 timeout 없음
- 재시도 없음 — 첫 실패로 500 응답
- 회로 차단 없음 — 외부 Oracle 다운 지속 시 모든 요청이 10초 대기 후 fail
- 운영자가 외부 Oracle 장애를 즉시 식별할 모니터링 부재 (호출이력만 기록 — 운영자가 능동 조회해야 발견)

## 영향

- 외부 GIMS Oracle 장애 시 Type B 17종 동시 다운 (Type A 메타등록형은 우리 PG 라 영향 없음)
- 운영 인프라가 외부 (NGW Oracle) — 우리가 직접 운영 불가
- 실시간성 우선 설계라 PVD 적재로 회피 불가 (의도된 결정)

## 수정 범위 제안

> 의도된 설계 결정이라 "수정"보다는 **운영 방어 + 모니터링 보강**.

### A. JdbcTemplate 단계 방어
- HikariConfig 에 `connectionTestQuery` 또는 `validationTimeout` 명시
- `JdbcTemplate.setQueryTimeout(seconds)` 핸들러 공통 적용 (예: 30초)

### B. 호출 단계 방어 (선택적)
- Resilience4j CircuitBreaker — 실패율 50% 도달 시 차단, 30초 후 half-open
- 또는 단순 in-memory failure counter (의존성 추가 없이)

### C. 모니터링 / 알람
- `ApiPrvCallHistory` 의 status=FAILED 비율을 노출하는 admin endpoint
- 외부 Oracle 헬스체크 별도 스케줄러 (`@Scheduled`) — DOWN 감지 시 로그 + (옵션) 알람

### D. 운영 가이드 명시
- `verify/deployment/post-deploy-smoke.md` 에 "외부 Oracle 장애 시 Type B 다운 = 의도된 설계" 명시
- `verify/deployment/rollback.md` 에 외부 Oracle 장애 시 응대 절차 (Type A 만 살림 / 운영자 공지)

## 회귀 확인

- 정상 외부 Oracle 상태에서 7 핸들러 응답 동일
- 외부 Oracle 인위 차단 → 핸들러 응답 시간 < 30초 + 500 에러 일관 + 회로 차단 동작
- ApiPrvCallHistory 에 FAILED 누적 + 알람/모니터링 작동

## 관련 문서

- `verify/_invariants/00-overview.md` § 11C (장애 격리 / 복원력)
- `dev_plan/2026_04/27/type-b-builtin-handler.md` § 1.2 (의도된 설계 결정)
- VER-011 (datasource ID 하드코딩 — 함께 검토 시 효율적)
- 2026-04-28 검증 실행 기록 (`verify/runs/2026-04-28.md`)
