# 조건실행 테이블 바인딩 수정 계획

## 1. 문제

조건실행 시 conditions가 **테이블 구분 없이** 모든 Step에 동일하게 적용됨.
예: `obsv_date BETWEEN` 조건이 jewon Step에도 걸려서, `sec_jewon_view`에 `obsv_date` 컬럼이 없어 SQL 에러 발생.

**현재 흐름:**
```
프론트 (테이블 선택 UI 있음, condTableSelections)
  ↓ conditions: [{column, operator, value}]  ← tableName 누락!
Orchestrator → Agent
  ↓ 모든 Step에 동일 conditions 전달
jewon Step: WHERE obsv_date BETWEEN → 에러 (컬럼 없음)
obsvdata Step: WHERE obsv_date BETWEEN → 정상
```

## 2. 수정 방안

### 2-1. ExecutionCondition에 tableName 필드 추가

```java
// common: ExecutionCondition.java
private String tableName;  // 조건 대상 테이블 (select-tables 기준)
```

### 2-2. 프론트에서 tableName 포함하여 전송

이미 `condTableSelections[idx]`에 테이블명을 갖고 있음. 전송 시 포함만 하면 됨.

```typescript
// 현재
const validConditions = conditions.filter(c => ...);

// 수정
const validConditions = conditions
  .map((c, idx) => ({ ...c, tableName: condTableSelections[idx] }))
  .filter(c => ...);
```

**타입 수정:**
```typescript
// types/index.ts
export interface ExecutionCondition {
  tableName?: string;  // 추가
  column: string;
  operator: ...;
  value?: string;
  value2?: string;
}
```

### 2-3. SourceToIfStep에서 자기 테이블 조건만 필터링

```java
// SourceToIfStep.fetchSimpleCopy() 내부
// 현재: execConditions = options.getConditions() 전체 사용
// 수정: sourceTable과 tableName이 일치하는 조건만 필터

List<ExecutionCondition> execConditions = options.getConditions().stream()
    .filter(c -> c.getTableName() == null
            || c.getTableName().equalsIgnoreCase(config.getSourceTable()))
    .toList();
```

- `tableName == null`: 하위호환 (테이블 지정 없으면 전체 적용)
- 대소문자 무시: keunsan 대문자 테이블 대응

### 2-4. Loader Step에서도 동일 필터링

DMZ Loader (`DmzBojoLoadStep`)와 Internal Loader (`InternalBojoLoadStep`)도 conditions를 쓰므로 동일 필터링 적용.

Loader는 if-table(source)에서 읽으므로:
```java
// if-table 기준 필터링
List<ExecutionCondition> filtered = options.getConditions().stream()
    .filter(c -> c.getTableName() == null
            || c.getTableName().equalsIgnoreCase(ifObsvdataTable)
            || c.getTableName().equalsIgnoreCase(ifJewonTable))
    .toList();
```

---

## 3. 수정 파일 목록

| 모듈 | 파일 | 작업 |
|------|------|------|
| sync-agent-common | `step/ExecutionCondition.java` | `tableName` 필드 추가 |
| sync-agent-common | `step/SourceToIfStep.java` | `fetchSimpleCopy()`에서 tableName 필터링 |
| sync-agent-bojo | `loader/step/DmzBojoLoadStep.java` | conditions tableName 필터링 |
| sync-agent-bojo-int | `loader/step/InternalBojoLoadStep.java` | conditions tableName 필터링 |
| sync-orchestrator/frontend | `types/index.ts` | `tableName` 필드 추가 |
| sync-orchestrator/frontend | `app/agents/[id]/page.tsx` | `condTableSelections`를 conditions에 포함 |

---

## 4. 검증

### API 레벨
- [ ] `obsv_date BETWEEN` 조건 (obsvdata 테이블 지정) → obsvdata만 필터, jewon은 조건 없이 전체 복사 → 성공
- [ ] `obsv_code EQ` 조건 (양쪽 테이블 지정) → 양쪽 다 필터 → 성공
- [ ] `tableName` 없는 조건 (하위호환) → 기존처럼 전체 적용
- [ ] keunsan 대문자 테이블 → 대소문자 무시 매칭

### 프론트
- [ ] 조건 추가 시 테이블 드롭다운 선택 정상
- [ ] 실행 시 tableName이 API에 포함됨
