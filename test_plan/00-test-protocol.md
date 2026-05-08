# 00 — 통합 테스트 공통 프로토콜

> 작성일: 2026-05-07
> 본 문서 = 01~08 모든 테스트 문서가 참조하는 공통 룰. 각 문서 헤더에 "00-test-protocol.md 참조" 박힘.

---

## 1. ⭐ 핵심 룰 — 0건은 테스트 아님

> **실행 결과가 0건이면 테스트를 안 한 것.**
> 반드시 **유의미한 숫자가 출력되는** 테스트여야 한다.

### 적용
- RCV 실행 → `read > 0` 그리고 `write > 0`
- Loader 실행 → `write > 0` (IF_RSV 데이터가 사전 적재되어 있어야)
- API Collector 실행 → `totalCount > 0` AND (`insertedCount > 0` OR `updatedCount > 0`)
- API Provider 호출 → `data` 배열 N건 (N > 0)
- LOOKUP 매칭 → 매칭된 컬럼 채워진 행 > 0
- 추적 (trace-source) → source 1건 이상 매칭 (`traceStatus=FOUND`)
- Retention cleanup → `totalDeleted > 0` (검증용 만료 데이터 사전 삽입)
- Schedule 자동 실행 → 자동 발생한 executionId 의 `read/write > 0`

### 예외 (0건이 의도된 검증)
- 비활성 토글 후 실행 → 0건 = 비활성 정합 검증
- 빈 conditions 거부 → 400 응답 (실행 자체 안 됨)
- enabled=false retention → totalDeleted=0 = skip 정합
- 미인증 호출 → 401 (실행 자체 안 됨)

→ 위 예외는 **명시적으로 "0건이 맞는 케이스"** 로 검증 항목 표기. 그 외 일반 흐름의 0건은 통과 X.

---

## 2. ⭐ 핵심 룰 — 분기점 (Branching points) 검증 의무

> **같은 흐름 안에서 입력에 따라 다르게 동작하는 지점은 모두 검증.**
> 단순 정상 200 만 보지 말고 분기 case 별로 별도 검증 항목.

### 분기점 분류 (참고)

| 분기 | 영역 | 예 |
|---|---|---|
| 정상 vs 에러 | 모든 endpoint | 200 / 400 / 401 / 404 / 409 |
| 신규 INSERT vs UPSERT 갱신 | RCV / Loader / Collector | 첫 실행 vs 재실행 (멱등성) |
| LOOKUP 매칭 / 매칭 실패 | API Collector | 코드 매칭 시 컬럼 채움 / null fallback |
| INSERT vs UPSERT 토글 | API Collector | conflictKey 분기 |
| 단일 PK vs 복합 PK | 추적 | obsv_code vs (obsv_code, obsv_date) |
| DB 타입 (PG/MySQL/Oracle) | 모든 SQL | 인용부호 / LIMIT vs FETCH FIRST / DUAL |
| 대문자/소문자 테이블 | 추적 / DDL | keunsan (대문자) / 다른 (소문자) |
| 직접 매칭 vs 정규식 추출 | LOOKUP | sourceField 직접 / regex group(1) |
| conditions 적용 / 없음 | RCV / Loader | CUSTOM_STAGING 바이패스 분기 |
| Trace 3단계 모드 | 추적 | PK 파싱 / source_refs IN / SND PK 새 source_refs |
| Schedule 활성/비활성 | 자동 실행 | cron 시점 실행 vs 미실행 |
| Retention enabled true/false | cleanup | 삭제 / skip |
| 현재값 = 갱신값 (no-op) | UPSERT | DO UPDATE skip 또는 갱신 |
| ApiKeyFilter strict / soft | 인증 | 401 vs cookie 양보 |
| validation 실패 4종 | 사용자 등록 | DUPLICATE / REQUIRED / INVALID / TOO_LONG |
| Provide API key 정상/위조/비활성 | 외부 사용자 | 200 / 401 / 401 |
| traceStatus 4종 | 추적 | FOUND / SOURCE_NOT_FOUND / NOT_TRACKABLE / FOUND_IN_IF |
| 페이징 boundary | API Provider | 1페이지 / 마지막 페이지 / pageSize 초과 클램프 |
| 옵션 파라미터 입력 / 누락 | B4 핸들러 등 | 단건 필터 / 다건 |
| 필수 파라미터 누락 | API 호출 | 400 |

→ 각 8 문서의 검증 항목이 분기마다 별도 행으로 분리되어야. "200 OK" 하나에 여러 분기 묶기 X.

---

## 3. ⭐ 핵심 룰 — 데이터 클린 테스트

> 본 사이클은 **클린 시작** — IF / Target / 실행이력 / sync_log / refresh_token 모두 사전 TRUNCATE.

### 클린 범위

| 영역 | 처리 |
|---|---|
| Orchestrator PG `execution_history`, `execution_step_history` | ✅ TRUNCATE CASCADE |
| DMZ PG (29001/dev) `if_rsv_*`, `if_snd_*`, `sec_jewon`, `sec_obsvdata`, `sync_log` | ✅ TRUNCATE CASCADE |
| Internal Oracle (29004) `if_rsv_*`, `pm_gd970201`, `tm_gd980002` (link 증분) | ✅ 1개씩 TRUNCATE (Oracle CASCADE 미지원) |
| `tm_gd970001` (제원 매칭용) | ⚠️ **보존** — Loader 가 매칭 의존 |
| `tm_gd970101` (결과 코드 매핑) | ⚠️ **보존** — ensureResultId 의존 |
| `link_ngwis` 기준점 (DMZ PG) | ✅ 갱신: `obsv_date='테스트전날', obsv_time='235959'` |
| api_provider PG (29006) `api_prv_*` 적재 테이블 | ✅ TRUNCATE |
| API Collector target (`tm_gd014000`, `tm_gd014001` 등) | ✅ TRUNCATE |
| 외부 PG (daejeon, keunsan, infoworld 등) source 테이블 | ⚠️ TRUNCATE 후 **테스트 데이터 N건 신규 INSERT** (link_ngwis 기준점 이후 시점) |
| auth_users (admin, test1) | ⚠️ 보존 |
| auth_refresh_tokens | ✅ TRUNCATE — cookie 새로 발급 |
| 등록된 메타 (datasource / agent / endpoint / operation) | ⚠️ 보존 |

### 사전 INSERT 절차 (외부 DB)

각 RCV 가 검증 가능한 분량의 데이터 사전 적재 — **0건 룰** 정합:

| 외부 DB | 테이블 | 사전 INSERT 건수 (권장) | 날짜 |
|---|---|:--:|---|
| daejeon (PG) | sec_jewon_view + sec_obsvdata_view | 제원 N건 + 측정 N건 | link_ngwis 기준점 이후 (어제~오늘) |
| keunsan (PG, 대문자) | SEC_JEWON_VIEW + SEC_OBSVDATA_VIEW | 제원 N건 + 측정 N건 | 동일 |
| infoworld-local (MySQL) | sec_jewon_view + sec_obsvdata_view | 제원 N건 + 측정 N건 | 동일 |
| (기타 7 업체) | (선택 — 본 사이클 핵심 외) | — | — |

> 핵심 3 업체 (PG/대문자PG/MySQL — DB 타입 분기 모두 커버) 만 데이터 적재해도 분기점 충분.

### TRUNCATE 명령 (참고)

```sql
-- DMZ PG (29001/dev)
TRUNCATE if_rsv_sec_jewon, if_rsv_sec_obsvdata,
         sec_jewon, sec_obsvdata,
         if_snd_sec_jewon, if_snd_sec_obsvdata,
         sync_log CASCADE;

-- Internal Oracle (29004) — FK 순서 (자식→부모)
TRUNCATE TABLE pm_gd970201;
TRUNCATE TABLE tm_gd980002;
TRUNCATE TABLE if_rsv_sec_obsvdata;
TRUNCATE TABLE if_rsv_sec_jewon;
-- ※ tm_gd970001, tm_gd970101 보존

-- Orchestrator PG (29001/orchestrator)
TRUNCATE execution_history, execution_step_history CASCADE;
TRUNCATE auth_refresh_tokens;

-- DMZ PG link_ngwis 기준점 갱신
UPDATE link_ngwis SET obsv_date = '테스트전날 (예: 20260506)', obsv_time = '235959';
```

> ⚠️ 재테스트 시 추가 주의: DMZ IF_SND `link_status` PENDING 리셋 필수
> ```sql
> UPDATE if_snd_sec_obsvdata SET link_status = 'PENDING' WHERE link_status = 'SUCCESS';
> UPDATE if_snd_sec_jewon SET link_status = 'PENDING' WHERE link_status = 'SUCCESS';
> ```

---

## 4. 추적(Trace) 검증 3단계

> 모든 실행 테스트는 추적 검증 포함. 3단계 모두 통과해야 "정상".

### 1단계: 건수 확인
```
GET /api/executions/{executionId}/data/summary   → read/write/total 건수
GET /api/executions/{executionId}/data/target?tableName=...  → target 건수 (execution_id 기준)
GET /api/executions/{executionId}/data/source?tableName=...  → source 건수 (역추적 매칭)
```
- 건수 일치 (target = source, EAV 확장 제외)
- **건수 > 0 (0건 룰)**

### 2단계: 단건 역추적 (API)
```
GET /api/executions/{executionId}/trace-source?sourceRefs=...  → source 원본 1건 매칭
```
- target 행 1건의 source_refs 로 source 원본 정상 반환
- `traceStatus=FOUND` 또는 `FOUND_IN_IF` (의도 기준)

### 3단계: 프론트 UI 검증
- 실행 상세 → 테이블별 처리 현황 → TARGET 행 클릭 → 데이터 목록 표시
- 데이터 목록의 행 클릭 → Source 테이블/데이터 정상 표시
- Oracle (대문자 컬럼) 환경에서도 정상

→ **API 만 확인하고 프론트 미확인 시 "정상" 판단 X.**

---

## 5. ⭐ 사용자 직접 확인 룰

> claude API 호출 결과만으로는 통과 판단 X.
> **사용자가 직접 프론트 (`localhost:3000`) 에서 같은 흐름을 클릭/조회해서 시각적으로 확인** 후에만 다음 단계 진입.

### 절차 (각 검증 항목)
1. claude — API 호출 / 응답 확인 / 1차 검증
2. claude — 사용자에게 "이 단계 결과 OK 인지 프론트에서 확인해주세요" 요청
3. **사용자 — 프론트에서 해당 화면 직접 클릭 / 조회 / 검증**
4. 사용자 OK → 다음 단계 진입
5. 사용자 "이상 발견" → issue 등록 (`verify/issues/`) + fix → 재검증

### 메모리 룰 정합
- `feedback_trace_definition` — 추적 검증 = 건수 + 단건 역추적 (/trace-source) + 프론트 UI 까지
- `feedback_test_scenario` — 단위테스트 결과서 양식 엄수

---

## 6. 본 사이클 범위 (5/7 사용자 결정)

상세는 `dev_plan/2026_05/07/test-plan-construction.md §9` 참조. 요약:

### 포함 (집중)
- ✅ 실행 (파이프라인 / API 호출 / CUSTOM 핸들러)
- ✅ 데이터 흐름 (외부 → DMZ → Internal)
- ✅ 추적 (3단계 / Forward / Backward)
- ✅ 운영 작업 — **Schedule + Retention 자유 등록/수정/삭제** (메타 정책)
- ✅ 모니터링 (이력 / 통계 / 대시보드)
- ✅ 인증 흐름 (JWT 로그인/refresh/logout/me + ApiKeyFilter)
- ✅ 사용자 (auth) **peer multi 사이클** (등록/비번 변경/탈퇴)
- ✅ 회귀 (5/6 B4 핸들러 등)

### 제외 (별 사이클)
- ❌ datasource / agent / api-collector endpoint / api-provider operation 의 **등록 (POST)** + 수정/삭제
  - 이미 운영 데이터 등록됨, 신규 등록 검증은 운영 환경에서

---

## 7. 검증 항목 작성 패턴

각 8 문서의 체크박스는 다음 패턴 따름:

```markdown
### X-Y. {기능} {분기 case}

**시나리오**: 어떤 상태에서 어떤 동작
**기대 결과**: 200 / 400 / 401 / 데이터 N건 등 분기 명시

```bash
curl ... (실행 명령)
```

- [ ] 응답 HTTP 코드 정합
- [ ] 응답 body 의 핵심 필드 정합 (예: `data.length > 0`, `traceStatus=FOUND`)
- [ ] DB 또는 외부 자료에 기대 변경 일어남
- [ ] **건수 > 0** (0건 룰 — 또는 명시적 0 케이스)
- [ ] **사용자 검증**: 프론트 화면에서 같은 흐름 직접 확인 후 OK
```

---

## 8. 다음 단계 (작성 → 실행)

본 프로토콜 작성 후:
1. 8 문서 각각 본 프로토콜 룰 적용 — 분기점별 검증 항목 분리 + 사전 데이터 INSERT 절차 강화
2. 실행 단계 진입 — 의존 순서대로 (07 → 01 → 02 → 03 → 04 → 05 → 06 → 08)
3. 각 단계 사용자 직접 확인 후 다음
