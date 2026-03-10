# 보조관측망 (BOJO) 기능 테스트 문서

> 보조관측망 시스템의 전체 기능을 검증하기 위한 재사용 가능 테스트 문서.
> 기능 추가/수정 시 해당 섹션을 업데이트하여 반복 사용한다.

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [E2E 파이프라인](#2-e2e-파이프라인)
3. [Source 추적 (Trace)](#3-source-추적-trace)
4. [Schedule (스케줄 실행)](#4-schedule-스케줄-실행)
5. [Retention (데이터 보존)](#5-retention-데이터-보존)
6. [프론트엔드 UI](#6-프론트엔드-ui)

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
| 용도 | 타입 | 포트 | DB명 |
|------|------|------|------|
| Orchestrator | PostgreSQL | 29001 | orchestrator |
| DMZ IF/Target | PostgreSQL | 29001 | dev |
| Internal IF/Target | PostgreSQL | 29002 | dev |
| 외부 PG (4개) | PostgreSQL | 29000 | daejeon, bytek, chungnam, keunsan |
| 외부 MySQL (6개) | MySQL | 29010 | infoworld_*, hydronet_* |

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
IF_RSV (if_rsv_sec_jewon, if_rsv_sec_obsvdata) + link_ngwis 갱신
  ↓ Loader (dmz-bojo-loader)
Target (sec_jewon, sec_obsvdata)
  ↓ SND (dmz-bojo-snd)
IF_SND (if_snd_sec_jewon, if_snd_sec_obsvdata)
  ↓ Proxy DMZ→Internal
  ↓ Internal RCV (internal-bojo-rcv)
Internal IF_RSV (if_rsv_sec_jewon, if_rsv_sec_obsvdata)
  ↓ Internal Loader (internal-bojo-loader)
GIMS (pm_gd970201) — obsvdata만, jewon 매핑 없음
```

### 1-5. 테이블 구조 요약
| 단계 | 테이블 | DB | 비고 |
|------|--------|-----|------|
| 외부 Source | sec_jewon_view, sec_obsvdata_view | 외부 DB | 읽기 전용 뷰 |
| DMZ IF_RSV | if_rsv_sec_jewon, if_rsv_sec_obsvdata | 29001/dev | |
| DMZ Target | sec_jewon, sec_obsvdata | 29001/dev | |
| DMZ IF_SND | if_snd_sec_jewon, if_snd_sec_obsvdata | 29001/dev | |
| Link | link_ngwis | 29001/dev | RCV 증분 기준점 |
| Internal IF_RSV | if_rsv_sec_jewon, if_rsv_sec_obsvdata | 29002/dev | |
| GIMS Target | pm_gd970201 | 29002/dev | 1 obsvdata = 3행 (gwdep/gwtemp/ec) |

---

## 2. E2E 파이프라인

### 2-1. 사전 준비

#### 데이터 초기화 (클린 테스트 시)
```sql
-- DMZ DB (29001/dev): IF/Target TRUNCATE (link_ngwis 제외)
TRUNCATE if_rsv_sec_jewon, if_rsv_sec_obsvdata,
         sec_jewon, sec_obsvdata,
         if_snd_sec_jewon, if_snd_sec_obsvdata,
         sync_log CASCADE;

-- Internal DB (29002/dev): 동일
TRUNCATE if_rsv_sec_jewon, if_rsv_sec_obsvdata,
         pm_gd970201, sync_log CASCADE;

-- Orchestrator DB (29001/orchestrator):
TRUNCATE execution_history, execution_step CASCADE;

-- link_ngwis: 기준점 설정 (데이터 없이 남김)
UPDATE link_ngwis SET obsv_date = '테스트전날', obsv_time = '235959';
```

#### 테스트 데이터 삽입
```sql
-- 외부 DB 3개 (daejeon, keunsan, infoworld-local)에 obsvdata INSERT
-- 날짜: 테스트 당일, 건수: jewon 수 × 1 = 402건/업체
-- link_ngwis 기준점보다 이후 시점이어야 수집됨
```

### 2-2. 실행 순서
| 순서 | Agent | 방법 | 예상 결과 |
|------|-------|------|----------|
| 1 | RCV (3개 업체) | Orchestrator 실행 | 각 업체 jewon+obsvdata = 804건 |
| 2 | DMZ Loader | Orchestrator 실행 | 3업체 합산 = 2412건 (jewon 1206 + obsvdata 1206) |
| 3 | DMZ SND | Orchestrator 실행 | Loader와 동일 건수 |
| 4 | Internal RCV | Orchestrator 실행 | SND와 동일 건수 |
| 5 | Internal Loader | Orchestrator 실행 | obsvdata만 (jewon 매핑 없음) |

### 2-3. 검증 항목

#### 파이프라인 실행
- [ ] 각 Agent 상태 = SUCCESS
- [ ] Read/Write 건수 일치
- [ ] 콜백 정상 수신 (started + finished)
- [ ] ExecutionHistory에 기록

#### 데이터 정합성
| 체크포인트 | jewon | obsvdata | 비고 |
|-----------|-------|----------|------|
| 외부 Source (×N업체) | 업체수×402 | 업체수×402 | |
| IF_RSV (DMZ) | 합산 | 합산 | |
| Target (DMZ) | 합산 | 합산 | |
| IF_SND (DMZ) | 합산 | 합산 | |
| IF_RSV (Internal) | 합산 | 합산 | |
| pm_gd970201 (GIMS) | — | 합산×3 | 측정항목별 행 분리 |
| link_ngwis | 날짜 갱신됨 | — | |

#### 특이 케이스
- [ ] keunsan: 대문자 테이블명 처리 정상
- [ ] infoworld-local: MySQL 쿼리 정상
- [ ] link_ngwis: 시간비교 `>` (초과) — 경계값 중복 없음
- [ ] tm_gd970101: SyncLog에 포함되지 않음

---

## 3. Source 추적 (Trace)

### 3-1. 추적 API
```
GET /api/executions/{executionId}/source?tableName={sourceTable}
GET /api/executions/{executionId}/trace
```

### 3-2. 3단계 분기 로직
| 조건 | 모드 | 해당 Agent | 설명 |
|------|------|-----------|------|
| source에 source_refs 컬럼 없음 | pk | RCV | 외부 DB — PK 파싱 매칭 |
| source_refs 있고 값 일치 | source_refs | Loader | IF의 source_refs를 그대로 복사 |
| source_refs 있지만 값 불일치 | pk | SND | PK 기반 새 source_refs 생성 |

### 3-3. 검증 항목 (Agent별)
| Agent | Source 테이블 | 기대 모드 | 기대 건수 |
|-------|-------------|----------|----------|
| RCV daejeon | sec_jewon_view | pk | 업체당 건수 |
| RCV daejeon | sec_obsvdata_view | pk | 업체당 건수 |
| RCV keunsan | SEC_JEWON_VIEW | pk | 업체당 건수 |
| RCV keunsan | SEC_OBSVDATA_VIEW | pk | 업체당 건수 |
| DMZ Loader | if_rsv_sec_jewon | source_refs | 합산 |
| DMZ Loader | if_rsv_sec_obsvdata | source_refs | 합산 |
| DMZ SND | sec_jewon | pk | 합산 |
| DMZ SND | sec_obsvdata | pk | 합산 |
| Internal Loader | if_rsv_sec_obsvdata | source_refs | 합산 |

---

## 4. Schedule (스케줄 실행)

### 4-1. 개요
- Orchestrator가 cron 스케줄에 따라 Agent 파이프라인 자동 실행
- DB `schedule` 테이블에 저장, 앱 기동 시 `ScheduleExecutor`가 로드
- 실행 시 `executionService.triggerExecution()` → Agent POST

### 4-2. API 엔드포인트
| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/schedules | 전체 조회 |
| GET | /api/schedules/{id} | 단건 조회 |
| POST | /api/schedules | 생성 |
| PUT | /api/schedules/{id} | 수정 |
| PUT | /api/schedules/{id}/toggle | 활성/비활성 토글 |
| DELETE | /api/schedules/{id} | 삭제 |

### 4-3. 테스트 항목

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

#### 필터 포함 실행 (선택)
```json
{
  "agentId": 7,
  "cronExpression": "0 */2 * * * *",
  "isEnabled": true,
  "executionOptions": "{\"filters\":[{\"paramId\":\"obsv-code\",\"value\":\"GPM-123\"}]}"
}
```
- [ ] executionOptions의 filters가 Agent에 전달됨

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

## 5. Retention (데이터 보존)

### 5-1. 개요
- Agent IF/Target 테이블의 오래된 데이터 자동 삭제
- Orchestrator `DataRetentionScheduler`: 매일 새벽 2시 (기본, `retention.cron` 설정)
- Agent별 `retentionConfig` JSON → Agent `POST /api/cleanup/{agentCode}` 호출
- Agent `DataRetentionService`: `DELETE FROM table WHERE dateColumn < cutoff` 실행

### 5-2. retentionConfig JSON 구조
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

### 5-3. API 엔드포인트

#### Orchestrator (설정 관리)
| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/agents/{id}/retention | 설정 조회 |
| PUT | /api/agents/{id}/retention | 설정 저장 |

#### Agent (실행)
| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/cleanup/{agentCode} | 데이터 정리 실행 |

### 5-4. 테스트 항목

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

# Internal Agent cleanup (targetDatasourceId 필수!)
curl -X POST http://localhost:8092/api/cleanup/internal-bojo-rcv \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "targetDatasourceId": "internal",
    "targets": [
      {"table":"if_rsv_sec_obsvdata","dateColumn":"obsv_date","retentionDays":1}
    ]
  }'

# pm_gd970201 (날짜 컬럼 = obsrvn_dt)
curl -X POST http://localhost:8092/api/cleanup/internal-bojo-loader \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "targetDatasourceId": "internal",
    "targets": [
      {"table":"pm_gd970201","dateColumn":"obsrvn_dt","retentionDays":1}
    ]
  }'
```

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

---

## 6. 프론트엔드 UI

### 6-1. Execution 목록/상세
- [ ] /executions — 실행 이력 목록 표시
- [ ] /executions/{id} — 상세 (Step별 결과, Read/Write 건수)
- [ ] triggeredBy 표시 (MANUAL / SCHEDULE / CHAIN)
- [ ] 테이블명 옆 한글 alias 표시

### 6-2. Agent 상세 → Schedule 섹션
- [ ] 스케줄 목록 표시 (cron | 필터 | 상태 | 액션)
- [ ] cron 한글 변환 표시 (예: "매일 오전 2시", "2분마다")
- [ ] 스케줄 추가 (cron 입력 + 활성화 체크)
- [ ] 스케줄 수정/토글/삭제
- [ ] 필터 포함 스케줄 (executionParams 있는 Agent만)

### 6-3. Agent 상세 → Retention 섹션
- [ ] Retention 설정 표시 (테이블 | dateColumn | retentionDays)
- [ ] 설정 편집 (테이블/컬럼 드롭다운, 보존일수 입력)
- [ ] 설정 저장/취소
- [ ] DB_CON_PROXY 타입 Agent에서 비활성화

### 6-4. Source 추적
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
