# Loader 실행모드별 Step 교체 구조

## 목적
- Loader Agent 실행 시 `executionModeId`에 따라 **다른 Step 구현체**로 파이프라인 실행
- 기본 모드는 현재 로직 유지, 수동 모드에서는 다른 조회/처리 로직 사용
- 수동 모드는 스케줄 불가, 수동 실행 전용

## 현재 구조 (AS-IS)
```
LoaderPipelineConfig (@PostConstruct)
  → PipelineRegistry.register(agentCode, "LOADER", runner)
     runner = PipelineRunner(agentCode, List.of(daejeonLoadStep))

PipelineService.executeAsync(executionId, params)
  → pipelineRegistry.getRunner(agentCode)  // agentCode만으로 조회
  → runner.run(executionId, params)

PipelineRegistry
  → Map<String, PipelineRunner> runners  // key: agentCode
```

- PipelineRunner는 agentCode당 1개만 등록
- executionModeId는 ExecutionOptions에 존재하지만 Runner 선택에 사용되지 않음
- Step 내부에서 하드코딩 분기 (isTimeRangeExecution, obsv-code 필터 등)

## 변경 구조 (TO-BE)
```
LoaderPipelineConfig (@PostConstruct)
  → PipelineRegistry.register(agentCode, "LOADER", "default", defaultRunner)
  → PipelineRegistry.register(agentCode, "LOADER", "manual-region", regionRunner)
  → PipelineRegistry.register(agentCode, "LOADER", "manual-xxx", xxxRunner)

PipelineService.executeAsync(executionId, params)
  → modeId = params.get("executionModeId") ?? "default"
  → pipelineRegistry.getRunner(agentCode, modeId)  // 복합키 조회
  → runner.run(executionId, params)

PipelineRegistry
  → Map<String, Map<String, PipelineRunner>> runners  // key: agentCode → modeId → runner
```

## 설계 원칙
1. **Step은 monolithic 유지** — jewon/obsvdata 분리하지 않음 (향후 경계 변경 대비)
2. **모드별 Step 구현체 교체** — PipelineConfig에서 모드별 Runner 미리 등록
3. **공통 처리 로직 헬퍼 추출** — 변환/UPSERT/IF상태/SyncLog를 재사용 가능한 헬퍼로
4. **modeId "default" fallback** — 미지정 시 기존 동작 보장

## 수정 대상 파일

### 1. PipelineRegistry — 복합키 지원
**파일**: `sync-agent-bojo/.../config/PipelineRegistry.java`

변경 내용:
- `Map<String, PipelineRunner>` → `Map<String, Map<String, PipelineRunner>>`
  - 외부 key: agentCode, 내부 key: modeId
- `register(agentCode, agentType, runner)` → `register(agentCode, agentType, modeId, runner)`
  - 기존 시그니처는 modeId="default"로 오버로드 유지 (하위호환)
- `getRunner(agentCode)` → `getRunner(agentCode, modeId)`
  - modeId에 해당 Runner 없으면 "default" fallback
  - 기존 시그니처는 modeId="default"로 오버로드 유지 (하위호환)

### 2. PipelineService — modeId 전달
**파일**: `sync-agent-bojo/.../pipeline/PipelineService.java`

변경 내용 (78줄 부근):
```java
// AS-IS
PipelineRunner baseRunner = pipelineRegistry.getRunner(finalAgentCode);

// TO-BE
String modeId = params.get("executionModeId") != null
    ? params.get("executionModeId").toString() : "default";
PipelineRunner baseRunner = pipelineRegistry.getRunner(finalAgentCode, modeId);
```

### 3. LoaderPipelineConfig — 모드별 등록
**파일**: `sync-agent-bojo/.../config/LoaderPipelineConfig.java`

변경 내용:
- 모드별 Step 빈 주입 + Runner 등록
- 당장은 "default" 모드만 등록 (기존 동작 유지)
- 새 모드 추가 시 Step 구현체 + 여기에 등록 한 줄 추가

### 4. LoaderStepHelper — 공통 처리 로직 추출
**파일**: `sync-agent-bojo/.../loader/step/LoaderStepHelper.java` (신규)

DaejeonLoadStep에서 추출할 공통 로직:
- `batchConvertAndUpsertJewon(List<IfRsvSecJewon>, String executionId)` → 변환+UPSERT+결과 반환
- `batchConvertAndUpsertObsvdata(List<IfRsvSecObsvdata>, String executionId)` → 변환+UPSERT+결과 반환
- `batchUpdateIfStatus(String tableName, List<Object> ids, String status, String executionId)` → IF 상태 배치 업데이트
- `saveSyncLogMapping(...)` → SyncLog 저장

각 Step 구현체는 **조회 로직만 자체 구현**하고, 나머지는 헬퍼 호출.

### 5. DaejeonLoadStep → DefaultLoadStep 리네이밍 + 리팩터링
**파일**: `sync-agent-bojo/.../loader/step/DaejeonLoadStep.java` → `DefaultLoadStep.java`

변경 내용:
- 클래스명 `DaejeonLoadStep` → `DefaultLoadStep`
- 공통 로직을 LoaderStepHelper 호출로 교체
- 조회 로직(findIfRsvJewonPending 등)만 유지
- 기능 변경 없음, 구조만 정리
- LoaderPipelineConfig의 참조도 함께 변경

### 6. RCV/SND PipelineConfig — 하위호환 적용
**파일**: `sync-agent-bojo/.../config/RcvPipelineConfig.java`, `SndPipelineConfig.java`

변경 내용:
- `register(agentCode, type, runner)` 기존 시그니처 유지되므로 변경 불필요
- 또는 명시적으로 `register(agentCode, type, "default", runner)`로 변경 (선택)

## 영향 범위
- **PipelineRegistry**: bojo 내부 클래스, 외부 의존 없음
- **PipelineService**: bojo 내부 클래스
- **LoaderPipelineConfig**: Loader 전용
- **DaejeonLoadStep**: Loader 전용
- **RCV/SND**: register 시그니처 하위호환이므로 영향 없음
- **sync-agent-common**: 변경 없음
- **Internal Agent (bojo-int)**: 별도 PipelineRegistry 사용, 변경 없음 (추후 동일 패턴 적용 가능)

## 단계별 진행 순서
1. PipelineRegistry 복합키 지원 (하위호환 오버로드 포함)
2. PipelineService에서 modeId 추출 + getRunner 호출 변경
3. LoaderStepHelper 추출
4. DaejeonLoadStep 리팩터링 (헬퍼 사용)
5. LoaderPipelineConfig 모드별 등록 구조 적용
6. 빌드 + 기동 테스트
7. (향후) 새 모드 Step 구현체 추가

## 비고
- 이번 작업은 **구조 변경만** — 새 모드 Step 구현체 추가는 별도 작업
- 기존 동작(default 모드) 100% 유지가 최우선
- Internal Agent(bojo-int)는 이번 범위 밖, 추후 동일 패턴 적용
