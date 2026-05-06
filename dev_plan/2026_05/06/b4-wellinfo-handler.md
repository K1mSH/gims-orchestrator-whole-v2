# B4 WellInfoHandler 본 구현 — Type B 17번째 핸들러

> 작성일: 2026-05-06
> 위치: `gims-api-provider:8095` (Type B 커스텀 핸들러)
> 선행: 5/4 Oracle 함수 사전 배치 (`FN_GD_GET_CMMTNDCODE` / `FN_GD_GET_GUBUN`) 7/7 검증 PASS
> 동반: `dev_plan/2026_05/04/b4-oracle-functions-ddl.md`

---

## 1. 목적

레거시 v3 의 인허가관정 상세정보 (`opnService/getWellInfo`) 를 Type B 커스텀 핸들러로 이식.

레거시 호출:
- v3 endpoint: `OPNController.java:309~311` — `service=opnService, operation=getWellInfo`
- v3 SQL: `sql_opn.xml:13~39` — `RGETNPMMS01` + `TC_GD000100` (5/6 표준화 RENAME 적용) LEFT OUTER JOIN + 함수 3종 호출

신 시스템:
- 핸들러 클래스: `WellInfoHandler` (gims-api-provider, custom/handler/)
- operationId: `opnService/getWellInfo` (v3 URL 그대로)
- 호출 시점 직접 쿼리 (제공 테이블 없음 — Type B 정합)

## 2. 영향 범위

### 신규
- `gims-api-provider/.../custom/handler/WellInfoHandler.java` (1 파일)

### 의존
- ✅ `FN_GD_GET_CMMTNDCODE` / `FN_GD_GET_GUBUN` (5/4 배치, internal-oracle k1m schema)
- ✅ Internal Oracle 29004 의 `RGETNPMMS01` / `TC_GD000100` 테이블 (확인됨)
- ✅ 기존 `CustomOperationHandler` / `CustomHandlerRegistry` 인프라 (4/27)
- ✅ `ProviderDataSourceService` (datasource_id="internal" 라우팅)

### 영향 없음
- 기존 16개 Type B 핸들러 — 코드 변경 0
- Type A 흐름 — 영향 0
- auth Phase 2~5 흐름 — 영향 0

## 3. v3 SQL → 신 시스템 변환

### v3 원본 (`sql_opn.xml:13~39`)
```sql
SELECT
   FN_GD_GET_GUBUN(A.PERM_NT_FORM_CODE, 1) PERM_NT_FORM_CODE,
   B.BRTC_NM || ' ' || B.SIGUN_NM || ' ' || B.EMD_NM || ' ' || DECODE(B.LI_NM, NULL, '', B.LI_NM || ' ') ADDR,
   FN_GD_GET_GUBUN(A.PERM_NT_FORM_CODE, 1) PERM_NT_FORM_CODE,    -- v3 원본 중복
   NVL(FN_GD_GET_CMMTNDCODE('NGW_0003', '0'||A.UWATER_SRV_CODE), ' ') UWATER_SRV_CODE,
   NVL(FN_GD_GET_CMMTNDCODE('NGW_0013', A.UWATER_DTL_SRV_CODE), ' ') UWATER_DTL_SRV_CODE,
   A.UWATER_POTA_YN UWATER_POTA_YN,
   DECODE(A.DIG_DPH, NULL, ' ', A.DIG_DPH) DIG_DPH,
   DECODE(A.DIG_DIAM, NULL, ' ', A.DIG_DIAM) DIG_DIAM,
   DECODE(A.ESB_DPH, NULL, ' ', A.ESB_DPH) ESB_DPH,
   DECODE(A.ND_QT, NULL, ' ', A.ND_QT) ND_QT,
   DECODE(A.FRW_PLN_QUA, NULL, ' ', A.FRW_PLN_QUA) FRW_PLN_QUA,
   DECODE(A.RWT_CAP, NULL, ' ', A.RWT_CAP) RWT_CAP,
   NVL(A.DYN_EQN_HRP, 0) DYN_EQN_HRP,
   DECODE(A.PIPE_DIAM, NULL, ' ', A.PIPE_DIAM) PIPE_DIAM
FROM RGETNPMMS01 A
LEFT OUTER JOIN TC_GD00100 B
   ON B.LEGALDONG_CODE = A.DVOP_LOC_REGN_CODE
WHERE A.REL_TRANS_CGG_CODE = #rel_trans_cgg_code#
   <isNotEmpty property="perm_nt_no" prepend="AND">
       A.PERM_NT_NO = #perm_nt_no#
   </isNotEmpty>
```

### 표준화 매핑 — TC_GD00100 (v3 → 실 컬럼)

| v3 컬럼 | 실 internal-oracle 컬럼 |
|---|---|
| `LEGALDONG_CODE` | `STDG_CD` |
| `BRTC_NM` | `CTPV_NM` |
| `SIGUN_NM` | `SGG_NM` |
| `EMD_NM` | (동일) |
| `LI_NM` | (동일) |

RGETNPMMS01 — v3 컬럼명 그대로 (표준화 영향 없음, 15개 사용 컬럼 모두 확인).

### 변환된 SQL (핸들러 안)
```sql
SELECT
   FN_GD_GET_GUBUN(A.PERM_NT_FORM_CODE, 1) AS PERM_NT_FORM_CODE,
   B.CTPV_NM || ' ' || B.SGG_NM || ' ' || B.EMD_NM || ' ' || DECODE(B.LI_NM, NULL, '', B.LI_NM || ' ') AS ADDR,
   NVL(FN_GD_GET_CMMTNDCODE('NGW_0003', '0'||A.UWATER_SRV_CODE), ' ') AS UWATER_SRV_CODE,
   NVL(FN_GD_GET_CMMTNDCODE('NGW_0013', A.UWATER_DTL_SRV_CODE), ' ') AS UWATER_DTL_SRV_CODE,
   A.UWATER_POTA_YN AS UWATER_POTA_YN,
   DECODE(A.DIG_DPH, NULL, ' ', A.DIG_DPH) AS DIG_DPH,
   DECODE(A.DIG_DIAM, NULL, ' ', A.DIG_DIAM) AS DIG_DIAM,
   DECODE(A.ESB_DPH, NULL, ' ', A.ESB_DPH) AS ESB_DPH,
   DECODE(A.ND_QT, NULL, ' ', A.ND_QT) AS ND_QT,
   DECODE(A.FRW_PLN_QUA, NULL, ' ', A.FRW_PLN_QUA) AS FRW_PLN_QUA,
   DECODE(A.RWT_CAP, NULL, ' ', A.RWT_CAP) AS RWT_CAP,
   NVL(A.DYN_EQN_HRP, 0) AS DYN_EQN_HRP,
   DECODE(A.PIPE_DIAM, NULL, ' ', A.PIPE_DIAM) AS PIPE_DIAM
FROM RGETNPMMS01 A
LEFT OUTER JOIN TC_GD000100 B
   ON B.STDG_CD = A.DVOP_LOC_REGN_CODE
WHERE A.REL_TRANS_CGG_CODE = ?
[ AND A.PERM_NT_NO = ? ]    -- perm_nt_no 가 있으면 추가
```

#### ✅ 결정 — PERM_NT_FORM_CODE 중복 제거 (5/6 사용자 승인)
- v3 SQL 에 같은 `PERM_NT_FORM_CODE` 가 두 번 (line 16, 18) — 같은 함수/인자/alias 의 정확한 중복.
- v3 작성자의 copy-paste 실수 추정. JDBC ResultSet → JSON 직렬화 시 같은 alias 두 번이면 키 1개로 덮어써 외부 응답에 1번만 노출.
- → **신 핸들러 SQL 에서도 1번만 박음.** 외부 응답은 v3 와 동일 (PERM_NT_FORM_CODE 1개).

#### ✅ 결정 — 응답 alias = v3 호환 (5/6 사용자 승인, 메모리 룰 `feedback_provide_response_v3_compat.md` 정합)
- 외부 응답 키 = **v3 컬럼명 그대로 (lowercase)** — 다른 16 핸들러 통일 패턴.
- 내부 SQL 의 표준화 매핑 (`STDG_CD/CTPV_NM/SGG_NM`) 은 SQL 안에서만 사용 → 외부 응답엔 노출 X.
- 표준화 효과 정리:

| 계층 | 처리 |
|---|---|
| **외부 응답 (JSON 키)** | v3 컬럼명 그대로 — `perm_nt_form_code`, `addr`, `uwater_srv_code`, `uwater_dtl_srv_code`, `uwater_pota_yn`, `dig_dph`, `dig_diam`, `esb_dph`, `nd_qt`, `frw_pln_qua`, `rwt_cap`, `dyn_eqn_hrp`, `pipe_diam` (lowercase, 13개) |
| **JDBC ResultSet alias (SQL `AS ...`)** | v3 alias 그대로 (RowMapper 가 lowercase 로 변환해 응답 키로) |
| **SQL 안 컬럼 참조** | `A.*` (RGETNPMMS01) = v3 그대로 / `B.*` (TC_GD000100, 5/6 RENAME) = 표준화 후 컬럼 (`STDG_CD`, `CTPV_NM`, `SGG_NM`) |
| **함수 호출** | 내부 표준화 후 함수 (5/4 배치, 입출력 동작 v3 동일) |

## 4. 핸들러 메타 (Java)

```java
@Slf4j @Component @RequiredArgsConstructor
public class WellInfoHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "opnService/getWellInfo";

    private static final String SQL = ...;   // §3 변환 SQL (필수+옵션 분기)

    private final ProviderDataSourceService dataSourceService;

    @Override public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
            .operationId(OPERATION_ID)
            .operationName("B4 인허가관정 상세")
            .description("관련 테이블: RGETNPMMS01 + TC_GD000100 LEFT JOIN\n함수: FN_GD_GET_GUBUN, FN_GD_GET_CMMTNDCODE")
            .datasourceId(DATASOURCE_ID)
            .tableName("RGETNPMMS01")
            .pageSize(20)
            .maxPageSize(100)
            // 13 응답 컬럼 (중복 제거 시) — alias = v3 호환 lowercase
            .column(perm_nt_form_code / addr / uwater_srv_code / uwater_dtl_srv_code /
                    uwater_pota_yn / dig_dph / dig_diam / esb_dph / nd_qt / frw_pln_qua /
                    rwt_cap / dyn_eqn_hrp / pipe_diam)
            // 파라미터 2종
            .param(rel_trans_cgg_code: required STRING, EQ)
            .param(perm_nt_no: optional STRING, EQ)
            .build();
    }

    @Override public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        // 1. rel_trans_cgg_code 필수 검증
        // 2. perm_nt_no 있으면 SQL 에 AND 추가, args 도 동적
        // 3. JdbcTemplate.query() — RowMapper 로 LinkedHashMap 응답 (lowercase alias)
        // 4. 페이징 — Oracle FETCH FIRST 또는 ROW_NUMBER (페이지 카운트가 통상 1~수십 건이라 단순 처리)
    }
}
```

## 5. 단계별 Step

### Step 1. 핸들러 클래스 작성 (~30분)
- 위 §4 메타 + §3 SQL + handle() 구현
- RowMapper 는 v3 응답 키 호환 lowercase

### Step 2. 컴파일 + 부팅 (~10분)
- `./gradlew clean compileJava`
- 부팅 로그에 `CustomHandlerRegistry: 등록된 핸들러 17개` (현재 16 → 17) 확인
- 메타 카탈로그에 `opnService/getWellInfo` 노출 확인

### Step 3. Operation 등록 (~10분)
- `/api/manage/operations` POST — operationId=`opnService/getWellInfo`, operationType=CUSTOM
- 메타 자동 동기화 (잠금)

### Step 4. 테스트 호출 (~20분)
실 RGETNPMMS01 데이터 샘플 1건 미리 확인 → 그 키로 호출:

```bash
# 운영자 cookie 필요 (Phase 2~5 적용됨)
curl -b cookies-admin.txt \
  "http://localhost:8095/api/provide/opnService/getWellInfo?rel_trans_cgg_code=...&perm_nt_no=..."
```

검증:
- 응답 200 + 1건 (perm_nt_no 지정 시)
- 13개 응답 키 모두 v3 호환 lowercase
- 함수 결과 (`PERM_NT_FORM_CODE`, `UWATER_SRV_CODE`, `UWATER_DTL_SRV_CODE`) 정상 변환
- ADDR concat 정상 (`CTPV_NM SGG_NM EMD_NM [LI_NM ]`)

### Step 5. 회귀 검증 (~10분)
- 기존 16개 핸들러 중 임의 1~2개 호출 → 영향 없음
- ApiPrvCallHistory 정상 기록

### Step 6. todo + dev_log + commit (~10분)
- `todo/system/08-provide-agent.md` — B4 본 구현 [x]
- dev_log 갱신
- commit

**합계 추정 ~1.5h**

## 6. 회귀 위험

### 위험 항목
| 위험 | 완화 |
|---|---|
| 함수 호출 (5/4 배치 함수) — 본 핸들러가 첫 사용 | 5/4 단위 검증 7/7 PASS, 의존성 명확 |
| TC_GD00100 표준화 매핑 누락 | §3 매핑 표 명시 (LEGALDONG_CODE→STDG_CD 등) |
| PERM_NT_FORM_CODE 중복 | §3 결정 필요 표시 — 사용자 확인 |
| Oracle DBLINK / 외부 스키마 의존 | 본 핸들러 = 같은 schema(k1m). DBLINK 사용 안 함 → 위험 0 |

### 영향 받는 흐름
- 신규 endpoint 1개만 추가 — 다른 endpoint 영향 0
- ApiPrvCallHistory 1건 추가 (호출 시)

## 7. 향후 정리 (별 사이클)
- todo/system 의 메모: `api_prv_permwell` 엔티티 모듈 위치 정합 (현재 `sync-agent-provide` / 다른 핸들러는 `gims-api-provider`) — Type B 본 구현은 엔티티 안 쓰므로 본 작업 무관. 정리는 별 사이클.

## 8. 승인 항목

- [x] **본 dev_plan 진행** (5/6 사용자 승인 — TC_* 표준화 + ApiKeyFilter 누락 보완 후 진행)
- [x] **PERM_NT_FORM_CODE 중복 제거 (1번만)** — 5/6 사용자 승인. v3 외부 응답 동등.
- [x] **응답 키 = v3 호환 lowercase** — 5/6 사용자 승인. 외부 응답 v3 동일 (내부 표준화는 SQL 안에서만).
- [x] **TC_GD000100 (5/6 표준화 RENAME)** SQL 적용. RGETNPMMS01 은 자료 매핑 없음 → 레거시 그대로.
