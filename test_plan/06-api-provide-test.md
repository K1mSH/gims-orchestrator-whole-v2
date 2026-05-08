# 06 — API Provider 기능 테스트 문서

> 작성일: 2026-05-07

---

## 공통 검증 규칙

- claude API 호출 → 1차 확인. **사용자가 직접 프론트(`localhost:3000/api-provide`)에서 같은 흐름 확인** 후에만 통과.
- 단계마다 사용자 OK 후 다음 진입.
- **사전 의존**: `01-security-test.md` (운영자 cookie) + `02-datasource-test.md` (target DB 등록 — `internal` 등)

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [사전 준비](#2-사전-준비)
3. [Operation CRUD](#3-operation-crud)
4. [동적 쿼리 (META)](#4-동적-쿼리-meta)
5. [커스텀 핸들러 (CUSTOM, B4 등)](#5-커스텀-핸들러-custom-b4-등)
6. [외부 사용자 흐름 (Provide API Key)](#6-외부-사용자-흐름-provide-api-key)
7. [응답 호환 (v3 alias)](#7-응답-호환-v3-alias)
8. [WHERE 연산자 10종 + 자바 후처리](#8-where-연산자-10종--자바-후처리)
9. [페이징 / LIMIT](#9-페이징--limit)
10. [호출 이력 (ApiPrvCallHistory)](#10-호출-이력-apiprvcallhistory)
11. [프론트엔드 UI (6탭)](#11-프론트엔드-ui-6탭)
12. [알려진 허점 / 주의사항](#12-알려진-허점--주의사항)

---

## 1. 시스템 구성

### 1-1. 모듈 구성
| 역할 | 모듈 | 포트 | 비고 |
|---|---|:-:|---|
| API 제공자 | infolink-api-provider | 8095 | 외부 사용자 + 운영자 양쪽 endpoint |
| 운영자 UI | infolink-orchestrator-frontend | 3000 | `/api-provide` |
| 자격증명 패스스루 | infolink-proxy-internal | 8093 | `internal` datasource 사용 시 (B4 등) |

### 1-2. DB
- 자체 DB: PG 29006 / `api_provider` (5/4 신규 컨테이너 — 옛 Oracle 단일에서 분리)
- 운영자 자격: Backend `auth_users` (Auth 통합 — 5/6)
- target DB: 운영자가 operation 등록 시 datasource 선택 (`internal` Oracle 29004 / `dmz` PG 등)

### 1-3. 두 가지 endpoint 영역
| Path | 영역 | 인증 |
|---|---|---|
| `/api/manage/**` | 운영자 (CRUD / 테스트) | JWT cookie |
| `/api/provide/{operationId}` | 외부 사용자 | Provide API Key (DB 등록) |
| `/api/mock/**` | 개발 mock (Mock API key 검증기) | permitAll (개발 default) |

### 1-4. 두 가지 operation type
| operationType | 핸들링 | 등록 |
|---|---|---|
| **META** | `dynamicQueryService` 가 컬럼/조건/정렬/limit 으로 SELECT 자동 생성 | UI 만으로 |
| **CUSTOM** | Java 핸들러 클래스 (`ProviderHandler` 인터페이스) | 코드 + UI 등록 (`POST /api/manage/custom-handlers/register`) |

### 1-5. 등록된 핸들러 (5/6 시점)
| id | operationId | type | 비고 |
|:-:|---|:-:|---|
| 다수 | A1~A12, B1~B7 등 | META | Type A (단일 테이블 SELECT) + Type B (커스텀) |
| 36 | `opnService/getWellInfo` | CUSTOM | **B4 핸들러** (5/6 신규) — RGETNPMMS01 + TC_GD000100 LEFT JOIN |

---

## 2. 사전 준비

### 2-1. 서비스 기동
- [ ] infolink-api-provider (8095)
- [ ] infolink-proxy-internal (8093) — internal datasource 의존 핸들러용
- [ ] infolink-orchestrator-backend (8080)
- [ ] infolink-auth (8096)
- [ ] infolink-orchestrator-frontend (3000)

### 2-2. 사전 데이터
- [ ] Internal Oracle (29004) 의 RGETNPMMS01 9 row + TC_GD000100 16553 row (5/6 §B4_setup)
- [ ] auth_users 의 admin (id=27) — Auth 통합

### 2-3. mock 토글
- [ ] `mock.api-key.enabled=true` (default) — `/api/mock/**` 활성
- [ ] 운영 시점 false → 외부 검증 API 로 대체

---

## 3. Operation CRUD

> ⚠️ **본 사이클 SKIP** (등록/수정/삭제) — `dev_plan/2026_05/07/test-plan-construction.md §9` 결정.
> 16+ operation 이 이미 운영 데이터로 등록됨 (B4 id=36 포함). 신규 operation 등록 검증은 운영 환경 별 사이클.
> **본 사이클은 §3-1 목록 / §3-2 단건 (조회) + §3-5 활성/비활성 토글 + §3-6 인증** 만. §3-3~3-4 (등록/수정) 는 미실행.

### 3-1. 목록 (`GET /api/manage/operations`)
```bash
curl -s -b /tmp/cookies.txt http://localhost:8095/api/manage/operations | jq | head -40
```
- [ ] HTTP 200, 배열 (16+ 항목)
- [ ] 각 항목 = `{id, operationId, operationName, datasourceId, tableName, operationType, isPublished, isActive, isLocked, columns, params}`
- [ ] 사용자 검증: 프론트 `/api-provide` → 같은 목록

### 3-2. 단건 조회 (`GET /api/manage/operations/{id}`)
- [ ] HTTP 200
- [ ] B4 (id=36) 조회 → operationType=CUSTOM, isLocked=true, columns=13, params=2

### 3-3. 등록 (`POST /api/manage/operations`)
- [ ] META 등록 — UI 만으로 가능
- [ ] **CUSTOM 등록 X** (직접 X) — `POST /api/manage/custom-handlers/register {"operationId":"..."}` 사용 (메모리 룰 `feedback_custom_handler_registration`)
- [ ] 사용자 검증: 프론트 "+ 새 operation" → 컬럼 선택 → 조건 → 저장

### 3-4. 수정 (`PUT /api/manage/operations/{id}`)
- [ ] isLocked=true (CUSTOM) 인 항목은 일부 필드 수정 차단
- [ ] META 는 자유 수정

### 3-5. 활성/비활성
- [ ] `PATCH /api/manage/operations/{id}/active` 토글
- [ ] 비활성 → `/api/provide/{operationId}` 호출 시 404

### 3-6. 인증
- [ ] cookie 없이 → 401
- [ ] 외부 사용자 path (`/api/provide/...`) 는 cookie 무관 (별 흐름 §6)

---

## 4. 동적 쿼리 (META)

### 4-1. 컬럼 선택
- [ ] 운영자가 datasource + table 선택 → 컬럼 자동 로드 (Backend 의 `/api/datasources/{id}/tables/{tableName}/columns`)
- [ ] 제공할 컬럼 토글
- [ ] 컬럼 alias (응답 키 변경)

### 4-2. WHERE 조건
- [ ] WHERE 조건 빌더로 필드 + 연산자 + 값 입력
- [ ] 파라미터 바인딩 (실제 외부 호출 시 query param 으로 받음)
- [ ] 필수/옵션/숨김 표시

### 4-3. 정렬 / LIMIT
- [ ] orderByColumn / orderByDirection
- [ ] pageSize (default 100, max 1000)

### 4-4. 컬럼 가공 함수 (4종)
- [ ] ROUND
- [ ] DATE_FORMAT
- [ ] COALESCE
- [ ] SUBSTRING

### 4-5. 파라미터 옵션
- [ ] **필수**: 누락 시 400
- [ ] **숨김**: 외부 노출 X
- [ ] **기본값**: 누락 시 default 사용

### 4-6. 테스트 호출 (`POST /api/manage/operations/{id}/test`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8095/api/manage/operations/2/test \
  -H "Content-Type: application/json" \
  -d '{"params":{}}'
```
- [ ] HTTP 200 + `{data: [...], pagination: {...}, executedSql: "...", durationMs: ?}`
- [ ] 사용자 검증: 프론트 테스트 탭 → "실행" → 결과 + 생성 SQL 하이라이팅

---

## 5. 커스텀 핸들러 (CUSTOM, B4 등)

### 5-1. CustomHandler 인터페이스
- [ ] `getMetadata()` — operationId, columns, params, datasourceId 메타
- [ ] `execute(params)` — 실 비즈니스 로직 (JOIN / DECODE / 좌표변환 등)
- [ ] `@Component` 자동 수집 → `CustomHandlerRegistry`

### 5-2. CustomHandlerCatalogService — `register` 메타 자동 sync
```bash
curl -s -b /tmp/cookies.txt -X POST http://localhost:8095/api/manage/custom-handlers/register \
  -H "Content-Type: application/json" \
  -d '{"operationId":"opnService/getWellInfo"}'
```
- [ ] HTTP 201 또는 200
- [ ] DB `api_prv_operations` 에 row 생성 (operationType=CUSTOM, isLocked=true)
- [ ] columns/params 자동 sync (handler 의 metadata 그대로)
- [ ] 메모리 룰 정수: 직접 `POST /api/manage/operations` 로 CUSTOM 등록 X (operationType=META 가 default 박혀 dynamicQueryService 잘못 호출됨)

### 5-3. B4 핸들러 (id=36, `opnService/getWellInfo`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8095/api/manage/operations/36/test \
  -H "Content-Type: application/json" \
  -d '{"params":{"rel_trans_cgg_code":"3030000"}}'
```
- [ ] HTTP 200
- [ ] data 배열 1건 (대전 동구) + 13 컬럼 (permNtFormCode/addr/uwaterSrvCode/uwaterDtlSrvCode/uwaterPotaYn/digDph/digDiam/esbDph/ndQt/frwPlnQua/rwtCap/dynEqnHrp/pipeDiam)
- [ ] 사용자 검증: 프론트 operation 36 → 테스트 탭 → params 입력 → 같은 결과

### 5-4. B4 옵션 파라미터 (perm_nt_no)
```bash
... -d '{"params":{"rel_trans_cgg_code":"3030000","perm_nt_no":"..."}}'
```
- [ ] perm_nt_no 입력 시 추가 필터링 (단건 반환)
- [ ] perm_nt_no 미입력 시 → rel_trans_cgg_code 만으로 다건

### 5-5. B4 필수 파라미터 누락
- [ ] rel_trans_cgg_code 누락 → 400 + 메시지

### 5-6. 다른 CUSTOM 핸들러 회귀 (16개)
- [ ] B14 (id=15) 등 다른 핸들러 회귀 테스트 — 5/6 회귀 PASS 확인

---

## 6. 외부 사용자 흐름 (Provide API Key)

### 6-1. Provide API Key 등록
- [ ] DB `api_prv_keys` 또는 별 테이블에 등록 (운영자가 미리 발급)
- [ ] 5/6 시점 등록된 keys (예: `test-key-2026`)

### 6-2. 외부 호출 (`GET /api/provide/{operationId}?apiKey=...&...`)
```bash
curl -s -w "\nHTTP %{http_code}\n" \
  "http://localhost:8095/api/provide/opnService/getWellInfo?apiKey=test-key-2026&rel_trans_cgg_code=3030000"
```
- [ ] HTTP 200
- [ ] 응답 = data 배열 (13 컬럼)
- [ ] 호출 이력 `api_prv_call_history` 에 기록 (외부 호출만 — test 제외)

### 6-3. API Key 검증 실패
- [ ] apiKey 누락 → 401 또는 403
- [ ] apiKey 불일치 → 401
- [ ] 비활성 key → 401

### 6-4. Mock API Key 검증기 (`/api/mock/api-key/validate`)
- [ ] mock 활성 시 `/api/mock/api-key/validate?apiKey=...` 호출 가능
- [ ] 운영 시 외부 검증 API 로 교체

### 6-5. 슬래시 operationId 지원 (4/24)
- [ ] `opnService/getWellInfo` 같이 `/` 포함 path 지원 — v3 레거시 URL 그대로 재현

### 6-6. IP 제한 (있는 경우)
- [ ] DB 등록된 허용 IP 외 호출 시 차단

---

## 7. 응답 호환 (v3 alias)

### 7-1. v3 alias 정렬 (4/28)
- [ ] 11종 핸들러 응답 키가 v3 레거시 alias 유지 (`obsv_code` → `OBSV_CODE` 같은 case 보존)
- [ ] 외부 호환 우선 — 내부 DB 만 표준화 (메모리 룰 `feedback_provide_response_v3_compat`)

### 7-2. PG identifier lowercase 회피 (4/24)
- [ ] PG 가 자동 lowercase 하는 column alias 를 쌍따옴표 alias 로 보존
- [ ] 응답 키가 대소문자 정합

### 7-3. 응답 정렬 검증
- [ ] 5/6 시점 11 핸들러 + B4 = 12 핸들러 응답 키 일관 정합
- [ ] 외부 시스템이 기대하는 키 그대로 반환

---

## 8. WHERE 연산자 10종 + 자바 후처리

### 8-1. 10종 연산자
| 연산자 | SQL |
|---|---|
| `=` | `col = ?` |
| `>`, `>=`, `<`, `<=` | 동일 |
| `LIKE` 포함 | `col LIKE '%?%'` |
| `LIKE` 시작 | `col LIKE '?%'` |
| `LIKE` 끝 | `col LIKE '%?'` |
| `IN` | `col IN (?, ?, ...)` |
| `BETWEEN` | `col BETWEEN ? AND ?` |

### 8-2. 동적 쿼리 보안
- [ ] 화이트리스트 정규식 — 컬럼명/테이블명/연산자 등 허용 패턴만 통과
- [ ] **PreparedStatement 바인딩** — value 는 `?` 로 전달 (SQL injection 방지)

### 8-3. RNUM / JOSACODE 후처리 (4/29)
- [ ] A7 의 RNUM 컬럼은 SQL 의 `ROW_NUMBER() OVER()` 또는 자바 후처리
- [ ] B2 의 RNUM + JOSACODE 도 자바 후처리 (DynamicQueryService 화이트리스트, ROWNUM_PREPEND_OPS)
- [ ] 다른 10종 응답 키 변화 없음 (회귀 PASS — 4/29)

### 8-4. B2 josacode column (display_order=22) — v3 환경 등록 정합
- [ ] 1행 INSERT 정합 (4/29)

---

## 9. 페이징 / LIMIT

### 9-1. page / pageSize
```
?page=2&pageSize=50
```
- [ ] HTTP 200 + `pagination: {page: 2, pageSize: 50, totalCount: ?, totalPages: ?}`
- [ ] LIMIT 50 OFFSET 50 으로 자동 변환

### 9-2. pageSize 제한
- [ ] default 100, max 1000
- [ ] pageSize 초과 시 max 로 클램프

### 9-3. totalCount 정확성
- [ ] 별 COUNT 쿼리로 totalCount 산출
- [ ] 페이징 무관

---

## 10. 호출 이력 (ApiPrvCallHistory)

### 10-1. finally 패턴 — 외부 호출만
- [ ] `/api/provide/...` 호출 시 정상 + 실패 모두 이력 기록 (try/finally)
- [ ] `/api/manage/operations/{id}/test` 운영자 테스트 호출은 **이력 미기록**

### 10-2. 이력 조회 (`GET /api/manage/operations/{id}/history`)
- [ ] HTTP 200, 페이징
- [ ] 각 row = 시각 / clientIp / params / status / durationMs / errorMessage?

### 10-3. 비활성 차단
- [ ] 비활성 operation 호출 → 404 (이력 X)

### 10-4. 통계 (TODO)
- [ ] 호출량 통계 / 에러 모니터링 — 미구현 (todo §실행/모니터링)

---

## 11. 프론트엔드 UI (6탭)

### 11-1. `/api-provide` 목록
- [ ] 목록 = id / operationId / operationName / datasource / target / type / 활성 / 작업

### 11-2. `/api-provide/{id}` 상세 (6탭)
1. **기본정보** (InfoTab) — operationId/name/datasource/table/type
2. **컬럼** (ColumnsTab) — 컬럼 선택 + alias + 가공함수
3. **파라미터** (ParamsTab) — WHERE 조건 + 필수/옵션/숨김
4. **테스트** (TestTab) — params 입력 + 실행 + 결과 + 생성 SQL
5. **명세서** (SpecTab) — API 명세 자동 생성 (Markdown 또는 OpenAPI)
6. **이력** (HistoryTab) — 호출 이력 페이징

### 11-3. CUSTOM 핸들러 일부 잠금
- [ ] B4 (CUSTOM, isLocked=true) 진입 시 컬럼/파라미터 탭 readonly
- [ ] 기본정보 탭 일부 (description) 만 수정 가능

### 11-4. Next.js proxy
- [ ] `/provider-api/:path*` → `localhost:8095/api/:path*`

### 11-5. SpecTab — 명세서 자동 생성
- [ ] operationId / 파라미터 / 응답 형태 자동 정리
- [ ] 사용자 검증: 프론트 명세서 탭 → 외부 사용자에게 전달 가능한 형태

---

## 12. 알려진 허점 / 주의사항

### 12-1. CUSTOM 핸들러는 코드 + 등록 필수
- 메모리 룰 `feedback_custom_handler_registration` — 직접 `POST /api/manage/operations` X. 반드시 `/custom-handlers/register` 사용

### 12-2. v3 레거시 alias 보존
- 메모리 룰 `feedback_provide_response_v3_compat` — 응답 키 대소문자/case 변경 시 외부 시스템 깨짐. 신규 핸들러도 v3 정합

### 12-3. provide 타겟 테이블 = endpoint 단위 분리
- 메모리 룰 `feedback_provide_target_per_api` — 컬럼 공유해도 타겟 테이블 분리

### 12-4. provide Loader 항상 UPSERT + UK
- 메모리 룰 `feedback_provide_layer_upsert` — RCV 와 다른 전략. 외부 제공 안정성 우선

### 12-5. 호출량 통계 / 에러 모니터링 미구현
- 본 작업 시점 미구현. 별 사이클 추가 예정

### 12-6. 슬래시 operationId 라우팅
- Spring path matching 에서 `/` 포함 segment 처리 (4/24 fix). path variable 정상 파싱 회귀 점검

### 12-7. PG identifier lowercase 회피
- 쌍따옴표 alias 로 case 보존. 일부 DB 환경 (MySQL 등) 에서 대소문자 비구분 시 의도와 다른 동작 가능 — Provider 는 PG/Oracle 만 지원

### 12-8. UserServiceTest 환경 의존 (5/6 §단위 테스트 — 별 사이클)
- 본 통합 테스트 영역 X

