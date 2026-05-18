# 제주 Agent retention/where 매트릭스 정합

> 2026-05-18
> 사이클: 04-others 후속 (yaksoter / api-collect / 새올 / 이용량 5/14 종료 후 마지막 남은 갭)
> 참고:
> - `dev_plan/2026_05/08/retention-candidates-safety.md` (4-layer 검증 정책)
> - `dev_plan/2026_05/14/use-retention-where-merge-fix.md` (yaksoter/api-collect 박을 때 동일 패턴)
> - `docs/agent-retention-where.md` (매트릭스 단일 진실원)

---

## 1. 배경

- 5/12 ~ 5/13 사이클에 제주 본 흐름(SND/RCV/Loader) 다 PASS — source_refs 공통화 fix + trace prefix-collision fix 까지 적용.
- 5/14 매트릭스 작성 시 제주 3 yml 의 `retention-candidates: []` 가 ⚠️ TBD 로 남음. internal-jeju-loader 의 where-filters 도 미선언.
- 04-others 한 바퀴 마지막 남은 갭. 제주 정리 후 → API 쪽 (api-provider / api-collector 본 검증) 진입.

## 2. 분류 결정 (사용자 확인 완료, 2026-05-18)

| Agent | yml retention | where-filters | 이유 |
|-------|---------------|---------------|------|
| `dmz-others-snd-jeju` (SND) | **6 후보** | (없음 — SND 관례) | IF_SND 3 테이블 × (extracted_at + updated_at). 우리 메타 컬럼 채워짐 |
| `internal-jeju-rcv` (RCV) | **6 후보** | (없음 — RCV 관례) | IF_RSV 3 테이블 × (EXTRACTED_AT + UPDATED_AT) |
| `internal-jeju-loader` (LOADER) | **`[]` 유지** | **(없음 — 비대상 명시)** | (a) target 8 = 환경부 표준 GIMS 마스터 / EAV fan-out, 우리 메타 컬럼 없음. (b) PM_GD970201·OBSRVN_DT 후보는 bojo-internal-loader 가 이미 보유 — 제주 쪽 중복 등록 시 운영자 혼란 우려, 단일 진실원 유지. (c) 제주는 자동화 운영 중심이라 운영자 수동실행 conditions 의미 약함 |

### 분류 근거

**SND/RCV — 6 후보씩:**
- Entity (`IfSndTbJeju`, `IfRsvTbJeju` 외 2종) 확인 — `extracted_at` / `updated_at` 컬럼 명시
- yaksoter (4 후보), api-collect (4 후보), 새올 (32 후보) SND/RCV 와 동일 패턴

**Loader — `[]` 유지:**
- `JejuJewonLoadStep.java` MERGE SQL 확인 (line 247~266 등) — `EXECUTION_ID`, `SOURCE_REFS` 만 박고 `EXTRACTED_AT`/`UPDATED_AT` 미적용
- target = 환경부 표준 (TM_GD970001/120001/970130/970002/970101, PM_GD970201/202, TM_GD111010)
- 동일 패턴: yaksoter Loader `[]`, api-collect Loader `[]`
- 대안 검토 — PM_GD970201/202 의 `OBSRVN_DT` (bojo-internal-loader 가 이미 등록한 비즈니스 시각). 사용자 결정 = 중복은 한 곳에서만 관리, 제주 Loader 는 빈 배열 유지

## 3. 변경 파일

### 3-1. yml (3개)

#### `infolink-agent-others-dmz/.../config/agents/dmz-others-snd-jeju.yml`
```yaml
# 기존 (line 32~34)
# Retention 비대상 — 마스터 / Link / 시설 등록 데이터 (등록 후 영구 보존)
# dev_plan/2026_05/08/retention-candidates-safety.md
retention-candidates: []

# 변경 후
# Retention 후보 — 제주 IF_SND transit 의 우리 메타 시각 기준만.
# 운영 정책: 외부 원본 시각(jewon LO_VALUE 등 비즈니스 컬럼)은 영구 보존,
# 우리 시스템 통해 적재된 row 만 extracted_at/updated_at 기준으로 정리 가능.
# Loader target 은 환경부 표준 GIMS 마스터 — 메타 컬럼 없음 (별 yml).
# 정책: dev_plan/2026_05/08/retention-candidates-safety.md, 매트릭스: docs/agent-retention-where.md
retention-candidates:
  - { table: if_snd_tb_jeju_jewon, dateColumn: extracted_at, description: 제주 관측점 마스터 SND transit — 시스템 입수 }
  - { table: if_snd_tb_jeju_jewon, dateColumn: updated_at,   description: 제주 관측점 마스터 SND transit — 마지막 변경 }
  - { table: if_snd_tb_jeju,       dateColumn: extracted_at, description: 제주 수위 관측 SND transit — 시스템 입수 }
  - { table: if_snd_tb_jeju,       dateColumn: updated_at,   description: 제주 수위 관측 SND transit — 마지막 변경 }
  - { table: if_snd_rgetstgms01,   dateColumn: extracted_at, description: 제주 이용실태 SND transit — 시스템 입수 }
  - { table: if_snd_rgetstgms01,   dateColumn: updated_at,   description: 제주 이용실태 SND transit — 마지막 변경 }
```

#### `infolink-agent-bojo-internal/.../config/agents/internal-jeju-rcv.yml`
같은 패턴, IF_RSV 3 테이블 × 2 컬럼 = 6 후보 (대문자 컬럼명).

#### `infolink-agent-bojo-internal/.../config/agents/internal-jeju-loader.yml`
```yaml
# 기존 (line 26~28)
# Retention 비대상 — 마스터 / Link / 시설 등록 데이터 (등록 후 영구 보존)
# dev_plan/2026_05/08/retention-candidates-safety.md
retention-candidates: []

# 변경 후 (의도 명시)
# Retention 비대상 — target 8개 모두 환경부 표준 GIMS 마스터 + EAV fan-out 패턴.
# 운영 정책: MERGE SQL 이 EXECUTION_ID/SOURCE_REFS 만 박고 우리 메타 시각 컬럼 미적용 (DDL 표준 위반 회피).
# PM_GD970201/202 의 OBSRVN_DT 비즈니스 시각은 bojo-internal-loader 에 후보 등록 (단일 진실원).
# Where-filters 도 비대상 — 제주는 자동화 운영 중심, 운영자 수동실행 의미 약함.
# 정책: dev_plan/2026_05/08/retention-candidates-safety.md, 매트릭스: docs/agent-retention-where.md
retention-candidates: []
```

### 3-2. docs (1개)

#### `docs/agent-retention-where.md` 매트릭스 부분 갱신

**2-1. DMZ Agent 표:**
```diff
- | dmz-others-snd-jeju   | SND | `[]` | jeju (수위/제원/시설 혼합) — TBD ⚠️ |
+ | dmz-others-snd-jeju   | SND | 3 if_snd × (extracted_at + updated_at) = **6 후보** | 제주 SND transit (Loader target 은 환경부 표준 마스터라 별 yml) |
```

**2-2. Internal Agent 표:**
```diff
- | internal-jeju-rcv     | RCV    | `[]` | jeju 혼합 — 수위 데이터 시계열 가능성 ⚠️ |
+ | internal-jeju-rcv     | RCV    | 3 IF_RSV × (EXTRACTED_AT + UPDATED_AT) = **6 후보** | 제주 RCV transit |

- | internal-jeju-loader  | LOADER | `[]` | 〃 |
+ | internal-jeju-loader  | LOADER | `[]` | target = 환경부 표준 GIMS + EAV fan-out, 메타 컬럼 없음. PM_GD970201·OBSRVN_DT 는 bojo-internal-loader 에 후보 등록 (단일 진실원) |
```

**3. Where-filters 표:**
```diff
- | internal-jeju-loader  | LOADER | (없음) — TBD ⚠️ |
+ | internal-jeju-loader  | LOADER | (없음) — 자동화 운영, 수동실행 의미 약함 |
```

**4. 발견 사항:**
```diff
- ### ⚠️ 2. jeju 4 agent retention 검토 필요
- ...
+ ### ✅ 2. jeju retention/where (2026-05-18 완료)
+ - `dmz-others-snd-jeju` / `internal-jeju-rcv` retention 6 후보씩 등록
+ - `internal-jeju-loader` 는 환경부 표준 GIMS target + EAV fan-out 패턴이라 `[]` 유지 (의도 주석 명시)
+ - 자세히: `dev_plan/2026_05/18/jeju-retention-yml.md`
```

**6. 변경 이력 추가:**
```diff
+ | 2026-05-18 | jeju 3 yml 정합. SND/RCV 6 후보씩 등록, Loader `[]` 유지(의도 주석). 매트릭스 ⚠️ 제거. |
```

## 4. 영향 범위

| 영역 | 영향 | 비고 |
|------|:----:|------|
| **공통 모듈** | 없음 | yml 만, common JAR 미변경 → 9 모듈 JAR 복사 불요 |
| **빌드 대상** | others-dmz + bojo-internal | yml resources 만 변경, `./gradlew clean build -x test` 후 재기동 |
| **DB 스키마** | 없음 | 메타 컬럼 이미 IF 테이블에 존재 (검증 entity) |
| **운영자 UI** | retention 등록 화면 dropdown 에 jeju IF 후보 12 신규 노출 | yaksoter/api-collect 처럼 그대로 자연스러움 |
| **회귀** | jeju 5/13 본 흐름 회귀 없음 | yml retention 키만 추가, step 로직 무변경 |

## 5. 검증 계획 (Phase 별)

### Phase 1 — yml 박기 (코드 변경 시점)
- 3 yml 갱신
- docs/agent-retention-where.md 갱신

### Phase 2 — 빌드 & 재기동
- `infolink-agent-others-dmz` clean build → 재기동 (8085)
- `infolink-agent-bojo-internal` clean build → 재기동 (8092)
- backend 재기동 불요 (yml resource 만 변경)

### Phase 3 — 4-layer 검증
1. **yml → Frontend dropdown**: agent 상세 페이지 retention 등록 화면에서 jeju agent 선택 시 dropdown 에 6 후보씩 노출 확인
2. **Backend PUT validation**: PUT `/api/manage/datasource/retention-config` 로 jeju 후보 1건 등록 시 검증 통과
3. **Agent self-check**: `GET /api/pipeline/{agent}/retention-candidates` 호출 → 6 후보 반환 확인
4. **운영자 의도**: 등록 후 정상 동작

### Phase 4 — E2E cleanup
yaksoter/api-collect 검증 패턴 동일:
- cron 임시 단축 (`0/30 * * * * *`)
- `if_snd_tb_jeju` SN/RID 임시 INSERT (extracted_at='2025-01-01')
- retention_config 등록 (dmz datasource, if_snd_tb_jeju, extracted_at, 80일)
- 30s 안에 cleanup `1건 삭제` log 확인
- DB row 사라짐 확인
- cron 원복

## 6. 리스크 (8건 — yaksoter/api-collect 패턴이라 작음)

| # | 리스크 | 완화 |
|---|--------|------|
| R1 | extracted_at NULL row 존재 시 retention 누락 | yaksoter/api-collect 검증 통과 패턴 — 정상 row 만 정리, NULL 은 운영자 책임 |
| R2 | IF 테이블 컬럼명 mismatch (대소문자) | RCV 는 대문자, SND 는 소문자. yml 분리 박음 (saeol 매트릭스 패턴) |
| R3 | Loader 비대상 의도가 매트릭스에서 다시 ⚠️ 로 보일 위험 | 주석 + 매트릭스 비고에 명시 (변경 이력 추가) |
| R4 | PM_GD970201 중복 등록 — bojo 와 제주 두 agent 모두 후보 노출 가능성 | **선제 차단** — 제주 Loader `[]` 유지로 결정 (단일 진실원) |
| R5 | retention E2E 검증 시 cron 임시 변경 잔재 | 5/14 yaksoter/api-collect 검증 시 원복 패턴 동일 |
| R6 | 매트릭스 docs/yml 동기화 깨짐 | `feedback_retention_where_matrix_sync` 룰 따라 같은 PR 에 박기 |
| R7 | 운영자가 "제주 Loader 적재된 row 정리하고 싶다" 요구 시 | 의도 주석에 PM_GD970201·OBSRVN_DT bojo 등록 안내 |
| R8 | 회귀 — 5/13 source_refs 공통화 fix 영향 받지 않는지 확인 | yml retention 키만 추가, source_refs 로직 무변경 |

## 7. 산출물 예상

- **yml (3)**: `dmz-others-snd-jeju.yml`, `internal-jeju-rcv.yml`, `internal-jeju-loader.yml`
- **docs (1)**: `docs/agent-retention-where.md`
- **dev_log (1)**: `dev_logs/2026_05/2026-05-18.md` (제주 사이클)
- **빌드**: others-dmz + bojo-internal 각 clean build (JAR)
- **검증**: 4-layer + E2E cleanup 통과 후 커밋

## 8. 커밋 메시지 (예정)

```
feat(jeju): retention 12 후보 yml (SND/RCV) + Loader 비대상 의도 명시 + 매트릭스

- dmz-others-snd-jeju: IF_SND 3 × (extracted_at + updated_at) = 6 후보
- internal-jeju-rcv: IF_RSV 3 × (EXTRACTED_AT + UPDATED_AT) = 6 후보
- internal-jeju-loader: [] 유지 — 환경부 표준 GIMS + EAV fan-out, PM_GD970201·OBSRVN_DT 는 bojo-internal-loader 단일 진실원
- docs/agent-retention-where.md: 매트릭스 ⚠️ 제거
- E2E cleanup 검증 통과 (if_snd_tb_jeju SN=99991 → 30s 안에 1건 삭제)
```

## 9. 다음 사이클 (제주 후 — 사용자 합의)

api-collector / api-provider 본 검증 진입 — 04-others 한 바퀴 종료.
