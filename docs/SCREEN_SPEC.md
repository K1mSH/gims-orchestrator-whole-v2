# Sync Orchestrator 화면정의서

> 작성일: 2026-03-03
> 대상: Sync Orchestrator v2 프론트엔드
> 범위: 개발 완료 화면 + 미개발 화면(API 관리, 프로시저 관리)

---

## 목차

1. [공통 레이아웃](#1-공통-레이아웃)
2. [SCR-01 대시보드](#2-scr-01-대시보드)
3. [SCR-02 실행이력 목록](#3-scr-02-실행이력-목록)
4. [SCR-03 실행 상세](#4-scr-03-실행-상세)
5. [SCR-04 DB 관리](#5-scr-04-db-관리)
6. [SCR-05 Agent 목록](#6-scr-05-agent-목록)
7. [SCR-06 Agent 등록](#7-scr-06-agent-등록)
8. [SCR-07 Agent 상세](#8-scr-07-agent-상세)
9. [SCR-08 Agent 체인](#9-scr-08-agent-체인)
10. [SCR-09 API 관리 (미개발)](#10-scr-09-api-관리-미개발)
11. [SCR-10 프로시저 관리 (미개발)](#11-scr-10-프로시저-관리-미개발)

---

# 1. 공통 레이아웃

## 1.1 화면 구조

```
┌──────────────────────────────────────────────────────────┐
│  Sync Orchestrator (로고)                                │
├───────────┬──────────────────────────────────────────────┤
│           │                                              │
│  사이드바  │              메인 컨텐츠 영역                │
│           │                                              │
│ 📊 대시보드│                                              │
│ 📋 실행이력│                                              │
│ 🗄️ DB 관리│                                              │
│ 🖥️ Agent  │                                              │
│ 🔗 체인   │                                              │
│ 🌐 API    │  ← 미개발                                    │
│ ⚙️ 프로시저│  ← 미개발                                    │
│           │                                              │
└───────────┴──────────────────────────────────────────────┘
```

## 1.2 사이드바 메뉴 사용법

1. 화면 좌측의 **사이드바**에서 원하는 메뉴를 클릭합니다.
2. 현재 선택된 메뉴는 **하이라이트**로 표시됩니다.
3. 사이드바 메뉴 구성은 다음과 같습니다:

| 순서 | 메뉴명 | 경로 | 상태 |
|------|--------|------|------|
| 1 | 대시보드 | `/` | 개발완료 |
| 2 | 실행 이력 | `/executions` | 개발완료 |
| 3 | DB 관리 | `/datasources` | 개발완료 |
| 4 | Agent 관리 | `/agents` | 개발완료 |
| 5 | Agent 체인 | `/chains` | 개발완료 |
| 6 | API 관리 | `/apis` | **미개발** |
| 7 | 프로시저 관리 | `/procedures` | **미개발** |

## 1.3 공통 표시 요소

화면 전체에서 반복 사용되는 배지(Badge) 유형입니다.

1. **Zone 배지** — Agent가 소속된 네트워크 존을 색상으로 구분합니다.
   - EXTERNAL(외부망) / DMZ / INTERNAL_COMMON(내부공통망) / INTERNAL_SERVICE(내부서비스망)

2. **상태 배지** — 현재 상태를 색상으로 구분합니다.
   - ONLINE(초록) / OFFLINE(회색) / RUNNING(파랑, 애니메이션) / SUCCESS(초록) / FAILED(빨강)

3. **Agent 타입 배지** — Agent의 역할을 구분합니다.
   - RCV(수신) / LOADER(적재) / SND(송신)

4. **트리거 배지** — 실행이 어떻게 시작되었는지 구분합니다.
   - MANUAL(수동) / SCHEDULED(스케줄) / CHAIN(체인) / API(외부호출)

---

# 2. SCR-01 대시보드

**경로:** `/` | **자동갱신:** 10초 간격

시스템 전체 현황을 한눈에 파악하는 화면입니다.

## 2.1 통계 카드 확인하기

1. 화면 상단에 **6개의 통계 카드**가 가로로 배치되어 있습니다.
2. 각 카드는 다음 정보를 표시합니다:
   - **전체 Agent** — 등록된 Agent 총 수
   - **온라인 Agent** — 현재 정상 연결된 수
   - **오프라인 Agent** — 연결이 끊긴 수 (1개 이상이면 경고색)
   - **현재 실행 중** — 지금 동기화가 진행 중인 수
   - **오늘 실행** — 오늘 실행된 총 횟수
   - **오늘 실패** — 오늘 실패한 횟수 (1개 이상이면 빨간색)
3. 각 카드를 **클릭**하면 해당 조건으로 필터링된 상세 화면으로 이동합니다.
   - 예: "오늘 실패" 클릭 → 실행이력 목록이 `상태=FAILED, 시작일=오늘`로 필터링되어 표시

## 2.2 Agent 상태 테이블 확인하기

1. 통계 카드 아래에 **Agent 상태 테이블**이 표시됩니다.
2. 각 행에는 Agent 이름, Zone, 상태(ONLINE/OFFLINE/RUNNING), 마지막 실행 결과, 마지막 실행 시간이 표시됩니다.
3. **Agent 이름을 클릭**하면 해당 Agent의 상세 페이지(SCR-07)로 이동합니다.

## 2.3 최근 실행 이력 확인하기

1. Agent 상태 테이블 아래에 **최근 실행 이력 테이블**이 표시됩니다.
2. 각 행에는 Agent 이름, 타입(RCV/LOADER/SND), 상태, 읽기/쓰기/스킵 건수, 소요시간, 트리거, 시작 시간이 표시됩니다.
3. 상단의 통계 카드를 클릭하면 이 테이블이 **해당 조건으로 필터링**됩니다.
4. 실행 행을 **클릭**하면 해당 실행의 상세 페이지(SCR-03)로 이동합니다.

---

# 3. SCR-02 실행이력 목록

**경로:** `/executions`

전체 Agent의 실행 이력을 조회하고 필터링하는 화면입니다.

## 3.1 실행이력 검색하기

1. 화면 상단의 **필터 영역**에서 검색 조건을 설정합니다.
2. **1줄째 필터:**
   - (1) **상태** 드롭다운에서 원하는 상태를 선택합니다 (전체 / SUCCESS / FAILED / RUNNING).
   - (2) **Zone** 드롭다운에서 네트워크 존을 선택합니다.
   - (3) **Agent** 드롭다운에서 특정 Agent를 선택합니다.
   - (4) **Agent 타입** 드롭다운에서 타입을 선택합니다 (전체 / RCV / SND / LOADER).
3. **2줄째 필터:**
   - (5) **시작일**과 **종료일**을 입력하여 기간을 지정합니다.
   - (6) **검색어**에 Agent 이름을 입력합니다.
   - (7) **검색** 버튼을 클릭하여 필터를 적용합니다.

## 3.2 실행이력 목록 확인하기

1. 필터 아래에 **실행이력 테이블**이 표시됩니다.
2. 각 행에 표시되는 정보:
   - **Agent 이름 + 코드** — Agent 명과 코드
   - **타입** — RCV / LOADER / SND (배지)
   - **상태** — SUCCESS / FAILED / RUNNING (배지)
   - **읽기/쓰기/스킵** — 처리 건수 (실행 중에는 "-")
   - **소요시간** — 포맷: "13s"
   - **트리거** — MANUAL / SCHEDULED / CHAIN / API (배지)
   - **시작 시간** — 한국 날짜-시간 포맷
3. 행을 **클릭**하면 해당 실행의 상세 페이지(SCR-03)로 이동합니다.

## 3.3 페이지 이동하기

1. 테이블 하단에 **페이지 네비게이션**이 있습니다.
2. [첫 페이지] [이전] [페이지 번호들] [다음] [마지막] 버튼으로 이동합니다.
3. 페이지 번호는 현재 페이지 중심으로 최대 5개가 표시됩니다.
4. 헤더에 **전체 N건**, 하단에 **20건/페이지**가 표시됩니다.

---

# 4. SCR-03 실행 상세

**경로:** `/executions/[id]` | **자동갱신:** RUNNING 상태일 때 5초 간격

특정 실행의 상세 정보 확인, 처리 데이터 조회, 데이터 추적(Trace)을 수행하는 화면입니다.

## 4.1 페이지 헤더

1. 화면 상단에 **"실행 상세"** 제목과 **상태 배지**(성공/실패/실행중)가 표시됩니다.
2. 우측 버튼:
   - **"Agent 상세"** — 해당 Agent의 상세 페이지(SCR-07)로 이동
   - **"뒤로"** — 이전 페이지로 돌아가기

## 4.2 실행 정보 카드 확인하기

1. 헤더 아래에 **실행 정보 카드**가 4열 그리드로 표시됩니다.
2. 표시 항목:
   - (1) **실행 ID** — 모노스페이스 폰트 (포맷: `{agentCode}_{uuid}`)
   - (2) **Agent** — Agent 이름 (클릭 시 Agent 상세로 이동)
   - (3) **시작 시간** — 한국 날짜-시간 포맷
   - (4) **종료 시간** — 한국 날짜-시간 포맷 (실행 중이면 "-")
   - (5) **소요 시간** — "N.N초" 포맷 (실행 중이면 "-")
   - (6) **총 읽기** — 파란색 큰 숫자
   - (7) **총 쓰기** — 초록색 큰 숫자
   - (8) **총 스킵** — 회색 큰 숫자
3. **오류 메시지** — 실행 실패 시 빨간 배경의 오류 메시지 박스가 추가로 표시됩니다.

## 4.3 실행 중 안내 (RUNNING 상태)

1. 실행이 진행 중이면 아래 섹션 대신 **파란색 안내 카드**가 표시됩니다.
   - "실행 중..."
   - "실행이 완료되면 처리된 데이터를 확인할 수 있습니다."
2. **5초 간격으로 자동 갱신**되어 실행 완료 시 자동으로 상세 정보가 표시됩니다.

## 4.4 테이블별 처리 현황 확인하기

1. 실행이 **완료된 경우**에만 표시됩니다.
2. **"테이블별 처리 현황"** 테이블에 각 테이블의 처리 통계가 표시됩니다.
3. 테이블 컬럼:
   - **구분** — 테이블 유형 배지 (SOURCE: 파랑, IF: 노랑, TARGET: 초록)
   - **테이블명** — 모노스페이스 코드 포맷
   - **건수** — 총 처리 건수
   - **성공** — 성공 건수 (초록색)
   - **실패** — 실패 건수 (1건 이상이면 빨간색 볼드)
   - **상세** — "상세" 버튼 (클릭 시 해당 테이블의 데이터 뷰어 열기)
4. 테이블 순서는 **SOURCE → IF → TARGET** 순으로 정렬됩니다.
5. 실패 건수가 있는 행은 **연한 빨간 배경**으로 강조됩니다.

## 4.5 테이블 상세 조회 (데이터 브라우저)

처리 현황에서 "상세" 버튼을 클릭하면 해당 테이블의 데이터를 조회하는 영역이 열립니다.

### 4.5.1 선택된 테이블 표시

1. 상단에 **테이블 유형 배지 + 테이블명**이 표시됩니다.
2. 행을 클릭하면 추적할 수 있다는 **안내 문구**가 표시됩니다: "(행을 클릭하면 처리 상태를 추적할 수 있습니다)"
3. **"닫기" 버튼**으로 데이터 뷰어를 닫을 수 있습니다.

### 4.5.2 검색/필터 사용하기

1. **검색 컬럼** 드롭다운에서 검색 대상을 선택합니다 (기본: "전체 컬럼").
2. **검색어 입력란**에 값을 입력하고 Enter 또는 **"검색" 버튼**을 클릭합니다.
3. IF 또는 TARGET 테이블인 경우, **상태 필터** 드롭다운이 추가로 표시됩니다:
   - 전체 상태 / 성공(SUCCESS) / 실패(FAILED)

### 4.5.3 Fallback 경고 배너

1. execution_id가 후속 실행에 의해 **덮어씌워진 경우** 노란색 경고 배너가 표시됩니다.
2. 메시지 예시:
   - IF/TARGET: "이 실행의 execution_id가 덮어씌워져 정확한 데이터를 표시하지 못할 수 있습니다."
   - SOURCE: "Source 테이블은 현재 전체 데이터를 표시합니다."

### 4.5.4 데이터 테이블 사용하기

1. 상단에 **"총 N건"** 건수가 표시됩니다.
2. **컬럼 헤더를 클릭**하면 해당 컬럼 기준으로 정렬됩니다.
   - 정렬 중인 컬럼은 배경 하이라이트 + ▲/▼ 표시
3. 셀 값 자동 포맷팅:
   - `null` → "-"
   - `boolean` → "Y" / "N"
   - 날짜/ISO datetime → 한국 날짜 포맷
   - 긴 값은 최대 300px에서 말줄임표(...)로 잘림
4. source_refs 컬럼은 **Zone별 색상 배지**로 표시됩니다.
   - 배지를 **클릭**하면 팝업이 열려 데이터소스명, 테이블명, PK 값을 확인할 수 있습니다.
   - Zone별 색상: EXTERNAL(노랑), DMZ(파랑), INTERNAL_COMMON(초록), INTERNAL_SERVICE(보라)
5. 페이지당 **5건** 표시, 하단 페이징: [처음] [이전] "N / M" [다음] [마지막]

### 4.5.5 데이터 추적(Trace) 사용하기

1. 데이터 테이블에서 **행(Row)을 클릭**합니다.
2. 클릭한 행 좌측에 **▶ 화살표**가 회전하며, 행 아래에 **Trace 패널**이 인라인으로 펼쳐집니다.
3. 같은 행을 다시 클릭하면 패널이 접힙니다.

**정방향 추적 (SOURCE 행 클릭 → Target 조회):**

1. SOURCE 테이블의 행을 클릭합니다.
2. Trace 패널에 다음이 표시됩니다:
   - **PK 정보** — "pkColumn: pkValue" (예: "id: 12345")
   - **추적 상태 배지**:
     - 동기화 완료 (초록) — SYNCED, FULLY_SYNCED
     - 미동기화 — 그 외
   - **Target 테이블** — 테이블명, 건수, 실제 레코드 데이터 (초록 헤더)

**역방향 추적 (IF/TARGET 행 클릭 → Source 조회):**

1. IF 또는 TARGET 테이블의 행을 클릭합니다.
2. Trace 패널에 다음이 표시됩니다:
   - **source_refs의 PK 값** 표시
   - **추적 상태 배지**:
     - 원본 찾음 (초록) — FOUND, FOUND_IN_IF
     - 원본 없음 (빨강) — SOURCE_NOT_FOUND
   - **Source 테이블** — 테이블명, 건수, 실제 레코드 데이터 (파랑 헤더)

**추적 흐름 예시:**
```
[SOURCE] sec_jewon_view (id: 493)
    ↓  정방향 추적
[IF_RSV] if_rsv_sec_jewon (source_refs: ["E:8:26:493"], status: SUCCESS)
    ↓
[TARGET] sec_jewon (id: 456, status: PENDING)

[TARGET] sec_jewon (source_refs: ["E:8:26:493"])
    ↓  역방향 추적
[SOURCE] sec_jewon_view (id: 493)  ← 원본 찾음
```

3. Trace 실패 시 빨간색으로 **오류 메시지**가 표시됩니다.
4. PK는 JDBC 메타데이터 기반으로 **자동 감지**됩니다 (단일 PK, 복합 PK 모두 지원).

---

# 5. SCR-04 DB 관리

**경로:** `/datasources`

데이터 동기화에 사용할 데이터베이스를 등록하고 관리하는 화면입니다.

## 5.1 데이터소스 목록 확인하기

1. 화면에 등록된 **데이터소스 목록 테이블**이 표시됩니다.
2. 각 행에 표시되는 정보: ID, 이름, DB 타입(배지), Zone(배지), Host, Port, Database명, 상태(활성/비활성)
3. 각 행의 **액션 버튼**:
   - (1) **테이블 관리** — 동기화 대상 테이블 등록/관리 모달 열기
   - (2) **연결 테스트** — DB 연결 상태 확인
   - (3) **수정** — 데이터소스 정보 수정 폼 열기
   - (4) **삭제** — 데이터소스 삭제 (확인 다이얼로그)

## 5.2 새 데이터소스 등록하기

1. 목록 상단의 **"등록" 버튼**을 클릭합니다.
2. 등록 폼에서 다음 항목을 입력합니다:
   - (1) **Datasource ID** (필수) — 고유 식별자 (예: dmz_ent1_ds)
   - (2) **이름** (필수) — 표시용 이름
   - (3) **DB 타입** (필수) — 드롭다운에서 선택: PostgreSQL / MySQL / MariaDB / Oracle / MSSQL
     - 선택 시 **Port가 기본값으로 자동 설정**됩니다 (PostgreSQL: 5432, MySQL: 3306 등)
   - (4) **Host** (필수) — DB 호스트 주소
   - (5) **Port** (필수) — DB 포트 (자동 설정값 수정 가능)
   - (6) **Database** (필수) — 데이터베이스명
   - (7) **사용자명** (필수) — DB 접속 계정
   - (8) **비밀번호** (필수) — DB 접속 비밀번호
   - (9) **설명** (선택) — 메모
   - (10) **네트워크 Zone** (선택) — Orchestrator 직접 / EXTERNAL / DMZ / INTERNAL_COMMON / INTERNAL_SERVICE
3. **"연결 테스트" 버튼**을 클릭하여 DB 연결을 확인합니다.
   - 성공 시: 응답 시간과 함께 성공 메시지 표시
   - 실패 시: 오류 메시지 표시
4. 연결 테스트를 **통과해야** "저장" 버튼이 활성화됩니다.
5. **"저장" 버튼**을 클릭하여 등록을 완료합니다.

## 5.3 데이터소스 수정하기

1. 목록에서 수정할 데이터소스의 **"수정" 버튼**을 클릭합니다.
2. 등록과 동일한 폼이 표시됩니다. 단, 다음 차이점이 있습니다:
   - **Datasource ID는 읽기전용**입니다.
   - **사용자명/비밀번호는 선택 입력**입니다 (빈칸으로 두면 기존값이 유지됩니다).
3. 연결 관련 필드(Host, Port, Database 등)를 변경한 경우 **연결 테스트를 다시 수행**해야 저장할 수 있습니다.

## 5.4 테이블 관리하기

1. 목록에서 **"테이블 관리" 버튼**을 클릭하면 모달이 열립니다.

2. **테이블 추가하기** (모달 좌측):
   - (1) 검색 입력란에 테이블명을 입력합니다 (빈 입력 시 전체 조회).
   - (2) 검색 결과 드롭다운에서 **테이블을 선택**합니다 (이미 등록된 테이블은 상태 표시).
   - (3) 선택한 테이블의 **컬럼 목록**이 체크박스로 표시됩니다.
     - 각 컬럼: 컬럼명, 데이터 타입, PK 여부, NOT NULL 여부 확인 가능
     - **"전체 선택/해제" 버튼**으로 일괄 토글 가능
   - (4) 원하는 컬럼을 체크한 후 **"등록" 버튼**을 클릭합니다.

3. **등록된 테이블 관리하기** (모달 우측):
   - 등록된 테이블 목록이 표시됩니다.
   - 각 테이블 옆의 **"삭제" 버튼**으로 등록을 해제할 수 있습니다.
   - 각 테이블의 컬럼이 배지 형태로 나열되며, **PK 컬럼은 색상이 다르게** 표시됩니다.

---

# 6. SCR-05 Agent 목록

**경로:** `/agents`

등록된 Agent를 조회하고 관리하는 화면입니다.

## 6.1 Agent 목록 확인하기

1. Agent는 **타입별(RCV, LOADER, SND) 접이식 그룹**으로 분류되어 표시됩니다.
   ```
   ▼ RCV (10)
     rcv-bojo-01 | 대전시 | EXTERNAL | ONLINE | …
     rcv-bojo-02 | 바이텍 | EXTERNAL | ONLINE | …
     ...
   ▼ LOADER (1)
     loader-bojo | Loader | DMZ | ONLINE | …
   ▼ SND (1)
     snd-bojo | SND | DMZ | ONLINE | …
   ```
2. 각 행에 표시되는 정보: Agent Code, 이름, Zone(배지), 상태(배지), 마지막 실행 시간, 마지막 실행 결과
3. **Agent Code를 클릭**하면 Agent 상세(SCR-07)로 이동합니다.

## 6.2 Agent 상태 필터링하기

1. 목록 상단의 **상태 필터**를 사용하여 Online / Offline Agent만 표시할 수 있습니다.

## 6.3 Agent 관리 액션

1. 각 Agent 행의 액션 버튼:
   - (1) **상태 확인** — Health Check 실행 (ONLINE / OFFLINE 확인)
   - (2) **상세** — Agent 상세 페이지(SCR-07)로 이동
   - (3) **삭제** — Agent 삭제 (확인 다이얼로그)

## 6.4 새 Agent 등록하기

1. 목록 상단의 **"Agent 등록" 버튼**을 클릭하면 등록 화면(SCR-06)으로 이동합니다.

---

# 7. SCR-06 Agent 등록

**경로:** `/agents` (멀티스텝 폼)

6단계를 거쳐 새로운 Agent를 등록합니다.

## Step 1: Agent 탐색

1. **Endpoint URL**을 입력합니다 (예: `http://localhost:8082`).
2. **"탐색" 버튼**을 클릭합니다.
3. 해당 엔드포인트에서 발견된 Agent 목록이 표시됩니다.
   - 각 Agent의 코드, 타입, 이미 등록 여부가 표시됩니다.
   - 탐색된 Zone 정보도 함께 표시됩니다.

## Step 2: Agent 선택

1. 탐색 결과에서 **등록할 Agent를 선택**합니다.
2. Agent Code와 Agent Type이 **자동 입력**(읽기전용)됩니다.

## Step 3: 기본 설정

1. 다음 항목을 입력합니다:
   - **Agent Code** — 읽기전용 (Step 2에서 결정됨)
   - **Agent Type** — 읽기전용
   - **이름** (필수) — 사용자 지정 표시명
   - **Datasource Tag** (선택)
   - **설명** (선택)

## Step 4: Datasource & 테이블 선택

1. **Source Datasource** 드롭다운에서 소스 DB를 선택합니다.
2. Source DB의 **테이블 목록**에서 동기화 대상 테이블을 선택합니다 (멀티 선택 가능, 각 테이블의 컬럼 수 표시).
3. **Target Datasource** 드롭다운에서 타겟 DB를 선택합니다.
4. Target DB의 **테이블 목록**에서 대상 테이블을 선택합니다.

## Step 5: 실행 파라미터

1. **"Agent에서 가져오기" 버튼**을 클릭하여 Agent가 지원하는 실행 파라미터를 조회합니다.
2. 조회된 파라미터 목록에서 각 파라미터의 정보를 확인합니다: paramId, label, dataType, description, defaultValue
3. 각 파라미터의 **활성화 체크박스**를 토글하여 사용 여부를 설정합니다.

## Step 6: 스케줄 설정

1. **Cron 표현식**을 입력합니다 (6필드: 초 분 시 일 월 요일).
   - 예: `0 0/30 * * * *` → 매 30분마다
2. **"즉시 활성화" 체크박스**를 선택하면 등록 직후 스케줄이 활성 상태가 됩니다.
3. **"등록" 버튼**을 클릭하여 Agent 등록을 완료합니다.

---

# 8. SCR-07 Agent 상세

**경로:** `/agents/[id]` | **폴링:** 실행 중일 때 30초 간격 상태 확인

특정 Agent의 상세 정보를 확인하고, 실행/설정을 관리하는 화면입니다.

## 8.1 상단 버튼 사용하기

1. **"뒤로가기"** — Agent 목록(SCR-05)으로 이동
2. **"상태 확인"** — Agent 서버와의 연결 상태를 확인합니다 (Health Check)
3. **"테스트 데이터 생성"** — 개발/테스트용 데이터를 삽입합니다
4. **"테스트 데이터 삭제"** — 삽입한 테스트 데이터를 정리합니다
5. **"실행"** — Agent가 ONLINE일 때만 활성화됩니다. 클릭하면 기본 옵션으로 즉시 동기화를 실행합니다.
6. **"실행 옵션 ▾"** — 클릭하면 아래에 상세 실행 옵션 패널이 펼쳐집니다.
7. **"삭제"** — Agent를 삭제합니다 (확인 다이얼로그).

## 8.2 실행 옵션을 설정하여 실행하기

"실행 옵션 ▾" 버튼을 클릭하면 상세 실행 옵션 패널이 펼쳐집니다.

### 8.2.1 실행 모드 선택하기

1. Agent에 2개 이상의 실행 모드가 있는 경우에만 표시됩니다.
2. **라디오 버튼**으로 원하는 모드를 선택합니다.
3. 각 모드에 이름, 설명이 표시되며, 기본 모드에는 **(기본)** 마커가 붙습니다.

### 8.2.2 시간 범위 지정하기

1. 특정 기간의 데이터를 **재동기화**하고 싶을 때 사용합니다.
2. **시작 시간**과 **종료 시간**을 날짜-시간 입력으로 지정합니다.
3. 지정한 기간의 데이터가 전체 재처리됩니다 (UPSERT 방식, link_status=RESYNC).

### 8.2.3 Step 선택하기

1. Agent에 선택 가능한 Step이 있는 경우에만 표시됩니다.
2. 각 Step의 **체크박스**를 토글하여 실행할 Step을 선택합니다.
   - Step명, 설명, 실행 순서(displayOrder)가 표시됩니다.
   - 기본 선택은 enabledByDefault 플래그를 따릅니다.
3. **"전체 선택/해제" 버튼**으로 일괄 토글할 수 있습니다.

### 8.2.4 데이터 필터 설정하기

1. 활성화된 실행 파라미터가 있는 경우에만 표시됩니다.
2. 각 파라미터의 **체크박스**를 선택하고, **값을 입력**합니다.
3. 파라미터의 라벨, 설명, 데이터 타입이 함께 표시됩니다.

### 8.2.5 실행하기

1. 옵션 설정이 완료되면 **"실행 (옵션 적용)" 버튼**을 클릭합니다.
2. **"취소" 버튼**으로 옵션 패널을 닫을 수 있습니다.

## 8.3 기본정보 탭 사용하기

화면 중앙의 탭에서 **[기본정보]** 탭을 선택합니다.

### 8.3.1 Agent 정보 확인/수정하기

1. Agent의 기본 정보가 표시됩니다: Agent Code, 이름, 타입(배지), Zone(배지), Endpoint URL, 활성 상태, 생성일, 설명
2. **"수정" 버튼**을 클릭하면 편집 모드로 전환됩니다.
3. 수정 가능 항목: 이름, Agent 타입(드롭다운), Zone(드롭다운), Endpoint URL, 설명
4. **활성화/비활성화 토글**로 Agent의 활성 상태를 변경할 수 있습니다.
5. 수정 완료 후 **"저장" 버튼** 클릭, 또는 **"취소" 버튼**으로 원래 상태로 되돌립니다.

### 8.3.2 Datasource & 테이블 정보 확인하기

1. Agent에 Datasource가 설정된 경우 표시됩니다.
2. **Source 영역**과 **Target 영역**이 좌우로 나뉘어 표시됩니다.
   ```
   ┌─────────────────────────────┬─────────────────────────────┐
   │ Source                      │ Target                      │
   │ dmz_ent1_ds (PostgreSQL)    │ dmz_target_ds (PostgreSQL)  │
   │ 192.168.1.10:5432           │ 192.168.1.20:5432           │
   │                             │                             │
   │ sec_jewon_view              │ if_rsv_sec_jewon            │
   │  → obsv_code, obsv_name ..  │  → obsv_code, obsv_name ..  │
   └─────────────────────────────┴─────────────────────────────┘
   ```
3. 수정 모드에서는 Datasource와 테이블을 변경할 수 있습니다.

### 8.3.3 실행 파라미터 확인하기

1. Agent의 실행 파라미터 테이블이 표시됩니다.
2. 각 파라미터: 활성 상태(✅/⬜), 파라미터명(label + paramId), 데이터 타입, 기본값, 설명
3. **"Agent에서 가져오기" 버튼**을 클릭하면 Agent API를 호출하여 최신 파라미터 정보를 갱신합니다.

### 8.3.4 스케줄 관리하기

1. 등록된 **스케줄 목록**이 표시됩니다. 각 스케줄에 다음 정보가 표시됩니다:
   - Cron 표현식 + **한글 해석** (예: `0 0/30 * * * *` → "매 30분마다")
   - 필터 정보 (스케줄에 실행 필터가 설정된 경우)
   - 활성/비활성 상태

2. 스케줄별 액션:
   - **활성화/비활성화 토글** — 스케줄을 삭제하지 않고 일시 중지/재개
   - **"수정" 버튼** — Cron 표현식 및 필터 변경
   - **"삭제" 버튼** — 스케줄 삭제

3. **스케줄 추가하기:**
   - (1) 목록 아래의 **"스케줄 추가" 영역**을 펼칩니다.
   - (2) **Cron 표현식**을 입력합니다 — 입력 즉시 한글 해석이 표시됩니다.
   - (3) **"즉시 활성화" 체크박스**로 등록 직후 활성화 여부를 선택합니다.
   - (4) 실행 파라미터가 있는 경우, **실행 필터를 설정**할 수 있습니다.
   - (5) **"추가" 버튼**을 클릭하여 스케줄을 등록합니다.

4. **Cron 표현식 한글 해석 예시:**
   - `0 0 * * * *` → "매시 정각"
   - `0 0/30 * * * *` → "매 30분마다"
   - `0 0 9 * * *` → "매일 09:00"
   - `0 0 9-18 * * MON-FRI` → "평일 09시~18시 매시"
   - `0 0 0 L * *` → "매월 마지막 날 자정"

## 8.4 모니터링 탭 사용하기

화면 중앙의 탭에서 **[모니터링]** 탭을 선택합니다.

1. Agent가 **RUNNING 상태**일 때 실시간 데이터가 표시됩니다 (3초 간격 폴링).
2. 표시되는 정보:
   - **실행 상태** — RUNNING 애니메이션, 완료 시 결과 배지
   - **읽기/쓰기/스킵 건수** — 실시간 갱신
   - **소요 시간** — 경과 시간
3. 실행이 **완료되면** 성공/실패 스타일로 완료 요약이 표시됩니다.
4. **"실행 상세 보기" 링크**를 클릭하면 실행 상세(SCR-03)로 이동합니다.

## 8.5 실행이력 탭 사용하기

화면 중앙의 탭에서 **[실행이력]** 탭을 선택합니다.

1. 해당 Agent의 **과거 실행 기록 테이블**이 표시됩니다.
2. 각 행에 표시되는 정보: 상태, 읽기/쓰기/스킵, 소요시간, 트리거, 시작 시간
3. **"상세" 버튼**을 클릭하면 해당 실행의 상세 페이지(SCR-03)로 이동합니다.

---

# 9. SCR-08 Agent 체인

**경로:** `/chains`

여러 Agent를 순차적으로 실행하도록 묶어 관리하는 화면입니다.

## 9.1 체인 목록 확인하기

1. 등록된 체인이 **카드 형태**로 표시됩니다.
2. 각 카드에 표시되는 정보:
   - 체인 이름 + 설명
   - 실행 유형: 순차 실행(SEQUENTIAL) / 개별 실행(INDIVIDUAL)
   - 상태 배지: ONLINE / OFFLINE
   - **체인 흐름 시각화**: Agent1 → Agent2 → Agent3 (각 노드에 Agent명, Zone 배지, 순번)
3. 카드별 액션 버튼:
   - **"실행" 버튼** — 확인 다이얼로그 후 체인 전체 실행
   - **"삭제" 버튼** — 확인 다이얼로그 후 체인 삭제

## 9.2 새 체인 등록하기

### 9.2.1 기본 설정

1. **Chain ID** (필수) — 고유 식별자를 입력합니다.
2. **체인 이름** (필수) — 표시용 이름을 입력합니다.
3. **실행 유형** (필수) — 드롭다운에서 선택합니다:
   - **SEQUENTIAL (순차 실행)** — 앞의 Agent가 완료되어야 다음 Agent 실행
   - **INDIVIDUAL (개별 실행)** — 각 Agent를 독립적으로 실행
4. **설명** (선택) — 체인에 대한 메모를 입력합니다.

### 9.2.2 Agent 선택

1. 등록된 Agent 목록에서 체인에 포함할 Agent를 **체크박스로 선택**합니다.
2. 각 Agent의 이름, 코드, Zone 배지가 표시됩니다.
3. **최소 2개 이상** 선택해야 합니다.

### 9.2.3 실행 순서 지정

1. 선택한 Agent가 **순번과 함께** 목록으로 표시됩니다.
2. 각 Agent 옆의 **위/아래 화살표 버튼**으로 순서를 변경합니다.
3. 일반적인 체인 구성 예시:
   ```
   1. rcv-bojo-01 (RCV, EXTERNAL) → 외부 데이터 수집
   2. loader-bojo (LOADER, DMZ)   → IF → Target 적재
   3. snd-bojo (SND, DMZ)         → Target → IF_SND 전송 준비
   ```
4. **"등록" 버튼**을 클릭하여 체인 등록을 완료합니다.

---

# 10. SCR-09 API 관리 (미개발)

**경로:** `/apis`
**상태:** 미개발 (ARCHITECTURE.md 섹션 2)

외부 시스템과의 데이터 연동 API를 관리하는 화면입니다. **수신 API**(외부 → 우리 DB에 저장)와 **제공 API**(우리 DB → 외부에 데이터 제공) 두 가지로 운용됩니다.

## 10.1 API 목록 확인하기

1. 화면 상단에 **탭**으로 API 유형이 구분됩니다:
   - **[수신 API]** — 외부 시스템이 우리 DB에 데이터를 저장하는 API
   - **[제공 API]** — 우리 DB의 데이터를 외부 시스템에 제공하는 API
2. 각 탭의 API 목록 테이블에 표시되는 정보:
   - **API 이름** — API 식별 명칭
   - **대상 테이블** — 데이터가 저장/조회되는 테이블명
   - **공개 컬럼 수** — 해당 API에서 허용하는 컬럼 수 / 전체 컬럼 수
   - **상태** — 활성 / 비활성
   - **마지막 호출** — 마지막 호출 시각

## 10.2 수신 API 등록하기

외부 시스템이 우리 DB에 데이터를 저장할 수 있도록 API를 등록합니다.

1. [수신 API] 탭에서 **"등록" 버튼**을 클릭합니다.
2. 등록 폼에서 다음 항목을 입력합니다:
   - (1) **API 이름** (필수) — 식별 명칭 (예: "대전시 관측데이터 수신")
   - (2) **대상 데이터소스** (필수) — 드롭다운에서 데이터가 저장될 DB를 선택합니다.
   - (3) **대상 테이블** (필수) — 선택한 DB에서 저장 대상 테이블을 선택합니다.
   - (4) **저장 허용 컬럼 설정** — 테이블의 전체 컬럼 목록이 체크박스로 표시됩니다.
     - 외부에서 값을 넣을 수 있는 컬럼만 **체크**합니다.
     - 각 컬럼의 컬럼명, 데이터 타입, PK 여부, NOT NULL 여부를 확인할 수 있습니다.
     - **"전체 선택/해제" 버튼**으로 일괄 토글할 수 있습니다.
   - (5) **설명** (선택)
3. **"저장" 버튼**을 클릭하여 등록을 완료합니다.

## 10.3 제공 API 등록하기

우리 DB의 데이터를 외부 시스템에 제공하는 API를 등록합니다.

1. [제공 API] 탭에서 **"등록" 버튼**을 클릭합니다.
2. 등록 폼에서 다음 항목을 입력합니다:
   - (1) **API 이름** (필수) — 식별 명칭 (예: "관측소 제원 목록 제공")
   - (2) **대상 데이터소스** (필수) — 드롭다운에서 데이터를 조회할 DB를 선택합니다.
   - (3) **대상 테이블** (필수) — 선택한 DB에서 조회 대상 테이블을 선택합니다.
   - (4) **제공 컬럼 설정** — 테이블의 전체 컬럼 목록이 체크박스로 표시됩니다.
     - 외부에 공개할 컬럼만 **체크**합니다.
     - 민감 컬럼(비밀번호, 내부 ID 등)은 반드시 제외해야 합니다.
     - 각 컬럼의 컬럼명, 데이터 타입을 확인할 수 있습니다.
     - **"전체 선택/해제" 버튼**으로 일괄 토글할 수 있습니다.
   - (5) **설명** (선택)
3. **"저장" 버튼**을 클릭하여 등록을 완료합니다.

## 10.4 API 상세에서 컬럼 매핑 관리하기

1. API 목록에서 **API 이름을 클릭**하면 상세 화면이 표시됩니다.
2. **대상 테이블 정보**가 표시됩니다: 데이터소스명, 테이블명, DB 타입
3. **컬럼 매핑 테이블**에서 현재 설정된 컬럼을 확인할 수 있습니다:
   - 컬럼명, 데이터 타입, PK 여부, 필수 여부(NOT NULL), 활성/비활성 상태
4. **"수정" 버튼**을 클릭하면 컬럼 체크를 변경할 수 있습니다.

## 10.5 API 호출 이력 확인하기

1. API 상세 화면 하단에 **호출 이력 테이블**이 표시됩니다.
2. 각 행에 표시되는 정보: 호출 시간, 호출자, 응답 코드, 처리 건수, 응답 시간
3. 행을 **클릭**하면 요청/응답 상세를 펼쳐서 확인할 수 있습니다.

---

# 11. SCR-10 프로시저 관리 (미개발)

**경로:** `/procedures`
**상태:** 미개발 (ARCHITECTURE.md 섹션 3)

개발자가 미리 작성한 DB 프로시저(Stored Procedure) SQL문을 암호화하여 DB에 보관하고, 사용자에게 **조회 전용**으로 제공하는 화면입니다. 사용자가 프로시저를 등록하거나 수정하는 기능은 없습니다.

## 11.1 프로시저 목록 확인하기

1. 개발자가 사전 등록한 **프로시저 목록 테이블**이 표시됩니다.
2. 각 행에 표시되는 정보:
   - **프로시저 이름** — 관리용 명칭
   - **대상 데이터소스** — 프로시저가 사용되는 DB
   - **타입** — PROCEDURE / FUNCTION
   - **등록일** — 등록 시각
   - **설명** — 프로시저의 용도 요약

## 11.2 프로시저 SQL문 조회하기

1. 목록에서 **프로시저 이름을 클릭**하면 상세 화면이 표시됩니다.
2. 상세 화면에서 확인 가능한 정보:
   - 프로시저 이름, 대상 데이터소스, 타입, 등록일
   - **프로시저 SQL문** — DB에 암호화 저장된 SQL을 복호화하여 코드 뷰어(모노스페이스 폰트, 줄번호, 구문 하이라이팅) 형태로 표시
   - 설명
3. **"복사" 버튼**을 클릭하면 SQL문이 클립보드에 복사됩니다.

---

# 부록. 프론트엔드 파일별 연관 DB 테이블

프론트엔드 파일 단위로 API 호출과 관여하는 DB 테이블을 정리합니다.

> **DB 구분:**
> - **Orch DB** — Orchestrator PostgreSQL (`orchestrator` 데이터베이스, 14개 테이블)
> - **Agent DB** — Agent PostgreSQL (`dev` 데이터베이스, `execution`·`sync_log` 테이블) — Proxy Agent 경유
> - **원격 DB** — Source/Target 외부 DB (PG/MySQL/Oracle 등) — Proxy Agent 경유

---

## F.1 `app/page.tsx` — 대시보드

**자동갱신:** 10초 간격 `setInterval`

### 페이지 로드 (3개 API 병렬 호출)

| API | Orch DB (READ) | 비고 |
|-----|----------------|------|
| `GET /api/executions/dashboard/stats` | `agent`, `execution_history` | COUNT 쿼리 (총 Agent, 온라인, 오프라인, 오늘 실행/실패, 실행 중) |
| `GET /api/executions/status` | `agent` | 전체 Agent 상태 + 마지막 실행 정보 |
| `GET /api/executions/history` | `execution_history` | 최근 실행 50건 |

---

## F.2 `app/agents/page.tsx` — Agent 목록 + 등록 폼

### 페이지 로드

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/agents` | `agent` |

### Agent 상태확인 버튼

| API | Orch DB | Agent 원격 |
|-----|---------|------------|
| `POST /api/agents/{id}/health-check` | READ: `agent` / WRITE: `agent` (status 업데이트) | Agent `/health` HTTP 호출 |

### Agent 삭제 버튼

| API | Orch DB (WRITE) |
|-----|-----------------|
| `DELETE /api/agents/{id}` | `agent` CASCADE → `agent_table`, `agent_execution_param`, `agent_execution_mode`, `agent_step_definition`, `schedule`, `agent_chain_member` |

### 등록 폼 — AgentForm (인라인 컴포넌트)

**Step 1: Agent 탐색**

| API | Orch DB (READ) | Agent 원격 |
|-----|----------------|------------|
| `GET /api/agents/discover?endpointUrl=...` | `agent` (중복 체크) | Agent `/health` HTTP 호출 |

**Step 4: Datasource & 테이블 선택**

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/datasources/simple` | `datasource` (active만) |
| `GET /api/datasources/{id}/tables` | `datasource_table` |

**Step 5: 실행 파라미터 가져오기**

| 호출 대상 | 비고 |
|-----------|------|
| `GET {endpointUrl}/api/pipeline/execution-params` | Agent 프로세스 직접 호출 (Orchestrator 미경유) |

**등록 제출**

| API | Orch DB (WRITE) |
|-----|-----------------|
| `POST /api/agents` | `agent`, `agent_table`, `agent_execution_param` |
| `POST /api/schedules` | `schedule` |

---

## F.3 `app/agents/[id]/page.tsx` — Agent 상세

### 페이지 로드 (3개 API 병렬 호출)

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/agents/{id}` | `agent` (executionParams, executionModes, stepDefinitions 포함) |
| `GET /api/schedules` | `schedule` (agent JOIN, 클라이언트에서 agentId 필터) |
| `GET /api/executions/history/agent/{id}` | `agent`, `execution_history` |

### RUNNING 상태 자동 폴링 (30초 간격)

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/agents/{id}` | `agent` |

### 상태확인 버튼

| API | Orch DB | Agent 원격 |
|-----|---------|------------|
| `POST /api/agents/{id}/health-check` | READ: `agent` / WRITE: `agent` | Agent `/health` HTTP 호출 |

### 실행 버튼 (즉시 실행 / 옵션 적용 실행)

| API | Orch DB | Agent 원격 |
|-----|---------|------------|
| `POST /api/executions/{id}/run` | READ: `agent`, `datasource`, `datasource_table`, `zone_config` / WRITE: `agent` (status=RUNNING), `datasource_table`·`agent_table` (자동발견 시) | Agent `/api/pipeline/execute` HTTP 호출 |

### 테스트 데이터 생성 버튼

| API | Orch DB (READ) | Agent 원격 |
|-----|----------------|------------|
| `POST /api/agents/{id}/generate-test-data` | `agent`, `datasource` | Agent `/api/test/generate-data` → 외부 Source DB에 INSERT |

### 테스트 데이터 삭제 버튼

| API | Orch DB (READ) | Agent 원격 |
|-----|----------------|------------|
| `DELETE /api/agents/{id}/clear-test-data` | `agent`, `datasource` | Agent `/api/test/clear-data` → 외부 Source DB에서 DELETE |

### Agent 삭제 버튼

| API | Orch DB (WRITE) |
|-----|-----------------|
| `DELETE /api/agents/{id}` | `agent` CASCADE → `agent_table`, `agent_execution_param`, `agent_execution_mode`, `agent_step_definition`, `schedule`, `agent_chain_member` |

---

## F.4 `components/agent/InfoTab.tsx` — 기본정보 탭

`app/agents/[id]/page.tsx`의 하위 컴포넌트. Props로 `agent`, `schedules` 수신 + 자체 API 호출.

### 마운트 (Datasource 정보 로드)

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/datasources/{sourceDatasourceId}` | `datasource` |
| `GET /api/datasources/{sourceDatasourceId}/tables` | `datasource_table` |
| `GET /api/datasources/{targetDatasourceId}` | `datasource` |
| `GET /api/datasources/{targetDatasourceId}/tables` | `datasource_table` |

### 수정 모드 진입

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/datasources/simple` | `datasource` |

### Datasource 변경 (수정 모드)

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/datasources/{id}/tables` | `datasource_table` |

### Agent 정보 저장

| API | Orch DB |
|-----|---------|
| `PUT /api/agents/{id}` | READ: `agent` / WRITE: `agent`, `agent_table` (clear+재삽입), `agent_execution_param` (clear+재삽입) |

### 실행 파라미터 갱신

| API | Orch DB | Agent 원격 |
|-----|---------|------------|
| `POST /api/agents/{id}/refresh-execution-params` | READ: `agent` / WRITE: `agent_execution_param` (DELETE ALL + INSERT) | Agent `/api/pipeline/execution-params` HTTP 호출 |

### 스케줄 등록

| API | Orch DB |
|-----|---------|
| `POST /api/schedules` | READ: `agent` / WRITE: `schedule` |

### 스케줄 수정

| API | Orch DB (WRITE) |
|-----|-----------------|
| `PUT /api/schedules/{id}` | `schedule` |

### 스케줄 활성화/비활성화

| API | Orch DB (WRITE) |
|-----|-----------------|
| `PUT /api/schedules/{id}/toggle` | `schedule` (is_enabled 반전) |

### 스케줄 삭제

| API | Orch DB (WRITE) |
|-----|-----------------|
| `DELETE /api/schedules/{id}` | `schedule` |

---

## F.5 `components/agent/HistoryTab.tsx` — 실행이력 탭

Props로 실행이력 데이터를 수신하여 표시만 하는 순수 UI 컴포넌트. **자체 API 호출 없음.**

링크: `/executions/{executionId}` → 실행 상세 페이지로 이동.

---

## F.6 `components/agent/MonitorTab.tsx` — 모니터링 탭

> **참고:** 현재 `app/agents/[id]/page.tsx`에서 import하지 않는 **미사용 코드**입니다.

### (설계상) RUNNING 상태일 때 3초 간격 폴링

| API | Orch DB (READ) | Agent DB |
|-----|----------------|----------|
| `GET /api/executions/agent/{id}` | `agent`, `zone_config` | Proxy Agent 경유 → Agent DB `execution` 테이블 |

---

## F.7 `app/chains/page.tsx` — Agent 체인

### 페이지 로드 (2개 API 병렬 호출)

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/chains` | `agent_chain`, `agent_chain_member` (fetch join) |
| `GET /api/agents` | `agent` (체인 등록 폼의 Agent 선택 목록) |

### 체인 실행 버튼

| API | Orch DB | Agent 원격 |
|-----|---------|------------|
| `POST /api/chains/{id}/execute` | READ: `agent_chain`, `agent_chain_member`, `agent` / WRITE: `agent` (status=RUNNING), `execution_history` (INSERT) | 각 Agent `/api/pipeline/execute` 순차/병렬 호출 |

### 체인 삭제 버튼

| API | Orch DB (WRITE) |
|-----|-----------------|
| `DELETE /api/chains/{id}` | `agent_chain` CASCADE → `agent_chain_member` |

### 체인 등록 — ChainForm (인라인 컴포넌트)

| API | Orch DB |
|-----|---------|
| `POST /api/chains` | READ: `agent` / WRITE: `agent_chain`, `agent_chain_member` |

---

## F.8 `app/datasources/page.tsx` — DB 관리

### 페이지 로드

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/datasources` | `datasource` |

### 연결 테스트 버튼 (목록)

| API | Orch DB (READ) | 외부 |
|-----|----------------|------|
| `POST /api/datasources/{id}/test-connection` | `datasource`, `zone_config` | 직접 JDBC 또는 Proxy Agent 경유 외부 DB 연결 테스트 |

### 데이터소스 삭제 버튼

| API | Orch DB (WRITE) |
|-----|-----------------|
| `DELETE /api/datasources/{id}` | `datasource` (CASCADE → `datasource_table`, `datasource_column`) |

### 등록/수정 폼 — DatasourceForm (인라인 컴포넌트)

**연결 테스트 (폼 내)**

| API | Orch DB (READ) | 외부 |
|-----|----------------|------|
| `POST /api/datasources/test-connection` | `zone_config` (zone 지정 시) | 직접 JDBC 또는 Proxy Agent 경유 |

**등록 제출**

| API | Orch DB (WRITE) |
|-----|-----------------|
| `POST /api/datasources` | `datasource` |

**수정 제출**

| API | Orch DB |
|-----|---------|
| `PUT /api/datasources/{id}` | READ: `datasource` / WRITE: `datasource` |

### 테이블 관리 모달 — TableManagementModal (인라인 컴포넌트)

**모달 오픈**

| API | Orch DB (READ) | 외부 |
|-----|----------------|------|
| `GET /api/datasources/{id}/tables` | `datasource_table` | — |
| `GET /api/datasources/{id}/search-tables` | `datasource`, `zone_config` | 외부 DB `information_schema` / JDBC 메타데이터 |

**테이블 검색**

| API | Orch DB (READ) | 외부 |
|-----|----------------|------|
| `GET /api/datasources/{id}/search-tables?query=...` | `datasource`, `zone_config` | 외부 DB 테이블 메타데이터 |

**테이블 선택 (컬럼 조회)**

| API | Orch DB (READ) | 외부 |
|-----|----------------|------|
| `GET /api/datasources/{id}/search-columns?tableName=...` | `datasource`, `zone_config` | 외부 DB 컬럼 메타데이터 |

**테이블 등록**

| API | Orch DB |
|-----|---------|
| `POST /api/datasources/{id}/tables` | READ: `datasource_table` (중복 체크) / WRITE: `datasource_table`, `datasource_column` |

**테이블 삭제**

| API | Orch DB (WRITE) |
|-----|-----------------|
| `DELETE /api/datasources/{id}/tables/{tableId}` | `datasource_table` (CASCADE → `datasource_column`) |

---

## F.9 `app/executions/page.tsx` — 실행이력 목록

### 페이지 로드 + 필터/페이지 변경

| API | Orch DB (READ) |
|-----|----------------|
| `GET /api/agents` | `agent` (필터 드롭다운 옵션) |
| `GET /api/executions/history/paged?page=&size=&status=&agentCode=&agentType=&zone=&startDate=&endDate=&search=` | `agent` (zone 필터 시), `execution_history` (JPA Specification 동적 필터, 페이징) |

---

## F.10 `app/executions/[id]/page.tsx` — 실행 상세

### 페이지 로드

| API | Orch DB (READ) | Agent DB | 비고 |
|-----|----------------|----------|------|
| `GET /api/executions/{id}/detail` | `agent`, `zone_config` | Proxy → `execution` | 실행 상태, 건수, 시간 |
| `GET /api/executions/{id}/data/summary` | `agent`, `zone_config` | Proxy → `sync_log` | 성공/실패 통계 (RUNNING 아닐 때만) |
| `GET /api/executions/{id}/tables` | `agent`, `zone_config` | Proxy → `sync_log` | 테이블별 처리 현황 (RUNNING 아닐 때만) |
| `GET /api/datasources/sourceref-lookup` | `datasource`, `datasource_table` | — | source_refs 배지 표시용 룩업맵 |

### RUNNING 상태 자동 폴링 (5초 간격)

| API | Orch DB (READ) | Agent DB |
|-----|----------------|----------|
| `GET /api/executions/{id}/detail` | `agent`, `zone_config` | Proxy → `execution` |

### 데이터 브라우저 — SOURCE 탭 선택/검색/정렬/페이징

| API | Orch DB (READ) | Agent DB | 원격 DB |
|-----|----------------|----------|---------|
| `GET /api/executions/{id}/data/source?page=&size=&tableName=&sortColumn=&sortDirection=&search=&searchColumn=` | `agent`, `zone_config` | Proxy → `execution` (datasourceId) | Source 테이블 실제 레코드 조회 |

### 데이터 브라우저 — TARGET_IF 탭

| API | Orch DB (READ) | Agent DB | 원격 DB |
|-----|----------------|----------|---------|
| `GET /api/executions/{id}/data/target-if?...` | `agent`, `zone_config` | Proxy 경유 | IF 테이블 (`execution_id`, `link_status` 필터) |

### 데이터 브라우저 — TARGET 탭

| API | Orch DB (READ) | Agent DB | 원격 DB |
|-----|----------------|----------|---------|
| `GET /api/executions/{id}/data/target?...` | `agent`, `zone_config` | Proxy 경유 | Target 테이블 (`execution_id`, `link_status` 필터) |

### 정방향 추적 (SOURCE 행 클릭)

| API | Orch DB (READ) | Agent DB | 원격 DB |
|-----|----------------|----------|---------|
| `GET /api/executions/{id}/trace?pkValue=&pkColumn=&sourceTable=` | `agent`, `agent_table`, `datasource_table`, `zone_config` | Proxy → `sync_log`, `execution` | IF 테이블 (source_refs LIKE 검색, PK/UK fallback) |

### 역방향 추적 (TARGET/IF 행 클릭)

| API | Orch DB (READ) | Agent DB | 원격 DB |
|-----|----------------|----------|---------|
| `GET /api/executions/{id}/trace-source?sourceRefs=&sourceTable=` | `agent`, `datasource`, `datasource_table`, `zone_config` | Proxy → `sync_log` | Source 테이블 (source_refs에서 PK 추출 → 원본 조회) |

---

## F.11 백그라운드 프로세스 (화면 없음)

프론트엔드 파일은 없으나 시스템 동작에 관여하는 백엔드 스케줄러/콜백입니다.

### AgentHealthScheduler (30초 간격)

| Orch DB | Agent 원격 |
|---------|------------|
| READ: `agent` (is_active=true), `execution_history` (status=RUNNING) / WRITE: `agent` (status 업데이트), `execution_history` (RUNNING 잔류 → SUCCESS 자동 복구) | 각 Agent `/health` HTTP 호출 |

### CallbackController — Agent 실행 시작 콜백

| API | Orch DB |
|-----|---------|
| `POST /api/callback/started` | READ: `agent` / WRITE: `agent` (status=RUNNING), `execution_history` (INSERT) |

### CallbackController — Agent 실행 완료 콜백

| API | Orch DB |
|-----|---------|
| `POST /api/callback/finished` | READ: `agent`, `execution_history` / WRITE: `agent` (status=ONLINE), `execution_history` (UPDATE), `execution_step_history` (INSERT) |

### ScheduleExecutor (앱 시작 시)

| Orch DB (READ) |
|----------------|
| `schedule` (is_enabled=true, agent JOIN) |

---

## 참조: Orchestrator DB 전체 테이블 목록

| # | 테이블명 | 주요 용도 |
|---|----------|-----------|
| 1 | `agent` | Agent 등록 정보 (코드, 이름, 타입, Zone, Endpoint, 상태) |
| 2 | `agent_table` | Agent ↔ Datasource 테이블 매핑 (source/target) |
| 3 | `agent_execution_param` | Agent 실행 파라미터 정의 (label, dataType, defaultValue) |
| 4 | `agent_execution_mode` | Agent 실행 모드 정의 (이름, 설명, 기본 여부) |
| 5 | `agent_step_definition` | Agent Step 정의 (이름, 설명, 순서, 기본 활성 여부) |
| 6 | `agent_chain` | 체인 정의 (이름, 실행 유형) |
| 7 | `agent_chain_member` | 체인 구성원 (Agent, 실행 순서) |
| 8 | `datasource` | 데이터소스 등록 정보 (DB타입, Host, Port, 자격증명, Zone) |
| 9 | `datasource_table` | 데이터소스에 등록된 테이블 메타데이터 |
| 10 | `datasource_column` | 테이블의 컬럼 메타데이터 (타입, PK, NOT NULL) |
| 11 | `execution_history` | 실행 이력 (상태, 건수, 시작/종료 시간, 트리거) |
| 12 | `execution_step_history` | 실행 Step별 결과 (상태, 건수, 오류 메시지) |
| 13 | `schedule` | 스케줄 정의 (Cron 표현식, 활성 상태, 실행 필터) |
| 14 | `zone_config` | 네트워크 Zone별 Proxy Agent URL 설정 |

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-03-03 | 최초 작성 — 전체 화면정의서 (개발완료 8개 + 미개발 2개) |
| 2026-03-04 | 부록 1차 — 화면별 세부 동작별 테이블 매핑 |
| 2026-03-04 | 부록 전면 개편 — 프론트엔드 파일 기반 API→테이블 매핑 |
