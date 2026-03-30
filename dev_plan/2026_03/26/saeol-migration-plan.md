# 새올 16개 테이블 이관 계획서

## 1. 기존 소스 분석

### 1.1 saeol.java 구조
- **역할**: DMZ DB → 내부망 GIMS DB 데이터 이관
- **conn (소스)**: DMZ DB — 읽기만 (쓰기 없음)
- **tConn (타겟)**: 내부망 GIMS DB (Oracle/Tibero) — MERGE/DELETE + 진행추적
- **실행 방식**: 수동 (스케줄 없음, 담당자가 필요 시 jar 실행)
- **처리 방식**: LINK_PLAN 테이블에서 변경 건 읽음 → 해당 테이블 SELECT → 타겟에 MERGE/DELETE

### 1.2 LINK_PLAN 구조
- DMZ 소스 DB에 존재
- 현재 row 1건만 존재 (큐가 아니라 현재 처리 건)
- 컬럼: link_idx(NOT NULL), flag(I/U/D), table_name, 등록일자, 등록번호 등
- 새올 행정시스템이 변경 건을 여기에 넣으면, saeol이 읽어서 내부망에 반영

### 1.3 TM_GD70001 (진행추적)
- 내부망 타겟 DB에 존재, row 1건
- LAST_CNTC_NO: 마지막 처리한 link_idx
- LAST_CNTC_DTHR: 마지막 처리 시각
- 20분 이상 미처리 감지 로직 있음

### 1.4 처리 로직
- flag=I 또는 U → MERGE INTO (Insert or Update)
- flag=D → DELETE
- 컬럼 메타데이터를 ResultSetMetaData로 동적 추출 → SQL 자동 생성
- 테이블별 WHERE 조건(PK)만 다르고, MERGE/DELETE 로직은 공통

### 1.5 새올 DB 실존 테이블 (16개 전부 확인, 3/30 정정)

※ 이전에 5개만 확인했으나, 새올 DB(Tibero)에 16개 전부 실존 확인됨 (3/30)
※ 이전 확인이 잘못된 DB를 참조한 것이었음

| # | 테이블 | 설명 | 실존 |
|---|--------|------|:----:|
| 1 | RGETSTGMS01 | 이용실태 | O |
| 2 | RGETNOPMS01 | 지하수이용신고 | O |
| 3 | RGETNTGMS02 | 지표수수질검사 | O |
| 4 | RGETNWAVI06 | 지하수수질검사내역 | O |
| 5 | RGETNPMMS01 | 허가신고정보 | O |
| 6 | RGETNKCNO01 | 케이싱정보 | O |
| 7 | RGETNWAMS01 | 지표수환경기준 | O |
| 8 | RGETNWAVI05 | 지하수수질검사 | O |
| 9 | RGETNSIMS01 | 심화조사정보 | O |
| 10 | RGETNYYMS01 | 용년정보 | O |
| 11 | RGETNJHMS01 | 정화조정보 | O |
| 12 | RGETNSCKT01 | 스케치정보 | O |
| 13 | RGETNKMTB01 | 공간매체테이블 | O |
| 14 | RGETHKMIR01 | 현황정보 | O |
| 15 | RGETNYCSG01 | 연락처 | O |
| 16 | RGETNMNFE01 | 인력및장비관리내역관리 | O |

### 1.6 saeol 코드상 16개 테이블 (전체)

| # | 테이블 | 설명 | PK (복합키) | DMZ 실존 |
|---|--------|------|-------------|---------|
| 1 | RGETSTGMS01 | 이용실태 | sf_team_code, perm_nt_no, yy_gbn | O |
| 2 | RGETNOPMS01 | 지하수이용신고 | rel_trans_cgg_code, perm_nt_no | X |
| 3 | RGETNTGMS02 | 지표수수질검사 | sf_team_code, yy_gbn | X |
| 4 | RGETNWAVI06 | 지하수수질검사내역 | sf_team_code, perm_nt_no, qw_isp_sno, qw_isp_sort_code, list_code | O |
| 5 | RGETNPMMS01 | 허가신고정보 | sf_team_code, perm_nt_no | O |
| 6 | RGETNKCNO01 | 케이싱정보 | sf_team_code, perm_nt_no, dig_ord | X |
| 7 | RGETNWAMS01 | 지표수환경기준 | sf_team_code, qw_isp_sort_code, list_code | X |
| 8 | RGETNWAVI05 | 지하수수질검사 | sf_team_code, perm_nt_no, qw_isp_sno | O |
| 9 | RGETNSIMS01 | 심화조사정보 | sf_team_code, regnum, sno | X |
| 10 | RGETNYYMS01 | 용년정보 | sf_team_code, regnum, sno | X |
| 11 | RGETNJHMS01 | 정화조정보 | sf_team_code, regnum, sno | X |
| 12 | RGETNSCKT01 | 스케치정보 | sf_team_code, list_code | X |
| 13 | RGETNKMTB01 | 공간매체테이블 | sf_team_code, list_code | X |
| 14 | RGETHKMIR01 | 현황정보 | sf_team_code, perm_nt_no, list_code, jgong_nt_sno | X |
| 15 | RGETNYCSG01 | 연락처 | sf_team_code, list_code | X |
| 16 | RGETNMNFE01 | 인력및장비관리내역관리 | sno, regnum | O |

---

## 2. 구현 방향

### 2.1 기본 방침
- Agent(bojo-int) 파이프라인에 편입
- IF 테이블 시스템 적용 (기존 패턴과 통일)
- 구현은 16개 전부 대응 (기존 소스 기능 범위 유지)
- 테스트는 DMZ 실존 5개로 진행

### 2.2 파이프라인 흐름
```
DMZ 새올 테이블 → [RCV] → IF_SND → [Proxy] → IF_RSV → [Loader] → 내부망 GIMS
```

### 2.3 미결 사항 (팀장님 논의 필요)
- [ ] **IF 테이블 구조** — 핵심 결정 사항
  - A안: 테이블별 16쌍 (32개 추가) → 과다
  - B안: 범용 IF 1쌍 (row_data를 JSON으로) → 구조 단순하지만 기존 패턴과 다름
  - C안: IF 없이 직접 소스→타겟 → Agent 편입 시 일관성 문제
- [ ] DELETE 처리: flag=D 지원 여부 (현재 DMZ에서 D 존재 미확인 → 보류)
- [ ] 동일 스키마 복사 시 Agent YAML 설정 방식
- [ ] 스케줄 설정 (기존은 수동, 우리는 자동화?)
