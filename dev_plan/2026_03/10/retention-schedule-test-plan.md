# Retention & Schedule 테스트 계획서
**날짜**: 2026-03-10

---

## 1. 테스트 대상 기능 요약

### 1-1. Schedule (스케줄 실행)
- Orchestrator가 Agent 파이프라인을 cron 스케줄에 따라 자동 실행
- DB(schedule 테이블)에 저장, 앱 기동 시 ScheduleExecutor가 로드
- CRUD: 생성/수정/토글/삭제 API + 프론트 UI
- 실행 시 `executionService.triggerExecution()` 호출 → Agent에 POST

### 1-2. Retention (데이터 보존/정리)
- Agent의 IF/Target 테이블 오래된 데이터 자동 삭제
- Orchestrator `DataRetentionScheduler`가 매일 새벽 2시 실행 (기본)
- Agent별 `retentionConfig` JSON → Agent `/api/cleanup/{agentCode}` POST
- Agent 측 `DataRetentionService`가 `DELETE FROM table WHERE dateColumn < cutoff` 실행

---

## 2. Schedule 테스트

### 2-1. API CRUD 테스트

#### (A) 스케줄 생성 (대전 RCV 예시, 이후 7개 Agent 모두 동일 패턴)
```
POST /api/schedules
Body: {
  "agentId": 7,              // daejeon (이후 9,12,17,18,19,20 순)
  "cronExpression": "0 */2 * * * *",  // 2분마다 (테스트용)
  "isEnabled": true
}
```
- **검증**: 201 응답, DB에 레코드 생성, ScheduleExecutor에 등록

#### (B) 스케줄 조회
```
GET /api/schedules
GET /api/schedules/{id}
```
- **검증**: 생성한 스케줄 반환

#### (C) 스케줄 토글 (비활성화)
```
PUT /api/schedules/{id}/toggle
```
- **검증**: isEnabled = false, 스케줄 실행 중단

#### (D) 스케줄 수정
```
PUT /api/schedules/{id}
Body: { "cronExpression": "0 */5 * * * *", "isEnabled": true }
```
- **검증**: cron 변경 반영, 재활성화

#### (E) 스케줄 삭제
```
DELETE /api/schedules/{id}
```
- **검증**: DB 삭제, ScheduleExecutor에서 제거

### 2-2. 스케줄 자동 실행 테스트

#### 대상 Agent (E2E와 동일 순서)
| 순서 | Agent | ID | 비고 |
|------|-------|----|------|
| 1 | dmz-bojo-rcv-daejeon | 7 | PG |
| 2 | dmz-bojo-rcv-infoworld-local | 9 | MySQL |
| 3 | dmz-bojo-rcv-keunsan | 12 | PG 대문자 |
| 4 | dmz-bojo-loader | 17 | |
| 5 | dmz-bojo-snd | 18 | |
| 6 | internal-bojo-rcv | 19 | |
| 7 | internal-bojo-loader | 20 | |

#### 준비
- 외부 DB에 테스트 데이터 존재 확인 (E2E에서 이미 입력한 3/10 데이터)
- link_ngwis 기준점 확인 (현재 3/10으로 갱신됨 → 새 데이터 없으면 0건 수집 예상)

#### 실행
1. RCV 3개 Agent에 2분 간격 스케줄 생성 (`0 */2 * * * *`)
2. 2분 대기 후 실행 확인
3. 이어서 Loader → SND → Internal RCV → Internal Loader 순으로 스케줄 생성/실행 확인
4. 각 Agent마다: ExecutionHistory + 콜백 + 로그 확인

#### 검증 항목
- [ ] 7개 Agent 모두 ExecutionHistory 생성됨 (triggeredBy = "SCHEDULE")
- [ ] 각 Agent 파이프라인 실행됨
- [ ] 콜백 정상 수신 (started + finished)
- [ ] 새 데이터 없으므로 read=0, write=0 예상 (정상)

### 2-3. 필터 포함 스케줄 테스트 (선택)
- executionOptions에 필터 포함 스케줄 생성
- Agent가 필터를 받아 실행하는지 확인
- (Agent가 executionOptions/filters를 지원하는지 먼저 확인 필요)

### 2-4. 프론트엔드 UI 테스트
- Agent 상세 → InfoTab에서 스케줄 섹션 확인
- 스케줄 추가/수정/토글/삭제 UI 동작 확인
- cron 표현식 한글 변환 표시 확인 (예: "2분마다")

---

## 3. Retention 테스트

### 3-1. Retention 설정 API 테스트

#### (A) 설정 조회 (초기 — 빈 상태)
```
GET /api/agents/7/retention
```
- **검증**: 빈 config 또는 null

#### (B) 설정 저장
```
PUT /api/agents/7/retention
Body: {
  "enabled": true,
  "targets": [
    {
      "table": "if_rsv_sec_jewon",
      "dateColumn": "created_at",
      "retentionDays": 1
    }
  ]
}
```
- retentionDays=1 (테스트용 — 1일 이전 데이터 삭제)
- **검증**: 200 응답, Agent.retentionConfig에 JSON 저장

#### (C) 설정 조회 (저장 후)
```
GET /api/agents/7/retention
```
- **검증**: 저장한 config 반환

### 3-2. Retention 대상 Agent
| 순서 | Agent | ID | Cleanup 대상 테이블 예시 | 비고 |
|------|-------|----|------------------------|------|
| 1 | dmz-bojo-rcv-daejeon | 7 | if_rsv_sec_jewon, if_rsv_sec_obsvdata | DMZ IF_RSV |
| 2 | dmz-bojo-rcv-infoworld-local | 9 | if_rsv_sec_jewon, if_rsv_sec_obsvdata | DMZ IF_RSV |
| 3 | dmz-bojo-rcv-keunsan | 12 | if_rsv_sec_jewon, if_rsv_sec_obsvdata | DMZ IF_RSV |
| 4 | dmz-bojo-loader | 17 | sec_jewon, sec_obsvdata | DMZ Target |
| 5 | dmz-bojo-snd | 18 | if_snd_sec_jewon, if_snd_sec_obsvdata | DMZ IF_SND |
| 6 | internal-bojo-rcv | 19 | if_rsv_sec_jewon, if_rsv_sec_obsvdata | Internal IF_RSV |
| 7 | internal-bojo-loader | 20 | pm_gd970201 | GIMS Target |

### 3-3. Retention 수동 실행 테스트

#### 방법 1: Agent cleanup 엔드포인트 직접 호출 (대전 예시)
```
POST http://localhost:8082/api/cleanup/dmz-bojo-rcv-daejeon
Body: {
  "enabled": true,
  "targets": [
    {
      "table": "if_rsv_sec_jewon",
      "dateColumn": "created_at",
      "retentionDays": 1
    }
  ]
}
```
- Internal Agent는 8092 포트로 호출

#### 방법 2: Orchestrator 스케줄러 트리거
- retention cron을 임시로 1분 간격으로 변경하거나
- 앱 재시작 후 스케줄러 실행 대기

#### 주의사항
- **테스트 전 현재 데이터 건수 확인** (삭제 대상 파악)
- retentionDays=1 → 어제 이전 데이터만 삭제 (오늘 데이터는 보존)
- 현재 DB에 오늘(3/10) 데이터만 있으면 삭제 0건 예상
- 테스트를 위해 retentionDays=0 (0일 보존 = 오늘 전 데이터 삭제) 또는 과거 데이터 INSERT 필요

### 3-4. 검증 항목

#### 데이터 정합성
- [ ] cleanup 엔드포인트 호출 성공 (200 응답)
- [ ] 응답에 totalDeleted, results 포함
- [ ] 대상 테이블에서 cutoff 이전 데이터 삭제됨
- [ ] cutoff 이후 데이터는 보존됨

#### 에러 케이스
- [ ] 존재하지 않는 테이블명 → 에러 처리
- [ ] enabled=false → 삭제 실행 안 됨
- [ ] targets 빈 배열 → 삭제 실행 안 됨

### 3-5. 프론트엔드 UI 테스트
- Agent 상세 → InfoTab → "데이터 보존 (Retention)" 섹션
- 설정 추가: 테이블 선택, dateColumn 선택, retentionDays 입력
- 저장/수정/삭제 동작 확인
- DB_CON_PROXY 타입 Agent에서는 비활성화 확인

---

## 4. 테스트 순서 + 기대 건수

### 사전 준비
1. DMZ/Internal/Orchestrator DB TRUNCATE (link_ngwis 제외)
2. 외부 DB 3개(daejeon, infoworld-local, keunsan)에 **3/8** obsvdata INSERT
   - 업체당 402건, remark='test-0308' 등으로 구분
   - 기존 3/10 데이터(remark='test-0310')는 유지
3. link_ngwis 기준점: obsv_date='2026-03-07', obsv_time='235959'
   → RCV가 3/7 이후 = **3/8 + 3/10 모두** 수집

#### 외부 DB 상태 (업체당)
| 테이블 | 3/8 | 3/10 | 합계 | 비고 |
|--------|-----|------|------|------|
| jewon | — | 402 | 402 | 마스터, 날짜 무관 |
| obsvdata | 402 | 402 | 804 | obsv_date로 구분 |

### Phase 1: Schedule — 3/8 + 3/10 동시 수집

#### 실행
1. Schedule CRUD API 테스트 (daejeon 기준 — 생성/조회/토글/수정/삭제)
2. RCV 3개 스케줄 생성 → 자동 실행
3. Loader → SND → Internal RCV → Internal Loader 순으로 스케줄 실행
4. 테스트 스케줄 삭제

#### 기대 건수 (3업체 합산)
| 위치 | 테이블 | 3/8 | 3/10 | 합계 |
|------|--------|-----|------|------|
| DMZ IF_RSV | if_rsv_sec_jewon | — | 1206 | **1206** |
| DMZ IF_RSV | if_rsv_sec_obsvdata | 1206 | 1206 | **2412** |
| DMZ Target | sec_jewon | — | 1206 | **1206** |
| DMZ Target | sec_obsvdata | 1206 | 1206 | **2412** |
| DMZ IF_SND | if_snd_sec_jewon | — | 1206 | **1206** |
| DMZ IF_SND | if_snd_sec_obsvdata | 1206 | 1206 | **2412** |
| Int IF_RSV | if_rsv_sec_jewon | — | 1206 | **1206** |
| Int IF_RSV | if_rsv_sec_obsvdata | 1206 | 1206 | **2412** |
| GIMS | pm_gd970201 | 3618 | 3618 | **7236** |

> jewon: 마스터 데이터, 날짜 구분 없이 업체당 402건
> pm_gd970201: 1 obsvdata = 3행 (gwdep/gwtemp/ec) → 2412 × 3 = 7236

#### 검증
- [ ] 7개 Agent 모두 triggeredBy="SCHEDULE"
- [ ] 콜백 정상 (started + finished)
- [ ] 위 건수표 일치

### Phase 2: Retention — 3/8 데이터만 삭제

#### 설정
- dateColumn: **obsv_date** (obsvdata 계열 테이블), **obsrvn_dt** (pm_gd970201)
- retentionDays: **1** → cutoff = 2026-03-09 00:00:00
- WHERE dateColumn < '2026-03-09' → **3/8 삭제, 3/10 보존**
- jewon 테이블: obsv_date 없으므로 Retention 대상 제외
- **⚠️ Internal Agent는 `targetDatasourceId` 필수** (body에 `"targetDatasourceId":"internal"` 명시)

#### 실행
1. Retention CRUD API 테스트 (daejeon 기준 — 설정 조회/저장)
2. 각 Agent에 Retention 설정 저장
3. Agent cleanup 실행: RCV → Loader → SND → Int RCV → Int Loader
4. 프론트 UI 확인
5. 테스트 retention 설정 제거

#### 기대 결과
| 위치 | 테이블 | dateColumn | 삭제(3/8) | 보존(3/10) |
|------|--------|-----------|----------|-----------|
| DMZ IF_RSV | if_rsv_sec_obsvdata | obsv_date | **1206** | 1206 |
| DMZ Target | sec_obsvdata | obsv_date | **1206** | 1206 |
| DMZ IF_SND | if_snd_sec_obsvdata | obsv_date | **1206** | 1206 |
| Int IF_RSV | if_rsv_sec_obsvdata | obsv_date | **1206** | 1206 |
| GIMS | pm_gd970201 | **obsrvn_dt** | **3618** | 3618 |

#### 검증
- [x] cleanup 응답의 totalDeleted = 기대 삭제 건수 ✅
- [x] 삭제 후 각 테이블 COUNT = 보존 건수 ✅
- [x] 3/10 데이터 온전히 보존 확인 ✅

### 테스트 중 발견 사항
1. **Internal Agent `targetDatasourceId` 필수**: cleanup은 파이프라인 컨텍스트 밖에서 호출 → ThreadLocal 비어있음 → fallback DS 잘못 참조. body에 `"targetDatasourceId":"internal"` 명시해야 정상 동작.
2. **pm_gd970201 날짜 컬럼은 `obsrvn_dt`**: `obsv_date`가 아닌 `obsrvn_dt` (timestamp). 컬럼명 주의.
3. **DMZ Agent는 `targetDatasourceId` 생략 가능**: 기본 Spring DataSource가 DMZ DB(29001/dev)이므로 fallback 정상.

### 추가: retentionDays 음수 방어
- DataRetentionService에 retentionDays < 0 검증 추가 (별도 코드 수정 필요)

---

## 5. 사전 확인 사항
- [ ] Orchestrator 실행 중 (port 8080)
- [ ] Agent DMZ 실행 중 (port 8082)
- [ ] Agent Internal 실행 중 (port 8092)
- [ ] Frontend 실행 중 (port 3000)
- [ ] 외부 DB 접근 가능 (29000/29010)
- [ ] 외부 DB에 3/10 데이터 존재
