# provide Agent — 테이블별 이식 전략

> 작성일: 2026-04-22
> 목적: 레거시 API 3종(MEGOKR/가뭄119/OPN)을 provide Agent로 이식 시 **각 소스 테이블의 이식 방식**을 명확히 정리
> 참고: `docs/Standardizedtable/_converted/` (NGW 원본 + 표준화 매핑 JSON/TSV)

---

## 1. 이식 이름 체계 원칙

### 1.1 세 가지 이름 체계

| 체계 | 예시 | 설명 |
|------|------|------|
| 이전 이름 | `WT_DREAM_PERMWELL_PUBLIC` | 현재 운영 DB의 실제 테이블명 |
| 새 이름 (5자리) | `TM_GD32002` | 환경부 표준화 작업의 중간 명명 |
| 11자리 이름 | `TM_GD112002` | 최종 표준화 후 테이블명 |

이전 컬럼명 → 환경부표준 컬럼명도 별도 매핑 존재.

### 1.2 이식 기준 원칙

**원칙 1 — 표준화 후 기준 사용**: 11자리 테이블명 + 환경부표준 컬럼명으로 이식한다.

- Oracle 소스 DDL: 표준화 후 이름으로 생성 (운영 DB가 나중에 마이그레이션되면 그대로 매칭)
- provide Agent 엔티티: 표준화 후 이름 기반 (`ApiPrvTmGd112002` 등)
- 제공 PG 테이블: `api_prv_{표준화_후_이름_소문자}`
- YAML source-table: 표준화 후 이름

**예외**: 표준화 매핑이 없는 테이블은 **이전 이름 그대로** 쓰거나 **의미 기반 임의 이름** 사용 (아래 케이스 B 참조).

**원칙 2 — 실운영 스키마 구조 재현**: 개발 환경에서도 운영의 스키마 분리/다중 오너 구조를 그대로 재현한다.

- 예: SDE_NGWS 스키마 테이블은 NGW 스키마로 합치지 않고 **개발 Oracle에도 SDE_NGWS 스키마 생성**
- 이유: 이식률 최대화. 배포 시점에 YAML/DDL 재작성 최소화.
- 권한/추적 컬럼 제약은 운영 시점에 담당자와 별도 협의 (개발 환경에선 추가해 동작 검증)

### 1.3 개발 환경 스키마 구성

레거시 SQL에서 사용하는 스키마:
- **NGW**: 기본 (개발 환경에서는 k1m 계정이 그 역할을 겸함 — 별도 생성 없음)
- **SDE_NGWS**: 가뭄119의 `WT_DREAM_PERMWELL_PUBLIC_21033` 용 GIS 공간 데이터 스키마 — **신규 생성**
- **DBLINKUSR**: 수위/우량관측소 DB Link 경유 테이블 재현용 — **신규 생성**

신규 스키마 생성 스크립트: `scripts/ddl/internal-oracle/provide-source/00_create_schemas.sql`

접근 권한: 각 테이블 생성 후 **개별 테이블별**로 `GRANT SELECT, INSERT, UPDATE, DELETE ON {schema}.{table} TO K1M` 부여 (실운영 권한 모델 재현 — `GRANT ANY TABLE` 같은 광범위 권한 피함)

---

## 2. 이식 케이스 분류

각 소스 테이블을 3가지 케이스로 분류:
- **케이스 A**: 운영 DB 존재 + 표준화 매핑 있음 → 표준화 후 이름 사용
- **케이스 B**: 운영 DB 존재 + 표준화 매핑 없음 → 이전 이름 or 의미 기반 임의 이름
- **케이스 C**: 운영 DB에 없음 (외부 스키마 or DBLINK) → 별도 확인/접근 방안 필요

---

## 3. Type A 테이블 (단순 복사 대상) — 컬럼 분석서 기준 6건

| API | 이전 이름 | 운영 DB | 표준화 매핑 | 사용 이름 | 엔티티 | 상태 |
|-----|---------|:-----:|:---------:|---------|---------|:---:|
| NGW_08 | TM_GD00203 | ✓ (11c) | ✓ → TM_GD000203 | **TM_GD000203** | ApiPrvTmGd000203 | **완료** (파일럿) |
| NGW_09 | WT_DREAM_PERMWELL_PUBLIC | ✓ (34c) | ✓ → TM_GD112002 | **TM_GD112002** | ApiPrvTmGd112002 | 엔티티만, DDL/YAML 대기 |
| 가뭄119 | WT_DREAM_PERMWELL_PUBLIC_21033 | ✗ (SDE) | ✗ | **미정** | - | **보류** (스키마 확인 필요) |
| info_general | TM_GD10001 | ✓ (33c) | ✓ → TM_GD120001 | **TM_GD120001** | ApiPrvTmGd120001 | 엔티티만, DDL/YAML 대기 |
| info_yhjs_info | TM_GD50001 | ✓ (13c) | ✓ → TM_GD130001 | **TM_GD130001** | ApiPrvTmGd130001 | 엔티티만, DDL/YAML 대기 |
| NGW_04 TMP | TMP_MEGOKR_API | ✓ (149c) | ✗ | **TMP_MEGOKR_API** (이전) | ? | **미정** (매핑 없음 + 의미상 임시 테이블) |

### 3.1 Type A 이식 우선순위 제안

1. **TM_GD000203** (완료) — 파일럿
2. **TM_GD112002** (34컬럼, WT_DREAM_PERMWELL_PUBLIC) — 실 서비스 가치 높음, 표준화 매핑 있음
3. **TM_GD120001** (33컬럼, TM_GD10001) — 관측망, 핵심 API 중 하나
4. **TM_GD130001** (13컬럼, TM_GD50001) — 영향조사, 컬럼 적고 단순
5. **TMP_MEGOKR_API** — 판단 필요 (임시 테이블? 실제 운영?)
6. **WT_DREAM_PERMWELL_PUBLIC_21033** — 스키마 확인 후

---

## 4. Type B 테이블 (전처리 대상) — 17건 (+ VIEW 1건)

Type B는 **원본 소스 테이블들을 그대로 쓰되, 전처리 결과를 별도 PG 테이블에 적재**하는 패턴.
원본 소스 테이블도 provide Agent가 Oracle에서 읽어야 하므로 **개발 환경 DDL 필요**.

### 4.1 원본 소스 테이블 매트릭스 (전처리 input)

| 이전 이름 | 운영 DB | 표준화 매핑 | 컬럼수 | 용도 | Oracle DDL 필요 시 이름 |
|---------|:-----:|:---------:|:-----:|------|----------------------|
| TM_GD30301 | ✓ | → TM_GD110301 | 22 | 수질측정망검사개요 | TM_GD110301 |
| TM_GD30302 | ✓ | → TM_GD110302 | 3 | 수질측정망검사결과 (EAV) | TM_GD110302 |
| TM_GD30310 | ✓ | → TM_GD110310 | 15 | 수질검사항목별기준 | TM_GD110310 |
| **RGETNPMMS01** | ✓ | **✗** | 95 | 허가신고정보 | **RGETNPMMS01** (이전) |
| TC_GD00100 | ✓ | → TC_GD000100 | 13 | 공통법정동코드 | TC_GD000100 |
| TC_GD00002 | ✓ | → TC_GD000002 | 10 | 공통코드 | TC_GD000002 |
| TM_GD60001 | ✓ | → TM_GD970001 | 16 | ODM관측소 | TM_GD970001 |
| TM_GD60002 | ✓ | → TM_GD970002 | 20 | ODM관측소사양 | TM_GD970002 |
| TM_GD60101 | ✓ | → TM_GD970101 | 18 | ODM결과 | TM_GD970101 |
| TM_GD60130 | ✓ | → TM_GD970130 | 56 | ODM관정사양 | TM_GD970130 |
| TM_GD70002 | ✓ | → TM_GD980002 | 9 | 보조수위측정망 연계기록 | TM_GD980002 |
| PM_GD60201 | ✓ | → PM_GD970201 | 5 | ODM관측자료 | PM_GD970201 |
| TM_GD70201 | ✓ | → TM_GD110350 | 24 | 정기수질검사개요 | TM_GD110350 |
| TM_GD70202 | ✓ | → TM_GD110351 | 4 | 정기수질검사결과 | TM_GD110351 |
| TM_GD20910 | ✓ | → TM_GD010910 | 29 | 홈페이지사용자 | TM_GD010910 |
| TM_GD20930 | ✓ | → TM_GD010930 | 3 | 홈페이지이용실태담당지자체 | TM_GD010930 |
| **RGETNTGMS02** | ✓ | **✗** | 7 | 이용실태조사완료 | **RGETNTGMS02** (이전) |
| TM_GD00301 | ✓ | → TM_GD023001 | 30 | 미등록지하수시설 | TM_GD023001 |
| **VIEW_GTEST** | ✓ | **✗** | 101 | (뷰) 수질측정망 통합 | **VIEW_GTEST** (이전, 뷰로 생성?) |

### 4.2 다른 스키마 / 외부 DB 테이블 (케이스 C)

원칙 2에 따라 **개발 환경에도 실운영 구조 재현**.

| 이전 이름 | 소유자 | 용도 | 개발 환경 대응 |
|---------|-------|------|--------------|
| WT_DREAM_PERMWELL_PUBLIC_21033 | SDE_NGWS | 가뭄119 (A4) | **개발 Oracle에 SDE_NGWS 스키마 생성 + 테이블** |
| DBLINKUSR.DUBWLOBSIF | 외부 DB (DB Link) | B7 수위관측소 | **담당자 확인 후 별도 결정** (DB Link 재현 or 별도 스키마 대체) |
| DBLINKUSR.DUBMMWL | 외부 DB | B7 | 동상 |
| DBLINKUSR.V_WP_WKSDAMSBSN | 외부 DB | B7/B8 | 동상 |
| DBLINKUSR.V_WR_HACHEON_MST | 외부 DB | B7 | 동상 |
| DUBRFOBSIF | 외부 DB | B8 우량관측소 | 동상 |
| DUBMMRF | 외부 DB | B8 | 동상 |

#### SDE_NGWS 스키마 처리 상세

ProvideLoadStep의 기존 구조가 이미 크로스 스키마 지원 (`tableName.contains(".")` 분기). YAML에 `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033` 형식으로 지정하면 그대로 동작.

개발 환경 준비:
1. Oracle 29004에 `SDE_NGWS` 유저/스키마 생성 + 권한 부여
2. `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033` DDL (WT_DREAM_PERMWELL_PUBLIC + OBJECTID)
3. `k1m` 계정에 해당 스키마 SELECT/UPDATE 권한 부여 (또는 public synonym)
4. 추적 컬럼은 개발 환경에만 추가 (운영 SDE 테이블 제약 따로 협의)
5. 샘플 데이터 몇 건

#### DBLINK 처리 고려

DB Link 기반 외부 테이블은 실운영 재현 어려움. 옵션:
- (1) 개발 Oracle에 별도 스키마(`DBLINKUSR`)로 생성 + synonym
- (2) DB Link 재현 (외부 Oracle 추가 컨테이너 — 복잡)
- (3) 담당자 확인 후 결정 (가장 현실적)

당분간 **(3) 보류**, 담당자 답변 후 (1) 또는 (2) 결정.

### 4.3 Type B 전처리 결과 테이블 (PG 제공 테이블)

전처리 Step이 적재하는 결과 테이블들 — 원본 테이블 매핑 없이 **의미 기반 임의 이름**.
어제 이미 엔티티 작성됨:

| 엔티티 | PG 테이블 | 대응 API (컬럼 분석서 B 번호) |
|-------|----------|---------------------------|
| ApiPrvNgw04 | api_prv_ngw04 | B3 MEGOKR NGW_04 (PIVOT 125컬럼) |
| ApiPrvPermwell | api_prv_permwell | B4 OPN info_permwell (JOIN+함수) |
| ApiPrvGeneral105 | api_prv_general_105 | B5 OPN info_general_105 (5JOIN) |
| ApiPrvLinkageChart | api_prv_linkage_chart | B9/B10 관측그래프/시간서비스 |
| ApiPrvWaterQuality | api_prv_water_quality | B11/B12 waterQualityInfo |
| ApiPrvWaterQualityMfds | api_prv_water_quality_mfds | B13 식약처 수질 |
| ApiPrvInspection | api_prv_inspection | B14/B15 검사항목 |
| ApiPrvActualUseDj | api_prv_actual_use_dj | B16 대전 이용실태 |
| ApiPrvUnregitsFcly | api_prv_unregits_fcly | B17 대전 미등록시설 |
| ApiPrvWqInputStatusDj | api_prv_wq_input_status_dj | B18 대전 수질입력현황 |

---

## 5. 이식 작업 단위 (테이블 1개당)

### 케이스 A (표준화 매핑 있음)
1. `scripts/ddl/internal-oracle/provide-source/{11자리이름}.sql`
   - 표준화 후 컬럼(환경부표준) 기반 DDL
   - 추적 컬럼 추가: LINK_STATUS / EXECUTION_ID / SOURCE_REFS / EXTRACTED_AT / UPDATED_AT
   - 샘플 데이터 3~5건 INSERT (PENDING 상태)
2. Oracle 컨테이너에 DDL 실행
3. provide Agent 엔티티 (이미 있음) 확인
4. `sync-agent-provide/config/agents/provide-{11자리이름_소문자}.yml` 작성
5. 재기동 → 실행 → 추적 검증

### 케이스 B (매핑 없음)
1. 원본 컬럼을 NGW 원본 문서에서 추출
2. `scripts/ddl/internal-oracle/provide-source/{이전이름}.sql`
   - 원본 컬럼 그대로 + 추적 컬럼 추가
3. 엔티티/PG 테이블 이름: 의미 기반 임의 (이미 일부 존재)
4. YAML: source-table = 이전 이름

### 케이스 C (운영 DB 없음)
- 담당자 확인 후 별도 논의 (이번 범위 밖)

---

## 6. 우선순위 로드맵

### 단기 (이번 주)
- [x] **TM_GD000203** (Type A) — 파일럿 완료
- [ ] **TM_GD112002** (WT_DREAM_PERMWELL_PUBLIC, Type A, 34컬럼) — 다음 대상
- [ ] TM_GD120001 (TM_GD10001, Type A, 33컬럼)
- [ ] TM_GD130001 (TM_GD50001, Type A, 13컬럼)

### 중기
- Type B 단순 매핑 있는 것부터 (TM_GD110301, TM_GD110302 등)
- VIEW_GTEST (뷰 정의 확보 필요)

### 장기 / 보류
- TMP_MEGOKR_API — 임시 테이블 성격. 실운영 쓰임새 확인 필요
- RGETNPMMS01 / RGETNTGMS02 — 표준화 매핑 없음. 이전 이름으로 갈지, 새 매핑을 추가할지 결정
- WT_DREAM_PERMWELL_PUBLIC_21033 + DBLINKUSR.* — 담당자 확인 후

---

## 7. 참조 파일

| 파일 | 용도 |
|------|------|
| `docs/Standardizedtable/_converted/ngw_original.tsv` | NGW 운영 DB 전체 스키마 (grep용) |
| `docs/Standardizedtable/_converted/ngw_tables.json` | NGW 운영 DB 테이블별 컬럼 (탐색용) |
| `docs/Standardizedtable/_converted/standardized_mapping.json` | 이전→표준화 매핑 (테이블명+컬럼명+타입) |
| `docs/Standardizedtable/_converted/table_name_mapping.tsv` | 테이블명 매핑 빠른 인덱스 |
| `dev_plan/2026_04/21/provide-api-column-analysis.md` | 레거시 SQL 25건 분류 + 컬럼 정의 |
| `dev_plan/2026_04/21/provide-agent-plan.md` | 모듈 설계 / 증분 전략 |
