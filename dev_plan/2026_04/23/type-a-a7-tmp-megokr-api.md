# Type A7 이식 — TMP_MEGOKR_API

> 작성일: 2026-04-23 오후
> 선행: `dev_plan/2026_04/22/provide-source-table-strategy.md`, 오늘 A4 작업 완료
> 목적: Type A 마지막 한 건(A7, MEGOKR 수질검사결과 TMP) 이식

---

## 1. 수정 목적

### 1.1 이식 대상
- API: MEGOKR `selectNgw04_01` (수질검사결과 TMP)
- 소스: **`TMP_MEGOKR_API`** (NGW 스키마, 149컬럼, 이미 PIVOT된 임시 테이블)
- 타겟: **`api_prv_tmp_megokr_api`** (PG 제공 테이블, 149컬럼 동일 구조)
- 표준화 매핑 **없음** → 이전 이름 유지 (케이스 B)

### 1.2 판단 근거

사용자 결정 (2026-04-23 오후):
- 실서비스에 TMP_MEGOKR_API 가 존재하는지 불확실하지만, **우리가 받은 소스 기준으로는 이 양식이 존재**
- 이식 후 이름/구조가 바뀌면 DDL·엔티티 업데이트로 대응
- 로직 자체는 먼저 구현해둠

---

## 2. 컬럼 현황 (NGW 원본 TSV 기준)

- **총 149컬럼** (NULLABLE=Y 전부, "사용하지않음" 메타 표시는 NGW 원본 문서 태그일 뿐 실제 동작에는 무관)
- 구성:
  - 메타 24컬럼: SN(NUMBER,PK 후보), JOSACODE, QLTWTR_INSPCT_SN, GENNUM, INVSTG_YEAR, ODR, DPH_CL_CODE, DPH_VALUE, WATSMP_DE, QLTWTR_INSPCT_DE, DTA_INPUT_DE, DCSN_DE, FRST_REGIST_DT(DATE), LAST_CHANGE_DT(DATE), UGRWTR_PRPOS_CODE, DRNK_AT, UGRWTR_WQN_INPUT_INSTT_CODE, QLTWTR_INSPCT_IMPRTY_RESN_CTNT(VARCHAR2(4000)), BRTC_NM, SIGUN_NM(VARCHAR2(508)), EMD_NM, LI_NM, ADDR(VARCHAR2(1000)), PUBWELL_AT
  - 수질항목 125컬럼: `WT_*` 전부 `VARCHAR2(100)` (WT_TOT_COL_CNTS, WT_TOT_CLF, WT_FCL_CFS, ... WT_WTL)

### 2.1 PK 판정
레거시 쿼리(`SELECT ... WHERE SN >= ? ORDER BY SN FETCH FIRST 10000 ROWS ONLY`)에서 **SN이 사실상 PK**. NUMBER(22) NOT NULL 가정으로 PK 제약 부여.

### 2.2 레거시 API 응답 구조
```sql
SELECT SN AS QLTWTR_INSPCT_SN, WT_TOT_COL_CNTS, WT_TOT_CLF, ...(125 WT_*)
FROM TMP_MEGOKR_API
WHERE SN >= ?  [AND GENNUM = ?]
ORDER BY SN FETCH FIRST 10000 ROWS ONLY
```
- 응답 컬럼 126개 (SN→QLTWTR_INSPCT_SN 별칭 + 125 WT_*)
- 나머지 23컬럼은 응답에서 제외 (있어도 됨, 없어도 됨)

---

## 3. 수정 범위

### 3.1 Oracle DDL (신규)
`scripts/ddl/internal-oracle/provide-source/TMP_MEGOKR_API.sql`

- 스키마: **NGW** (= k1m 계정, 기본 스키마 — cross-schema 아님)
- 149컬럼 전부 정의 (NGW 원본 TSV 파싱 기반 자동 생성)
- PK: `SN` NUMBER(22) NOT NULL
- 추적 컬럼 5종 (LINK_STATUS, EXECUTION_ID, SOURCE_REFS, EXTRACTED_AT, UPDATED_AT)
- 멱등성 패턴 (ORA-00955 무시, ALTER ORA-01430 무시)
- 샘플 데이터 3~5건 (SN=1,2,3,... 대표 WT_* 값 몇 개)

### 3.2 provide 엔티티 (신규)
`sync-agent-provide/.../entity/target/ApiPrvTmpMegokrApi.java`

- 패턴 (오늘 확립한 통일):
  - `@Id sn IDENTITY` (PG 자체 PK)
  - `@UniqueConstraint(columnNames = {"source_refs"})`
  - 자연키 `sn_src` (Oracle SN 복사용 — 충돌 방지 리네임 필요 → Oracle SN → PG 컬럼명 그대로 `sn` 쓰면 IDENTITY 와 혼재. **컬럼명은 그대로 `sn`으로 두고 IDENTITY 와 구분은 위치/의미로**)
- 149 필드
  - 125 WT_* 컬럼은 일괄 `@Column(name="wt_xxx", length=100) private String wt_xxx;` + `@Comment("수질항목 {code}")`
- 추적 컬럼 3종 (execution_id, source_refs, updated_at)

#### 3.2.1 컬럼명 충돌 주의
- Oracle 소스의 `SN` 은 provide 통일 패턴에서 `AUTO_INCREMENT_PK_COLUMNS = [id, sn]` 에 매칭되어 **INSERT 제외 대상**으로 들어갈 수도 있음.
- 하지만 여기선 `SN`이 소스 PK(비즈니스 자연키) → source_refs 에 그 값이 들어가야. INSERT 에서는 제외 OK(타겟 sn IDENTITY 가 자동 채번).
- 우리가 오늘 적용한 로직 그대로: **YAML `primary-key: SN` + 타겟 sn IDENTITY 제외**. source_refs 는 SN 값으로 빌드됨. OK.

### 3.3 provide YAML (신규)
`sync-agent-provide/config/agents/provide-tmp-megokr-api.yml`
```yaml
agent-code: provide-tmp-megokr-api
type: LOADER

steps:
  - id: provide-tmp-megokr-api
    name: MEGOKR 수질검사결과 TMP 복사
    factory-key: source-to-if
    source-table: TMP_MEGOKR_API
    target-table: api_prv_tmp_megokr_api
    primary-key: SN
    conflict-key: source_refs
    target-meta-columns:
      - source_refs
      - execution_id
      - updated_at
```

### 3.4 Orchestrator Agent 등록
- agentCode: `provide-tmp-megokr-api`
- endpointUrl: `http://localhost:8096`
- zone: `INTERNAL_COMMON`, agentType: `LOADER`
- sourceDatasourceId: `internal`, targetDatasourceId: `api-provider`
- sourceTableNames: `["TMP_MEGOKR_API"]`, targetTableNames: `["api_prv_tmp_megokr_api"]`

---

## 4. 수정 파일

| 구분 | 파일 | 내용 |
|------|------|------|
| DDL | `scripts/ddl/internal-oracle/provide-source/TMP_MEGOKR_API.sql` | 149컬럼 + PK_SN + 추적컬럼 + 샘플 + 주석 |
| 엔티티 | `sync-agent-provide/.../entity/target/ApiPrvTmpMegokrApi.java` | 149 비즈니스 + 3 추적 + @Id sn IDENTITY + source_refs UK |
| YAML | `sync-agent-provide/config/agents/provide-tmp-megokr-api.yml` | factory-key=source-to-if |
| (코드 변경 없음) | common 수정 없음 — A4에서 이미 cross-schema 지원 추가됨, 이건 NGW 단일 스키마이므로 해당 없음 |

---

## 5. 작업 순서

| Phase | 내용 | 완료 조건 |
|:---:|------|---------|
| 1 | TMP_MEGOKR_API DDL 작성 (149컬럼) | 파일 생성 |
| 2 | 엔티티 `ApiPrvTmpMegokrApi` 작성 (149 필드) | 빌드 성공 |
| 3 | YAML 작성 | 파일 생성 |
| 4 | Oracle DDL 실행 (k1m 계정) → 샘플 PENDING 확인 | 샘플 건수 > 0 |
| 5 | provide 재빌드 + 재기동 | PG 테이블 재생성 + 8개 Agent 로드 |
| 6 | Orchestrator Agent 등록 + health-check | ONLINE |
| 7 | Agent 실행 → PG 적재 + Oracle LINK_STATUS → SUCCESS | 샘플 건수 = 적재 건수 |
| 8 | trace-source 검증 (SN 기반) | FOUND |

---

## 6. 리스크 / 주의

| 리스크 | 완화 |
|--------|------|
| 149 컬럼 엔티티 작성 오타 | WT_* 125개는 기계적 반복 — 스크립트 또는 차분 검토 |
| SIGUN_NM VARCHAR2(508) 등 특이 길이 | 원본 TSV 그대로 반영 (임의 수정 안 함) |
| 실서비스 테이블 존재 여부 불확실 | "우리가 받은 소스 기준" 이식 — 이후 이름/구조 변동 시 DDL·엔티티 교체만으로 대응 |
| TMP_MEGOKR_API가 이미 어디선가 만들어져 있을 수도 | 멱등성 패턴 DDL — ORA-00955 skip. 기존 테이블 있으면 ALTER 만 수행 |

---

## 7. 완료 후

- dev_logs 업데이트 (A7 완료 반영)
- Type A 6건 전부 완료 → Type B 전처리 Step 설계로 이동
- 전략문서(provide-source-table-strategy.md) A7 상태 업데이트

---

## 8. 승인 요청

- 소스 149컬럼 / 타겟 149컬럼 동일 구조 ✓ (사용자 결정)
- PK = SN ✓
- 엔티티 이름 `ApiPrvTmpMegokrApi` / 타겟 `api_prv_tmp_megokr_api` OK?
- DDL 실행 계정 k1m (TMP_MEGOKR_API 는 NGW 스키마 = k1m 기본) OK?

승인 주시면 Phase 1 착수.
