# Monitoring Setup — 로그 / 알람 / 관측성

실배포 운영자가 이슈를 **즉시 식별 / 추적** 할 수 있도록 하는 관측성 인프라.
**장애 격리(invariant 11C) 와 연계** — 장애 지점이 불명확하면 통합 서비스 구조의 취약성이 그대로 드러남.

## 1. 로그

### 1.1 로그 규약
- [ ] 전 모듈 로그 언어 한글 통일 (프리픽스 `[Bojo]` / `[BojoInt]` / `[Provide]` 등 유지)
- [ ] Exception 메시지 한글
- [ ] 로그 레벨 기본 = `INFO` (`DEBUG` 금지)
- [ ] 에러 시 스택트레이스 포함 + **원인 식별 가능한 맥락** 로깅 (agentCode / executionId / mappingName)

### 1.2 로그 경로 / 로테이션
- [ ] 로그 경로 환경변수화 (`config-replacement E3`)
- [ ] 파일 로테이션 정책 (크기 / 날짜 기준)
- [ ] 보존 기간 (법정 보존 요구사항 확인 필요)

### 1.3 로그 수집 (중앙 집중)
- [ ] 수집 도구 선정 (ELK / Loki / CloudWatch 등 — **확정 필요**)
- [ ] 수집 대상 모듈 전체 (11 개 서비스)
- [ ] 검색 / 필터 기준: executionId, agentCode, mappingName, trace-id

## 2. 메트릭 / 대시보드

### 2.1 기본 헬스체크
- [ ] 각 서비스 `/actuator/health` 응답 확인
- [ ] 각 서비스 `/actuator/info` 버전 정보
- [ ] 헬스체크 주기 (예: 30s)
- [ ] 헬스체크 실패 시 알람

### 2.2 비즈니스 메트릭
- [ ] Agent 실행 성공률 (executionId 단위)
- [ ] Mapping 단위 read/write 건수
- [ ] Retention 수행 결과
- [ ] source 추적 조회 응답 시간
- [ ] DB 연결 풀 사용률
- [ ] 외부 API 응답 시간 / 실패율

### 2.3 대시보드 (확정 필요)
- [ ] 도구 선정 (Grafana / CloudWatch / 등)
- [ ] 운영자용 대시보드 (Agent 상태 / 최근 실행 / 에러 추세)
- [ ] 관리자용 대시보드 (시스템 리소스 / 의존 상태)

## 3. 알람

### 3.1 알람 채널
- [ ] 채널 선정 (메일 / 메신저 / SMS — 확정 필요)
- [ ] 에스컬레이션 순서
- [ ] 당직자 / 담당자 매핑

### 3.2 알람 조건
- [ ] 서비스 헬스체크 실패 (3 회 연속 등)
- [ ] Agent 실행 예외 발생
- [ ] 외부 DB 연결 실패
- [ ] 디스크 사용률 > 임계치
- [ ] JVM heap / GC 이상
- [ ] 특정 invariant 위반 감지 (장기 목표)

### 3.3 알람 피로 관리
- [ ] 반복 알람 suppress 정책
- [ ] 유지보수 창구 (scheduled silence)

## 4. 분산 추적 (선택)

- [ ] Trace ID 도입 여부 결정
- [ ] 도구 선정 (Zipkin / Jaeger / OpenTelemetry)

## 5. 감사 로그 (audit)

- [ ] Orchestrator 관리 작업 (Agent 시작/중지 / 설정 변경 / DataSource 등록 갱신) 감사 로그 보존
- [ ] Retention 정책이 감사 로그를 **지우지 않도록** (invariant 11D 연계)
- [ ] 감사 로그 접근 권한 관리

## 6. 확정 필요 항목 (TODO)

- [ ] 로그 수집 스택 선정
- [ ] 대시보드 도구 선정
- [ ] 알람 채널 선정
- [ ] 법정 보존 기간 확인
- [ ] 분산 추적 도입 여부
