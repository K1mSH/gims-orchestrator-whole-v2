# 8. 제공용 Agent (sync-agent-provide)

> **요구사항**: 레거시 제공 API 3종(MEGOKR / 가뭄119 / OPN)을 API Provider 시스템으로 이식한다.
> API Provider는 단순 SELECT만 지원하므로, 복잡 SQL은 전처리 Step이 제공 테이블(PG 29006)에 적재한다.
> Oracle 29004 원본 → PG 29006 `api_prv_*` 제공 테이블로 복사(Type A) 또는 전처리(Type B) 적재.

## 상태: 인프라 완료 + A3 파일럿 완료

---

## 모듈 구성 [참고]

```
내부망:
  bojo-int (8092)         → RCV + Loader (IF_RSV → Oracle 적재) — 기존
  sync-agent-provide (8096) → Oracle → PG 제공 테이블 적재 — 신규
  api-provider (8095)     → PG 제공 테이블 읽기 → 외부 응답 — 기존
```

| 구분 | 예시 | 관리 방식 | LINK_STATUS |
|------|------|---------|:---:|
| 원본 (Oracle 29004) | TM_GD10001, TM_GD30301 | DDL 스크립트 (수동) | O |
| 제공 (PG 29006) | api_prv_tm_gd30301 | JPA 엔티티 (ddl-auto) | X |

## 제공용 Agent 인프라 [Provide-Agent:8096]
- [x] 모듈 생성 (bojo-int 복제, port 8096, PG 29006, ddl-auto)
- [x] Proxy 경유 Oracle 소스 연결 (SyncDataSourceService)
- [x] DynamicEntityManagerService + CaseAwareNamingStrategy
- [x] ProvideLoadStep 신규 (Type A 범용 복사, Oracle→PG UPSERT)
- [ ] PreprocessLoadStepFactory 신규 (Type B, 전처리)
- [x] YAML 구조 — 테이블별 개별 파일 (신규 추가 시 파일 하나만 추가)
- [x] 원본 테이블 추적 컬럼 관리 (LINK_STATUS / EXECUTION_ID / SOURCE_REFS / EXTRACTED_AT / UPDATED_AT)
- [x] SyncLog·Execution PG 29006 저장 + Proxy 헤더 라우팅 (X-Manage-Datasource-Id)
- [ ] api-provider `entity/provide/` 4개 엔티티 이관 후 삭제

## Type A 단순 복사 등록 [등록]
- [ ] A1 가뭄119 (SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 → api_prv_wt_dream_permwell_public_21033)
- [ ] A2 MEGOKR NGW_03 (TM_GD30301 → api_prv_tm_gd30301)
- [x] A3 MEGOKR NGW_08 (TM_GD000203 → api_prv_tm_gd000203) — 파일럿 완료
- [ ] A4 MEGOKR NGW_09 (WT_DREAM_PERMWELL_PUBLIC → api_prv_wt_dream_permwell_public)
- [ ] A5 OPN 인허가관정 (RGETNPMMS01 → api_prv_rgetnpmms01)
- [ ] A6 OPN 관측망 기본 (TM_GD10001 → api_prv_tm_gd10001)
- [ ] A7 OPN 수질측정망 (VIEW_GTEST → api_prv_view_gtest)
- [ ] A8 OPN 영향조사 (TM_GD50001 → api_prv_tm_gd50001)
- [ ] A9 OPN 수질검사 항목 (TM_GD30310 → api_prv_tm_gd30310)

## Type B 전처리 Step 구현 [Provide-Agent:8096]
- [ ] B1/B2 MEGOKR NGW_04 PIVOT 125컬럼 Step (TM_GD30302 +30301 +10001 → api_prv_tm_gd30302)
- [ ] B3/B4 OPN waterQuality JOIN+CASE Step (TM_GD10001, 30301, 30302, TC_GD00002 → api_prv_water_quality)
- [ ] B5 OPN general_105 5 LEFT JOIN Step (TM_GD10001, 60101, 60130, 60001, 60002, 70002 → api_prv_general_105)
- [ ] B8/B9 OPN linkage_chart CTE+PIVOT+UNION Step (PM_GD60201, TM_GD60101, TM_GD10001 → api_prv_linkage_chart)

## Type B 전처리 Step 구현 [확정필요]
- [ ] B6 OPN observation_station1 DBLINK Step (DBLINKUSR.DUBWLOBSIF 외 3개 → api_prv_observation) — DBLINK 담당자 협의 후
- [ ] B7 OPN observation_station0 DBLINK Step (DUBRFOBSIF, DUBMMRF 외 1개 → api_prv_rainfall) — DBLINK 담당자 협의 후

## 개발 환경 DDL [scripts/ddl/internal-oracle/provide-source/]
- [ ] 누락 Oracle 테이블 DDL + 샘플 데이터 (15개: TM_GD10001, TM_GD30301/30302/30310, TM_GD00203, TM_GD50001, WT_DREAM_PERMWELL_PUBLIC, WT_DREAM_PERMWELL_PUBLIC_21033, VIEW_GTEST, TC_GD00002/00100, TM_GD60001/60002/60101/60130, TM_GD70002, PM_GD60201)
- [ ] RGETNPMMS01 추적 컬럼 ALTER TABLE (LINK_STATUS 기본값 pending 포함)

## 개발 환경 DDL [확정필요]
- [ ] DBLINKUSR.* 4개 — 수위/우량관측소 원본 접근 방식 담당자 협의 후

## E2E 검증 [테스트]
- [x] A3 TM_GD000203 E2E 파일럿 (Oracle 29004 → PG 29006) — 4/22 완료
- [x] Source 정방향 추적 / Target 역방향 추적 동작 검증 — 4/22 완료
- [ ] api-provider 연동 검증 (제공 테이블 → 동적 SELECT → 외부 응답)
- [ ] Type A 9건 전체 E2E 검증
- [ ] Type B 전처리 결과 검증 (실서버 접근 확보 후)
