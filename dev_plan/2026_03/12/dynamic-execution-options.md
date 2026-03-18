# 동적 실행 옵션 설계 — 동적 WHERE 조건 + Link 테이블 보호

## 목적

실행 시점에 **동적 WHERE 조건**을 지정하여 특정 데이터만 재동기화.
기존 YAML table-mappings(파이프라인 기본 구조)는 유지.
실행 옵션으로 conditions를 넘기면 기존 디폴트 조건을 대체/추가.

## 핵심 설계

### 조건 merge 규칙
- 디폴트 조건: 각 Step 코드에 `Map<String, Condition>`으로 구조화
- 실행 시 conditions: 프론트에서 입력
- **같은 컬럼** → 대체 (디폴트를 실행 조건으로 교체)
- **다른 컬럼** → 추가 (AND로 병합)
- conditions 없으면 → 디폴트 그대로 (기존 동작 100% 유지)

```java
// 예시: 디폴트 sido='전국', status='ACTIVE'
// conditions: [{column: "sido", operator: "EQ", value: "대전"}]
// 결과: WHERE sido = '대전' AND status = 'ACTIVE'  (sido 대체, status 유지)
```

### Link 테이블 보호
- conditions 있으면 → `skipLinkUpdate = true`
- 과거 데이터 UPSERT 시 link 테이블이 과거로 갱신 → 중복 수집 방지

### 모든 쿼리 통일 패턴
```java
Map<String, Condition> defaults = getDefaultConditions();     // Step별 고정
Map<String, Condition> merged = mergeConditions(defaults, executionConditions);
WhereClause where = ConditionBuilder.build(merged, dbType);
String sql = "SELECT * FROM " + table + where.toSql();
```

기존 `if (isTimeRangeExecution)` 분기, `applyObsvCodeFilter` 등 별도 경로 제거.

---

## 지원 연산자

| operator | SQL | value | value2 |
|----------|-----|-------|--------|
| EQ | `= ?` | 필수 | - |
| NEQ | `!= ?` | 필수 | - |
| GT | `> ?` | 필수 | - |
| GTE | `>= ?` | 필수 | - |
| LT | `< ?` | 필수 | - |
| LTE | `<= ?` | 필수 | - |
| BETWEEN | `BETWEEN ? AND ?` | 필수 | 필수 |
| IN | `IN (?, ?, ...)` | 쉼표 구분 | - |
| LIKE | `LIKE ?` | `%` 포함 | - |
| IS_NULL | `IS NULL` | - | - |
| IS_NOT_NULL | `IS NOT NULL` | - | - |

- LIKE: 사용자가 `%` 직접 포함 (`%대전%`, `GPM-305%`)
- IN: 쉼표 구분 (`GPM-001,GPM-002,GPM-003`)
- 보안: column 화이트리스트 + operator Enum + PreparedStatement 바인딩

---

## 변경 파일

### Agent Common (신규 3개)
| 파일 | 설명 |
|------|------|
| `ConditionOperator.java` | Enum (EQ, NEQ, GT, GTE, LT, LTE, BETWEEN, IN, LIKE, IS_NULL, IS_NOT_NULL) |
| `ExecutionCondition.java` | `{ column, operator, value, value2 }` DTO |
| `ConditionBuilder.java` | defaults + conditions merge → WhereClause 빌드 |

### Agent Common (변경)
| 파일 | 변경 |
|------|------|
| `SourceToIfStep.java` | 디폴트 조건 구조화 + ConditionBuilder 사용 + link 스킵 판별 |

### Agent Bojo (변경)
| 파일 | 변경 |
|------|------|
| `DefaultLoadStep.java` | 디폴트 조건 구조화 + ConditionBuilder 사용 |
| `LinkTableUpdateStep.java` | skipLinkUpdate 체크 → 스킵 |
| `LinkTableObsvDataFetcher.java` | 디폴트 조건 구조화 + ConditionBuilder 사용 |
| `LoaderStepHelper.java` | applyObsvCodeFilter 제거 |

### Agent Bojo-Int (변경)
| 파일 | 변경 |
|------|------|
| `InternalLoadStep.java` | 디폴트 조건 구조화 + Phase3 link 스킵 |

### Orchestrator (변경)
| 파일 | 변경 |
|------|------|
| `ExecutionDto.TriggerRequest` | conditions 필드 추가 |
| `ExecutionService` | conditions → Agent 전달 |

### 프론트엔드 (변경)
| 파일 | 변경 |
|------|------|
| `agents/[id]/page.tsx` | conditions UI (컬럼 선택 → 연산자 → 값) |

---

## 구현 순서

| 순서 | 작업 | 범위 |
|------|------|------|
| 1 | ConditionOperator + ExecutionCondition + ConditionBuilder | Agent Common |
| 2 | SourceToIfStep 리팩터링 | Agent Common |
| 3 | DefaultLoadStep + LoaderStepHelper 리팩터링 | Agent Bojo |
| 4 | LinkTableUpdateStep skipLinkUpdate | Agent Bojo |
| 5 | LinkTableObsvDataFetcher 리팩터링 | Agent Bojo |
| 6 | InternalLoadStep 리팩터링 | Agent Bojo-Int |
| 7 | ExecutionDto + ExecutionService | Orchestrator |
| 8 | 프론트 conditions UI | Frontend |
| 9 | 기존 isTimeRangeExecution / applyObsvCodeFilter 정리 | 정리 |
