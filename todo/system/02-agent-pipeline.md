# 2. Agent 파이프라인

> **요구사항**: 10개 외부 업체 DB, 새올 시스템, 제주 보조관측망, 이용량 등
> 산재된 데이터 소스를 자동 수집하여 내부 GIMS DB에 통합 적재한다.
> 기존에 수동/배치로 처리되던 프로세스를 Agent 기반으로 자동화·가시화한다.

## 상태: 개발완료 (등록 진행중)

---

## 파이프라인 엔진 [Common]
- [x] SourceToIfStep → SourceToTargetStep 개명·중립화 (IF 전제 제거, 4/23)
- [x] PipelineRegistry — (agentCode, modeId) 복합키 라우팅
- [x] LoaderStepHelper — Loader 공통 로직 (processJewon, processObsvdata, saveSyncLog)
- [x] StepFactory 다중 factory-key 지원
- [x] SimpleLoadStep merge-key List 확장 (4/30, 자연키 복수 컬럼 지원, 후방 호환)
- [x] YAML 기반 설정 — 코드 변경 없이 YAML만으로 파이프라인 추가
- [x] AgentConfigLoader — select-tables/table-mappings Step에서 자동 수집 (config 일원화)
- [x] IF entity 자동채번 표준 정합 (4/30, 8 entity @GeneratedValue(IDENTITY) + PG 6 테이블 sequence)
- [x] 룰 명문화 — Target IF entity PK = 자동채번 명시 기본, exclude-insert-columns 로 조정

## 조건실행/증분실행 [Common]
- [x] ConditionBuilder — 동적 WHERE 조건 생성 (날짜 캐스팅, Oracle/PG 호환)
- [x] 조건 테이블 바인딩 — tableName별 조건 필터링 (jewon/obsvdata 분리)
- [x] 증분실행 — LinkTable 기반 (last sync 이후 변경분만 수집)
- [x] RESYNC — 전체 재수집 모드

## 추적/SyncLog [Common]
- [x] SyncLog per-mapping — mappingName/sourceTables/targetTables/readCount/writeCount
- [x] Source 추적 3단계 분기 — RCV(PK파싱) / Loader(source_refs IN) / SND(PK파싱)
- [x] execution_id 인덱스 — IF/Target 6개 엔티티
- [x] 복합PK 추적 지원 (배치모드 서브쿼리)

## Multi-DB 호환/보안 [Common]
- [x] isMysql()/qi() 헬퍼 + Oracle 분기
- [x] PasswordEncryptor 중앙화 + ApiKeyFilter 공통
- [x] 컨트롤러 @ConditionalOnProperty — 모듈별 엔드포인트 선택적 활성화

## DMZ 수집 Agent [Bojo:8082]
- [x] RCV 파이프라인 — 10개 업체 YAML 설정, LinkTable 기반 증분 수집
- [x] DefaultLoadStep — IF → Target MERGE (DMZ Loader)
- [x] 조건실행/증분실행 통합 (isResyncExecution, buildMergedConditions)
- [x] SyncDataSourceService — Proxy 경유 + 암호화 통신
- [x] 환경부 표준 컬럼명 적용 (Target 4개 테이블 DDL 재생성 + 코드 반영)
- [x] HikariCP 풀 하드닝 (maxPool=10, timeout=10s, leak=60s)

## DMZ 전송 Agent [Others:8085]
- [x] 모듈 생성 (bojo 복제 기반, SND 전용)
- [x] IF_SND 엔티티 6개 + YAML 2개 Agent (제주/이용량)
- [x] SaeolLinkPlanSndStep — LINK_PLAN 커서 순회, flag→link_status 매핑, deduplicate
- [x] IF_SND 16개 테이블 Oracle DDL + 엔티티 (새올)
- [x] 커서 위치 관리 (LINK_PLAN_CURSOR, 전체 완료 후 1회 갱신)
- [ ] rgetnpmms01/rgetnwavi05/rgetnwavi06 SND 추가 (담당자 확인 후)

## 내부망 Oracle 전환 [Bojo-Int:8092]
- [x] Oracle Docker 환경 (gvenzl/oracle-xe:21-slim, 29004)
- [x] MERGE INTO, 날짜 함수, Multi-DB 분기
- [x] 자체 DB (sync_log/execution) Oracle 전환
- [x] Proxy Oracle 지원 (ojdbc8)
- [x] PG/Oracle 호환 (TEXT→length=4000, ILIKE 분기, Time 바인딩)

## 내부망 Loader Step [Bojo-Int:8092]
- [x] InternalBojoLoadStep — 기본 Loader (기존 10업체 데이터)
- [x] 새올 16개 테이블 Loader (DefaultLoadStep, 1:1 MERGE)
- [x] I1 JejuJewonLoadStep — 제원 (1소스→5타겟 순차 MERGE, 고정값, BRNCH_ID 확보)
- [x] I2 JejuObsvdataLoadStep — 관측 (MSN 분기 S11/S2X, EAV, BRNCH_ID 캐싱)
- [x] I3 JejuFacilityLoadStep — 이용시설 (JPA+MERGE+SyncLog)
- [x] I5 UseLoadStep — 이용량 (Legacy+Status 2소스→4타겟, 일집계 후처리, 음수→0 변환)
- [x] **약수터 Loader (4/30)** — 제원 1026 + 수질 1000 row, SimpleLoadStep merge-key 자연키 4키 dedup
- [x] 내부망 RCV 파이프라인 (6개 IF_RSV 테이블)
- [x] WTSMP_YMD length 8→10 정합 (4/30, bojo-internal IF_RSV_TD + TD entity + Oracle DROP/recreate)
- [x] bojo-internal 4 entity @org.hibernate.annotations.Table 제거 (4/30, NamingStrategy 충돌 해소)

## Entity 전환 [Bojo-Int:8092]
- [x] gen_entities.py — DDL→Entity 55개 자동 생성
- [x] DynamicEntityManagerService + CaseAwareNamingStrategy
- [x] ddl-auto:create 검증 (52개 100% 원본 일치)
- [x] Phase 5: Step 4개 JPA 읽기 전환
- [ ] Phase 6: E2E 검증

## DB 연결 프록시 [Proxy]
- [x] infolink-proxy-dmz (8083) — DMZ Agent ↔ 외부 DB
- [x] infolink-proxy-internal (8093) — Internal Agent ↔ 내부 DB
- [x] Oracle 지원 (ojdbc8 의존성)

---

## DMZ 수집 업체 등록 (RCV) [등록]
### 보조관측망
- [x] 대전 (PostgreSQL)
- [x] 바이텍 (PostgreSQL)
- [x] 충남 (PostgreSQL)
- [x] 근산 (PostgreSQL, 대문자)
- [x] 인포월드 로컬 (MySQL)
- [x] 인포월드 서울 (MySQL)
- [x] 하이드로넷 아라 (MySQL)
- [x] 하이드로넷 IDC (MySQL)
- [x] 하이드로넷 경남 (MySQL)
- [x] 하이드로넷 원주 (MySQL)

## DMZ Loader 등록 [등록]
### 보조관측망
- [x] 보조관측 제원/관측 적재 (IF_RSV → sec_jewon, sec_obsvdata)

## DMZ 전송 등록 (SND) [등록]
### 보조관측망
- [x] 보조관측 제원/관측 2테이블 전송 (sec_jewon, sec_obsvdata)
### 새올
- [x] 새올 16테이블 전송 (LINK_PLAN 기반)
### 제주
- [x] 제주 제원/관측/이용시설 3테이블 전송
- [ ] 제주 허가신고/수질검사 3테이블 — 담당자 확인 후
### 이용량
- [x] 이용량 3테이블 전송 (legacy, status, jejuday)
### API 수집
- [x] 나라장터/네이버 뉴스 2테이블 전송 (tm_gd014000, tm_gd014001)
### 약수터
- [x] 약수터 제원/수질 2테이블 전송 (tm_gd010310, td_gd010310, 4/30)

## 내부망 수신 등록 (RCV) [등록]
### 보조관측망
- [x] 보조관측 2테이블 수신
### 새올
- [x] 새올 16테이블 수신
### 제주
- [x] 제주 3테이블 수신
### 이용량
- [x] 이용량 3테이블 수신
### API 수집
- [x] 나라장터/네이버 뉴스 2테이블 수신
### 약수터
- [x] 약수터 제원/수질 2테이블 수신 (4/30)

## 내부망 적재 등록 (Loader) [등록]
### 보조관측망
- [x] 보조관측 적재 (IF_RSV → PM_GD970201, TM_GD970101, TM_GD980002)
### 새올
- [x] 새올 16테이블 적재 (1:1 MERGE)
### 제주
- [x] 제주 적재 (3소스 → 7+ 타겟)
### 이용량
- [x] 이용량 적재 (2소스 → 4타겟)
### API 수집
- [x] 나라장터/네이버 뉴스 적재 (2소스 → TM_GD014000, TM_GD014001)
### 약수터
- [x] 약수터 제원/수질 적재 (4/30, 자연키 UK + DO UPDATE / 4키 dedup)

## 파이프라인 전체 흐름 [참고]

```
보조관측: 외부DB 10개 →[RCV 10]→ IF_RSV →[Loader]→ Target →[SND]→ IF_SND →[RCV]→ IF_RSV →[Loader]→ GIMS (3)
새올:     새올 Oracle →[SND]→ IF_SND(16) →[RCV]→ IF_RSV(16) →[Loader]→ GIMS(16)
제주:     API Collector →[DMZ DB]→[SND]→ IF_SND(3) →[RCV]→ IF_RSV(3) →[Loader]→ GIMS(7+)
이용량:   API Collector →[DMZ DB]→[SND]→ IF_SND(3) →[RCV]→ IF_RSV(3) →[Loader]→ GIMS(4)
API수집:  API Collector →[DMZ DB]→[SND]→ IF_SND(2) →[RCV]→ IF_RSV →[Loader]→ GIMS(2)
```
