# 4. 모니터링/운영

> **요구사항**: 등록된 Agent와 API 수집의 실행 현황을 중앙에서 모니터링하고,
> 데이터 흐름을 추적하며, 보존기간 관리와 스케줄 실행을 제공한다.
> 기존에 로그 파일이나 DB 직접 조회로 확인하던 것을 대시보드로 통합한다.

## 상태: 개발완료

---

## Agent 관리 API [Backend]
- [x] Agent CRUD (등록/수정/삭제/조회)
- [x] auto-discover — Agent 프로세스의 가용 agentCode 목록 조회
- [x] 상태 확인 (health-check)
- [x] 테이블 동기화 — pipeline/info 기반 자동 갱신
- [x] Retention 설정 조회/저장
- [x] 조건실행용 select-tables 조회
- [x] 테스트 데이터 생성/삭제

## 실행 관리 API [Backend]
- [x] 실행 트리거 (수동/조건/증분 실행, conditions 파라미터)
- [x] 빈 conditions 거부 (400 안전가드)
- [x] 실행 콜백 (started/finished)
- [x] runningAgentCodes 동시 실행 방지 + finally 해제

## 실행 이력 API [Backend]
- [x] Agent별 이력 조회 (Agent DB 직접 / Orchestrator DB)
- [x] 페이징 이력 조회 — 필터(상태/Zone/타입/날짜/Agent) + 검색
- [x] 최근 50건 (대시보드용)
- [x] 실행 중 목록
- [x] 대시보드 통계

## 실행 상세/추적 API [Backend]
- [x] 실행 상세 + Step별 결과
- [x] 테이블별 통계 (per-mapping readCount/writeCount/failedCount/skipCount)
- [x] 테이블 레코드 조회 (페이징+검색) + 실패 레코드 조회
- [x] 실행 데이터 조회 (SOURCE/TARGET 페이징+검색, target-if는 /target으로 통합 4/22)
- [x] 정방향 추적 — Source PK → Target 데이터 흐름
- [x] 역추적 — Target → Source 역방향
- [x] 3단계 자동 분기 (RCV/Loader/SND)
- [x] 복합PK 추적 + 테이블명 fallback 검색

## 스케줄 관리 API [Backend]
- [x] 스케줄 CRUD (생성/수정/삭제/조회)
- [x] 활성화/비활성화 토글
- [x] ScheduleExecutor 패턴 통일 (@EventListener)

## Retention 보존기간 [Backend]
- [x] Agent별 Retention 설정 (테이블별 보존일수)
- [x] 보존기간 초과 데이터 자동 삭제
- [x] 음수 방어 4계층 (프론트 min=1 → API 검증 → Controller 예외 → Service skip)
- [x] targetDatasourceId body 필수 (Internal Agent cleanup)

## 관리 테이블 DB 라우팅 [Backend, 4/22 추가]
- [x] X-Manage-Datasource-Id 헤더 기반 라우팅 (DataSourceProvider 확장)
- [x] Orchestrator ExecutionService → Proxy 호출 시 Agent.targetDatasourceId 자동 주입
- [x] ExecutionDataController 조회 경로 JPA → JdbcTemplate 전환
- [x] ExecutionDataReader 공통 조회 유틸 도입

## Agent 자동 관리 [Backend]
- [x] auto-discover — pipeline/info 조회 → 파이프라인 구성 자동 등록
- [x] pipeline/info 인증 예외 처리 (ApiKeyFilter)
- [x] agent_table 자동 동기화 — OFFLINE→ONLINE 시 테이블 목록 갱신
- [x] 수동 테이블 갱신 버튼
- [x] Agent 등록 시 zone 드롭다운 + 필수 검증

## 대시보드 화면 [Frontend]
- [x] 통계 카드 — Agent 전체/온라인/오프라인, 실행 중, 오늘 실행/실패
- [x] 카드 클릭형 필터 — 해당 상태의 Agent/실행이력 테이블 표시
- [x] Agent 상태 테이블 — 이름, Zone, 상태(배지), 마지막 실행
- [x] 실행 이력 테이블 — Agent명, 타입, 상태, 건수, 소요시간
- [x] 10초 자동 갱신 (setInterval 폴링)

## Agent 관리 화면 [Frontend]
- [x] Agent 타입별 그룹화 (RCV/SND/Loader/DB Proxy 색상 코드)
- [x] 그룹 접힘/펼침 토글
- [x] 작업 버튼: 상태확인, 상세, 삭제

## Agent 등록 화면 [Frontend]
- [x] 3단계 auto-discover (URL 입력 → Agent 선택 → 검증+등록)
- [x] auto-discover 테이블 검증 — YAML vs Datasource 자동 대조 (✓/✗)
- [x] agentInfo 없을 때 수동 테이블 선택 (체크박스)

## Agent 상세 화면 [Frontend]
- [x] 상태 배지 (ONLINE/OFFLINE/RUNNING)
- [x] 헤더 버튼: 상태확인, 실행(온라인시만), 조건실행(드롭다운), 삭제
- [x] 기본정보 탭 — 파이프라인 구성, Datasource 매핑, pass/fail 검증, Retention, 스케줄
- [x] 실행이력 탭 — 상태, 건수, 소요시간, 트리거

## 조건실행 패널 [Frontend]
- [x] 동적 WHERE 조건 빌더
- [x] 테이블/컬럼/연산자/값 선택 (dataType별 필터링)
- [x] BETWEEN 시 range 입력
- [x] 조건 추가/제거

## 실행 이력 화면 [Frontend]
- [x] 2줄 필터 바 (상태/Zone/Agent Code/타입/날짜/검색)
- [x] 페이지네이션 (이전/다음 + 페이지 번호)
- [x] URL 쿼리 동기화

## 실행 상세 화면 [Frontend]
- [x] 실행 정보 (상태, 소요시간, 건수, 트리거, 시작/종료)
- [x] 테이블 스탯 — SOURCE/TARGET별 건수 요약
- [x] 테이블 데이터 탭 — 2타입 전환, 검색, 필터, 정렬, 페이징

## 데이터 추적 화면 [Frontend]
- [x] 행 클릭 → SOURCE ↔ TARGET 트레이싱 (IF 없는 Agent도 커버, 4/22)
- [x] source_refs 기반 자동 매칭
- [x] 추적 비대상 표시 (파생 데이터)
- [x] Fallback 모드 감지 (실행 덮어쓰기 시)

---

## 관리 테이블 네이밍 정리 [운영]

> 현행: orchestrator DB와 dev DB에 관리 테이블이 분산되어 있고 프리픽스 없이 범용 이름 사용.
> 목표: 모듈별 프리픽스 부여하여 한 DB로 합쳐도 구분 가능하게 정리.
> 배포 시: 보조망 PG에 관리용 스키마를 새로 파서 관리 테이블 통합.

### 현행 → 변경 매핑

**Agent 관리 (orchestrator DB)**
- [ ] `agent` → `agt_agent`
- [ ] `agent_table` → `agt_agent_table`
- [ ] `schedule` → `agt_schedule`
- [ ] `execution_history` → `agt_execution_history`
- [ ] `execution_step_history` → `agt_execution_step_history`

**공통 (orchestrator DB, 변경 없음)**
- [x] `datasource` — 그대로
- [x] `datasource_column` — 그대로
- [x] `datasource_table` — 그대로
- [x] `zone_config` — 그대로

**API Collector 관리 (dev DB → orchestrator로 이전 예정)**
- [ ] `api_endpoint` → `api_col_endpoint`
- [ ] `api_param` → `api_col_param`
- [ ] `api_field_mapping` → `api_col_field_mapping`
- [ ] `api_schedule` → `api_col_schedule`
- [ ] `api_execution_history` → `api_col_execution_history`

**API Provider 관리 (신규, orchestrator DB)**
- [ ] `api_prv_operation`
- [ ] `api_prv_operation_column`
- [ ] `api_prv_operation_param`
- [ ] `api_prv_call_history`
