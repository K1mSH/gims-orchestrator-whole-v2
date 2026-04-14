# infolink-api-collector (API 수집 모듈)

## [E3] 1. 기본 구조 (엔티티/CRUD)
- [x] ApiEndpoint, ApiParam, ApiFieldMapping, ApiSchedule, ApiExecutionHistory 엔티티
- [x] ApiEndpointService CRUD
- [x] ApiEndpointController REST API
- [x] ApiEndpointDto (Create/Update/Detail)

## [E3] 2. 파라미터 관리
- [x] ApiParam QUERY/BODY/PATH/HEADER 타입
- [x] DynamicParamResolver (TODAY/NOW + offset + format)
- [x] isApiKeyRef boolean 플래그 (이모지 방식 폐기)
- [x] API키 참조 → resolveApiKey() → Mock/본체 API 조회 + 캐싱
- [x] 프론트: 직접입력/API키/동적 3분기 UI

## [E3] 3. 테스트 호출
- [x] ApiCallService (HTTP 호출 엔진)
- [x] ApiTestService.testCall() (저장된 endpoint)
- [x] ApiTestService.testCallInline() (등록 전 폼 테스트)
- [x] ResponseParser (JSON → Tree/Records)
- [x] URL 인코딩 처리 (URLEncoder + build(true))
- [ ] 테스트 호출 resolvedParams에 API키 실제값 표시 (현재 키 ID 노출)

## [E3] 4. 매핑 설정
- [x] 데이터루트 선택 (JSON 트리 탐색)
- [x] 1:1 필드매핑 (sourceField → targetColumn)
- [x] LOOKUP 파생 컬럼 (정규식 키 추출 → 공통코드 매칭)
- [x] TransformType 8종 (NONE, DATE_FORMAT, NUMBER, SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE, LOOKUP)
- [x] 고정값 컬럼 추가 (DEFAULT_VALUE, target만)
- [x] targetColumn 미선택 행 자동 제외
- [x] conflict key 설정 (UPSERT용)
- [x] MappingTab 테스트 호출 → 미매핑 필드 머지
- [x] MappingTab 테이블 드롭다운 comment 표시
- [x] MappingTab 부분 업데이트 시 기존값 보존 (baseUpdateFields)

## [E3] 5. 적재 설정
- [x] 외부 Datasource 연동 (OrchestratorClient → DynamicDataSourceService)
- [x] targetDatasourceId/targetTableName 설정
- [x] UPSERT 모드 (conflictKey 기반 ON CONFLICT)

## [E3] 6. 실행 엔진
- [x] ApiExecutionService (범용 INSERT/UPSERT)
- [x] 파생 매핑 처리 (LOOKUP Map 사전 로딩)
- [x] ApiExecutionHistory 이력 저장
- [x] 수동 실행 (/endpoints/{id}/run)
- [x] 수동 실행 버튼 미설정 시 알림 표시 (disabled → 클릭+안내)
- [x] 나라장터 4개 오퍼레이션 등록 + E2E 검증 (공사/용역/외자/물품)

## [E3] 7. 스케줄 관리
- [x] ApiScheduleExecutor (TaskScheduler + CronTrigger)
- [x] @EventListener(ContextRefreshedEvent) 초기화
- [x] registerSchedule/unregisterSchedule/getActiveScheduleCount
- [x] ApiScheduleService CRUD
- [x] ScheduleTab UI (등록/해제/cron 입력)

## [E3] 8. 실행 이력
- [x] ApiHistoryController (페이징 + 날짜 검색)
- [x] HistoryTab (페이지 번호 방식, 날짜 range, 총 건수)
- [x] UPSERT 시 신규/갱신 카운트 분리 (insertCount + updateCount)

## [E3] 9. Mock/내부 시스템 연동
- [x] MockApiController — 공통코드 API (NGW_0118 언론사 65건)
- [x] MockApiController — API 키 목록
- [x] 외부 API 시뮬레이션 Mock 제거 (뉴스, 나라장터)
- [x] Mock 키 데이터: 인코딩된 값 → 원본 Base64로 수정

## [E3] 10. 프론트엔드
- [x] /api-collect 목록 페이지
- [x] /api-collect/[id] 상세 페이지 (4탭: 기본정보/매핑/스케줄/이력)
- [x] 등록 페이지 (page.tsx) — 인라인 테스트 → 매핑 → 저장
- [x] InfoTab — 기본정보 + 헤더/파라미터 통합
- [x] Next.js proxy (/collector-api/* → localhost:8084/api/*)
- [ ] InfoTab 저장 시 적재설정(dataRootPath 등) 유실 가능성 재검토
- [ ] 등록 플로우 브라우저 전체 테스트 (등록 → 수정 → 실행)

## [E3] 11. 나라장터 입찰공고 연동
- [x] 나라장터 API 등록 (#1공사)
- [x] target 테이블 tm_gd014000 준비
- [x] dataRootPath/targetTableName 복원 후 E2E 수집 테스트
- [x] #2용역, #3외자, #4물품 오퍼레이션 등록
- [ ] 스케줄 설정 (매일 전일 변경분 수집)

## [E3] 12. 커스텀 실행기
- [x] CustomExecutor 인터페이스 + CustomExecutionResult
- [x] CustomExecutorRegistry (Spring Bean 자동 등록)
- [x] ApiEndpoint에 executorType 필드 추가
- [x] ApiExecutionService 커스텀/범용 분기
- [x] ApiEndpointController 커스텀 실행기 목록 API
- [x] 프론트: 등록/수정 시 실행 방식 선택 (범용/커스텀)
- [x] 프론트: 커스텀 선택 시 매핑 탭 비활성화
- [x] 실행 옵션(executionParams) — Map 파라미터 슬롯

## [E3] 13. 안양시 이용량 연동
- [x] Mock API — /mock/anyang/fac (시설정보)
- [x] Mock API — /mock/anyang/data (이용량)
- [x] DB 테이블 생성 (anyang_api_fac, anyang_api_data, use_legacy_data)
- [x] AnyangUsageExecutor 구현 (FAC INSERT + DATA INSERT + LEGACY JOIN INSERT)
- [x] 엔드포인트 등록 + E2E 테스트

## [E3] 14. 제주 보조망 — DMZ 측 커스텀 실행기

### 14-1. jeju-jewon-master (관측점 마스터, 좌표변환)
- [x] Mock API — /mock/jeju/obsv
- [x] DB 테이블 생성 (jeju_jewon)
- [x] JejuJewonExecutor 구현 (코드변환 3종 + 좌표변환 EPSG:5186→4326)
- [x] 엔드포인트 등록 + E2E 테스트

### 14-2. jeju-obsv-data (수위 관측)
- [x] Mock API — /mock/jeju/obsv-data
- [x] DB 테이블 생성 (jeju_obsv_data)
- [x] JejuObsvDataExecutor 구현 (site_code별 루프 + UPSERT)
- [x] 엔드포인트 등록 + E2E 테스트

## [E3] 15. 제주 이용량 — DMZ 측 커스텀 실행기

### 15-1. jeju-facility (이용시설, 페이징+좌표변환+코드변환)
- [x] Mock API — /mock/jeju/facility
- [x] DB 테이블 생성 (jeju_facility_pmms, jeju_facility_stgms)
- [x] JejuFacilityExecutor 구현 (1000건 페이징, 코드변환 11종, 2테이블)
- [x] 엔드포인트 등록 + E2E 테스트

### 15-2. jeju-water-quality (수질검사)
- [x] Mock API — /mock/jeju/water-quality
- [x] DB 테이블 생성 (jeju_water_quality_05, jeju_water_quality_06)
- [x] JejuWaterQualityExecutor 구현 (항목명 매핑, 용도 A/D 분기, 2테이블)
- [x] 엔드포인트 등록 + E2E 테스트

## [E3] 16. Docker 배포
- [x] Docker 컨테이너 기동 테스트
- [x] 외부 Datasource 연동 확인

**진행도: 76/82 = 93%**
