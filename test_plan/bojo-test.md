# 보조관측망 (BOJO) 기능 테스트 문서

> 보조관측망 시스템의 전체 기능을 검증하기 위한 재사용 가능 테스트 문서.
> 기능 추가/수정 시 해당 섹션을 업데이트하여 반복 사용한다.

### 공통 테스트 규칙
> **모든 실행 테스트는 추적(Trace) 검증을 포함한다.**
> 추적 검증은 아래 **3단계를 모두** 수행해야 "정상"으로 판단한다.
>
> **1단계: 건수 확인**
> ```
> GET /api/executions/{executionId}/data/summary   → read/write/total 건수
> GET /api/executions/{executionId}/data/target?tableName=...  → target 건수 (execution_id 기준)
> GET /api/executions/{executionId}/data/source?tableName=...  → source 건수 (역추적 매칭)
> ```
>
> **2단계: 단건 역추적 (API)**
> ```
> GET /api/executions/{executionId}/trace-source?sourceRefs=...  → source 원본 1건 매칭
> ```
> target 행 1건의 source_refs로 source 원본 레코드가 정상 반환되는지 확인.
>
> **3단계: 프론트 UI 검증**
> - 실행 상세 화면 → 테이블별 처리 현황 → TARGET 행 클릭 → 테이블 상세 조회
> - 데이터 목록에서 **행 클릭** → 추적 결과(Source 테이블/데이터) 정상 표시
> - Oracle(대문자 컬럼) 환경에서도 동작하는지 확인
>
> API만 확인하고 프론트 미확인 시 "정상"으로 판단하지 않는다.

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [API Key / 엔드포인트 보안](#2-api-key--엔드포인트-보안)
3. [Proxy 패스스루 / 암호문 통신](#3-proxy-패스스루--암호문-통신)
4. [E2E 파이프라인](#4-e2e-파이프라인)
5. [동적 WHERE 조건 (Conditions)](#5-동적-where-조건-conditions)
6. [Source 추적 (Trace)](#6-source-추적-trace)
7. [Schedule (스케줄 실행)](#7-schedule-스케줄-실행)
8. [Retention (데이터 보존)](#8-retention-데이터-보존)
9. [프론트엔드 UI](#9-프론트엔드-ui)

---

## 1. 시스템 구성

### 1-1. 서비스 포트
| 서비스 | 포트 | 비고 |
|--------|------|------|
| Orchestrator Backend | 8080 | 중앙 관리 |
| Agent DMZ (sync-agent-bojo) | 8082 | RCV/Loader/SND |
| Proxy DMZ | 8083 | DMZ↔Internal 중계 |
| Agent Internal (sync-agent-bojo-int) | 8092 | Int RCV/Loader |
| Proxy Internal | 8093 | Internal↔DMZ 중계 |
| Frontend (Next.js) | 3000 | 모니터링 UI |

### 1-2. DB 포트
| 용도 | 타입 | 포트 | DB명 | 비고 |
|------|------|------|------|------|
| Orchestrator | PostgreSQL | 29001 | orchestrator | |
| DMZ IF/Target | PostgreSQL | 29001 | dev | |
| Internal IF/Target | **Oracle** | **29004** | **XEPDB1** | Docker oracle-xe:21-slim, 계정 k1m/1111 |
| 외부 PG (4개) | PostgreSQL | 29000 | daejeon, bytek, chungnam, keunsan | |
| 외부 MySQL (6개) | MySQL | 29010 | infoworld_*, hydronet_* | |

> **Oracle 전환 참고**:
> - Internal IF/Target이 PG 29002 → Oracle 29004로 변경됨 (실운영 환경 = Oracle/Tibero)
> - DMZ 구간은 기존 PG 그대로 유지
> - Oracle DDL: `scripts/oracle-init.sql` (Target 4테이블), `scripts/oracle-init-if.sql` (IF 2테이블)
> - JDBC URL: `jdbc:oracle:thin:@//localhost:29004/XEPDB1`

### 1-3. Agent 목록
| ID | agentCode | 타입 | 설명 | 외부 DB |
|----|-----------|------|------|---------|
| 7 | dmz-bojo-rcv-daejeon | RCV | 대전 | PG daejeon |
| 8 | dmz-bojo-rcv-bytek | RCV | 바이텍 | PG bytek |
| 9 | dmz-bojo-rcv-infoworld-local | RCV | 인포월드(로컬) | MySQL |
| 10 | dmz-bojo-rcv-infoworld-seoul | RCV | 인포월드(서울) | MySQL |
| 11 | dmz-bojo-rcv-chungnam | RCV | 충남 | PG chungnam |
| 12 | dmz-bojo-rcv-keunsan | RCV | 근산 | PG keunsan (대문자) |
| 13 | dmz-bojo-rcv-hydronet-ara | RCV | 아라 | MySQL |
| 14 | dmz-bojo-rcv-hydronet-idc | RCV | IDC | MySQL |
| 15 | dmz-bojo-rcv-hydronet-kyungnam | RCV | 경남 | MySQL |
| 16 | dmz-bojo-rcv-hydronet-wonju | RCV | 원주 | MySQL |
| 17 | dmz-bojo-loader | Loader | DMZ Loader | — |
| 18 | dmz-bojo-snd | SND | DMZ SND | — |
| 19 | internal-bojo-rcv | RCV | 내부 RCV | — |
| 20 | internal-bojo-loader | Loader | 내부 Loader | — |

### 1-4. 파이프라인 흐름
```
외부 DB (10업체)
  ↓ RCV (dmz-bojo-rcv-*)
IF_RSV (if_rsv_sec_jewon, if_rsv_sec_obsvdata) + link_ngwis 갱신  [PG 29001/dev]
  ↓ Loader (dmz-bojo-loader)
Target (sec_jewon, sec_obsvdata)                                   [PG 29001/dev]
  ↓ SND (dmz-bojo-snd)
IF_SND (if_snd_sec_jewon, if_snd_sec_obsvdata)                    [PG 29001/dev]
  ↓ Proxy DMZ→Internal
  ↓ Internal RCV (internal-bojo-rcv)
Internal IF_RSV (if_rsv_sec_jewon, if_rsv_sec_obsvdata)           [Oracle 29004/XEPDB1]
  ↓ Internal Loader (internal-bojo-loader)
GIMS (pm_gd970201 + tm_gd970001 + tm_gd970101 + tm_gd980002)     [Oracle 29004/XEPDB1]
```

> **Oracle 구간**: Internal RCV 이후부터 Oracle. DMZ까지는 PG 그대로.

### 1-5. 테이블 구조 요약
| 단계 | 테이블 | DB | 비고 |
|------|--------|-----|------|
| 외부 Source | sec_jewon_view, sec_obsvdata_view | 외부 DB | 읽기 전용 뷰 |
| DMZ IF_RSV | if_rsv_sec_jewon, if_rsv_sec_obsvdata | 29001/dev | |
| DMZ Target | sec_jewon, sec_obsvdata | 29001/dev | |
| DMZ IF_SND | if_snd_sec_jewon, if_snd_sec_obsvdata | 29001/dev | |
| Link | link_ngwis | 29001/dev | RCV 증분 기준점 |
| Internal IF_RSV | if_rsv_sec_jewon, if_rsv_sec_obsvdata | **Oracle 29004/XEPDB1** | Oracle MERGE INTO |
| GIMS Target | pm_gd970201, tm_gd970001, tm_gd970101 | **Oracle 29004/XEPDB1** | 1 obsvdata = 3행 (gwdep/gwtemp/ec) |
| GIMS Link | tm_gd980002 | **Oracle 29004/XEPDB1** | 증분 추적 |

---

## 2. API Key / 엔드포인트 보안

### 2-1. 개요
- common 모듈의 컨트롤러에 `@ConditionalOnProperty`를 적용하여 모듈별 활성/비활성 제어
- common 모듈에 `ApiKeyFilter`를 두어 Agent/Proxy 모두 `/api/**` 요청에 `X-API-Key` 필수
- `/health`만 인증 없이 접근 가능, `/debug/datasources`는 제거됨

### 2-2. 컨트롤러 활성화 현황

| 컨트롤러 | Agent (bojo/bojo-int) | Proxy (DMZ/Internal) |
|----------|:---:|:---:|
| DataRetentionController (`/api/cleanup`) | O | **X** |
| ExecutionDataController (`/api/execution-data`) | O | O |
| DatasourceController (`/api/datasource`) | O | O |
| ExecutionParamsController (`/api/pipeline/execution-params`) | O | **X** |
| StepDefinitionController (`/api/pipeline/step-definitions`) | (ConditionalOnBean) | **X** |

### 2-3. 테스트 항목

#### API Key 인증 (Agent)
- [ ] `X-API-Key` 없는 요청 → 401
- [ ] `X-API-Key` 틀린 값 → 401
- [ ] `X-API-Key` 정상 → 200 (정상 동작)
- [ ] `/health` → 인증 없이 200

#### API Key 인증 (Proxy)
- [ ] `X-API-Key` 없는 요청 → 401
- [ ] `X-API-Key` 정상 → 200 (정상 동작)
- [ ] `/health` → 인증 없이 200

#### Proxy 컨트롤러 차단
- [ ] `POST /api/cleanup/...` → 404 (비활성)
- [ ] `GET /api/pipeline/step-definitions` → 404 (비활성)
- [ ] `GET /api/pipeline/execution-params` → 404 (비활성)
- [ ] `GET /api/execution-data/...` → 200 (활성)
- [ ] `POST /api/datasource/test-connection` → 200 (활성)

#### 디버그 엔드포인트 제거
- [ ] Agent `GET /debug/datasources` → 404
- [ ] Proxy `GET /debug/datasources` → 404

#### E2E 연동 (Orchestrator RestTemplate에 api-key 자동 포함)
- [ ] Orchestrator → Agent 파이프라인 실행 → SUCCESS
- [ ] Orchestrator → Proxy → execution-data 조회 → 정상
- [ ] Orchestrator → Agent cleanup → 정상

---

## 3. Proxy 패스스루 / 암호문 통신

### 3-1. 개요
- 3/17~3/18 암호문 통신 전환으로 connection-info 흐름 변경
- Agent는 **반드시 Proxy 경유**로만 datasource 정보를 받음 (Orchestrator 직접 fallback 제거)
- Proxy는 Orchestrator 응답을 **복호화 없이 그대로** Agent에게 전달 (패스스루)
- Agent가 로컬에서 복호화하여 JDBC 연결 생성

### 3-2. 통신 흐름
```
Agent (connection-info 요청)
  → Proxy (ConnectionInfoController — 패스스루)
    → Orchestrator (GET /api/datasources/{id}/connection-info)
    ← 암호화된 credential JSON 응답
  ← 그대로 전달 (복호화 안 함)
← Agent (Jasypt 복호화 → JdbcTemplate 생성)
```

### 3-3. 테스트 항목

#### 정상 흐름
- [ ] 파이프라인 실행 시 Proxy 로그: `[Proxy] Passthrough connection-info request: {datasourceId}`
- [ ] Agent 로그: `Fetched datasource from Proxy: {datasourceId} ({host}:{port})`
- [ ] Agent가 복호화된 credential로 외부 DB 연결 성공
- [ ] DMZ (Proxy 8083 → Orchestrator 8080) 정상
- [ ] Internal (Proxy 8093 → Orchestrator 8080) 정상

#### Oracle 연결 검증 (Internal)
- [ ] Orchestrator에 internal datasource 등록: type=ORACLE, host=localhost, port=29004, db=XEPDB1
- [ ] Orchestrator connection-info 응답에 Oracle JDBC URL 포함: `jdbc:oracle:thin:@//...`
- [ ] Internal Agent가 Oracle JDBC 드라이버(ojdbc11)로 연결 성공
- [ ] Proxy Internal(8093)에 **ojdbc11 필수** — 추적 조회 시 Oracle에 직접 연결함

#### 실패/예외 케이스
- [ ] Proxy 미기동 시 Agent 즉시 예외 발생 (재시도/fallback 없음)
- [ ] Orchestrator 미기동 시 Proxy가 에러 반환 → Agent 예외
- [ ] 존재하지 않는 datasourceId 요청 → 404/에러 응답

#### URL 통일 확인
- [ ] Proxy → Orchestrator 경로: `GET /api/datasources/{id}/connection-info`
- [ ] 이전 경로 `/api/datasource/connection-info/{id}` 는 404 (삭제됨)

---

## 4. E2E 파이프라인

### 4-1. 사전 준비

#### 데이터 초기화 (클린 테스트 시)
```sql
-- DMZ DB (29001/dev, PostgreSQL): IF/Target TRUNCATE (link_ngwis 제외)
TRUNCATE if_rsv_sec_jewon, if_rsv_sec_obsvdata,
         sec_jewon, sec_obsvdata,
         if_snd_sec_jewon, if_snd_sec_obsvdata,
         sync_log CASCADE;

-- Internal DB (Oracle 29004/XEPDB1): Oracle은 TRUNCATE 한 테이블씩
-- ※ Oracle은 CASCADE 미지원 — FK 순서 주의 (자식→부모)
TRUNCATE TABLE pm_gd970201;
TRUNCATE TABLE tm_gd980002;
TRUNCATE TABLE tm_gd970101;
TRUNCATE TABLE tm_gd970001;
TRUNCATE TABLE if_rsv_sec_obsvdata;
TRUNCATE TABLE if_rsv_sec_jewon;
-- sync_log는 Internal Agent 자체 H2/메모리이므로 서비스 재시작으로 초기화

-- Orchestrator DB (29001/orchestrator, PostgreSQL):
TRUNCATE execution_history, execution_step CASCADE;

-- link_ngwis (29001/dev): 기준점 설정 (데이터 없이 남김)
UPDATE link_ngwis SET obsv_date = '테스트전날', obsv_time = '235959';
```

> **Oracle 주의사항**:
> - Oracle TRUNCATE는 테이블 1개씩만 가능 (`TRUNCATE TABLE 테이블명`)
> - CASCADE 옵션 없음 — FK 있는 경우 자식 테이블 먼저 TRUNCATE
> - IDENTITY 컬럼 시퀀스 리셋: 테이블 DROP 후 재생성하거나, `ALTER TABLE ... MODIFY (id GENERATED BY DEFAULT AS IDENTITY (START WITH 1))`
> - Oracle 접속: `sqlplus k1m/1111@//localhost:29004/XEPDB1` 또는 DBeaver

#### 테스트 데이터 삽입
```sql
-- 외부 DB 3개 (daejeon, keunsan, infoworld-local)에 obsvdata INSERT
-- 날짜: 테스트 당일, 건수: jewon 수 × 1 = 402건/업체
-- link_ngwis 기준점보다 이후 시점이어야 수집됨
```

#### Internal Loader 제원 사전 등록
```sql
-- Oracle TM_GD970001에 제원 데이터 INSERT (IF_RSV_SEC_JEWON 기반)
-- spot_ty_mng_word_nm = '보조지하수관측망' 필수
-- tm_gd970101(결과 매핑)은 Loader가 ensureResultId로 자동 생성 (time_unit_id=3)
INSERT INTO TM_GD970001 (OBSRVT_ID, SPOT_TY_MNG_WORD_NM, OBSRVT_NM, ...)
SELECT DISTINCT OBSV_CODE, '보조지하수관측망', OBSV_NAME, ... FROM IF_RSV_SEC_JEWON;
```

#### 재테스트 시 주의사항
> **link_status 리셋 필수**: Oracle IF_RSV TRUNCATE만으로는 부족.
> DMZ IF_SND의 `link_status`가 이전 실행에서 `SUCCESS`로 변경되어 있으면
> Internal RCV 증분 조회 시 0건 반환됨.
> ```sql
> -- DMZ DB (29001/dev)
> UPDATE if_snd_sec_obsvdata SET link_status = 'PENDING' WHERE link_status = 'SUCCESS';
> UPDATE if_snd_sec_jewon SET link_status = 'PENDING' WHERE link_status = 'SUCCESS';
> ```
> jewon은 `full-copy: true`라 link_status 무관하게 전체 복사되지만,
> obsvdata는 증분이므로 PENDING 리셋이 필수.

### 4-2. 실행 순서
| 순서 | Agent | 방법 | 예상 결과 |
|------|-------|------|----------|
| 1 | RCV (3개 업체) | Orchestrator 실행 | 각 업체 jewon+obsvdata = 804건 |
| 2 | DMZ Loader | Orchestrator 실행 | 3업체 합산 = 2412건 (jewon 1206 + obsvdata 1206) |
| 3 | DMZ SND | Orchestrator 실행 | Loader와 동일 건수 |
| 4 | Internal RCV | Orchestrator 실행 | SND와 동일 건수 (**Oracle IF_RSV 적재**) |
| 5 | Internal Loader | Orchestrator 실행 | obsvdata만 (**Oracle pm_gd970201**, 1:3 EAV) |

### 4-3. 검증 항목

#### 파이프라인 실행
- [ ] 각 Agent 상태 = SUCCESS
- [ ] Read/Write 건수 일치
- [ ] 콜백 정상 수신 (started + finished)
- [ ] ExecutionHistory에 기록

#### Oracle 적재 검증 (Internal RCV + Loader)
- [ ] Internal RCV: Oracle IF_RSV에 MERGE INTO 정상 (if_rsv_sec_jewon, if_rsv_sec_obsvdata)
- [ ] Internal RCV: Oracle IDENTITY 자동 증가 정상 (id 컬럼)
- [ ] Internal RCV: execution_id, source_refs 정상 기록
- [ ] Internal RCV: VARCHAR2(4000) 범위 내 데이터 잘림 없음
- [ ] Internal RCV: **obsv_time VARCHAR2 변환 정상** (`HH:mm:ss` 형식, `70/01/01` 아님)
- [ ] Internal Loader: Oracle pm_gd970201에 EAV 적재 (1 obsvdata → 3행)
- [ ] Internal Loader: tm_gd970001 제원 매칭 (loadSpotIdMap — `spot_ty_mng_word_nm = '보조지하수관측망'`)
- [ ] Internal Loader: tm_gd970101 결과코드 매칭 (ensureResultId — `time_unit_id = 3`)
- [ ] Internal Loader: tm_gd980002 MERGE INTO (link 증분 추적)
- [ ] Internal Loader: **Oracle DATE→Timestamp 변환 정상** (buildObsrvnDt)
- [ ] Internal Loader: **제원 없을 때 FAILED 반환** (write=0 & read>0 → status=FAILED + 에러메시지)

#### Oracle 인프라 검증
- [ ] **HikariCP connectionTestQuery**: Oracle은 `SELECT 1 FROM DUAL` (bojo-int, proxy-internal)
- [ ] **Proxy Internal ojdbc11**: 추적 조회 시 Proxy가 Oracle에 직접 연결 가능
- [ ] **Oracle 컬럼명 대문자**: qi() 인용 없음, 프론트 SOURCE_REFS 대문자 대응

#### Proxy 패스스루 검증 (모든 실행 필수)
- [ ] Proxy 로그: `[Proxy] Passthrough connection-info request: {datasourceId}` 출력
- [ ] Agent 로그: `Fetched datasource from Proxy: {datasourceId}` 출력
- [ ] Orchestrator에 connection-info 직접 요청 로그 없음 (fallback 제거됨)

#### 추적 검증 (모든 실행 직후 즉시 검증 — 이후 조건실행으로 execution_id 덮어씌워지면 검증 불가)

> **주의**: 추적 검증은 해당 실행 직후에 수행해야 함. UPSERT 구조라 이후 실행이 같은 행을 덮어쓰면
> execution_id가 갱신되어 원래 실행의 target 데이터가 0건으로 보임.

**각 Step 실행 후 필수 검증 항목 (3단계):**

**1단계: 건수 확인**
- [ ] Summary API: read/write/total 건수 일치
- [ ] Target 조회: execution_id 기준 target 테이블 데이터 → read/write 건수와 일치
- [ ] Source 조회: 역추적 매칭으로 source 데이터 건수 확인
- [ ] Target 건수 = Source 건수 (EAV 확장 제외)

**2단계: 단건 역추적 (API)**
- [ ] Target 행 1건의 source_refs로 `GET /trace-source` → source 원본 1건 매칭
- [ ] traceStatus = `FOUND`
- [ ] source 레코드의 주요 필드(obsv_code, obsv_date 등) 값 정상

**3단계: 프론트 UI 검증**
- [ ] 실행 상세 → TARGET 테이블 클릭 → 데이터 목록 표시
- [ ] 데이터 행 클릭 → 추적 결과 (Source 테이블명 + 데이터) 정상 표시
- [ ] Oracle 대문자 컬럼 환경에서도 행 클릭 추적 동작

**Step별 추적 검증 테이블:**

| Step | Agent | target 테이블 | source 테이블 | 매칭 모드 | 비고 |
|------|-------|-------------|-------------|----------|------|
| 1 | RCV (대전) | if_rsv_sec_jewon, if_rsv_sec_obsvdata | sec_jewon_view, sec_obsvdata_view | pk | 외부 DB 뷰 |
| 1 | RCV (근산) | if_rsv_sec_jewon, if_rsv_sec_obsvdata | SEC_JEWON_VIEW, SEC_OBSVDATA_VIEW | pk | 대문자 |
| 1 | RCV (인포월드) | if_rsv_sec_jewon, if_rsv_sec_obsvdata | sec_jewon_view, sec_obsvdata_view | pk | MySQL |
| 2 | DMZ Loader | sec_jewon, sec_obsvdata | if_rsv_sec_jewon, if_rsv_sec_obsvdata | source_refs | IF→Target |
| 3 | DMZ SND | if_snd_sec_jewon, if_snd_sec_obsvdata | sec_jewon, sec_obsvdata | pk | Target→IF_SND |
| 4 | Internal RCV | if_rsv_sec_jewon, if_rsv_sec_obsvdata | if_snd_sec_jewon, if_snd_sec_obsvdata | source_refs | IF_SND→IF_RSV |
| 5 | Internal Loader | pm_gd970201 | if_rsv_sec_obsvdata | source_refs | 1:3 EAV 확장 |

**Forward/Backward Trace (Step 2, 5에서 검증):**
- [ ] Forward Trace: source PK → target 레코드 매칭 확인
- [ ] Backward Trace: target source_refs → source 레코드 역추적 확인

#### 데이터 정합성
| 체크포인트 | jewon | obsvdata | 비고 |
|-----------|-------|----------|------|
| 외부 Source (×N업체) | 업체수×402 | 업체수×402 | |
| IF_RSV (DMZ) | 합산 | 합산 | |
| Target (DMZ) | 합산 | 합산 | |
| IF_SND (DMZ) | 합산 | 합산 | |
| IF_RSV (Internal, **Oracle**) | 합산 | 합산 | Oracle MERGE INTO |
| pm_gd970201 (GIMS, **Oracle**) | — | 합산×3 | 측정항목별 행 분리 |
| tm_gd980002 (**Oracle**) | — | — | link 증분 갱신 확인 |
| link_ngwis (DMZ, PG) | 날짜 갱신됨 | — | |

#### 특이 케이스
- [ ] keunsan: 대문자 테이블명 처리 정상
- [ ] infoworld-local: MySQL 쿼리 정상
- [ ] link_ngwis: 시간비교 `>` (초과) — 경계값 중복 없음
- [ ] tm_gd970101: SyncLog에 포함되지 않음

#### Oracle 특이 케이스
- [ ] Oracle IDENTITY PK: GENERATED BY DEFAULT AS IDENTITY — 배치 INSERT 시 시퀀스 충돌 없음
- [ ] Oracle MERGE INTO: USING DUAL 패턴 — conflict key 매칭 정상
- [ ] Oracle VARCHAR2(4000): PG TEXT→VARCHAR2 변환 시 4000자 초과 데이터 없는지
- [ ] Oracle 날짜: PG timestamp→Oracle TIMESTAMP, PG date→Oracle DATE 매핑 정상
- [ ] Oracle 인용부호: ConditionBuilder에서 Oracle은 인용 없이 자동 대문자
- [ ] Oracle NULL 처리: 빈 문자열 '' = NULL (PG와 차이) — 영향 범위 확인
- [ ] Oracle 재실행 (멱등성): 같은 데이터 2회 실행 시 MERGE INTO 정상 (UPDATE 분기)

#### Oracle 타입 변환 (SourceToIfStep)
- [ ] **java.sql.Time → String**: PG TIME이 Oracle VARCHAR2에 `HH:mm:ss`로 저장 (epoch `70/01/01` 아님)
- [ ] **java.sql.Clob → String**: Oracle CLOB 컬럼 읽기 시 문자열 변환
- [ ] obsv_time 값 확인: Oracle IF_RSV에서 `SELECT OBSV_TIME FROM IF_RSV_SEC_OBSVDATA FETCH FIRST 3 ROWS ONLY`

#### Oracle 추적 검증 주의사항
- [ ] **ExecutionDataController qi()**: Oracle에서 컬럼 인용 안 함 (대문자 자동 매칭)
- [ ] **LIMIT → FETCH FIRST**: 모든 페이징/샘플 쿼리에서 Oracle 호환
- [ ] **information_schema → user_tables**: Oracle 테이블명 조회
- [ ] **TRIM 1문자 제한**: `TRIM(BOTH '[]' ...)` 불가 → `REPLACE` 사용
- [ ] **대량 source 필터 (>5000건)**: 서브쿼리 대신 배치 분할 (1000건씩)
- [ ] **프론트 행 클릭**: `row.SOURCE_REFS` 대문자로도 접근 가능

---

## 5. 동적 WHERE 조건 (Conditions)

### 5-1. 개요
- 수동 실행 시 프론트엔드에서 동적 WHERE 조건을 입력하여 특정 데이터만 재동기화
- 흐름: 프론트 UI → Orchestrator API → Agent PipelineRunner → ConditionBuilder → SQL WHERE
- 조건 실행 시 자동으로: CUSTOM_STAGING 바이패스(SIMPLE_COPY 강제), skipLinkUpdate=true

### 5-2. ConditionBuilder 동작
```
defaults (Step 디폴트)  ─┐
                          ├── merge → build → WHERE 절 + params
conditions (사용자 입력) ─┘
```
- 같은 컬럼: 사용자 조건이 디폴트를 **대체**
- 다른 컬럼: AND로 **추가**
- conditions 없으면: 디폴트 그대로 (기존 동작)

### 5-3. 지원 연산자
| 연산자 | SQL | value2 필요 |
|--------|-----|-------------|
| EQ | `= ?` | N |
| NEQ | `!= ?` | N |
| GT | `> ?` | N |
| GTE | `>= ?` | N |
| LT | `< ?` | N |
| LTE | `<= ?` | N |
| BETWEEN | `BETWEEN ? AND ?` | Y |
| IN | `IN (?, ?, ...)` | N (쉼표 구분) |
| LIKE | `LIKE ?` | N |
| IS_NULL | `IS NULL` | N |
| IS_NOT_NULL | `IS NOT NULL` | N |

### 5-4. 적용 범위 (Agent별)
| Agent | Step | 적용 방식 | DB | 비고 |
|-------|------|----------|-----|------|
| DMZ RCV | SourceToIfStep (jewon) | SIMPLE_COPY + ConditionBuilder | PG | fullCopy 디폴트 대체 |
| DMZ RCV | SourceToIfStep (obsvdata) | SIMPLE_COPY + ConditionBuilder | PG | CUSTOM_STAGING 바이패스 |
| DMZ Loader | DmzBojoLoadStep | Native SQL (EntityManager) + ConditionBuilder | PG | IF→Target 쿼리 |
| Internal RCV | SourceToIfStep | SIMPLE_COPY + ConditionBuilder | **Oracle** | Oracle MERGE INTO + 인용 없음 |
| Internal Loader | InternalBojoLoadStep | JDBC + ConditionBuilder | **Oracle** | Oracle WHERE 문법 |

> **Oracle Conditions 주의사항**:
> - ConditionBuilder: Oracle은 컬럼명 인용 없음 (자동 대문자) — `obsv_code` → `obsv_code` (인용X)
> - LIKE 연산자: Oracle도 `%`, `_` 와일드카드 동일하게 지원
> - BETWEEN: Oracle DATE 비교 시 문자열→DATE 암시적 변환 주의 (NLS_DATE_FORMAT 의존)
> - IS_NULL: Oracle에서 '' (빈 문자열) = NULL이므로 PG와 다른 결과 가능

### 5-5. 안전가드 (전체 데이터 긁어오기 방지)
| 계층 | 방어 방식 |
|------|----------|
| 프론트엔드 | conditions UI 활성화 시 최소 1개 입력 필수 (실행 버튼 disable) |
| Orchestrator | `conditions: []` (빈 배열) → 400 Bad Request |
| Agent | 예외 발생 (최후 방어선) |

### 5-6. 테스트 항목

#### API 레벨
```bash
# 1. EQ 조건 (단일)
curl -X POST http://localhost:8080/api/executions/{agentId}/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "obsv_code", "operator": "EQ", "value": "DJ-DJC-G1-0001"}]}'

# 2. LIKE 조건
curl -X POST http://localhost:8080/api/executions/{agentId}/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "obsv_code", "operator": "LIKE", "value": "DJ-%"}]}'

# 3. BETWEEN 조건 (날짜 범위)
curl -X POST http://localhost:8080/api/executions/{agentId}/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "obsv_date", "operator": "BETWEEN", "value": "2024-01-01", "value2": "2024-12-31"}]}'

# 4. 복합 조건 (EQ + BETWEEN)
curl -X POST http://localhost:8080/api/executions/{agentId}/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "obsv_code", "operator": "EQ", "value": "DJ-DJC-G1-0001"}, {"column": "obsv_date", "operator": "BETWEEN", "value": "2024-01-01", "value2": "2024-12-31"}]}'

# 5. IN 조건 (쉼표 구분)
curl -X POST http://localhost:8080/api/executions/{agentId}/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "obsv_code", "operator": "IN", "value": "DJ-DJC-G1-0001,DJ-DJC-G1-0002"}]}'

# 6. NONEXISTENT 값 → 0건 반환 확인
curl -X POST http://localhost:8080/api/executions/{agentId}/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "obsv_code", "operator": "EQ", "value": "NONEXISTENT"}]}'

# 7. 빈 conditions → 400 거부
curl -X POST http://localhost:8080/api/executions/{agentId}/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": []}'
```

#### RCV 검증 (DMZ)
- [ ] PG 소스 (daejeon 등): conditions 적용, 0건/필터건수 확인
- [ ] MySQL 소스 (infoworld 등): MySQL 백틱 인용 정상
- [ ] keunsan (대문자 테이블): 대문자 컬럼명 조건 정상
- [ ] CUSTOM_STAGING 바이패스 로그 확인: `"bypassing CUSTOM_STAGING, using SIMPLE_COPY"`
- [ ] skipLinkUpdate 로그 확인: `"skipLinkUpdate=true"`
- [ ] 조건 없는 일반 실행: 기존 CUSTOM_STAGING 경로 정상

#### Loader 검증 (DMZ + Internal)
- [ ] DMZ Loader (DefaultLoadStep): conditions → IF 테이블 필터 쿼리 (Native SQL, PG)
- [ ] Internal Loader (InternalLoadStep): conditions → IF 테이블 필터 쿼리 (JDBC, **Oracle**)
- [ ] Internal Loader conditions: Oracle WHERE 절 생성 확인 (인용 없음, 대문자)
- [ ] Loader conditions 없는 일반 실행: 기존 경로 정상

#### 추적 검증 (조건실행)
- [ ] RCV: Summary read/write 건수 확인, target(if_rsv) 조건 필터 건수 일치
- [ ] DMZ Loader: target(sec_jewon/sec_obsvdata) 조건 건수 일치, source(if_rsv) 매칭
- [ ] SND: target(if_snd) 건수, source(sec_*) 매칭
- [ ] Internal RCV: target(if_rsv Internal, **Oracle**) 건수, source(if_snd) 매칭
- [ ] Internal Loader: target(pm_gd970201, **Oracle**) = obsvdata × 3행, source(if_rsv Internal) 매칭

#### 안전가드 검증
- [ ] 빈 conditions `[]` → 400 응답
- [ ] conditions 없이 body 비움 → 정상 증분 실행

---

## 6. Source 추적 (Trace)

### 6-1. 추적 API
```
GET /api/executions/{executionId}/source?tableName={sourceTable}
GET /api/executions/{executionId}/trace
```

### 6-2. 3단계 분기 로직
| 조건 | 모드 | 해당 Agent | 설명 |
|------|------|-----------|------|
| source에 source_refs 컬럼 없음 | pk | RCV | 외부 DB — PK 파싱 매칭 |
| source_refs 있고 값 일치 | source_refs | Loader | IF의 source_refs를 그대로 복사 |
| source_refs 있지만 값 불일치 | pk | SND | PK 기반 새 source_refs 생성 |

### 6-3. 검증 항목 (Agent별)
| Agent | Source 테이블 | 기대 모드 | DB | 기대 건수 |
|-------|-------------|----------|-----|----------|
| RCV daejeon | sec_jewon_view | pk | PG | 업체당 건수 |
| RCV daejeon | sec_obsvdata_view | pk | PG | 업체당 건수 |
| RCV keunsan | SEC_JEWON_VIEW | pk | PG | 업체당 건수 |
| RCV keunsan | SEC_OBSVDATA_VIEW | pk | PG | 업체당 건수 |
| DMZ Loader | if_rsv_sec_jewon | source_refs | PG | 합산 |
| DMZ Loader | if_rsv_sec_obsvdata | source_refs | PG | 합산 |
| DMZ SND | sec_jewon | pk | PG | 합산 |
| DMZ SND | sec_obsvdata | pk | PG | 합산 |
| Internal RCV | if_snd_sec_jewon (DMZ) | source_refs | PG→**Oracle** | 합산 |
| Internal RCV | if_snd_sec_obsvdata (DMZ) | source_refs | PG→**Oracle** | 합산 |
| Internal Loader | if_rsv_sec_obsvdata | source_refs | **Oracle** | 합산 |

> **Oracle 추적 주의**: Internal 구간의 source/target 조회 시 Oracle 쿼리 생성 확인
> - ExecutionDataController가 Oracle dbType에 맞는 쿼리를 생성하는지
> - source_refs 매칭 시 Oracle VARCHAR2 비교 정상 동작 확인

---

## 7. Schedule (스케줄 실행)

### 7-1. 개요
- Orchestrator가 cron 스케줄에 따라 Agent 파이프라인 자동 실행
- DB `schedule` 테이블에 저장, 앱 기동 시 `ScheduleExecutor`가 로드
- 실행 시 `executionService.triggerExecution()` → Agent POST

### 7-2. API 엔드포인트
| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/schedules | 전체 조회 |
| GET | /api/schedules/{id} | 단건 조회 |
| POST | /api/schedules | 생성 |
| PUT | /api/schedules/{id} | 수정 |
| PUT | /api/schedules/{id}/toggle | 활성/비활성 토글 |
| DELETE | /api/schedules/{id} | 삭제 |

### 7-3. 테스트 항목

#### CRUD
- [ ] 스케줄 생성 (cronExpression, agentId, isEnabled)
- [ ] 스케줄 조회 (전체 / 단건)
- [ ] 스케줄 수정 (cron 변경)
- [ ] 스케줄 토글 (활성↔비활성)
- [ ] 스케줄 삭제

#### 자동 실행
- [ ] 스케줄 등록 후 cron 시점에 파이프라인 실행됨
- [ ] ExecutionHistory에 triggeredBy = "SCHEDULE" 기록
- [ ] Agent 로그에 파이프라인 실행 로그
- [ ] 콜백 정상 수신 (started + finished)
- [ ] 비활성 스케줄은 실행 안 됨

#### 테스트용 스케줄 예시
```bash
# 2분마다 실행 (테스트용)
curl -X POST http://localhost:8080/api/schedules \
  -H "Content-Type: application/json" \
  -d '{"agentId":7,"cronExpression":"0 */2 * * * *","isEnabled":true}'

# 조회
curl http://localhost:8080/api/schedules

# 토글
curl -X PUT http://localhost:8080/api/schedules/{id}/toggle

# 삭제
curl -X DELETE http://localhost:8080/api/schedules/{id}
```

---

## 8. Retention (데이터 보존)

### 8-1. 개요
- Agent IF/Target 테이블의 오래된 데이터 자동 삭제
- Orchestrator `DataRetentionScheduler`: 매일 새벽 2시 (기본, `retention.cron` 설정)
- Agent별 `retentionConfig` JSON → Agent `POST /api/cleanup/{agentCode}` 호출
- Agent `DataRetentionService`: `DELETE FROM table WHERE dateColumn < cutoff` 실행

### 8-2. retentionConfig JSON 구조
```json
{
  "enabled": true,
  "targetDatasourceId": "dmz",
  "targets": [
    {
      "table": "if_rsv_sec_obsvdata",
      "dateColumn": "obsv_date",
      "retentionDays": 90
    }
  ]
}
```

> **주의**: jewon 테이블은 obsv_date 없으므로 Retention 대상에서 제외
> **주의**: pm_gd970201의 날짜 컬럼은 `obsrvn_dt` (obsv_date 아님)
> **⚠️ Internal Agent는 `targetDatasourceId` 필수** — 파이프라인 외부 호출 시 ThreadLocal 비어있어 fallback DS 잘못 참조

### 8-3. API 엔드포인트

#### Orchestrator (설정 관리)
| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/agents/{id}/retention | 설정 조회 |
| PUT | /api/agents/{id}/retention | 설정 저장 |

#### Agent (실행)
| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/cleanup/{agentCode} | 데이터 정리 실행 |

### 8-4. 테스트 항목

#### 설정 CRUD
- [ ] Retention 설정 조회 (초기 빈 상태)
- [ ] Retention 설정 저장
- [ ] Retention 설정 재조회 (저장값 반환)
- [ ] Retention 설정 수정
- [ ] enabled=false로 비활성화

#### 수동 실행 (Agent 직접 호출)
```bash
# DMZ Agent cleanup (targetDatasourceId 생략 가능)
curl -X POST http://localhost:8082/api/cleanup/dmz-bojo-rcv-daejeon \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "targets": [
      {"table":"if_rsv_sec_obsvdata","dateColumn":"obsv_date","retentionDays":1}
    ]
  }'

# Internal Agent cleanup (targetDatasourceId 필수! → Oracle 29004)
curl -X POST http://localhost:8092/api/cleanup/internal-bojo-rcv \
  -H "Content-Type: application/json" \
  -H "X-API-Key: {api-key}" \
  -d '{
    "enabled": true,
    "targetDatasourceId": "internal",
    "targets": [
      {"table":"if_rsv_sec_obsvdata","dateColumn":"obsv_date","retentionDays":1}
    ]
  }'

# pm_gd970201 (날짜 컬럼 = obsrvn_dt) → Oracle DELETE
curl -X POST http://localhost:8092/api/cleanup/internal-bojo-loader \
  -H "Content-Type: application/json" \
  -H "X-API-Key: {api-key}" \
  -d '{
    "enabled": true,
    "targetDatasourceId": "internal",
    "targets": [
      {"table":"pm_gd970201","dateColumn":"obsrvn_dt","retentionDays":1}
    ]
  }'
```

> **Oracle Retention 주의사항**:
> - Oracle DELETE 문법은 PG와 동일하지만 날짜 비교 방식이 다를 수 있음
> - Oracle DATE 타입: `DELETE FROM pm_gd970201 WHERE obsrvn_dt < TO_DATE('2026-03-26', 'YYYY-MM-DD')`
> - 문자열 날짜 컬럼이면 문자열 비교 — 형식 일관성 확인 필요
> - Oracle TRUNCATE는 ROLLBACK 불가 — Retention은 DELETE 사용 (정상)

#### 검증
- [ ] cleanup 엔드포인트 호출 성공 (200)
- [ ] 응답에 totalDeleted, results 포함
- [ ] cutoff 이전 데이터 삭제됨
- [ ] cutoff 이후 데이터 보존됨
- [ ] enabled=false → 삭제 실행 안 됨
- [ ] targets 빈 배열 → 삭제 실행 안 됨

#### 주의사항
- retentionDays=1이면 어제 이전 데이터 삭제 (오늘 데이터 보존)
- 오늘 데이터만 있는 경우 삭제 0건 (정상)
- 실제 삭제 테스트하려면 과거 데이터가 필요하거나 retentionDays 조정
- **Internal Agent는 `targetDatasourceId: "internal"` 필수** (누락 시 잘못된 DS 참조)
- **pm_gd970201 날짜 컬럼은 `obsrvn_dt`** (obsv_date 아님 — 존재하지 않는 컬럼 지정 시 SQL 에러)
- **Oracle**: DELETE 문의 날짜 비교가 Oracle DATE/TIMESTAMP 타입과 호환되는지 확인
- **Oracle**: obsrvn_dt가 VARCHAR2인 경우 문자열 비교로 동작 (형식: YYYYMMDD 등 일관성 필요)

---

## 9. 프론트엔드 UI

### 9-1. Execution 목록/상세
- [ ] /executions — 실행 이력 목록 표시
- [ ] /executions/{id} — 상세 (Step별 결과, Read/Write 건수)
- [ ] triggeredBy 표시 (MANUAL / SCHEDULE / CHAIN)
- [ ] 테이블명 옆 한글 alias 표시

### 9-2. Agent 상세 → Schedule 섹션
- [ ] 스케줄 목록 표시 (cron | 필터 | 상태 | 액션)
- [ ] cron 한글 변환 표시 (예: "매일 오전 2시", "2분마다")
- [ ] 스케줄 추가 (cron 입력 + 활성화 체크)
- [ ] 스케줄 수정/토글/삭제

### 9-3. Agent 상세 → Retention 섹션
- [ ] Retention 설정 표시 (테이블 | dateColumn | retentionDays)
- [ ] 설정 편집 (테이블/컬럼 드롭다운, 보존일수 입력)
- [ ] 설정 저장/취소
- [ ] DB_CON_PROXY 타입 Agent에서 비활성화

### 9-4. Agent 상세 → 조건실행 (Conditions)
- [ ] "조건실행 ▾" 펼치면 WHERE 조건 입력 UI 표시
- [ ] 조건 추가: 컬럼명 입력 + 연산자 선택 + 값 입력
- [ ] BETWEEN 선택 시 value2 입력란 표시
- [ ] IS_NULL/IS_NOT_NULL 선택 시 값 입력란 숨김
- [ ] 조건 삭제 (X 버튼)
- [ ] 조건 있으면 실행 버튼 활성화, 빈 조건이면 비활성화
- [ ] 실행 시 conditions가 API에 전달됨
- [ ] 취소 시 conditions 초기화

### 9-5. Source 추적
- [ ] Execution 상세 → Source 탭 데이터 표시
- [ ] Trace (Forward/Backward) 동작

---

## 부록: 빌드 명령어
```bash
# common 수정 시
cd sync-agent-common && ./gradlew clean build -x test
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo/libs/
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo-int/libs/
cp build/libs/sync-agent-common-*.jar ../sync-proxy-dmz/libs/
cp build/libs/sync-agent-common-*.jar ../sync-proxy-internal/libs/

# 개별 빌드
cd sync-agent-bojo && ./gradlew clean build -x test
cd sync-agent-bojo-int && ./gradlew clean build -x test
cd sync-orchestrator/backend && ./gradlew clean build -x test

# 프론트 타입체크
cd sync-orchestrator/frontend && npx tsc --noEmit
```
