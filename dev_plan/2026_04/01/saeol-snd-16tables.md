# 새올 SND — 16개 테이블 등록 계획

## 목적
새올 Oracle(29005)에서 LINK_PLAN 기반으로 변경분 읽어서 **같은 Oracle에 IF_SND 적재**.
sync-agent-others(8085)에 YAML + 커스텀 SND step 추가.

## 구조

```
[새올 Oracle 29005]                    [PG dev 29001]
RGETSTGMS01 (소스)                     tb_jeju_jewon (소스)
     ↓ SND (LINK_PLAN 기반)                ↓ SND (link_status 기반)
IF_SND_RGETSTGMS01 (Oracle)            if_snd_tb_jeju_jewon (PG)
     ↓ Proxy                              ↓ Proxy
[Internal]                             [Internal]
IF_RSV (Oracle RCV agent)              IF_RSV (PG RCV agent) ← 기존
     ↓ Loader                              ↓ Loader
GIMS Oracle                            GIMS Oracle
```

- **소스 DB = IF_SND DB** 원칙 유지 (구조적 일관성)
- 새올 IF_SND: Oracle(29005)에 생성 — DDL로 초기 생성
- 내부망 RCV: 새올 전용 agent 따로 분리 (Oracle IF_SND 담당)

## 전제
- 소스: 새올 Oracle (29005, XEPDB1, k1m/1111) — 읽기 전용
- IF_SND: **같은 Oracle(29005)**에 생성
- IF_SND 테이블명: `IF_SND_XXX` (Oracle 대문자)
- 가공 없음 (순수 copy)
- DDL 원본: `D:\dev\claude\copySource\test\dmz\dmz_tables\sol.txt`

## 증분 전략
- **새올 → IF_SND**: LINK_PLAN 테이블 기반
  - LINK_PLAN: link_idx(순번), table_name, flag(I/U/D), PK값들
  - 마지막 처리 link_idx 기록 필요
- **IF_SND 이후**: 우리 표준 (link_status + execution_id + source_refs)
- **flag → link_status 매핑**:
  - I/U → `PENDING` (MERGE/upsert)
  - D → `DELETE` (삭제 처리)
  - 처리 완료 → `SUCCESS`

## TODO (후속)
- [ ] link_status에 `DELETE` 값 추가 (전 모듈 IUD 체계 전환 시)
- [ ] trace 로직에서 Oracle IF_SND 테이블명 처리

## 16개 테이블

| # | 소스 (Oracle) | IF_SND (Oracle) | 컬럼 | PK | 도메인 |
|---|--------------|-----------------|------|----|--------|
| 1 | RGETSTGMS01 | IF_SND_RGETSTGMS01 | 90 | CGG_CODE,PERM_NT_NO,YY_GBN | 이용실태 |
| 2 | RGETNPMMS01 | IF_SND_RGETNPMMS01 | 95 | CGG_CODE,PERM_NT_NO | 허가신고 |
| 3 | RGETNWAVI05 | IF_SND_RGETNWAVI05 | 12 | CGG_CODE,PERM_NT_NO,QW_ISP_SNO | 수질검사 |
| 4 | RGETNWAVI06 | IF_SND_RGETNWAVI06 | 8 | 5컬럼 복합PK | 수질검사내역 |
| 5 | RGETNMNFE01 | IF_SND_RGETNMNFE01 | 37 | CGG_CODE,SNO | 인력장비 |
| 6 | RGETNOPMS01 | IF_SND_RGETNOPMS01 | 46 | CGG_CODE,PERM_NT_NO,JGONG_NT_SNO | 이용신고 |
| 7 | RGETNTGMS02 | IF_SND_RGETNTGMS02 | 7 | CGG_CODE,SF_TEAM_CODE,CRIT_YY | 지표수수질 |
| 8 | RGETNKCNO01 | IF_SND_RGETNKCNO01 | 57 | CGG_CODE,PERM_NT_NO,DIG_ORD (레거시 기준) | 케이싱 |
| 9 | RGETNWAMS01 | IF_SND_RGETNWAMS01 | 8 | CGG_CODE,QW_ISP_SORT_CODE,LIST_CODE | 지표수환경기준 |
| 10 | RGETNSIMS01 | IF_SND_RGETNSIMS01 | 27 | CGG_CODE,OP_UPCH_REGNUM,OP_UPCH_SNO | 심화조사 |
| 11 | RGETNYYMS01 | IF_SND_RGETNYYMS01 | 26 | CGG_CODE,ORG_REGNUM,ORG_SNO | 용년 |
| 12 | RGETNJHMS01 | IF_SND_RGETNJHMS01 | 27 | CGG_CODE,PRFCN_UPCH_REGNUM,PRFCN_UPCH_SNO | 정화조 |
| 13 | RGETNSCKT01 | IF_SND_RGETNSCKT01 | 55 | CGG_CODE,MW_TAKE_NO | 스케치 |
| 14 | RGETNKMTB01 | IF_SND_RGETNKMTB01 | 12 | CGG_CODE,MW_TAKE_NO | 공간매체 |
| 15 | RGETHKMIR01 | IF_SND_RGETHKMIR01 | 19 | CGG_CODE,PERM_NT_NO,EXE_HIS_YMD,ORD | 현황 |
| 16 | RGETNYCSG01 | IF_SND_RGETNYCSG01 | 28 | CGG_CODE,MW_TAKE_NO | 연락처 |

## 작업 순서

### 1. Oracle DDL — 소스 16개 + LINK_PLAN + IF_SND 16개
- sol.txt에서 소스 DDL 추출 → K1M 스키마, VARCHAR→VARCHAR2
- IF_SND DDL: 소스 컬럼 + 공통 컬럼 (ID, EXECUTION_ID, SOURCE_REFS, EXTRACTED_AT, UPDATED_AT, LINK_STATUS)
- `scripts/saeol-oracle-init.sql`
- 테스트 데이터: 소스 테이블별 2~3건 + LINK_PLAN에 대응 이벤트

### 2. 커스텀 SND Step
- `SaeolLinkPlanSndStep`: LINK_PLAN 조회 → flag별 분기 → 소스 SELECT → IF_SND UPSERT/마킹
- 마지막 link_idx 기록
- sync-agent-others에 구현

### 3. YAML 작성
- `config/agents/dmz-others-snd-saeol.yml`
- factory-key: `saeol-link-plan-snd` (커스텀 step)
- source/target 모두 Oracle(29005)
- `link-plan-keys`: LINK_PLAN 컬럼 → 소스 WHERE 컬럼 매핑 (테이블마다 다름)
- 일부 테이블은 LINK_PLAN에 전체 PK 없음 → 그룹 단위 재동기화 (레거시 동일)

#### step 구조
- Runner가 LINK_PLAN 1회 조회 (link_idx 범위) → table_name별 그룹핑
- 16개 step 순회하며 자기 테이블 행만 전달
- 각 step: 받은 행의 키로 소스 SELECT → IF_SND UPSERT

#### YAML step 예시
```yaml
- id: saeol-stgms01
  source-table: RGETSTGMS01
  target-table: IF_SND_RGETSTGMS01
  primary-key: REL_TRANS_CGG_CODE,PERM_NT_NO,YY_GBN
  link-plan-keys:
    sf_team_code: REL_TRANS_CGG_CODE
    perm_nt_no: PERM_NT_NO
    yy_gbn: YY_GBN

- id: saeol-nopms01
  source-table: RGETNOPMS01
  target-table: IF_SND_RGETNOPMS01
  primary-key: REL_TRANS_CGG_CODE,PERM_NT_NO,JGONG_NT_SNO
  link-plan-keys:          # LINK_PLAN에 2개만 → 그룹 SELECT
    sf_team_code: REL_TRANS_CGG_CODE
    perm_nt_no: PERM_NT_NO
```

#### LINK_PLAN → 소스 WHERE 매핑 전체

| 테이블 | LINK_PLAN 키 | 소스 WHERE 컬럼 | 비고 |
|--------|-------------|----------------|------|
| RGETSTGMS01 | sf_team_code,perm_nt_no,yy_gbn | REL_TRANS_CGG_CODE,PERM_NT_NO,YY_GBN | 1:1 |
| RGETNPMMS01 | sf_team_code,perm_nt_no | REL_TRANS_CGG_CODE,PERM_NT_NO | 1:1 |
| RGETNWAVI05 | sf_team_code,perm_nt_no,qw_isp_sno | REL_TRANS_CGG_CODE,PERM_NT_NO,QW_ISP_SNO | 1:1 |
| RGETNWAVI06 | sf_team_code,...,list_code | 5컬럼 전부 | 1:1 |
| RGETNMNFE01 | sno,regnum | SNO,REL_TRANS_CGG_CODE | 이름 다름 |
| RGETNOPMS01 | sf_team_code,perm_nt_no | REL_TRANS_CGG_CODE,PERM_NT_NO | 그룹 (PK 3개중 2개) |
| RGETNTGMS02 | sf_team_code,yy_gbn | REL_TRANS_CGG_CODE,CRIT_YY | 그룹, 이름 다름 |
| RGETNKCNO01 | sf_team_code,perm_nt_no,dig_ord | REL_TRANS_CGG_CODE,PERM_NT_NO,DIG_ORD | 1:1 |
| RGETNWAMS01 | sf_team_code,qw_isp_sort_code,list_code | REL_TRANS_CGG_CODE,QW_ISP_SORT_CODE,LIST_CODE | 1:1 |
| RGETNSIMS01 | sf_team_code,regnum,sno | REL_TRANS_CGG_CODE,OP_UPCH_REGNUM,OP_UPCH_SNO | 이름 다름 |
| RGETNYYMS01 | sf_team_code,regnum,sno | REL_TRANS_CGG_CODE,ORG_REGNUM,ORG_SNO | 이름 다름 |
| RGETNJHMS01 | sf_team_code,regnum,sno | REL_TRANS_CGG_CODE,PRFCN_UPCH_REGNUM,PRFCN_UPCH_SNO | 이름 다름 |
| RGETNSCKT01 | sf_team_code,list_code | REL_TRANS_CGG_CODE,MW_TAKE_NO | 이름 다름 |
| RGETNKMTB01 | sf_team_code,list_code | REL_TRANS_CGG_CODE,MW_TAKE_NO | 이름 다름 |
| RGETHKMIR01 | sf_team_code,perm_nt_no,list_code,jgong_nt_sno | REL_TRANS_CGG_CODE,PERM_NT_NO,EXE_HIS_YMD,ORD | 이름 다름 |
| RGETNYCSG01 | sf_team_code,list_code | REL_TRANS_CGG_CODE,MW_TAKE_NO | 이름 다름 |

### 4. Orchestrator 등록
- Agent: dmz-others-snd-saeol (port 8085)
- Datasource: saeol-oracle (29005) — source/target 동일

### 5. 내부망 구성 (후속)
- **RCV**: 새올 전용 agent 분리 (Oracle IF_SND → IF_RSV) — SND:RCV = 1:1
- **IF_RSV**: 제주/새올 합침 (같은 테이블)
- **Loader**: 하나로 통합 (여러 출처 합류 → 최종 GIMS 적재)
- 역할 분리: SND/RCV는 출처별 운반, Loader가 합류 지점

### 6. 테스트
- LINK_PLAN에 I/U/D 이벤트 넣고 실행
- Oracle IF_SND 적재 + link_status 확인
- 추적 현황 확인

## 수정 파일 (예상)

| 모듈 | 파일 | 내용 |
|------|------|------|
| scripts | saeol-oracle-init.sql | **신규** — 소스 DDL + IF_SND DDL + LINK_PLAN + 테스트 데이터 |
| sync-agent-others | step/SaeolLinkPlanSndStep.java | **신규** — LINK_PLAN 기반 커스텀 SND |
| sync-agent-others | config/agents/dmz-others-snd-saeol.yml | **신규** — YAML |
| sync-orchestrator/backend | data.sql | Agent + Datasource 등록 |

## PK 정책
- 소스 Oracle 테이블: PK 제약 추가하지 않음 (읽기 전용)
- IF_SND(Oracle): PK/UK 설정 가능 (우리가 생성하는 테이블)
- RGETNKCNO01: DDL에 PK 없으나 실서버 유니크 확인 완료 (CGG_CODE,PERM_NT_NO,DIG_ORD)

## 추가 작업
- **Oracle JDBC 드라이버**: others의 build.gradle에 ojdbc 의존성 추가
- **Oracle ID 생성**: IF_SND의 id 컬럼 — Oracle 12c+ IDENTITY 또는 SEQUENCE 방식 결정
- **link_idx 마지막 위치**: Execution 메타 또는 별도 테이블에 기록
- **Proxy 설정**: Oracle IF_SND datasource 등록 + Proxy가 Oracle에서도 읽을 수 있도록 연결
- **Orchestrator datasource 등록**: 새올 Oracle(29005)을 datasource로 등록, agent에 연결
- **추적(trace) Oracle 지원**: others의 ExecutionDataController가 Oracle datasource 조회 가능하도록 (bojo-int에는 이미 있을 수 있음, others에 적용)
- **SyncLog 위치**: agent PG에 기록 (기존 유지). 추적 시 SyncLog에서 테이블명 → 해당 datasource(Oracle)로 실제 데이터 조회

## 주의사항
- 컬럼수 90, 95개 테이블 → DDL 자동 생성 권장
- 복합PK 대부분 → 오늘 수정한 추적 로직 활용
- DELETE 처리: link_status='DELETE' → IUD 전환 시 구현
- IF_SND가 Oracle이므로 JPA 엔티티 불필요 — DDL + JdbcTemplate
- 실서버 Tibero 호환성 확인 필요
