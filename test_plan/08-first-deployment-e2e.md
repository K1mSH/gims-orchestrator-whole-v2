# 08 — 1차 반입 통합 시나리오 (E2E)

> 검증 baseline: `stable-2026-05-07-rename` (commit: dad8a1b)
> 통과 시: `stable-2026-05-07` 신규 tag 박음 (이름 보류) + 1차 반입 GO/NO-GO 결정 근거
> 작성일: 2026-05-07

---

## 공통 검증 규칙

- claude API 호출 → 1차 확인. **사용자가 직접 프론트(`localhost:3000`)에서 모든 화면 실제 클릭/조회** 후에만 통과.
- 단계마다 사용자 OK 후 다음 진입.
- **사전 의존**: 01~07 모든 기능 테스트 통과. 본 문서는 그 통합 흐름.

---

## 0. 본 시나리오의 의도

**1차 반입 전 시점에서 시스템이 "운영자 시점 핵심 흐름" 을 끝까지 통과하는지 확인.**

- 모듈별 단위 검증 (01~07) 은 따로 통과 가정.
- 본 문서는 **운영자 페르소나** 기준 시간 흐름:
  로그인 → DB 등록 → API 수집 → 보조관측망 파이프라인 → API 제공 → 모니터링 → 운영 작업 (Schedule/Retention)

**시나리오 깊이 결정 (5/7 보류 → 본 문서에서 확정)** — **본격 데이터 흐름** 채택. smoke 수준 X. 실제 데이터가 외부 → DMZ → Internal 까지 흐름 + 외부 사용자가 그 데이터를 API 로 받아옴 + 운영자가 모니터링 화면에서 추적 확인.

---

## 1. 사전 환경 셋업

### 1-1. 7 서비스 + frontend 기동
| 순서 | 모듈 | 포트 |
|:-:|---|:-:|
| 1 | infolink-auth | 8096 |
| 2 | infolink-orchestrator-backend | 8080 |
| 3 | infolink-proxy-dmz | 8083 |
| 4 | infolink-proxy-internal | 8093 |
| 5 | infolink-api-collector | 8084 |
| 6 | infolink-api-provider | 8095 |
| 7 | infolink-agent-bojo-dmz | 8082 |
| 8 | infolink-orchestrator-frontend | 3000 |
| (선택) | infolink-agent-bojo-internal | 8092 |
| (선택) | infolink-agent-others-dmz | 8085 |

- [ ] 7/7 health UP (`/actuator/health` 200)
- [ ] frontend `/` → 307 `/login` (middleware 정합)

### 1-2. DB 컨테이너
- [ ] Orchestrator PG (29001) — orchestrator + dev DB
- [ ] 외부 PG (29000) — daejeon, bytek, chungnam, keunsan
- [ ] 외부 MySQL (29010) — infoworld_*, hydronet_* (6개)
- [ ] Internal Oracle (29004) — XEPDB1
- [ ] (선택) 새올 Oracle (29005)
- [ ] API Provider PG (29006) — api_provider

### 1-3. 사전 데이터 (시나리오 시작 전 보존 상태)
- [ ] auth_users — admin (id=27) 살아있음
- [ ] datasource — 외부 10 + 내부 5+ 등록됨
- [ ] api_collector endpoint — 12+ 등록됨
- [ ] api_provider operation — 16+ 등록됨 (B4 id=36 포함)
- [ ] agents — 14+ 등록됨 (DMZ RCV 10 + Loader + SND + Internal RCV + Loader)

---

## 2. 시나리오 — 운영자 페르소나

> 본 시나리오는 **연속 호출**. 단계 사이 cookie / executionId / data 등 상태가 다음 단계 입력으로 사용됨.

---

### Step 1: 로그인 (07 통합)

**페르소나 행동**: 운영자가 출근해서 운영 시스템에 접속.

```
1. 브라우저 → http://localhost:3000/
2. middleware → 307 /login?next=/
3. 로그인 폼: admin / pass1234 입력
4. 로그인 → 200 + cookies → router.replace('/') → 대시보드
5. 헤더 사용자명 "운영자" 표시 (5/6 stale fix 회귀)
```

- [ ] **사용자 검증**: 위 흐름 직접 수행 → 헤더에 "운영자" 즉시 표시 (F5 없이)
- [ ] cookie jar 보관 (이후 단계 사용)

---

### Step 2: 대시보드 — 시스템 상태 한눈에 (06 통합)

**페르소나 행동**: 대시보드에서 전체 상태 파악.

```
1. /
2. 통계 카드 5장 — Agent 전체/온라인/오프라인, 실행 중, 오늘 실행/실패
3. Agent 상태 테이블 — 14+ Agent
4. 실행 이력 테이블 — 최근 50건
5. 10초 자동 갱신 (페이지에 머무는 동안)
```

- [ ] **사용자 검증**: 카드 + 테이블 + 자동 갱신 (10초마다 timestamp 변경 확인)

---

### Step 3: 외부 DB 연결 확인 (01 통합)

**페르소나 행동**: 데이터 흐름 시작 전 외부 DB 가 모두 정상 연결되는지 확인.

```
1. /datasources
2. 외부 10 + 내부 5 = 15 datasource 표시
3. daejeon (PG) 행 → "연결테스트" 버튼 클릭 → 토스트 "연결 성공"
4. internal (Oracle) 행 → "연결테스트" → 토스트 "연결 성공"
5. infoworld-local (MySQL) 행 → 연결 성공
```

- [ ] **사용자 검증**: 3종 DB 타입 (PG/Oracle/MySQL) 모두 연결 OK

---

### Step 4: API Collector — 외부 데이터 수집 (04 통합)

**페르소나 행동**: 나라장터 신규 입찰 정보 수집.

```
1. /api-collect
2. "나라장터 공사 입찰" endpoint 진입
3. 이력 탭 → 최근 실행 확인 (Schedule 으로 자동 또는 수동)
4. "수집 실행" 버튼 → 토스트 결과
5. 결과 = 신규 N건 / 갱신 M건 / 스킵 K건
6. target (tm_gd014000) 에 데이터 적재됨
```

- [ ] **사용자 검증**: 실행 후 이력 탭에 새 행 + target 테이블에 데이터 존재 (사용자가 별도 SQL 또는 다른 화면으로 확인)

---

### Step 5: BOJO 파이프라인 — 핵심 데이터 흐름 (02 통합)

**페르소나 행동**: 보조관측망 데이터를 외부 → DMZ → Internal 까지 흘려보냄.

#### 5-1. DMZ RCV (대전)
```
1. /agents → "dmz-bojo-rcv-daejeon" 진입
2. 상태확인 → ONLINE
3. "실행" 버튼 → 토스트 → 실행 이력 1건 추가
4. 결과: jewon + obsvdata 합산 N건
5. IF_RSV (if_rsv_sec_jewon, if_rsv_sec_obsvdata) 적재됨
```
- [ ] **사용자 검증**: 실행 상세 → SOURCE/TARGET 건수 일치 + 행 클릭 → 추적 (Source: sec_jewon_view 데이터 표시)

#### 5-2. DMZ Loader
```
1. "dmz-bojo-loader" 실행
2. IF_RSV → Target (sec_jewon, sec_obsvdata) 복사
3. SyncLog 매핑 단위 read/write/skip
```
- [ ] **사용자 검증**: 행 클릭 → Source = if_rsv_*, source_refs 매칭 모드

#### 5-3. DMZ SND
```
1. "dmz-bojo-snd" 실행
2. Target → IF_SND (if_snd_sec_jewon, if_snd_sec_obsvdata)
```
- [ ] **사용자 검증**: 행 클릭 → Source = sec_jewon, PK 모드 (새 source_refs 생성)

#### 5-4. Internal RCV
```
1. infolink-agent-bojo-internal 기동 후 (8092)
2. "internal-bojo-rcv" 실행
3. DMZ IF_SND → Internal IF_RSV (Oracle 29004)
```
- [ ] **사용자 검증**: Oracle IF_RSV 데이터 적재 + obsv_time VARCHAR2 변환 정합

#### 5-5. Internal Loader
```
1. "internal-bojo-loader" 실행
2. Internal IF_RSV → GIMS Target (pm_gd970201, tm_gd970001/970101)
3. EAV 1:3 (gwdep/gwtemp/ec)
```
- [ ] **사용자 검증**: Oracle pm_gd970201 데이터 적재 + 1 obsvdata = 3행 EAV + Internal Loader 의 source_refs 매칭

---

### Step 6: API 제공 — 외부 사용자 호출 (05 통합)

**페르소나 행동**: 운영자가 외부 시스템에 API 정상 응답 가능한지 확인.

#### 6-1. 운영자 테스트 호출
```
1. /api-provide → B4 (id=36) 진입
2. 테스트 탭 → params: rel_trans_cgg_code=3030000
3. "실행" → 200 + data 1건 (대전 동구) + 13 컬럼
```
- [ ] **사용자 검증**: 결과 표 표시 + 생성 SQL 하이라이팅

#### 6-2. 외부 사용자 흐름 (curl)
```bash
curl -s -w "\nHTTP %{http_code}\n" \
  "http://localhost:8095/api/provide/opnService/getWellInfo?apiKey=test-key-2026&rel_trans_cgg_code=3030000"
```
- [ ] HTTP 200 + 같은 13 컬럼
- [ ] 호출 이력 (`api_prv_call_history`) 에 기록됨
- [ ] **사용자 검증**: 프론트 이력 탭 → 새 호출 1건 추가

#### 6-3. 다른 META operation 1건 (회귀)
- [ ] A1 또는 다른 META operation 테스트 호출 → 200
- [ ] **사용자 검증**: B4 외 다른 operation 도 정상 응답

---

### Step 7: 모니터링 — 추적 검증 (06 통합)

**페르소나 행동**: Step 5 의 데이터가 외부 → Internal 까지 정합인지 추적.

#### 7-1. 실행 이력 필터링
```
1. /executions
2. 필터: agentCode=dmz-bojo-rcv-daejeon, date=오늘
3. Step 5-1 의 실행 검색 → 클릭 → 상세
```
- [ ] **사용자 검증**: 같은 executionId 의 SOURCE/TARGET 건수 일치

#### 7-2. 3단계 추적 검증 (각 Step)
- [ ] **건수**: Summary read/write/total 일치
- [ ] **단건 역추적**: target 1건의 source_refs → trace-source API → source 원본 1건 (traceStatus=FOUND)
- [ ] **프론트 UI**: 행 클릭 → Source 데이터 표시

#### 7-3. forward + backward (Step 2, 5)
- [ ] DMZ Loader: source(if_rsv) PK → target(sec_*) 매칭 (forward)
- [ ] Internal Loader: target(pm_gd970201) source_refs → source(if_rsv internal) (backward)

#### 7-4. NOT_TRACKABLE (link 테이블)
- [ ] tm_gd970101, tm_gd980002, link_ngwis 행 클릭 → "추적 비대상" 라벨

---

### Step 8: 운영 작업 — Schedule + Retention (06 통합)

**페르소나 행동**: 운영 자동화 설정.

#### 8-1. Schedule 등록
```
1. dmz-bojo-rcv-daejeon → 스케줄 섹션
2. "+ 새 스케줄" → cron: "0 */30 * * * *" (30분마다) → 활성
3. 토스트 "등록됨"
4. cron 한글 표시: "30분마다"
```
- [ ] **사용자 검증**: 등록 후 30분 내 자동 실행 발생 (또는 cron 수정 → 2분마다 단축 후 빠른 검증)
- [ ] 실행 이력에 `triggeredBy=SCHEDULE`

#### 8-2. Retention 설정
```
1. dmz-bojo-rcv-daejeon → Retention 섹션
2. enabled=true / target=if_rsv_sec_obsvdata / dateColumn=obsv_date / retentionDays=90
3. 저장
```
- [ ] **사용자 검증**: 저장 후 재조회 시 같은 값 표시
- [ ] (선택) 직접 cleanup 호출 → 만료 데이터 삭제 결과 확인

#### 8-3. 비활성 스케줄 토글
```
1. 스케줄 토글 OFF
2. 다음 cron 시점에 실행 X 확인
```
- [ ] **사용자 검증**: 토글 OFF 후 30분 (또는 단축 cron) 동안 실행 이력 추가 X

---

### Step 9: 사용자 관리 (07 통합)

**페르소나 행동**: 신규 운영자 추가 → 본인 비번 변경.

#### 9-1. peer multi 추가
```
1. /users → "+ 새 사용자"
2. authUsersId=alice / password=pass1234 / name=앨리스
3. 추가 → 목록에 alice
4. 별 채널로 alice 에게 자격 전달 (시뮬레이션)
```
- [ ] **사용자 검증**: 목록 즉시 갱신 + 비번 평문 노출 X

#### 9-2. alice 별 cookie 로 로그인 검증
- [ ] 별 브라우저 (또는 시크릿 창) → /login → alice / pass1234 → 200
- [ ] **사용자 검증**: alice 헤더 표시

#### 9-3. alice 비번 변경
```
1. alice "내 정보 변경" → 현재 비번 + 새 비번 입력 → 204
2. 자동 로그아웃 (모든 refresh revoke)
3. 새 비번으로 재로그인 → 200
```

#### 9-4. 마지막 1명 차단 (별 시나리오)
- [ ] alice 본인 탈퇴 → 204 (count 2→1)
- [ ] admin 본인 탈퇴 시도 → 409 LAST_USER_CANNOT_DELETE

---

### Step 10: 로그아웃

```
1. 헤더 → "로그아웃" → /login 자동 이동
2. 만료 cookie 로 /api/auth/me → 401
3. /agents 직접 진입 시도 → 307 /login?next=/agents
```
- [ ] **사용자 검증**: 모든 cookie 만료 + middleware 가드 동작

---

## 3. 통과 기준

본 시나리오 = **각 Step 의 사용자 직접 확인이 모두 ✅ 일 때 통과**.

| Step | 통과 기준 |
|:-:|---|
| 1 로그인 | 헤더 사용자명 즉시 표시 + cookie 박힘 |
| 2 대시보드 | 5장 카드 + Agent/실행 테이블 + 10초 갱신 |
| 3 DB 연결 | 3종 DB 타입 모두 OK |
| 4 API Collect | 신규 데이터 적재 + 이력 1건 추가 |
| 5 BOJO 5단계 | 외부→DMZ→Internal 5 Step 모두 SUCCESS + 3단계 추적 PASS |
| 6 API Provide | 운영자 테스트 200 + 외부 사용자 호출 200 + 호출 이력 |
| 7 모니터링 추적 | 건수 + 역추적 + UI 행 클릭 모두 정합 |
| 8 운영 작업 | Schedule 자동 실행 + Retention 설정 + 토글 OFF 정합 |
| 9 사용자 관리 | peer multi + 비번 변경 + 마지막 1명 차단 |
| 10 로그아웃 | cookie 만료 + middleware 차단 |

→ **10/10 통과 시 1차 반입 GO 결정 + `stable-2026-05-XX` 신규 tag 박음**

→ 일부 미통과 시:
- **블로커** (Step 5 BOJO / Step 6 API Provide / Step 7 추적 / Step 1 로그인) → fix → 재실행
- **비블로커** (Step 8 자동 실행 검증 등) → 별 사이클 등록 + 1차 반입 진행 가능 검토

---

## 4. 시나리오 추적 — 별 자료 보존

본 시나리오 실행 시 다음 데이터 별 파일로 보존 (재현 가능성):
- 사용된 admin 자격 / cookie 만료
- Step 5 의 executionId 5개 (각 Step)
- Step 6 의 외부 호출 이력 row 1건
- Step 8 의 cron 표현식 / 자동 실행된 executionId

→ `dev_logs/2026_05/2026-05-XX.md` 에 시나리오 결과 + 발견 issue 기록

---

## 5. 알려진 허점 / 주의사항 (시나리오 흐름 기준)

### 5-1. Step 5 의 데이터 의존성
- DMZ RCV → DMZ Loader → SND → Internal RCV → Internal Loader 가 순차로 실행되어야 데이터 흐름 정합
- 사이에 다른 RCV 가 끼면 source_refs 가 덮어씌워질 수 있음 (Fallback 모드 필요)

### 5-2. Step 6 의 외부 호출 = Provide API Key 사전 등록 필요
- 운영자가 미리 발급해 둔 key 사용
- 본 시나리오 시점에 `test-key-2026` 같은 dev key 활성 가정

### 5-3. Step 8 자동 실행은 시간 의존 (30분/2분 cron)
- 실시간 검증 어려우면 cron 단축 (`0 */1 * * * *` 1분마다) 또는 즉시 실행 (`triggerExecution` 직접 호출)

### 5-4. middleware 우회 검증
- middleware 통과 후 backend Filter 가 결국 인증 검증. middleware 만 우회해도 endpoint 401

### 5-5. 1차 반입 시점에 운영 환경 차이
- 본 dev 환경 = mock 활성 / dev default JASYPT 키 / 9 모듈 libs/ 자동 복사
- 운영 환경 = mock 비활성 / 실 JASYPT 키 / 폐쇄망 nexus → **별 운영 환경 검증 사이클 필요** (본 통합 시나리오는 dev 환경 검증 한계 명시)

---

## 6. Baseline 태그 갱신

```
실행 시작 baseline: stable-2026-05-07-rename (commit dad8a1b)
검증 통과 일시: 2026-05-XX
신규 stable tag: stable-2026-05-XX (이름 보류 — 통합 시나리오 통과 시 결정)
신규 tag commit: ?????? (실행 시점 main HEAD)

1차 반입 GO 결정: 2026-05-XX
```
