# TC_* 표준화 + Entity @Comment 보강 — B4 선행 작업

> 작성일: 2026-05-06
> 후속: B4 WellInfoHandler 본 구현 (`b4-wellinfo-handler.md`)
> 동반: `docs/Standardizedtable/_converted/standardized_detail.tsv`

---

## 1. 목적

표준화 자료 기준 11자리 테이블명 정합 — 현재 `TC_*` 만 표준화 미완료 (다른 TM_*는 적용 완료).
동시에 엔티티 `@Comment` 자료 부가설명 활용해 보강.

## 2. 영향 범위 — 전수 조사 결과

### 2.1 실 internal-oracle 미표준화 = **2개 테이블만**
| 표준화 전 (실 DB) | 표준화 후 (자료) | 컬럼 표준화 |
|---|---|:--:|
| `TC_GD00002` | `TC_GD000002` | ✅ 이미 적용됨 (UGRWTR_CMMN_CODE→UGWTR_COM_CD 등) |
| `TC_GD00100` | `TC_GD000100` | ✅ 이미 적용됨 (LEGALDONG_CODE→STDG_CD 등) |

→ **테이블명 RENAME 만 필요.** 컬럼은 이미 표준화돼있음.

### 2.2 영향 받는 코드 (8 파일)

**핸들러 SQL — 6 핸들러:**
| 파일 | 사용 테이블 |
|---|---|
| `ActualUseDetailDjHandler.java` (B16-DJ) | `TC_GD00100` |
| `ActualUseDetailKbHandler.java` (B16-KB) | `TC_GD00100` |
| `InspectionDistinctHandler.java` (B15) | `TC_GD00002` |
| `InspectionListHandler.java` (B14) | `TC_GD00002` |
| `WaterQualityInfoHandler.java` (B11) | `TC_GD00002` |
| `WaterQualityMfdsHandler.java` (B13) | `TC_GD00002` (helper) |

**DDL / 함수:**
| 파일 | 내용 |
|---|---|
| `scripts/ddl/internal-oracle/create-lookup-tables.sql` | TC_* 2개 정의 |
| `scripts/ddl/internal-oracle/provide-source/B4_functions.sql` (5/4 배치) | `FN_GD_GET_CMMTNDCODE` 본문 `FROM TC_GD00002` |

**기타:**
- `CustomOperationMetadata.java` 주석 — `TC_GD00002` 언급 (단순 주석, 동작 무관)

### 2.3 RGETN* — 자료 매핑 없음 (사용자 결정: 그대로)
RGETNSCKT01, RGETNSIMS01, RGETNTGMS02, RGETNWAMS01, RGETNWAVI05/06, RGETNYCSG01, RGETNYYMS01, RGETSTGMS01, RGETNPMMS01 — 모두 레거시 이름 그대로 사용.

### 2.4 Entity @Comment 보강 대상 (sync-agent-provide, 18 파일)

| 분류 | 엔티티 | 자료 매핑 활용도 |
|---|---|:--:|
| **자료 직매핑** | `ApiPrvTmGd000203`, `ApiPrvTmGd110301`, `ApiPrvTmGd110302`, `ApiPrvTmGd112002`, `ApiPrvTmGd120001`, `ApiPrvTmGd130001` | ✅ 자료 한글명/부가설명 직접 매핑 |
| **변환/조합** | `ApiPrvPermwell`, `ApiPrvActualUseDj`, `ApiPrvInspection`, `ApiPrvLinkageChart`, `ApiPrvNgw04`, `ApiPrvGeneral105`, `ApiPrvUnregitsFcly`, `ApiPrvWaterQuality`, `ApiPrvWaterQualityMfds`, `ApiPrvWqInputStatusDj` | ⚠️ 부분 매핑 (조인 결과 컬럼은 본 의미 추론 필요) |
| **레거시 직배치** | `ApiPrvWtDreamPermwellPublic21033`, `ApiPrvTmpMegokrApi` | ⚠️ 자료 매핑 없는 컬럼 다수 |

→ **방침**: 자료 매핑 있는 컬럼만 자료 한글명/부가설명으로 채우기. 매핑 없는 컬럼은 기존 @Comment 유지 또는 비워둠 (추측 X).

## 3. 작업 단위

### Phase A — TC_* 표준화 (~1.5h)

#### Step A1. 실 DB RENAME (10분)
```sql
RENAME TC_GD00002 TO TC_GD000002;
RENAME TC_GD00100 TO TC_GD000100;
-- 기존 데이터 자동 이동 (Oracle RENAME 기본 동작)
```

#### Step A2. DDL 스크립트 갱신 (15분)
- `create-lookup-tables.sql` — CREATE TABLE 이름 + COMMENT ON TABLE/COLUMN 갱신

#### Step A3. 5/4 함수 본문 갱신 (15분)
- `B4_functions.sql` — `FROM TC_GD00002` → `FROM TC_GD000002`
- `TC_GD00002.GROUP_CD_SN%TYPE` → `TC_GD000002.GROUP_CD_SN%TYPE`
- 재배치 (CREATE OR REPLACE FUNCTION) → 검증 7/7 재실행

#### Step A4. 6 핸들러 SQL 갱신 (30분)
- 각 파일의 SQL 상수 + 주석 + getMetadata()의 description 갱신
- TC_GD00100 → TC_GD000100 (2 파일)
- TC_GD00002 → TC_GD000002 (4 파일)

#### Step A5. CustomOperationMetadata 주석 정리 (5분)
- 단순 문서 주석. 표준화 후 이름으로.

#### Step A6. 컴파일 + 부팅 + 회귀 (15분)
- `./gradlew clean compileJava` (api-provider)
- bootRun → 부팅 OK
- 6 핸들러 각 1번씩 호출 → 200 OK

### Phase B — internal-oracle DDL COMMENT 보강 (옵션 A, ~30분)

> 정정: 5/6 사용자 영역 명확화 — 표준화는 internal-oracle 29004 측 원본 테이블이고, sync-agent-provide PG 측 엔티티는 그걸 보고 만든 후속물 (본 작업 범위 밖).

#### Step B1. `create-lookup-tables.sql` — TC_* 2개 자료 보강 (~25분)
- TC_GD00100 → TC_GD000100 (RENAME 후) — 자료 $9 한글명 + $16 도메인 활용
  - 컬럼 14개: STDG_CD, CTPV_NM, SGG_NM, EMD_NM, LI_NM, SORT_NO, CRT_YMD, DEL_YMD, LCLGV_CD, CTPV_ENG_NM, SGG_ENG_NM, EMD_ENG_NM, LI_ENG_NM (+ SN)
- TC_GD00002 → TC_GD000002 (RENAME 후) — 자료 매핑 활용
  - 컬럼: GROUP_CD_SN, UGWTR_COM_CD, CD_CN, REG_DT, MDFCN_DT, USE_YN, ANNLS_USE_YN, DEL_YMD 등

#### Step B2. 실 DB 에 ALTER COMMENT 직접 적용 (~5분)
- DDL 재실행으로는 RENAME 후 안전성 확인 필요 → `COMMENT ON COLUMN ...` 별도 스크립트로 실행
- `user_col_comments` 조회로 검증

> ② Type A 샘플 setup (B*_setup.sql 7개) / 다른 TM_* 자료 풍성화는 별 사이클

### Phase C — 통합 검증 (~30분)

#### Step C1. Provide 흐름 회귀
- sync-agent-provide 부팅 → ddl-auto 가 새 @Comment 적용 (PG COMMENT 갱신)
- 임의 1~2개 Type A 호출 → 정상

#### Step C2. 6 핸들러 회귀
- 각 핸들러 호출 → 응답 정합 (이전 응답과 동일)

#### Step C3. Phase 2~5 무결
- /api/manage/operations cookie 호출 → 200
- /auth/me → 200

#### Step C4. RGETN* 영향 0
- RGETNPMMS01 (B4 의존), RGETNTGMS02 (B16 의존) — 그대로 사용 검증

### Phase D — B4 WellInfoHandler 본 구현 (~1.5h)
별도 dev_plan: `b4-wellinfo-handler.md` 참고. SQL 안의 `TC_GD00100` → `TC_GD000100` 갱신만 추가.

## 4. 영향 / 회귀

### 영향 0
- Phase 2~5 (auth/JWT) — 본 작업은 internal-oracle 측 + JPA 엔티티 @Comment 만, 인증 흐름 무관
- 다른 17개 핸들러 — 본 작업 6개 외 변경 0

### 회귀 위험
| 위험 | 완화 |
|---|---|
| RENAME 후 다른 곳에서 옛 이름 참조 | grep 전수 조사 완료 (8 파일) |
| 함수 본문 갱신 후 기존 검증 깨짐 | 5/4 검증 7/7 재실행 |
| @Comment 갱신 시 컬럼 metadata 손상 | @Comment 는 어노테이션 추가만, 컬럼 정의 무관 |
| ddl-auto=update 가 PG COMMENT 갱신 안 함 (Hibernate 구현 한계 가능성) | 검증 시 실 PG `\d+ table` 로 COMMENT 확인 |

## 5. 산출물

| 종류 | 파일 |
|---|---|
| 코드 | 6 핸들러 .java + CustomOperationMetadata 주석 |
| DDL | `create-lookup-tables.sql`, `B4_functions.sql` (재배치) |
| 엔티티 | sync-agent-provide 의 ApiPrv*.java 6+개 (자료 매핑 가능 우선) |
| dev_log | 5/6 일지에 본 작업 항목 추가 |

## 6. 추정 시간

| Phase | 작업 | 시간 |
|:--:|---|:--:|
| A | TC_* 표준화 | ~1.5h |
| B | Entity @Comment 보강 | ~2h |
| C | 통합 검증 | ~30분 |
| D | B4 본 구현 | ~1.5h |
| **합계** | — | **~5.5h** |

## 7. 승인 항목

- [ ] 본 dev_plan 진행 OK?
- [x] **TC_* 표준화 = TC_GD00002→TC_GD000002, TC_GD00100→TC_GD000100** (사용자 5/6 결정)
- [x] **RGETN* = 자료 없는 것 = 레거시 그대로** (사용자 5/6 결정)
- [x] **Entity @Comment = 자료 매핑 가능 컬럼만 보강 (추측 X)** (사용자 5/6 결정 정합)
- [ ] Entity @Comment 작업 범위 — 6개 직매핑 엔티티 우선, 변환/조합 엔티티는 자료 매핑 명확한 컬럼만? 아니면 18개 모두 처리 시도?
