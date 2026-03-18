# API 관리 기능 (외부 API 정보 수집)

## 개요
- Agent 관리와 유사한 구조이나 데이터 소스가 **외부 API 호출**
- DMZ/내부망 각각에서 외부 API 호출 → 자체 DB에 적재
- DMZ 적재 데이터는 최종적으로 내부망으로 전달
- Orchestrator 시스템 하위 기능

## 메뉴 구조
```
API 관리 (새 탭)
├── 외부 API 정보 수집  ← 이번 개발
└── 내부 API 제공       ← 추후 개발
```

## 핵심 기능 흐름

### 1. API 등록
- 외부 API URL, HTTP Method, 헤더, 인증정보 등록
- 호출 파라미터 정의 (key-value)
- 파라미터 메타데이터 가공 (예: 날짜 → `20220102`, `2022-01-02` 등 포맷 변환)

### 2. API 테스트
- 등록된 API를 1회 호출하여 응답 확인
- 응답 형태(JSON/XML) 자동 감지
- 응답 필드 목록 추출 → 매핑 UI에 표시

### 3. 응답-DB 매핑
- API 응답 필드 → DB 테이블 컬럼 매핑 설정
- DB 대상은 기존 **DB 관리(Datasource)**에서 등록된 테이블/컬럼
- nested 경로 지원 여부 미정 (예: `data.items[].value`)

### 4. 스케줄 등록 + 자동 수집
- 기존 Schedule 시스템 재활용 (cron 기반)
- 주기적으로 API 호출 → 응답 파싱 → 매핑에 따라 DB INSERT

### 5. DMZ → 내부망 전달
- 방식 미정 (기존 SND/RCV 파이프라인 활용 vs 별도 경로)

## 엔티티 설계 (초안)

### ApiSource (API 소스 등록)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| apiName | String | 표시명 |
| apiUrl | String | 호출 URL |
| httpMethod | String | GET/POST |
| headers | TEXT (JSON) | 요청 헤더 (인증키 등) |
| responseType | String | JSON / XML |
| targetDatasourceId | String | 적재 대상 DB (FK → Datasource) |
| targetTableName | String | 적재 대상 테이블 |
| zone | String | DMZ / INTERNAL |
| isActive | Boolean | 활성 여부 |
| description | TEXT | 설명 |
| createdAt | LocalDateTime | 생성일 |
| updatedAt | LocalDateTime | 수정일 |

### ApiSourceParam (호출 파라미터)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| apiSource | ApiSource (FK) | 소속 API |
| paramKey | String | 파라미터 키 |
| paramValue | String | 고정 값 (없으면 가공) |
| valueType | String | FIXED / DYNAMIC |
| dynamicRule | String | 가공 규칙 (예: `TODAY`, `TODAY-1`, `YYYYMMDD`) |

### ApiFieldMapping (응답 필드 → DB 컬럼 매핑)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| apiSource | ApiSource (FK) | 소속 API |
| responsePath | String | 응답 내 필드 경로 (예: `items[].waterLevel`) |
| targetColumn | String | 대상 DB 컬럼명 |
| dataType | String | 변환 타입 (STRING/NUMBER/DATE 등) |
| dateFormat | String | 날짜 변환 포맷 (해당 시) |

### ApiCollectionLog (수집 이력)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동생성 |
| apiSource | ApiSource (FK) | 소속 API |
| executedAt | LocalDateTime | 실행 시각 |
| status | String | SUCCESS / FAILED |
| readCount | Integer | 응답 건수 |
| writeCount | Integer | 적재 건수 |
| errorMessage | TEXT | 에러 내용 |
| triggeredBy | String | MANUAL / SCHEDULE |

## API 엔드포인트 (초안)

### API 소스 관리
| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/api-sources | 전체 조회 |
| GET | /api/api-sources/{id} | 단건 조회 |
| POST | /api/api-sources | 등록 |
| PUT | /api/api-sources/{id} | 수정 |
| DELETE | /api/api-sources/{id} | 삭제 |

### API 테스트 + 매핑
| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/api-sources/{id}/test | API 1회 호출 + 응답 반환 |
| GET | /api/api-sources/{id}/mappings | 매핑 목록 조회 |
| PUT | /api/api-sources/{id}/mappings | 매핑 저장 (전체 교체) |

### 수집 실행
| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/api-sources/{id}/collect | 수동 수집 실행 |
| GET | /api/api-sources/{id}/logs | 수집 이력 조회 |

### 스케줄
- 기존 Schedule 시스템 확장 또는 ApiSource 전용 스케줄 별도 관리 (미정)

## 기존 인프라 재활용

| 기능 | 재활용 대상 | 비고 |
|------|------------|------|
| DB/테이블/컬럼 | Datasource + DatasourceTable + DatasourceColumn | 매핑 대상 선택 UI |
| 스케줄 | Schedule + ScheduleExecutor | cron 기반 자동 수집 |
| 프론트 패턴 | Agent 관리 페이지 구조 | 목록 + 상세 + 탭 |
| 인증/보안 | Jasypt 암호화, API Key | API 인증 정보 암호화 저장 |

## 프론트 UI (초안)

### API 관리 목록 페이지 (`/api-sources`)
- API 소스 카드/테이블 목록
- 상태 뱃지 (활성/비활성)
- 최근 수집 결과 표시

### API 상세 페이지 (`/api-sources/[id]`)
- **기본 정보 탭**: URL, Method, 헤더, 파라미터, 대상 DB
- **매핑 탭**: 응답 필드 ↔ DB 컬럼 매핑 설정
- **이력 탭**: 수집 이력 (시간, 건수, 성공/실패)
- **테스트 버튼**: 1회 호출 + 응답 미리보기

## 미확정 사항 (논의 필요)
1. API 인증 방식 — API Key / OAuth / Basic Auth / 없음
2. nested JSON 경로 지원 범위 — `data.items[0].value` 같은 depth
3. DMZ→내부망 전달 방식 — 기존 SND/RCV 파이프라인 vs 별도
4. 파라미터 메타데이터 가공 — 미리 정의된 가공 방식 목록 (날짜 포맷 등)
5. 스케줄 구조 — 기존 Schedule 엔티티 확장 vs ApiSource 전용 스케줄
6. XML 응답 지원 범위 — JSON 우선, XML은 후순위?
7. 에러 처리 — 재시도 정책, 타임아웃 설정

## 개발 단계 (안)
1. **1단계**: 엔티티 + CRUD API + 프론트 목록/상세
2. **2단계**: API 테스트 호출 + 응답 파싱 + 매핑 UI
3. **3단계**: 수집 실행 (수동) + 이력 관리
4. **4단계**: 스케줄 연동 (자동 수집)
5. **5단계**: DMZ→내부망 전달
