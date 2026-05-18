# 8. 제공용 Agent (Type A) + Type B 커스텀 핸들러 (api-provider)

> **요구사항**: 레거시 제공 API (MEGOKR / 가뭄119 / OPN) 를 신 시스템으로 이식한다.
> Type A (단순 복사) = infolink-agent-provide:8096 가 Oracle 원본 → PG 제공 테이블 적재 후 api-provider 가 동적 SELECT.
> Type B (복잡 SQL) = api-provider:8095 안에 커스텀 Java 핸들러 구현 (4/27 결정 — 외부 호출 시점 직접 쿼리).
> Oracle 29004 원본 → PG 29006 `api_prv_*` 제공 테이블 (Type A) / Oracle 29004 직접 (Type B).

## 상태: Type A 6건 이식 완료 / Type B 16 핸들러 구현 완료 / B4 함수 사전 배치 완료

---

## 모듈 구성 [참고]

```
내부망:
  bojo-internal (8092)         → RCV + Loader (IF_RSV → Oracle 적재) — 기존
  infolink-agent-provide (8096) → Oracle → PG 제공 테이블 적재 (Type A) — 신규
  api-provider (8095)     → 동적 SELECT (Type A) + 커스텀 Java 핸들러 (Type B)
```

| 구분 | 예시 | 관리 방식 | LINK_STATUS |
|------|------|---------|:---:|
| 원본 (Oracle 29004) | TM_GD120001, TM_GD30301 | DDL 스크립트 (수동) | O |
| 제공 (PG 29006) | api_prv_tm_gd30301 | JPA 엔티티 (ddl-auto) | X |
| Type B 직접 쿼리 | (제공 테이블 없음, 외부 호출 시점) | 커스텀 핸들러 Java 코드 | — |

## 제공용 Agent 인프라 [Provide-Agent:8096]
- [x] 모듈 생성 (bojo-internal 복제, port 8096, PG 29006, ddl-auto)
- [x] Proxy 경유 Oracle 소스 연결 (SyncDataSourceService)
- [x] DynamicEntityManagerService + CaseAwareNamingStrategy
- [x] ProvideLoadStep 폐기 → SourceToIfStep 통합 (4/23 — 모든 Agent 단일 카피 Step 재사용)
- [x] SourceToIfStep → SourceToTargetStep 개명·중립화 (IF 전제 제거, 4/23)
- [x] YAML 구조 — 테이블별 개별 파일 (신규 추가 시 파일 하나만 추가)
- [x] 원본 테이블 추적 컬럼 관리 (LINK_STATUS / EXECUTION_ID / SOURCE_REFS / EXTRACTED_AT / UPDATED_AT)
- [x] SyncLog·Execution PG 29006 저장 + Proxy 헤더 라우팅 (X-Manage-Datasource-Id, 4/22)
- [x] 16 엔티티 UK 통일 (@Id=sn IDENTITY + @UniqueConstraint(source_refs), 4/23)
- [x] JdbcTableNameResolver cross-schema 지원 (TableRef.parse + schemaPattern, 4/23 A4 부수)
- [ ] api-provider `entity/provide/` 4개 엔티티 이관 후 삭제

## Type A 단순 복사 등록 [등록]
- [x] A1 가뭄119 (SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 → api_prv_wt_dream_permwell_public_21033) — cross-schema 4/23
- [x] A2 MEGOKR NGW_03 (TM_GD30301 → api_prv_tm_gd30301)
- [x] A3 MEGOKR NGW_08 (TM_GD000203 → api_prv_tm_gd000203) — 파일럿 4/22
- [ ] A4 MEGOKR NGW_09 (WT_DREAM_PERMWELL_PUBLIC → api_prv_wt_dream_permwell_public)
- [ ] A5 OPN 인허가관정 (RGETNPMMS01 → api_prv_rgetnpmms01)
- [x] A6 OPN 관측망 기본 (TM_GD120001 → api_prv_tm_gd120001) — 4/23 (TM_GD10001 의 표준화 후 테이블)
- [x] A7 OPN TMP_MEGOKR_API (149컬럼, 특이 케이스 @Id=id IDENTITY + sn 일반컬럼) — 4/23 → B1/B2/B3 재활용
- [ ] A8 OPN 영향조사 (TM_GD50001 → api_prv_tm_gd50001)
- [ ] A9 OPN 수질검사 항목 (TM_GD30310 → api_prv_tm_gd30310)
- [x] TM_GD112002 — 4/23 이식 + DDL + Agent 등록 + E2E
- [x] TM_GD120001 — 4/23 이식 + DDL + 샘플
- [x] TM_GD130001 — 4/23 (VARCHAR leading-zero 픽스 부수)

## Type B 커스텀 핸들러 인프라 [api-provider:8095, 4/27 신규]
- [x] CustomOperationHandler 인터페이스 + CustomOperationMetadata POJO
- [x] CustomColumnSpec / CustomParamSpec (운영자 화면 메타)
- [x] CustomHandlerRegistry (Spring Bean 자동 수집)
- [x] ApiPrvOperation ALTER (operation_type, is_locked) + 기존 12건 META 백필
- [x] ApiGatewayController + ApiPrvManageController.testOperation CUSTOM/META 분기
- [x] ApiPrvCallHistory finally 패턴 기록 (외부 호출만 / test 제외)
- [x] handler_key 분리 — operationId/Name 운영자 변경 가능 + 핸들러 매칭 보존
- [x] 잠금 보호 (ApiPrvOperationService)
- [x] Oracle JDBC driver 의존성 + Hikari connectionTestQuery 제거 (driver isValid)

## Type B 핸들러 구현 (Phase 1~6) [api-provider:8095]
- [x] **B14** InspectionListHandler — 파일럿 (TM_GD110310 + LEFT JOIN/필터/정렬/페이징, 4/27)
- [x] **B15** InspectionDistinctHandler — DISTINCT GROUP BY (4/27)
- [x] **B6** LinkageChartDailyHandler (4/27)
- [x] **B17** UnregitsFclySmrizeHandler (TM_GD023001 신규 DDL + ALTER, 4/27)
- [x] **B16-DJ** ActualUseDetailDjHandler (CTE 2 + 3JOIN + ROW_NUMBER + DECODE, 4/27)
- [x] **B16-KB** ActualUseDetailKbHandler (DJ SQL + brtcNm='경상북도' 분기, 4/27)
- [x] **B5** SupplementaryGroundwaterHandler (5-way LEFT JOIN, 6 테이블 컬럼 정합화, 4/27)
- [x] **B18** WqInputStatusDjHandler (UNION ALL + 3중 서브 + 대전 시군구 6행, 4/28)
- [x] **B9** ObservationStationTimeHandler (B9 파일과 명명 충돌 정리 후 — 정적 PIVOT 일 단위, 4/28)
- [x] **B10** GroundwaterQualityHandler (정적 PIVOT 시 단위, 4/28)
- [x] **B13** WaterQualityMfdsHandler (동적 PIVOT 패턴 확립 + helper fallback, 4/28)
- [x] **B11** WaterQualityInfoHandler (동적 PIVOT, 4/29 Phase 4)
- [x] **B12-DJ** WaterQualityInfoDjHandler (B11 + brtcNm 필터, 4/29)
- [x] **B12-KB** WaterQualityInfoKbHandler (4/29)
- [x] **B7** WaterLevelObservationHandler (DBLINKUSR DUBWLOBSIF, 4/30 Phase 6)
- [x] **B8** RainfallObservationHandler (DBLINKUSR DUBRFOBSIF, 4/30)
- [x] **B4** WellInfoHandler (info_permwell, RGETNPMMS01 + TC_GD000100 JOIN + 함수 3종) — 5/6 오후 완료
  - [x] B4 의존 Oracle 함수 사전 배치 (FN_GD_GET_CMMTNDCODE / FN_GD_GET_GUBUN, internal-oracle 5/4 적용 + 7/7 검증 PASS, 5/6 표준화 후 재배치)
  - [x] B4 핸들러 본 구현 (Java) — 5/6 오후 (operationId=`opnService/getWellInfo`, custom-handlers/register endpoint 로 메타 자동 sync, id=36)
  - [x] B4_setup.sql — 대전 5 + 경북 3 시군구 9 row 샘플 데이터
  - [x] E2E 검증: 운영자 cookie + 외부 사용자 (apiKey 쿼리) + Frontend rewrite + 메타 sync (columns 13 + params 2) 모두 PASS

## Operation 등록 + 호출 검증 [등록]
- [x] 12건 operation 전수 등록 + 호출 검증 (Python 일괄 등록, 12/12 200 OK, 4/24)
- [x] 슬래시 operationId 지원 (`megokrApi/ngw08` 레거시 URL 그대로 재현, 4/24)
- [x] 응답 alias 대소문자 보존 (PG identifier lowercase 회피, 쌍따옴표 alias, 4/24)
- [x] 응답 alias v3 호환 정렬 (11종 핸들러 일괄 수정 + frontend fix + 메모리, 4/28)
- [x] PRV 응답 키 = v3 레거시 alias 유지 메모리 룰 (`feedback_provide_response_v3_compat.md`)
- [x] A7 RNUM + B2 RNUM/JOSACODE 자바 후처리 (DynamicQueryService 화이트리스트, 4/29)

## 개발 환경 DDL [scripts/ddl/internal-oracle/provide-source/]
- [x] 00_create_schemas.sql — SDE_NGWS / DBLINKUSR 스키마 생성 정책
- [x] WT_DREAM_PERMWELL_PUBLIC_21033.sql (A4 cross-schema, 4/23)
- [x] TM_GD112002.sql / TM_GD120001.sql / TM_GD130001.sql / TMP_MEGOKR_API.sql (Type A 4/23)
- [x] TM_GD110310.sql (B14, 4/27)
- [x] TM_GD023001.sql (B17 신규)
- [x] VIEW_GTEST.sql (B 영역)
- [x] B5_setup.sql / B7_B8_setup.sql / B9_setup.sql / B10_setup.sql / B11_setup.sql / B13_setup.sql / B16_setup.sql / B18_setup.sql (Type B 샘플 데이터)
- [x] **B4_functions.sql** — Oracle 함수 2종 사전 배치 (5/4)
- [x] TC_GD00002 컬럼명 정합 (4/27 — UGRWTR_CMMN_CODE → UGWTR_COM_CD/CD_CN/GROUP_CD_SN)
- [ ] 누락 Oracle 테이블 잔여 — 사용자 자료 필요 시 추가
- [ ] RGETNPMMS01 추적 컬럼 ALTER TABLE (LINK_STATUS 기본값 pending) — A5 본 이식 시점

## 개발 환경 DDL [확정필요]
- [x] DBLINKUSR.* 외부 스키마 핸들러 — 로컬 재현 패턴으로 B7/B8 처리 (4/30)

## E2E 검증 [테스트]
- [x] A3 TM_GD000203 E2E 파일럿 (Oracle 29004 → PG 29006) — 4/22
- [x] Source 정방향 추적 / Target 역방향 추적 동작 검증 — 4/22
- [x] api-provider 연동 검증 (제공 테이블 → 동적 SELECT → 외부 응답) — Type A 12종 200 OK, 4/24
- [x] /trace-source 분기 3 개선 (target_tables 1순위 + 양방향 2순위, 4/22)
- [x] Type B 회귀 검증 (alias 변경 전후 2회 200 OK, 4/28)
- [ ] Type A 9건 전체 E2E 검증 (잔여 A4/A5/A8/A9)
- [ ] Type B B4 본 구현 후 E2E 검증
