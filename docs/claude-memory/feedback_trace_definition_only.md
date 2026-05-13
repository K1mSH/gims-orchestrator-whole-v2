---
name: feedback-trace-definition-only
description: "추적 코드는 정의 진실원만 + `=` 정확 동등성만 — prefix/contains/LIKE/baseName 휴리스틱 일체 금지"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: be73226d-e944-4630-9c9d-52d96ea9900e
---

추적(trace) 코드는 정의 진실원만 본다 — prefix 검사·차감, contains/substring 매칭, baseName 추출, 양방향 contains, 도메인 네이밍 판별, **LIKE 패턴 매칭 일체 금지**. **정의된 값을 정의된 함수(`SourceRefUtils.buildJson` 등)로 재현해서 `=` 로 찾는다.**

**진실원 (이게 전부 — 다른 추론 없음)**:
1. `sync_log.target_tables` / `source_tables` — `SyncLogWriter` 가 박은 실 매핑명 (JSON 배열, `parseJsonArray` 후 `equalsIgnoreCase` 정확매칭).
2. `source_refs.tableId` → `datasource_table.id` — 실 등록 ID (`resolveSourceTableByRefsTableId`).
3. `source_refs` 의 zone / dsId / tableName 같이 박힌 직접값 (옛 `I:dsId:tableName:pk` 형식 호환만).

**금지**:
- `startsWith("if_rsv_")` / `startsWith("if_snd_")` / `replaceFirst("^if_")` / `replace("_view","")` 같은 prefix 검사·차감.
- `tableName.contains(otherName)` / "source⊃target / target⊃source" 양방향 contains / JSON 문자열 contains (`"\"" + name + "\""` 박아 매칭).
- "이 테이블이 IF 야 / Loader 야 / SND 야" 같은 도메인 네이밍 판별. `isSndRelay`/`isLoaderTarget` 같은 prefix 기반 플래그를 결정 로직에 끌어들이는 거.
- 정확매칭 실패 시 휴리스틱 fallback 으로 메꾸기 (예: baseName 차감, pkValue 만으로 LIKE).
- **`source_refs` 매칭에 `LIKE` 일체 금지** — Pattern A0(tableId LIKE), A(tableName LIKE), B(pkValue LIKE), 토큰매칭(4개 LIKE OR) 등 일체 폐기. backend 가 `SourceRefUtils.buildJson` 과 동일 로직으로 exactSourceRefs 빌드 → `WHERE source_refs = ?` 정확 동등성 한 번. 정의된 걸 LIKE 로 찾으면 다른 거 잡힐 수 있음 (제주 복합 PK 발견의 뿌리).

**Why**: 추적의 자유도는 정의에 박힌 만큼. 휴리스틱은 그 시점 케이스엔 맞아도 새 패턴(예: Internal RCV — source `if_snd_*` / target `IF_RSV_*` 양쪽 prefix) 만나면 깨짐. 4/22(`67a0b3f`) 가 분기 3 만 정확매칭으로 강화하고 분기 1·2 (if_rsv_/if_snd_) 의 baseName 차감 fallback 을 "기존 기능이라 유지" 라 보수적으로 둔 결과, Internal RCV 등장하니 깨짐 (`internal-use-rcv` 역추적 400 "Source 테이블이 존재하지 않습니다: IF_RSV_USE_JEJU_DAY"). 휴리스틱은 디버깅도 어렵게 함 — 실패 원인이 입력이 아니라 추론 가정이라 노출이 안 됨.

**How to apply**:
- 추적 코드 작성/수정 시 sync_log target_tables/source_tables `equalsIgnoreCase` 정확매칭 한 줄로 끝내기. 1순위로 항상.
- 정확매칭 실패 = 명확한 에러 (실패한 입력값 + 시도한 진실원 노출). 원본 그대로 두고 다른 데서 깨지게 두지 않음. "Source 테이블이 존재하지 않습니다: X" 처럼 입력 의미가 흐려진 메시지 금지.
- 휴리스틱 fallback 이 실제 발동하는 케이스 발견하면 = sync_log 정의가 부족한 것 → 그 step 의 `SyncLogWriter` 호출 보강(정의 쪽 수정), 추적 코드에 휴리스틱으로 메꾸지 않음.
- 옛 형식 호환(`I:dsId:tableName:pk` source_refs 의 tableName 추출)은 휴리스틱 아님 — 정의에 박힌 이름 그대로 씀. 단 잔존 데이터 정리 후엔 제거.
- `SyncLogWriter` 표준화 + `TableCountTracker` 와 한 묶음 — 정의 진실원이 단일 진입점([[feedback-synclog-writer]])이라 추적이 이걸 신뢰할 수 있는 거.

**관련 메모/계획**:
- 발견·정리: `dev_plan/2026_05/13/trace-definition-only.md` (전수 감사 10곳 + 정리 방향)
- LIKE 일체 폐기 강화: `dev_plan/2026_05/13/trace-equality-only.md` (backend exactSourceRefs 빌드 + proxy `=` 매칭)
- 4/22 절반 정리: 커밋 `67a0b3f` (분기 3 만 정확매칭으로, 1·2 유지)
- 진실원 ②: 커밋 `30f393e` (source_ref 표준화 — `zone:dsId:tableId:pk`)
- 진실원 ①: 커밋 `30f393e` (`SyncLogWriter` — sync_log 저장 단일 진입점)
- [[feedback-no-regression-organic]] — 휴리스틱 제거는 유기적 회귀 검토 동반
- [[feedback-no-guess-ask-first]] — 매칭 실패 시 추적 코드가 추측하지 말고 정의로 돌려보내라
