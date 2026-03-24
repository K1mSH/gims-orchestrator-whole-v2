# API Collector 등록 플로우 통합 계획

## 1. 현재 문제

등록과 매핑이 분리되어 있어, 실행 불가능한 상태로 등록이 가능함.
- 현재: 등록(기본정보+파라미터) → 상세페이지 이동 → 매핑탭에서 설정
- 문제: 매핑 없이 등록된 API는 실행 불가 → 불완전한 데이터

## 2. 목표

**한 화면에서 등록 완결** — 실행에 필요한 모든 요소가 충족돼야 등록 가능.

등록 필수 조건 (하나라도 빠지면 alert로 차단):
1. 기본정보: apiName, url, httpMethod
2. 테스트 호출 성공
3. 데이터 루트 선택 (JSON 트리에서 배열 노드 클릭)
4. 타겟 datasource + 테이블 선택
5. 필드 매핑 1개 이상 (UK 1개 이상 포함)

## 3. 등록 페이지 UI 흐름

```
[요청 설정]     API명, URL, Method, Content-Type
[헤더]          커스텀 헤더 key-value
[인증]          NONE / BASIC / BEARER
[파라미터]      QUERY/BODY/PATH/HEADER, 고정값/동적값
                    ↓
[테스트 호출]   버튼 클릭 → 인라인 테스트 실행
                    ↓ 성공 시
[응답 트리]     JSON 트리 표시 → 배열 노드에 "데이터 루트 선택" 버튼
                    ↓ 루트 선택 시
[적재 설정]     Datasource 선택 → 테이블 선택 → UPSERT 토글
[필드 매핑]     API 필드 ↔ DB 컬럼 매핑 테이블 (UK 체크, 변환 타입)
                    ↓
[등록]          위 조건 전부 충족 시 활성화 → 클릭 시 일괄 저장
```

## 4. 수정 사항

### 4-1. 프론트엔드 (등록 페이지)

**파일**: `app/api-collect/page.tsx`

현재 등록 폼 아래에 다음 섹션 추가:

1. **응답 트리에 "데이터 루트 선택" 버튼 추가**
   - 현재 JsonTreeView는 읽기 전용 → 배열 노드에 선택 버튼 추가
   - 선택하면 `selectedDataRoot` 상태 저장 + 해당 배열의 필드 목록 자동 추출

2. **적재 설정 섹션 추가**
   - Datasource 드롭다운 (Orchestrator `/api/datasources/simple`에서 로드)
   - 테이블 드롭다운 (선택된 datasource의 테이블 목록)
   - UPSERT 토글

3. **필드 매핑 섹션 추가**
   - MappingTab.tsx의 매핑 테이블 로직 재사용
   - API 필드(테스트 결과에서 추출) ↔ DB 컬럼(테이블 선택 시 로드) 매핑
   - UK 체크박스, 변환 타입(NONE/DATE_FORMAT/NUMBER)

4. **등록 버튼 validation 강화**
   - `handleCreate`에서 5가지 조건 체크 → 미충족 시 alert
   - 조건: apiName, url, testResult.success, selectedDataRoot, mappingRows(UK 포함)

5. **등록 시 일괄 저장**
   - endpoint create → params save → endpoint update(dataRootPath, targetDatasourceId, targetTableName, upsertEnabled) → mappings save
   - 하나라도 실패하면 rollback (생성된 endpoint 삭제)

### 4-2. isPk → isConflictKey 리네이밍

`isPk`는 UPSERT conflict key를 의미하지만, 이름이 PK와 혼동됨.
실제로는 PK/UK 모두 `ON CONFLICT`에 사용 가능하므로 `isConflictKey`로 변경.

#### 백엔드
| 파일 | 변경 |
|------|------|
| `entity/ApiFieldMapping.java` | `isPk` → `isConflictKey`, 컬럼명 `is_conflict_key` |
| `dto/ApiEndpointDto.java` | `FieldMappingRequest.isPk` → `isConflictKey` |
| `dto/ApiEndpointDto.java` | `FieldMappingResponse.isPk` → `isConflictKey` |
| `service/ApiExecutionService.java` | `m.getIsPk()` → `m.getIsConflictKey()` |
| `service/ApiEndpointService.java` | 매핑 변환 시 필드명 변경 |

#### 프론트엔드
| 파일 | 변경 |
|------|------|
| `types/api-collect.ts` | `isPk` → `isConflictKey` |
| `components/api-collect/MappingTab.tsx` | isPk 참조 → isConflictKey |
| `app/api-collect/page.tsx` | 등록 폼 매핑 섹션 (신규 추가 시 적용) |

#### DB 마이그레이션
```sql
-- api_field_mapping 테이블
ALTER TABLE api_field_mapping RENAME COLUMN is_pk TO is_conflict_key;
```

### 4-3. 백엔드 API 변경

등록 플로우 자체는 기존 API 조합으로 충족:
- `POST /api/endpoints` (생성)
- `PUT /api/endpoints/{id}/params` (파라미터 저장)
- `PUT /api/endpoints/{id}` (dataRootPath, targetDatasourceId, targetTableName, upsertEnabled 업데이트)
- `PUT /api/endpoints/{id}/mappings` (매핑 저장)

### 4-4. 상세 페이지 변경

등록 시 매핑까지 완료되므로, 상세 페이지의 매핑탭은 **수정 전용**으로 유지.
신규 등록 기능은 등록 페이지에서만.

## 5. 수정 파일

| 파일 | 작업 |
|------|------|
| `app/api-collect/page.tsx` | 데이터 루트 선택 + 적재 설정 + 매핑 섹션 추가, validation 강화 |
| `components/api-collect/MappingTab.tsx` | isPk → isConflictKey |
| `types/api-collect.ts` | isPk → isConflictKey |
| `entity/ApiFieldMapping.java` | isPk → isConflictKey |
| `dto/ApiEndpointDto.java` | isPk → isConflictKey |
| `service/ApiExecutionService.java` | getIsPk → getIsConflictKey |
| `service/ApiEndpointService.java` | isPk → isConflictKey |

## 6. 검증

- [ ] 기본정보만 입력 후 등록 → alert "테스트 호출을 먼저 실행하세요"
- [ ] 테스트 성공 후 데이터 루트 미선택 → alert "데이터 루트를 선택하세요"
- [ ] 데이터 루트 선택 후 매핑 미설정 → alert "필드 매핑을 설정하세요"
- [ ] 매핑에 UK 없음 → alert "UK를 1개 이상 지정하세요"
- [ ] 전체 조건 충족 후 등록 → 성공, 목록에 표시
- [ ] 등록된 API 상세 → 매핑 설정 확인
- [ ] 수동 실행 → 데이터 적재 확인
