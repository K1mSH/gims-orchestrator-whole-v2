# 테이블 이름 옆에 설명(alias) 표시

## 목적
프론트엔드에서 테이블 이름(sec_jewon_view, if_rsv_sec_obsvdata 등)만 보면
무엇인지 알기 어려우므로, 괄호 안에 한글 설명을 함께 표시.

## 작업 내용

### 1. DB 데이터 업데이트 — `datasource_table.table_alias` 채우기
현재 33개 등록 테이블 모두 `table_alias`가 NULL.
각 테이블에 한글 설명 설정:

| table_name | table_alias |
|------------|-------------|
| sec_jewon_view / SEC_JEWON_VIEW | 제원 뷰 |
| sec_obsvdata_view / SEC_OBSVDATA_VIEW | 관측데이터 뷰 |
| if_rsv_sec_jewon | 수신IF 제원 |
| if_rsv_sec_obsvdata | 수신IF 관측데이터 |
| if_snd_sec_jewon | 송신IF 제원 |
| if_snd_sec_obsvdata | 송신IF 관측데이터 |
| link_ngwis | 동기화 시점추적 |
| sec_jewon | 타겟 제원 |
| sec_obsvdata | 타겟 관측데이터 |
| tm_gd970001 | 내부 제원 |
| tm_gd970101 | 내부 관측결과 |
| pm_gd970201 | 내부 관측데이터 |
| tm_gd980002 | 내부 연계정보 |

### 2. 백엔드 — 테이블 alias 조회 API 추가
실행이력 페이지 등에서 tableName만 있고 alias가 없는 경우를 위해
전역 lookup API 추가:
- `GET /api/datasources/table-alias-map`
- 응답: `{ "sec_jewon_view": "제원 뷰", "if_rsv_sec_obsvdata": "수신IF 관측데이터", ... }`
- `DatasourceTableRepository.findAll()` → tableName→tableAlias Map 변환

### 3. 프론트엔드 — 모든 화면에 `(alias)` 표시

#### 헬퍼 함수 (공용)
```ts
// lib/utils.ts 또는 유사 위치
const displayTableName = (name: string, alias?: string | null) =>
  alias ? `${name} (${alias})` : name;
```

#### 수정 대상 (총 4개 파일, 10곳)

| 파일 | 위치 | 데이터 소스 |
|------|------|-------------|
| **InfoTab.tsx** | 소스 테이블 체크박스 (~668) | DatasourceTable.tableAlias 있음 |
| **InfoTab.tsx** | 타겟 테이블 체크박스 (~705) | 동일 |
| **InfoTab.tsx** | 소스 테이블 목록 (~742) | 동일 |
| **InfoTab.tsx** | 타겟 테이블 목록 (~773) | 동일 |
| **InfoTab.tsx** | Retention dropdown (~890) | 이미 적용됨 (변경 불필요) |
| **agents/page.tsx** | 소스 체크박스 (~630) | DatasourceTable.tableAlias 있음 |
| **agents/page.tsx** | 타겟 체크박스 (~667) | 동일 |
| **datasources/page.tsx** | 등록 테이블 목록 (~930) | DatasourceTable.tableAlias 있음 |
| **executions/[id]/page.tsx** | 테이블 통계 목록 (~455) | alias-map API로 lookup |
| **executions/[id]/page.tsx** | 선택 테이블 헤더 (~544) | 동일 |

## 영향 범위
- DB: orchestrator.datasource_table (table_alias UPDATE만)
- Backend: DatasourceController에 1개 엔드포인트 추가
- Frontend: 4개 파일 수정
