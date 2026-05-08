# 05 — API Collector 기능 테스트 문서

> 작성일: 2026-05-07

---

## 공통 검증 규칙

- claude API 호출 → 1차 확인. **사용자가 직접 프론트(`localhost:3000/api-collect`)에서 같은 흐름 확인** 후에만 통과.
- 단계마다 사용자 OK 후 다음 진입.
- **사전 의존**: `01-security-test.md` (cookie) + `02-datasource-test.md` (target DB 등록)

---

## 목차
1. [시스템 구성](#1-시스템-구성)
2. [사전 준비](#2-사전-준비)
3. [Endpoint 등록 / CRUD](#3-endpoint-등록--crud)
4. [범용 실행기 (테스트 호출 → 매핑 → 적재)](#4-범용-실행기-테스트-호출--매핑--적재)
5. [LOOKUP 파생 컬럼](#5-lookup-파생-컬럼)
6. [커스텀 실행기 (Type B)](#6-커스텀-실행기-type-b)
7. [스케줄 실행](#7-스케줄-실행)
8. [Mock API 자기호출 (LOOKUP 의존)](#8-mock-api-자기호출-lookup-의존)
9. [Internal Collector (8094)](#9-internal-collector-8094)
10. [프론트엔드 UI](#10-프론트엔드-ui)
11. [알려진 허점 / 주의사항](#11-알려진-허점--주의사항)

---

## 1. 시스템 구성

### 1-1. 모듈 구성
| 역할 | 모듈 | 포트 | 비고 |
|---|---|:-:|---|
| API 수집 (DMZ) | infolink-api-collector | 8084 | 외부 공공 API → DMZ DB (29001/dev) |
| API 수집 (Internal) | infolink-api-collector | 8094 | 별 인스턴스 — 내부망 전용 (제주 등 내부 호출) |
| 운영자 UI | infolink-orchestrator-frontend | 3000 | `/api-collect` |

### 1-2. DB
- 등록 메타: dev DB (29001/dev) — endpoint, mappings, schedules, executions
- 적재 target: 등록 시 운영자가 datasource 선택 (보통 `dmz` = 29001/dev)
- (5/4 통합 후) `api_collector` 별도 DB 폐기 — 모두 dev 통합

### 1-3. 두 가지 실행기 모델
| 모델 | 설명 | 등록 방식 |
|---|---|---|
| **범용** (Type A) | URL + 매핑만 등록. 코드 변경 X. | UI 만으로 |
| **커스텀** (Type B) | 페이징 / 좌표변환 / 코드변환 등 비즈니스 로직 필요 시 | `CustomExecutor` 인터페이스 + Spring Bean (코드) + UI 등록 |

### 1-4. 자기호출 (LOOKUP)
- API Collector 가 자기 자신의 Mock 을 LOOKUP 의존성으로 호출 (`/mock/common/select/{groupCode}`)
- 운영 시점엔 실 endpoint 로 대체 (yml `lookup.common-code-url` override)
- **개발 default = mock 활성** (`@ConditionalOnProperty matchIfMissing=true`)
- SecurityConfig path = `/mock/**` permitAll (자기호출 보장)

---

## 2. 사전 준비

### 2-1. 서비스 기동
- [ ] infolink-api-collector (8084) DMZ
- [ ] infolink-api-collector (8094) Internal (선택 — §9 검증 시)
- [ ] infolink-orchestrator-backend (8080) — 인증 통합 의존
- [ ] infolink-orchestrator-frontend (3000)

### 2-2. 사전 등록 데이터 (5/6 시점)
| 종류 | 등록 |
|---|---|
| 범용 endpoint | 나라장터 (공사/용역/외자/물품) + 네이버 뉴스 = 5건 |
| 커스텀 executor | 제주 관측점/수위/이용시설/수질, 안양 이용량, 약수터 제원/수질 = 7건 |

### 2-3. mock.api.enabled 토글
- [ ] yml `mock.api.enabled=true` (default) — `/mock/**` 활성
- [ ] 운영 환경 yml override 시 `false` → `/mock/**` 비활성, 실 endpoint 호출

---

## 3. Endpoint 등록 / CRUD

> ⚠️ **본 사이클 SKIP** (등록/수정/삭제) — `dev_plan/2026_05/07/test-plan-construction.md §9` 결정.
> 12+ endpoint 가 이미 운영 데이터로 등록됨. 신규 endpoint 등록 검증은 운영 환경 별 사이클.
> **본 사이클은 §3-1 목록 / §3-2 단건 (조회) + §3-6 인증** 만. §3-3~3-5 (등록/수정/삭제) 는 미실행.

### 3-1. 목록 (`GET /api/endpoints`)
```bash
curl -s -b /tmp/cookies.txt http://localhost:8084/api/endpoints | jq | head -30
```
- [ ] HTTP 200, 배열 (12+ 항목)
- [ ] 각 항목 = `{id, apiName, url, httpMethod, authType, targetTableName, isActive, zone, executorType, hasMappings}`
- [ ] 사용자 검증: 프론트 `/api-collect` → 같은 목록

### 3-2. 단건 조회 (`GET /api/endpoints/{id}`)
- [ ] HTTP 200, 매핑 / 스케줄 포함
- [ ] 사용자 검증: 프론트 `/api-collect/{id}` → 4탭 (기본정보/매핑/스케줄/이력) 표시

### 3-3. 등록 (`POST /api/endpoints`)
- [ ] HTTP 201, 새 id 반환
- [ ] 사용자 검증: 프론트 "+ 새 endpoint" → URL 입력 → 인라인 테스트 호출 → JSON 트리 뷰 → 데이터 루트 선택 → 매핑 탭 → 저장

### 3-4. 수정 (`PUT /api/endpoints/{id}`)
- [ ] HTTP 200
- [ ] isActive 토글 정상

### 3-5. 삭제 (`DELETE /api/endpoints/{id}`)
- [ ] HTTP 204

### 3-6. 인증 (07 통과 후)
- [ ] cookie 없이 호출 → 401
- [ ] cookie 정상 → 200

---

## 4. 범용 실행기 (테스트 호출 → 매핑 → 적재)

### 4-1. 테스트 호출 (`POST /api/endpoints/{id}/test-call`)
- [ ] 외부 API 직접 호출 → JSON 응답 받음
- [ ] 응답을 트리 뷰로 표시 (사용자 검증: 프론트 등록 화면에서 트리 노드 클릭으로 데이터 루트 선택)
- [ ] API key 참조 시 (`isApiKeyRef=true`) `resolveApiKey` 가 datasource 등록된 키 사용

### 4-2. 매핑 정의
- [ ] sourceField (JSON path) → targetColumn 매핑
- [ ] TransformType 8종 모두 동작:
  - PASSTHROUGH (그대로)
  - TRIM
  - UPPER / LOWER
  - DATE_FORMAT (YYYY-MM-DD)
  - SUBSTRING
  - CONCAT (다중 sourceField)
  - LOOKUP (별 §5)
  - REGEX_EXTRACT
- [ ] 사용자 검증: 매핑 탭 → 각 컬럼별 변환 타입 드롭다운 → 저장 → 저장 후 재로드 시 그대로

### 4-3. 적재 실행 (`POST /api/endpoints/{id}/run`)
```bash
curl -s -b /tmp/cookies.txt -w "\nHTTP %{http_code}\n" \
  -X POST http://localhost:8084/api/endpoints/{id}/run
```
- [ ] HTTP 200
- [ ] 응답 = `{executionId, totalCount, insertedCount, updatedCount, skippedCount, status}`
- [ ] target 테이블에 데이터 적재됨 (`SELECT COUNT(*) FROM tm_gd014000` 등)
- [ ] 사용자 검증: 프론트 "수집 실행" 버튼 → 결과 토스트 → 이력 탭 갱신

### 4-4. INSERT / UPSERT 분기
- [ ] UPSERT 토글 ON 시 conflictKey 기준 ON CONFLICT DO UPDATE
- [ ] OFF 시 단순 INSERT (중복 시 PK 충돌 → SKIP)
- [ ] 사용자 검증: 매핑 탭의 UPSERT 토글 (4/29 통합)

### 4-5. 동적 파라미터 (TODAY/NOW + offset + format)
- [ ] URL 또는 query param 에 `${TODAY}`, `${NOW}`, `${TODAY-7d}`, `${TODAY,YYYYMMDD}` 같은 표기
- [ ] 실행 시점 자동 치환 (서버 시각 기준)

### 4-6. 인라인 테스트 (등록 화면)
- [ ] 등록 폼에서 URL/매핑 입력 → "테스트 호출" → JSON 응답 즉시 표시 → 매핑 자동 추론
- [ ] API key ID 표시 (실제 값 노출 X)

---

## 5. LOOKUP 파생 컬럼

### 5-1. LOOKUP 정의
- [ ] sourceField 에서 정규식으로 키 추출 → 공통코드 테이블 매칭 → 새 컬럼 생성
- [ ] 예: 뉴스 데이터의 url 에서 도메인 추출 → `NGW_0118` (언론사) 매칭 → `media_name` 컬럼 채움

### 5-2. 정규식 키 추출
- [ ] regex 패턴 입력 → 매칭 그룹 (`group(1)`) 키로 사용
- [ ] 매칭 실패 시 null 또는 default

### 5-3. 공통코드 매칭
- [ ] LOOKUP url = `http://localhost:8084/mock/common/select/{groupCode}` (개발 default)
- [ ] 응답 = `[{code, name}, ...]` 형식
- [ ] code 매칭 시 name 사용

### 5-4. mock 활성 시 자기호출 검증
- [ ] `GET /mock/common/select/NGW_0118` → 200 + 65건 (언론사)
- [ ] permitAll 정합 (인증 없이 호출 가능)
- [ ] 사용자 검증: LOOKUP 매핑 등록한 endpoint 실행 시 → 결과 데이터의 LOOKUP 컬럼이 정상 채워짐

---

## 6. 커스텀 실행기 (Type B)

### 6-1. 등록된 7 executor
| executorType | 설명 | target |
|---|---|---|
| `jeju-jewon` | 제주 관측점 제원 | tb_jeju_jewon |
| `jeju-obsv` | 제주 수위 관측 | tb_jeju |
| `jeju-fac-auto` / `jeju-fac-manual` | 제주 이용시설 (자동/수동) | rgetnpmms01, rgetstgms01 |
| `jeju-quality` | 제주 수질검사 | rgetnwavi05, rgetnwavi06 |
| `anyang-usage` | 안양 이용량 | anyang_api_fac, anyang_api_data, use_legacy_data |
| `yaksoter-jewon` | 약수터 제원 (4/29) | tm_gd010310 (B-1 자연키 UK + DO UPDATE) |
| `yaksoter-quality` | 약수터 수질 (4/29) | td_gd010310 (4키 dedup) |

### 6-2. 실행 옵션 (executionParams)
- [ ] 연도 지정 / 분기 옵션 등 Map 형태로 executor 에 전달
- [ ] 사용자 검증: 프론트 실행 버튼 옆 "옵션 설정" → params 입력 폼

### 6-3. 좌표변환 (제주 관측점)
- [ ] EPSG:5186 → EPSG:4326 변환
- [ ] proj4j 라이브러리 사용
- [ ] 변환 결과 = `latitude`, `longitude` 컬럼

### 6-4. 코드변환 (용도/지역/허가형태/상태)
- [ ] LOOKUP 비슷하나 비즈니스 코드 매핑
- [ ] target 컬럼에 변환된 한글명 적재

### 6-5. 페이징 반복 호출
- [ ] 1000건 단위 반복 (numOfRows=1000)
- [ ] totalCount 도달 시 종료
- [ ] (메모리 룰 — 범용 endpoint 자동 페이징 미지원, 커스텀만)

### 6-6. CustomExecutor 인터페이스
- [ ] Spring Bean 자동 등록 (`@Component`)
- [ ] `Registry` 가 executorType → 매칭 Executor 라우팅
- [ ] 새 executor 추가 시 코드 작성 + Bean 등록 + UI 에서 executorType 입력

---

## 7. 스케줄 실행

### 7-1. 스케줄 등록
```bash
curl -s -b /tmp/cookies.txt -X POST http://localhost:8084/api/endpoints/{id}/schedules \
  -H "Content-Type: application/json" \
  -d '{"cronExpression":"0 0 2 * * *","isEnabled":true}'
```
- [ ] HTTP 201
- [ ] 사용자 검증: 프론트 endpoint 상세 → 스케줄 탭 → 등록 폼 → cron 입력

### 7-2. 자동 실행
- [ ] cron 시점에 endpoint 자동 실행 (`ApiScheduleExecutor` + `TaskScheduler`)
- [ ] 실행 이력에 `triggeredBy=SCHEDULE` 기록
- [ ] 비활성 스케줄은 실행 X

### 7-3. 토글 / 삭제
- [ ] `PUT /api/endpoints/{id}/schedules/{sid}/toggle`
- [ ] `DELETE /api/endpoints/{id}/schedules/{sid}`

### 7-4. 초기화 (앱 기동 시)
- [ ] `@EventListener(ContextRefreshedEvent)` 가 DB 의 활성 스케줄 모두 로드
- [ ] `getActiveScheduleCount` 정합

---

## 8. Mock API 자기호출 (LOOKUP 의존)

### 8-1. Mock 활성 (개발 default)
- [ ] `GET /mock/common/select/NGW_0118` → 200 + JSON 배열
- [ ] `GET /mock/common/select/NGW_0001` → 200 (jeju 분류)
- [ ] **인증 없이 호출 가능** (permitAll — LOOKUP 자기호출 보장)

### 8-2. Mock API 키 목록
- [ ] `GET /mock/api-keys` → 200 + 등록된 mock API key 들

### 8-3. mock.api.enabled=false (운영 환경 시뮬레이션)
- [ ] yml override 시 `/mock/**` 모두 404
- [ ] LOOKUP url 도 운영 endpoint 로 변경 필요 (`lookup.common-code-url=https://real-host/...`)

---

## 9. Internal Collector (8094)

### 9-1. 별 인스턴스 — 동일 코드, 내부망 호출 전용
- [ ] 8094 health UP
- [ ] 같은 endpoint CRUD API 동작
- [ ] zone=INTERNAL 의 datasource 사용 시 proxy-internal 경유 X (collector 자체가 직접 연결 — collector 는 운영자 대신 외부 호출)

### 9-2. 8094 와 8084 의 차이
- [ ] 8084 = DMZ 외부 공공 API (나라장터/뉴스 등)
- [ ] 8094 = Internal 자기 망 호출 (만약 운영망에 별 endpoint 호출 필요 시)
- [ ] 본 작업 시점 8094 endpoint 등록은 0 또는 소수 — 인스턴스 가동만 확인

---

## 10. 프론트엔드 UI

### 10-1. `/api-collect` 목록
- [ ] 목록 = id / API명 / URL / 인증 / target / 활성 / 작업
- [ ] 사용자 검증: 5/6 시점 12+ endpoint 정합

### 10-2. `/api-collect/{id}` 상세 (4탭)
- [ ] **기본정보** (InfoTab): URL/인증/target/zone/실행기 타입. 실행기 = 범용 vs 커스텀 드롭다운. 4/29 보강 (적재 설정 섹션 제거 + isActive 토글)
- [ ] **매핑** (MappingTab): sourceField → targetColumn + TransformType. UPSERT 토글 통합 (4/29)
- [ ] **스케줄** (ScheduleTab): cron 등록/해제
- [ ] **이력** (HistoryTab): 페이징 + 날짜 검색 + 신규/갱신/스킵 카운트

### 10-3. 등록 화면 (인라인 테스트)
- [ ] URL 입력 → "테스트 호출" → JSON 트리 뷰
- [ ] 데이터 루트 선택 → 매핑 자동 추론
- [ ] 매핑 수정 → 저장

### 10-4. Next.js proxy
- [ ] `/collector-api/:path*` → `localhost:8084/api/:path*` rewrite

### 10-5. InfoTab ✓/✗ 컬럼 수 표시 (4/30 fix)
- [ ] 처음 렌더 시 컬럼 수 표시 → 다른 정보 받아 덮음 패턴 fix 회귀

---

## 11. 알려진 허점 / 주의사항

### 11-1. 범용 endpoint 자동 페이징 미지원
- 메모리 룰 `project_api_collector_paging_limit` — 범용은 단일 호출. 페이징 필요 시 커스텀 executor 작성
- 사용자 결정 = 그냥 둠 (5/4)

### 11-2. mock 자기호출이 운영에서 깨질 가능성
- yml `lookup.common-code-url` 운영 endpoint 미설정 시 LOOKUP 실패
- 배포 시점 yml override 패턴 정립 필요 (`mock.api.enabled=false` + 실 url)

### 11-3. API key 노출 (테스트 호출 응답)
- 테스트 호출 시 API key ID 만 표시 (실 값 노출 X) — 5/6 정합 점검

### 11-4. 동적 파라미터 시간대 (KST)
- `${TODAY}` 등 서버 시각 기준. 운영 서버 timezone 정합 (Asia/Seoul) 필요

### 11-5. 매핑 자동 추론 한계
- 인라인 테스트 시 JSON 트리에서 매핑 자동 추론 — depth 깊거나 array nested 시 수동 보정 필요

### 11-6. 약수터 4키 dedup 검증
- `yaksoter-quality` executor 의 4키 dedup (B-1 패턴) 정합. 실 데이터 1000행 E2E 회귀 (4/30) 재실행 권장

