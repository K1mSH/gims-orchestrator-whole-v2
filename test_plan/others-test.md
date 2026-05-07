# Others Agent (SND) 기능 테스트 문서

> infolink-agent-others-dmz(8085)의 전체 기능을 검증하기 위한 재사용 가능 테스트 문서.
> bojo-test.md와 동일한 공통 테스트 규칙 적용.

### 공통 테스트 규칙
> **모든 실행 테스트는 추적(Trace) 검증을 포함한다.**
> 추적 검증은 아래 **3단계를 모두** 수행해야 "정상"으로 판단한다.
>
> **1단계: 건수 확인**
> - Orchestrator 실행 이력: total_read_count / total_write_count / total_skip_count
> - step별 SyncLog: step_id별 read/write/skip
>
> **2단계: 단건 역추적 (API)**
> ```
> GET /api/execution-data/{executionId}/trace-source?sourceRefs=...&sourceTable=...
> ```
> target(IF_SND) 행 1건의 source_refs로 source 원본 레코드가 정상 반환되는지 확인.
>
> **3단계: 프론트 UI 검증**
> - 실행 상세 화면 → 테이블별 처리 현황 → TARGET 행 클릭 → 테이블 상세 조회
> - 데이터 목록에서 **행 클릭** → 추적 결과(Source 테이블/데이터) 정상 표시

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [API Key / 엔드포인트 보안](#2-api-key--엔드포인트-보안)
3. [Proxy 패스스루 / 암호문 통신](#3-proxy-패스스루--암호문-통신)
4. [SND 파이프라인 — 제주](#4-snd-파이프라인--제주) (증분 동기화 포함)
5. [SND 파이프라인 — 이용량](#5-snd-파이프라인--이용량)
6. [조건실행 (Conditions)](#6-조건실행-conditions)
7. [Source 추적 (Trace)](#7-source-추적-trace)
8. [Schedule (스케줄 실행)](#8-schedule-스케줄-실행)
9. [Retention (데이터 보존)](#9-retention-데이터-보존)
10. [프론트엔드 UI](#10-프론트엔드-ui)
11. [주의사항 / 알려진 허점](#11-주의사항--알려진-허점)

---

## 1. 시스템 구성

### 1-1. 필요 서비스
| 서비스 | 포트 | 필수 | 비고 |
|--------|------|:---:|------|
| Orchestrator Backend | 8080 | O | 실행 트리거 + 이력 관리 |
| **Agent Others (infolink-agent-others-dmz)** | **8085** | **O** | SND 전용 |
| Proxy DMZ | 8083 | O | datasource 해석 + 추적 API |
| API Collector | 8084 | △ | 소스 데이터 적재용 (사전 실행) |
| Frontend (Next.js) | 3000 | O | UI 검증 |

### 1-2. Agent 목록
| ID | agentCode | 타입 | 설명 | 소스 테이블 |
|----|-----------|------|------|------------|
| 23 | dmz-others-snd-jeju | SND | 제주 SND | tb_jeju_jewon, tb_jeju, rgetstgms01 |
| 24 | dmz-others-snd-use | SND | 이용량 SND | use_legacy_data, use_status_data, use_jeju_day |

### 1-3. 파이프라인 흐름
```
[API Collector / 외부 시스템]
  ↓ 적재
소스 테이블 (tb_jeju_jewon, tb_jeju, rgetstgms01 등)     [PG 29001/dev]
  ↓ SND (dmz-others-snd-jeju / dmz-others-snd-use)
IF_SND (if_snd_tb_jeju_jewon, if_snd_tb_jeju 등)         [PG 29001/dev]
  ↓ (향후) Proxy DMZ→Internal → Internal RCV → Internal Loader → GIMS
```

### 1-4. 테이블 구조

#### 제주 SND (dmz-others-snd-jeju)
| 단계 | 테이블 | PK | link_status |
|------|--------|-----|:-----------:|
| Source | tb_jeju_jewon | obsrvt_id (VARCHAR) | O |
| Source | tb_jeju | rid (BIGINT, serial) | O |
| Source | rgetstgms01 | perm_nt_no (VARCHAR) | O |
| IF_SND | if_snd_tb_jeju_jewon | obsrvt_id | O |
| IF_SND | if_snd_tb_jeju | rid | O |
| IF_SND | if_snd_rgetstgms01 | perm_nt_no | O |

#### 이용량 SND (dmz-others-snd-use)
| 단계 | 테이블 | PK | link_status |
|------|--------|-----|:-----------:|
| Source | use_legacy_data | sn (BIGINT) | O |
| Source | use_status_data | sn (BIGINT) | O |
| Source | use_jeju_day | obsrvt_id + obsr_de (복합) | O |
| IF_SND | if_snd_use_legacy_data | sn | O |
| IF_SND | if_snd_use_status_data | sn | O |
| IF_SND | if_snd_use_jeju_day | id (IDENTITY) | O |

### 1-5. 테스트 데이터 준비

> 핵심: 각 테스트 항목을 통과시킬 수 있는 **유효한 데이터**가 소스 테이블에 존재해야 함.

#### 제주 SND 데이터 (dmz-others-snd-jeju)

| 테스트 항목 | 필요 데이터 | 준비 방법 |
|------------|-----------|----------|
| 일반 실행 | 소스 3테이블에 link_status='PENDING' 데이터 | API Collector D1~D3 실행 (Mock API) |
| 2회 실행 (증분 0건) | 소스 전부 SUCCESS 상태 | 1회 실행 성공 후 자동 |
| 조건실행 (ymd) | tb_jeju에 ymd='20260330' 데이터 | D2 Mock 데이터 기본값 |
| 조건실행 (perm_nt_no) | rgetstgms01에 perm_nt_no='5001-001' 데이터 | D3 Mock 데이터 기본값 |
| 증분 (신규 추가) | 1차 SUCCESS 후 신규 PENDING 데이터 | API Collector 재실행 또는 수동 INSERT |
| FAILED 재처리 | link_status='FAILED' 데이터 | 수동 UPDATE |
| 추적 (VARCHAR PK) | tb_jeju_jewon, rgetstgms01에 데이터 | D1, D3 실행으로 자동 |
| 추적 (BIGINT PK) | tb_jeju에 데이터 | D2 실행으로 자동 |

#### 이용량 SND 데이터 (dmz-others-snd-use)

| 테스트 항목 | 필요 데이터 | 준비 방법 |
|------------|-----------|----------|
| use_legacy_data | sn, telno, obsr_dt 등 | D5 (AnyangUsageExecutor) 실행 |
| use_status_data | sn, telno, stat 등 | 수동 INSERT (적재 Executor 없음) |
| use_jeju_day | obsrvt_id, obsr_de 등 | 수동 INSERT (외부 시스템 적재 테이블) |

#### 데이터 리셋이 필요한 경우
- 소스 link_status를 'PENDING'으로 UPDATE
- IF_SND 테이블 DELETE (선택 — UPSERT이므로 남아있어도 무방)

---

## 2. API Key / 엔드포인트 보안

### 2-1. API Key 인증
- [ ] `X-API-Key` 없는 요청 → 401
- [ ] `X-API-Key` 틀린 값 → 401
- [ ] `X-API-Key` 정상 → 200
- [ ] `/health` → 인증 없이 200

### 2-2. 컨트롤러 활성화 현황

| 컨트롤러 | Others Agent |
|----------|:---:|
| DataRetentionController (`/api/cleanup`) | O |
| ExecutionDataController (`/api/execution-data`) | **X** (Proxy 경유) |
| DatasourceController (`/api/datasource`) | O |
| ExecutionParamsController (`/api/pipeline/execution-params`) | O |

### 2-3. 테스트 항목
- [ ] `POST /api/cleanup/...` → 200 (활성)
- [ ] `GET /api/execution-data/...` → 404 (비활성 — Proxy 경유)
- [ ] `POST /api/datasource/test-connection` → 200 (활성)
- [ ] `GET /api/pipeline/execution-params/{agentCode}` → 200 (활성)

---

## 3. Proxy 패스스루 / 암호문 통신

### 3-1. 통신 흐름
```
Orchestrator ──(암호문)──→ Proxy DMZ(8083) ──(암호문 그대로)──→ Others Agent(8085)
                                                              ↓ 복호화 → JDBC 연결
```

### 3-2. 테스트 항목
- [ ] Proxy `/api/datasources/dmz/connection-info` 호출 → 암호문 포함 응답
- [ ] Others Agent가 Proxy 경유로 dmz datasource 해석 성공
- [ ] 복호화 후 PG 29001/dev 연결 성공
- [ ] Proxy 미기동 시 즉시 실패 (재시도/fallback 없음)

---

## 4. SND 파이프라인 — 제주

### 2-1. 일반 실행 (증분)
소스에서 link_status='PENDING'인 건만 IF_SND로 복사.

**실행:**
```bash
curl -s -X POST http://localhost:8080/api/executions/23/run \
  -H "Content-Type: application/json" -d '{}'
```

**검증:**
- [ ] 실행 상태: SUCCESS
- [ ] 3개 step 모두 성공:
  - [ ] jeju-jewon-snd: read 12 / write 12 / skip 0
  - [ ] jeju-obsv-snd: read 12 / write 12 / skip 0
  - [ ] jeju-stgms-snd: read 12 / write 12 / skip 0
- [ ] 소스 link_status: 전부 SUCCESS로 변경
- [ ] IF_SND 건수: 각 12건
- [ ] source_refs 형식: `["D:1018:{tableId}:{pk}"]` (U:0:0이 아닌 정상 형식)

### 4-2. 2회 실행 (증분 — 변경 없음)
PENDING 건이 없으므로 0건 처리.

**검증:**
- [ ] 실행 상태: SUCCESS
- [ ] 3개 step: read 0 / write 0 / skip 0

### 4-3. 신규 데이터 추가 후 증분
- [ ] API Collector로 신규 데이터 적재 (link_status default='PENDING')
- [ ] SND 실행 → 신규 건만 IF_SND에 추가 (기존 SUCCESS 건은 스킵)

### 4-4. FAILED 재처리
- [ ] 실패한 건의 link_status = FAILED
- [ ] 재실행 시 FAILED 건도 다시 처리 대상에 포함

### 4-5. 추적 검증
**API 단건 역추적:**
```bash
# if_snd_tb_jeju의 source_refs로 tb_jeju 원본 조회
GET /api/execution-data/{executionId}/trace-source
  ?sourceRefs=["D:1018:103:145"]
  &sourceTable=if_snd_tb_jeju
```

- [ ] sourceTableName: `tb_jeju` (tb_jeju_jewon이 아닌 정확한 테이블)
- [ ] sourceRecords: 1건 반환
- [ ] traceStatus: FOUND

**프론트 UI:**
- [ ] 실행 상세 → 처리현황 3개 테이블 행 표시
- [ ] TARGET 행 클릭 → 데이터 목록 → 행 클릭 → Source 원본 표시

---

## 5. SND 파이프라인 — 이용량

### 3-1. 일반 실행
> use_legacy_data, use_status_data에 테스트 데이터가 있어야 함.
> use_jeju_day는 외부 시스템 적재 테이블 — 데이터가 없으면 해당 step만 0건.

**실행:**
```bash
curl -s -X POST http://localhost:8080/api/executions/24/run \
  -H "Content-Type: application/json" -d '{}'
```

**검증:**
- [ ] 실행 상태: SUCCESS
- [ ] use-legacy-snd: 소스 데이터 건수만큼 write
- [ ] use-status-snd: 소스 데이터 건수만큼 write
- [ ] use-jejuday-snd: 소스 데이터 유무에 따라 0건 또는 N건
- [ ] source_refs 형식 정상

---

## 6. 조건실행 (Conditions)

### 4-1. ymd 조건 (제주 SND)
tb_jeju의 ymd 컬럼으로 필터링.

**실행:**
```bash
curl -s -X POST http://localhost:8080/api/executions/23/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "ymd", "operator": "EQUALS", "value": "20260330"}]}'
```

**검증:**
- [ ] 실행 상태: SUCCESS
- [ ] jeju-obsv-snd: ymd='20260330'인 건만 처리
- [ ] 나머지 step: ymd 컬럼 없는 테이블은 조건 무시, PENDING 건 전부 처리
- [ ] 추적 정상

### 4-2. perm_nt_no 조건 (제주 SND)
rgetstgms01의 PK로 특정 건만 필터.

**실행:**
```bash
curl -s -X POST http://localhost:8080/api/executions/23/run \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"column": "perm_nt_no", "operator": "EQUALS", "value": "5001-001"}]}'
```

**검증:**
- [ ] jeju-stgms-snd: 1건만 처리
- [ ] 나머지 step: perm_nt_no 컬럼 없는 테이블은 조건 무시

---

## 7. Source 추적 (Trace)

### 6-1. VARCHAR PK 추적 (tb_jeju_jewon, rgetstgms01)
- [ ] source_refs에서 PK 추출 → VARCHAR 타입으로 매칭 (BIGINT 캐스팅 아님)
- [ ] trace-source API: sourceRecords 정상 반환

### 6-2. BIGINT PK 추적 (tb_jeju)
- [ ] source_refs에서 PK 추출 → BIGINT 타입으로 매칭
- [ ] trace-source API: sourceRecords 정상 반환

### 6-3. IF 테이블 → Source 테이블 자동 해석
- [ ] sourceTable=if_snd_tb_jeju → tb_jeju로 올바르게 해석
- [ ] sourceTable=if_snd_tb_jeju_jewon → tb_jeju_jewon으로 해석 (tb_jeju가 아님)
- [ ] sourceTable=if_snd_rgetstgms01 → rgetstgms01으로 해석

---

## 8. Schedule (스케줄 실행)

### 9-1. 스케줄 등록
- [ ] Orchestrator에서 dmz-others-snd-jeju 스케줄 등록 (예: `0 */30 * * * ?` 30분마다)
- [ ] 스케줄 활성화/비활성화 토글
- [ ] 스케줄 삭제

### 9-2. 스케줄 실행
- [ ] 등록된 cron 주기에 맞춰 자동 실행
- [ ] triggeredBy: SCHEDULE로 이력 기록
- [ ] 증분 동기화 동작 (PENDING 건만 처리)

### 9-3. 스케줄 + 조건실행 병행
- [ ] 스케줄 실행 중 수동 실행 시 충돌 없음 (runningAgentCodes 체크)

---

## 9. Retention (데이터 보존)

### 10-1. IF_SND 테이블 Retention
- [ ] retentionDays 설정 후 cleanup 호출
- [ ] cutoff 이전 IF_SND 데이터 삭제됨
- [ ] cutoff 이후 IF_SND 데이터 보존됨

### 10-2. 소스 테이블 Retention
- [ ] 소스 테이블(tb_jeju, rgetstgms01 등)에 대한 retention 설정 가능
- [ ] 소스 테이블은 API Collector 관할이므로 별도 정책 필요 시 확인

### 10-3. 주의사항
- retentionDays 음수 방어 (min=1)
- enabled=false → 삭제 실행 안 됨
- targets 빈 배열 → 삭제 실행 안 됨

---

## 10. 프론트엔드 UI

### 7-1. Agent 상세
- [ ] dmz-others-snd-jeju 상태확인 → 온라인
- [ ] dmz-others-snd-use 상태확인 → 온라인
- [ ] 실행 버튼 → SUCCESS
- [ ] 조건실행 → WHERE 조건 입력 후 실행 → SUCCESS
- [ ] 실행이력 정렬: 최신 순 (1970년 epoch 이력 없음)

### 7-2. 실행 상세 (처리현황)
- [ ] 테이블별 처리현황: 3쌍 표시 (제주 기준)
- [ ] SOURCE 건수 + TARGET 건수 일치
- [ ] TARGET 행 클릭 → 테이블 상세 데이터 표시

### 7-3. 추적 (행 클릭)
- [ ] if_snd_tb_jeju 행 클릭 → Source: tb_jeju 데이터 표시
- [ ] if_snd_tb_jeju_jewon 행 클릭 → Source: tb_jeju_jewon 데이터 표시
- [ ] if_snd_rgetstgms01 행 클릭 → Source: rgetstgms01 데이터 표시

### 7-4. 실행기 변경
- [ ] Agent 수정 화면에서 실행기 타입 드롭다운 변경 가능 (disabled 아님)

---

---

## 부록: 빌드 명령어
```bash
# common 수정 시
cd infolink-agent-common && ./gradlew clean build -x test
cp build/libs/infolink-agent-common-*.jar ../infolink-agent-others-dmz/libs/
cp build/libs/infolink-agent-common-*.jar ../infolink-proxy-dmz/libs/

# others 빌드
cd infolink-agent-others-dmz && ./gradlew clean build -x test

# JAR 직접 실행 (bootRun 캐시 문제 회피)
cd infolink-agent-others-dmz && java -jar build/libs/infolink-agent-others-dmz-1.0.0-SNAPSHOT.jar

# 프론트 타입체크
cd infolink-orchestrator-frontend && npx tsc --noEmit
```

---

## 11. 주의사항 / 알려진 허점

### 8-1. 직접 API 호출 시 source_refs 불완전
- **현상**: Agent API를 직접 호출(`POST /api/pipeline/execute`)하면 StepContext에 zone/dsId/tableId 정보가 없어 source_refs가 `U:0:0:pk` 형태로 생성됨
- **원인**: Orchestrator 경유 시에만 params에 datasource 메타정보(zone, datasourceDbId, tableIds)가 포함됨
- **영향**: 이후 Orchestrator 경유로 재실행하면 source_refs가 달라져(`D:1018:103:pk`) PK 충돌 발생
- **대응**: 테스트 시 반드시 **Orchestrator 경유로 실행** (`POST /api/executions/{agentId}/run`). 직접 API 호출로 생긴 IF_SND 데이터는 DELETE 후 재실행
- **상태**: 구조적 제약 (직접 호출 시 context 부족), 운영 환경에서는 항상 Orchestrator 경유이므로 문제 없음

### 8-2. Proxy 미기동 시 실행 실패
- **현상**: `[Others] Proxy에서 datasource 해석 실패: dmz`
- **원인**: Agent가 datasource 연결정보를 Proxy 경유로만 해석 (직접 fallback 없음)
- **대응**: Others Agent 기동 전 Proxy DMZ(8083) 반드시 먼저 기동
- **확인**: `curl http://localhost:8083/health` → 200 확인 후 실행
