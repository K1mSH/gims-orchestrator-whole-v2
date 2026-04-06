# Step Config Single Source of Truth 리팩토링

> 작성일: 2026-04-06
> 목적: YAML step 정의를 유일한 출처로, 분산 관리 제거

## 현재 문제

같은 정보가 여러 곳에 중복:
- IF 테이블명: Step 상수, @Value, YAML select-tables
- table-mappings: YAML에 수동 정의 (Step과 별개)
- select-tables: YAML에 수동 정의 (Step과 별개)
- SyncLog 테이블명: Step 내 문자열 하드코딩

## 변경 후 구조

### YAML (유일한 출처)
```yaml
agent-code: internal-jeju-loader
type: LOADER

steps:
  - id: jeju-jewon-load
    name: 제주 제원 적재
    factory-key: jeju-jewon-load
    source-table: [IF_RSV_TB_JEJU_JEWON]
    target-table: [TM_GD970001, TM_GD120001, TM_GD970130, TM_GD970002, TM_GD970101]

# select-tables: 생략 가능 (steps에서 자동 수집)
# table-mappings: 생략 가능 (steps에서 자동 생성)
```

### 자동 생성 규칙
- `select-tables` = 전체 step의 source-table 합집합 (중복 제거)
- `table-mappings` = step별 `{name: step.id, source: step.source-table, target: step.target-table}`
- 수동 정의가 있으면 수동 우선 (하위호환)

### source-table 단일값/리스트 양쪽 지원
```yaml
source-table: IF_RSV_TB_JEJU_JEWON           # 단일 → ["IF_RSV_TB_JEJU_JEWON"]
source-table: [IF_RSV_USE_LEGACY_DATA, IF_RSV_USE_STATUS_DATA]  # 리스트
```

## 수정 대상

### 1. AgentConfigLoader (파싱)
- step의 `source-table`, `target-table` 파싱 (단일값/리스트 양쪽)
- `select-tables` 미정의 시 steps에서 자동 수집
- `table-mappings` 미정의 시 steps에서 자동 생성

### 2. AgentDefinition
- step config에 source-table/target-table이 파싱된 상태로 보관 (기존 Map 그대로)

### 3. Factory (Step 주입)
- `config.get("source-table")` → Step 생성자에 전달
- 기존 @Value, 상수 제거

| Factory | 변경 |
|---------|------|
| InternalBojoLoadStepFactory | @Value 5개 → config에서 읽기 |
| JejuLoadStepFactory | Step 상수 → config에서 읽기 |

### 4. Step (상수/필드 제거)
| Step | 변경 |
|------|------|
| InternalBojoLoadStep | 생성자에서 받는 건 동일, @Value 경유 제거 |
| JejuJewonLoadStep | `IF_TABLE` 상수 → 생성자 파라미터 |

### 5. SyncLog 기록
- Step에서 saveSyncLog 호출 시 하드코딩 대신 config에서 받은 테이블명 사용

### 6. YAML 정리
- 기존 YAML의 `select-tables`, `table-mappings` 섹션 제거 (자동 생성)
- step에 `source-table`, `target-table` 추가 (없는 것만)

## 영향 범위

### bojo-int YAML (내부망)
| YAML | 변경 |
|------|------|
| internal-bojo-loader.yml | step에 source/target 추가, select-tables/table-mappings 제거 |
| internal-bojo-rcv.yml | 이미 source-to-if라 source-table 있음, select-tables 제거 |
| internal-jeju-loader.yml | 이미 있음, select-tables/table-mappings 제거 |
| internal-jeju-rcv.yml | source-to-if, select-tables 제거 |
| internal-saeol-loader.yml | source-to-if, select-tables 제거 |
| internal-saeol-rcv.yml | source-to-if, select-tables 제거 |
| internal-use-rcv.yml | source-to-if, select-tables 제거 |

### bojo YAML (DMZ)
| YAML | 변경 |
|------|------|
| dmz-bojo-*.yml | 동일 패턴 적용 |

### others YAML (DMZ)
| YAML | 변경 |
|------|------|
| dmz-others-snd-*.yml | 동일 패턴 적용 |

### 공통 코드
- AgentConfigLoader: bojo, bojo-int, others 3개 모듈에 각각 있음 → 동일 수정
- AgentDefinition: 동일

## 실행 순서

```
1. AgentConfigLoader 수정 (자동 수집 로직)
2. Factory 수정 (config에서 테이블명 읽기)
3. Step 수정 (상수/@Value 제거, 생성자 파라미터로)
4. YAML 정리 (step에 source/target 추가, 중복 섹션 제거)
5. 전 모듈 빌드 테스트
6. E2E 재검증
```

## 주의사항
- source-to-if Step은 이미 config에서 source-table을 읽고 있으므로 변경 없음
- 하위호환: select-tables/table-mappings 수동 정의가 있으면 그걸 우선 사용
- 3개 모듈(bojo, bojo-int, others)의 AgentConfigLoader가 별개 파일이므로 전부 수정
