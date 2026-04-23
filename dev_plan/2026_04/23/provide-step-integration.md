# provide Step → 공통 SourceToIfStep 통합

> 작성일: 2026-04-23 (오늘 오후 재설계)
> 선행:
>   - `dev_plan/2026_04/23/type-a-remaining-migration.md` — Phase 0~6 완료 후 trace 버그 발견
>   - 설계 비교 결과 (SourceToIfStep vs ProvideLoadStep 패턴 diff)
> 목적: **ProvideLoadStep 폐기 + SourceToIfStep 재사용**으로 중복 제거 + trace 버그 근본 해결

---

## 0. 배경 — 오늘 오후 발견된 사항 요약

1. **trace-source 0건 버그** (TM_GD112002): target source_refs가 merge-key(PRMSN_DCLR_NO) 기준으로 생성되어 Oracle PK(SN)와 불일치
2. 원인 분석: **ProvideLoadStep이 SourceToIfStep의 PK 탐지 로직을 재사용하지 않음**
3. 근본 분석: **ProvideLoadStep이 공통 Step(SourceToIfStep)을 재사용하지 않고 별도 구현**되어 여러 기능 누락
   - PK 탐지 로직 (→ trace 버그 원인)
   - 조건실행(conditions) 지원
   - 시간범위(startTime/endTime) 지원
   - fullCopy 모드
   - PK 기준 중복 제거
   - 메모리 룰(`feedback_condition_query_common.md`) 위배
4. 결정 (사용자): **공통 Step으로 통합**. ProvideLoadStep 제거. 회귀 테스트로 안정성 확보

---

## 1. 현재 상태 / 목표 상태

### 현재
| 모듈 | Step | Factory | 특징 |
|------|------|---------|------|
| common | `SourceToIfStep` | `SourceToIfStepFactory` (factory-key=source-to-if) | PK 탐지 + 조건실행 + 시간범위 + fullCopy |
| provide | `ProvideLoadStep` | `ProvideLoadStepFactory` (factory-key=provide-load) | merge-key 기반, 기능 축소 |

### 목표
| 모듈 | Step | Factory | 특징 |
|------|------|---------|------|
| common | **`SourceToIfStep` (중립화)** | `SourceToIfStepFactory` | 타겟 메타 컬럼 세트 Config에서 주입, IF 네이밍 제거 |
| provide | **제거** | **제거** | SourceToIfStepFactory 재사용 |

---

## 2. 수정 범위

### 2.1 common — `SourceToIfStep` → `SourceToTargetStep` 중립화 + 개명

#### 클래스명 변경
| 현재 | 변경 후 | 비고 |
|------|--------|------|
| `SourceToIfStep.java` | **`SourceToTargetStep.java`** | 파일/클래스명 변경 |
| `SourceToIfStepFactory.java` | **`SourceToTargetStepFactory.java`** | 파일/클래스명 변경 |
| `factory-key: "source-to-if"` | **그대로 유지** | 20개 기존 YAML 호환. 별도 이슈에서 마이그레이션 |

#### 클래스 상단 Javadoc 업데이트 (성격 명시)

```java
/**
 * Source → Target 복사 Step — 모든 Agent의 기본 카피 클래스
 *
 * Source 테이블에서 데이터를 조회(Extract)하여 Target 테이블에 적재(Load)하는 공통 Step.
 * Agent 유형(RCV / SND / Loader / Provide)과 무관하게 "외부 DB에서 데이터 가져와 자체 관리 테이블로 옮기기" 패턴은
 * 모두 이 Step 하나로 처리한다.
 *
 * ── 사용처 ──
 *  - RCV: 외부 업체 DB → IF_RSV (bojo/others)
 *  - SND: Target → IF_SND (bojo)
 *  - Internal RCV: IF_SND → 내부 IF_RSV (bojo-int)
 *  - Provide: Oracle 원본 → PG 제공 테이블 (provide)
 *  - 향후 새 Agent 추가 시에도 같은 Step을 재사용하는 것이 원칙
 *
 * 커스텀 변환·PIVOT·JOIN 등 복잡한 로직이 필요한 경우만 별도 Step 작성 (예: JejuJewonLoadStep, LinkTableUpdateStep).
 * 단순 1:1 복사는 반드시 이 Step 재사용.
 * ...
 */
```

#### 네이밍 / 의미 중립화
| 현재 | 변경 후 | 이유 |
|------|--------|------|
| `IF_META_COLUMNS` (상수) | `TARGET_META_COLUMNS_DEFAULT` | IF 전제 제거 |
| `getTargetIfTable()` (Config) | `getTargetTable()` | 타겟이 IF만은 아님 |
| 주석 "IF 테이블" | "Target 테이블" | |
| `AUTO_INCREMENT_PK_COLUMNS = ["id"]` | `["id", "sn"]` | provide target은 sn(IDENTITY) 사용 |

#### 동작 분기 — Config 기반
| 기존(IF 타겟 가정) | 변경 후 |
|----|----|
| 메타 컬럼 5종 INSERT 고정 | Config의 `targetMetaColumns`로 선택 — 기본 5종, provide는 `[source_refs, execution_id, updated_at]` |
| UPDATE SET에 link_status / extracted_at 포함 | 타겟에 해당 컬럼이 있을 때만 포함 (런타임 컬럼 감지 or Config flag) |
| `skipSourceStatusUpdate=false` 기본 | 기존대로 |

#### 기능 유지 (동일 동작 보장)
- `detectSourcePrimaryKey()` 그대로
- 조건실행 / 시간범위 / fullCopy 로직 그대로
- `updateSourceLinkStatus()` 그대로
- SyncLog `sourcePkColumn` 기록 그대로 (`config.getPrimaryKeyColumn()`)

### 2.2 common — `ExtractStepConfig` 확장

```java
// 신규 필드
private final List<String> targetMetaColumns;   // null이면 기본 IF 메타 5종
private final Boolean targetHasLinkStatus;      // null이면 타겟 컬럼 자동 감지 (현재 방식 유지)
```

기존 필드 변경 없음. 하위호환 유지.

### 2.3 common — Factory

- `SourceToIfStepFactory`: YAML에서 `target-meta-columns`, `target-has-link-status` 읽기 추가
- 기존 `target-table` 키 그대로 (이미 이름 중립적)

### 2.4 provide — YAML 마이그레이션 (6개)

**변경 전**
```yaml
steps:
  - id: provide-tm-gd112002
    factory-key: provide-load
    source-table: TM_GD112002
    target-table: api_prv_tm_gd112002
    merge-key: PRMSN_DCLR_NO
```

**변경 후**
```yaml
steps:
  - id: provide-tm-gd112002
    factory-key: source-to-if
    source-table: TM_GD112002
    target-table: api_prv_tm_gd112002
    primary-key: PRMSN_DCLR_NO          # merge-key → primary-key
    conflict-key: source_refs           # provide 계층 UK 고정
    skip-source-status-update: false    # Oracle 소스에 쓸 권한 있음
    target-meta-columns: [source_refs, execution_id, updated_at]
```

6개 YAML 일괄 수정:
- provide-tm-gd000203.yml
- provide-tm-gd110301.yml
- provide-tm-gd110302.yml
- provide-tm-gd112002.yml
- provide-tm-gd120001.yml
- provide-tm-gd130001.yml

### 2.5 provide — 파일 삭제

| 파일 | 비고 |
|------|------|
| `loader/step/ProvideLoadStep.java` | 삭제 |
| `loader/factory/ProvideLoadStepFactory.java` | 삭제 |

### 2.6 provide — 의존성 등록

`SourceToIfStepFactory`는 common에 있고 `@Component`이므로 provide가 common 의존하면 자동 주입됨. **ProvideAgentApplication의 컴포넌트 스캔 범위가 common까지 포함되는지 확인** 필요.

---

## 3. 회귀 체크리스트 (필수)

### 3.1 SourceToIfStep 사용 Agent 전수

| 모듈 | Agent 예시 | 시나리오 | 확인 항목 |
|------|-----------|--------|---------|
| **bojo** (DMZ RCV) | daejeon-rcv-jewon | 제원 1건 RCV | read/write, source_refs 포맷, link_status |
| **bojo** (DMZ RCV) | daejeon-rcv-obsvdata | 관측 1건 RCV (link 기반) | read/write, source_refs, link 업데이트 |
| **bojo** (DMZ SND) | dmz-snd-jewon / obsvdata | 1건 SND | read/write, source_refs, IF_SND 적재 |
| **bojo-int** (Internal RCV) | api-collect-loader | API 수집 → IF_RSV | read/write 건수, SyncLog |
| **others** | (있는 것들) | RCV | 동일 |
| **provide** (신규) | 4개 Agent | 전수 | source_refs PK 기반 정상, trace-source 정상 |

### 3.2 통합 전/후 비교 방법

통합 직전 스냅샷 저장 → 통합 후 재실행 → diff:
```sql
-- 스냅샷 예시
\copy (SELECT id, source_refs, link_status, execution_id FROM if_rsv_sec_jewon ORDER BY id DESC LIMIT 10) TO '/tmp/snap_if_rsv_sec_jewon_before.csv' CSV;
```

### 3.3 필수 통과 기준

- [ ] 모든 기존 Agent가 **source_refs 값 동일** (포맷 `"X:dsId:tbId:pk"` 일관성)
- [ ] read/write/skip 건수 동일
- [ ] SyncLog 메타 동일 (mappingName, sourcePkColumn)
- [ ] /trace 정방향 정상
- [ ] /trace-source 역방향 정상 (provide 포함)
- [ ] 조건실행(bojo 기존 케이스) 정상
- [ ] 시간범위 실행 정상

---

## 4. 영향 범위

### 4.1 JAR / 모듈
- **common JAR 수정** → 재빌드 → 6개 모듈(bojo / bojo-int / others / provide / proxy-dmz / proxy-internal) `libs/` 업데이트
- provide 내부 파일 삭제 → provide 재빌드

### 4.2 데이터
- provide PG `api_prv_*` 4개 테이블 drop → ddl-auto 재생성
- Oracle LINK_STATUS PENDING 전환 (4개 테이블)

### 4.3 UI / 프론트
- 변경 없음 (API 규격 동일)

### 4.4 Orchestrator
- 변경 없음

---

## 5. Phase 순서 (오늘)

| Phase | 내용 | 완료 조건 |
|:---:|------|---------|
| 0 | 회귀 스냅샷 저장 (현재 상태 실행 결과 백업) | IF/Target 최근 10건 CSV 백업 |
| 1 | `ExtractStepConfig` 확장 (`targetMetaColumns`, `targetHasLinkStatus`) | 빌드 성공 |
| 2 | `SourceToIfStep` 중립화 (이름 + 메타 컬럼 분기) | 빌드 성공, 기존 동작 동일 |
| 3 | `SourceToIfStepFactory` YAML 필드 추가 | 빌드 성공 |
| 4 | common JAR 재빌드 + 6개 모듈 배포 | 6개 모듈 bootRun 정상 |
| 5 | 회귀 검증 Phase 1 — bojo/bojo-int/others 기존 Agent 실행 | 스냅샷 diff 0건 |
| 6 | provide YAML 6개 마이그레이션 (factory-key/필드 변경) | 파일 갱신 |
| 7 | `ProvideLoadStep` / `ProvideLoadStepFactory` 삭제 | 빌드 성공 |
| 8 | provide 재빌드 + 재기동 | 4개 Agent 로드 확인 |
| 9 | provide PG 4개 테이블 drop + Oracle LINK_STATUS PENDING | 데이터 초기화 |
| 10 | provide 4개 Agent 재실행 + trace-source 검증 | source_refs PK 기반, trace 정상 |
| 11 | dev_logs 작성 + 커밋 제안 | 완료 |

---

## 6. 리스크 / 롤백

| 리스크 | 발생 조건 | 완화 |
|--------|---------|------|
| SourceToIfStep 중립화로 bojo/others 회귀 발생 | 메타 컬럼 분기 버그 | Phase 5에서 스냅샷 diff로 조기 발견 |
| provide 타겟에 link_status 없어서 INSERT 실패 | targetMetaColumns 필터 미적용 | Config flag 처리 + 단위 검증 |
| Factory 컴포넌트 스캔 누락 | provide가 common @Component 못 찾음 | @ComponentScan 확인 |
| 조건실행 로직이 provide 타겟에 안 맞음 | link_status 기반 필터 default 활성화 | Config `target-meta-columns` 지정 시 link_status 필터 스킵 |

### 롤백 절차
1. common JAR 이전 버전으로 되돌림 (git revert 해당 커밋)
2. 6개 모듈 libs/ 복구
3. provide YAML / 삭제 파일 git checkout
4. 각 모듈 재기동

---

## 7. 완료 후 할 일

- `dev_logs/2026_04/2026-04-23.md` 작성 (오전 리팩토링, 오후 Step 통합, 회귀 검증)
- 메모리 추가 검토:
  - `feedback_step_integration_over_duplication.md` (가칭) — 새 Agent 추가 시 공통 Step 재사용 우선 원칙
- 커밋 제안 (Step 통합 + 회귀 검증 결과 포함)
- A4(SDE_NGWS) / A7(TMP_MEGOKR_API) 이식 / Type B 전처리는 기존 계획대로 별도 이슈

---

## 8. 사용자 확인 포인트

1. **중립화 방식** — `targetMetaColumns` 같은 Config 필드 추가로 가는 방향 OK? 아니면 Step 내부에서 타겟 컬럼 메타데이터 자동 감지만으로 해결?
2. **`AUTO_INCREMENT_PK_COLUMNS`에 `"sn"` 추가** — 기존 bojo 테이블에 `sn` 컬럼이 있는 곳은 없는지? 있으면 충돌 가능. 확인 필요
3. **Phase 5 회귀 검증 범위** — bojo/bojo-int/others 각 Agent 전부 vs 대표 Agent만
4. **조건실행 기능 provide에도 활성화** — 지금 당장 활성? 또는 YAML로 옵션 유지?

승인 주시면 Phase 0(스냅샷)부터 착수하겠습니다.
