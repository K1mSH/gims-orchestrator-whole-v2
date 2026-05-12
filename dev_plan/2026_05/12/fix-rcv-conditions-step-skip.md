# RCV conditions 실행 — 적용 대상 없는 step skip 처리 (버그 수정 계획)

작성일: 2026-05-12
발견 경위: 03-bojo §5 테스트 중 보조 RCV daejeon 에 `sigungu=중구` WHERE 조건 실행 → obsvdata step 크래시

> **상태: 코드 수정·빌드·배포 완료 (2026-05-12).** `SourceToTargetStep.fetchSimpleCopy()` 에 ① 컬럼 존재 필터 ② "conditions 실행인데 이 step 에 적용될 조건 0개면 빈 리스트 반환(skip)" 적용. common JAR → 9개 모듈 libs/ 복사 + bojo-dmz / bojo-internal 재빌드·재기동. 화면 재검증은 사용자 진행 예정 (`sigungu=중구` → SUCCESS, jewon 80 + obsvdata skip 기대).

## 1. 증상

보조 RCV Agent 1개 = `jewon` step + `obsvdata` step 2개를 돈다.
`sigungu` 는 `sec_jewon_view` 에만 있는 컬럼 (`sec_obsvdata_view` 컬럼 = obsv_code, obsv_date, obsv_time, gwdep, gwtemp, ec, remark — sigungu·link_status 둘 다 없음).

| 입력 | jewon step | obsvdata step | 결과 |
|------|-----------|---------------|------|
| `sigungu=중구` (tableName=`sec_jewon_view`, ← 프론트 UI 가 항상 붙임) | `WHERE sigungu='중구'` → 80건 ✅ | 조건 제외됨 → step-level hasConditions=false → 디폴트 `link_status IN ('PENDING','RESYNC','FAILED')` 폴백 → `sec_obsvdata_view` 에 link_status 없음 → **`column "link_status" does not exist`** ❌ | FAILED |
| `sigungu=중구` (tableName 없음, raw API only) | 80건 ✅ | `WHERE sigungu='중구'` → `column "sigungu" does not exist` ❌ | FAILED |

## 2. 원인

`infolink-agent-common/.../step/SourceToTargetStep.java` `fetchSimpleCopy()`:

```java
boolean hasConditions = execConditions != null && !execConditions.isEmpty();   // step-level (tableName 필터 후)
Map<String, ExecutionCondition> defaults = new LinkedHashMap<>();
if (!config.isFullCopy()) {
    if (!hasConditions) {
        defaults.put("link_status", ExecutionCondition.in("link_status", "PENDING,RESYNC,FAILED"));  // ← 여기
    }
}
```

conditions 실행(execution-level `hasConditions()`=true)인데 이 step 의 source-table 에 적용될 조건이 0개면, `!config.isFullCopy()` 인 obsvdata step 은 디폴트 `link_status` 필터로 폴백한다. obsvdata 소스 뷰는 평소 CUSTOM_STAGING(link_ngwis 조인)으로 도는 뷰라 `link_status` 컬럼이 없어 크래시.

> 참고: DMZ Loader(`DmzBojoLoadStep`)는 이미 올바르게 처리함 — `isResyncExecution && !jewonConditions.isEmpty() ? resync : isResyncExecution ? List.of() : pending`. RCV(`SourceToTargetStep`)만 미커버.

## 3. 수정 방안 (핵심 룰)

> **conditions 실행 중인데 이 step 의 source-table 에 적용될 조건이 하나도 없으면 → 그 step 은 SKIP (0건, SUCCESS). 디폴트 link_status 필터 폴백 금지.** (DMZ Loader 와 동일 시맨틱)

### 수정 대상 (단일 파일)
`infolink-agent-common/src/main/java/com/infolink/agent/common/step/SourceToTargetStep.java` — `fetchSimpleCopy()`

### 변경 내용
1. `execConditions` 산출 시 tableName 필터에 더해 **컬럼 존재 필터** 추가: condition 의 `column` 이 이 source-table 의 조회 컬럼(`columns` 파라미터, 대소문자 무시) 에 없으면 제외. → tableName 미지정(Case A)도 안전하게 처리.
2. `boolean hasConditions` 산출 후, **execution-level conditions 실행인데 step-level hasConditions=false → `return new ArrayList<>()`** (빈 리스트 → 상위 로직에서 readCount=0, writeCount=0, SUCCESS). 디폴트 `link_status` 블록에 도달하지 않음.
3. 로그: `log.info("[{}] Conditions execution but no applicable condition for source '{}' — skipping step", getStepId(), config.getSourceTable())`
4. legacy `startTime`/`endTime`(`isTimeRangeExecution`) 경로는 손대지 않음 — 시간범위 실행은 양쪽 step 모두 해당 시간 컬럼 있으면 그대로 동작.

## 4. 영향 범위

- **공통 모듈 수정** → CLAUDE.md 규칙대로 9개 검증 모듈 `libs/` 에 JAR 복사 필수 (bojo-dmz / bojo-internal / others-dmz / provide-dmz / orchestrator-backend(?) / api-collector / api-provider / proxy-dmz / proxy-internal). 실제로 SourceToTargetStep 을 쓰는 건 RCV Agent 들(bojo-dmz, bojo-internal, others-dmz, provide-dmz, api-collector).
- 기존 동작 영향: conditions 없는 일반 증분 실행 — 변경 없음 (execOptions.hasConditions()=false → skip 룰 미적용 → 기존 link_status 폴백 그대로). 양쪽 step 다 적용되는 공통 컬럼(obsv_code 등) 조건 — 변경 없음. **회귀 없음.**
- 한쪽 테이블 전용 컬럼 조건 → 그 테이블 step 만 처리, 다른 step skip → 실행 SUCCESS. (원래 의도된 동작)

## 5. 빌드 & 배포 절차

```bash
cd infolink-agent-common && ./gradlew clean build -x test
# JAR 9개 모듈 복사
for m in bojo-dmz bojo-internal others-dmz provide-dmz orchestrator-backend api-collector api-provider proxy-dmz proxy-internal; do cp build/libs/infolink-agent-common-*.jar ../infolink-agent-$m/libs/ 2>/dev/null || cp build/libs/infolink-agent-common-*.jar ../infolink-$m/libs/; done
cd ../infolink-agent-bojo-dmz && ./gradlew clean build -x test
cd ../infolink-agent-bojo-internal && ./gradlew clean build -x test
# bojo-dmz, bojo-internal 재기동
```

## 6. 테스트

1. **회귀**: 보조 RCV daejeon 일반 증분 실행(조건 없음) → read=write 5359, jewon 402 / obsvdata 4957 (수정 전과 동일)
2. **버그 재현 케이스**: daejeon RCV + `sigungu=중구` (tableName=sec_jewon_view) → SUCCESS, jewon step 80건 처리 + obsvdata step 0건 skip
3. tableName 없이 `sigungu=중구` (raw API) → SUCCESS (컬럼 존재 필터로 obsvdata step skip)
4. 공통 컬럼 `obsv_code IN (...)` (tableName 없음) → 양쪽 step 다 처리 (기존 §4 동작 유지)
5. trace-source: 케이스 2 의 jewon 80건 → 역추적 FOUND
6. 이후 03-bojo §4 표준 3업체 E2E 정상 진행 확인

## 7. 본 수정 범위 밖 (별도)

- Case A(tableName 미지정 + 한쪽 전용 컬럼) — 위 컬럼 존재 필터로 함께 처리하므로 사실상 해소되지만, 의도적으로 양쪽에 다 적용하려는 잘못된 컬럼명 입력은 여전히 검증 안 함. UI는 항상 tableName 지정 → 영향 없음.
- `InternalBojoLoadStep` — obsvdata 단일 테이블 처리라 다중 테이블 이슈 없음. 손대지 않음.
