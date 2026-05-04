# 3. API 수집

> **요구사항**: 나라장터, 제주 보조관측망 등 외부 공공 API 데이터를
> 코드 변경 없이 UI 등록만으로 수집·적재할 수 있는 범용 수집 체계를 구축한다.
> API마다 별도 프로그램을 만들던 기존 방식을 통합 관리한다.

## 상태: 개발완료

---

## 범용 실행기 [API Collector:8084]
- [x] API 등록 (URL, 인증, 파라미터)
- [x] 테스트 호출 → JSON 트리 뷰 → 데이터 루트 선택
- [x] 필드 매핑 (sourceField → targetColumn, TransformType 8종)
- [x] LOOKUP 파생 컬럼 (정규식 키 추출 → 공통코드 매칭)
- [x] INSERT / UPSERT 적재 (conflictKey 기반)
- [x] 실행 이력 (페이징 + 날짜 검색 + 신규/갱신/스킵 카운트)
- [x] API Key 참조 (isApiKeyRef → resolveApiKey)
- [x] 동적 파라미터 (TODAY/NOW + offset + format)
- [x] 테스트 호출 시 API키 ID 표시 (실제값 노출 불필요, 설명으로 식별)
- [ ] 등록 플로우 브라우저 전체 재테스트

## 커스텀 실행기 [API Collector:8084]
- [x] CustomExecutor 인터페이스 + Registry (Spring Bean 자동 등록)
- [x] 실행 옵션 (executionParams Map 전달 — 연도 지정, 분기 옵션 등)
- [x] 프론트: 범용/커스텀 선택, 커스텀 시 매핑 탭 비활성화
- [x] 좌표변환 지원 (EPSG:5186 → EPSG:4326)
- [x] 코드변환 지원 (용도코드, 지역코드, 허가형태, 상태코드 등)
- [x] 페이징 반복 호출 (1000건 단위)

## 스케줄 실행 [API Collector:8084]
- [x] ApiScheduleExecutor (TaskScheduler + CronTrigger)
- [x] @EventListener(ContextRefreshedEvent) 초기화
- [x] registerSchedule/unregisterSchedule/getActiveScheduleCount
- [x] ScheduleTab UI (등록/해제/cron 입력)

## Mock API 테스트 환경 [API Collector:8084]
- [x] MockApiController — 공통코드 API (NGW_0118 언론사 65건)
- [x] MockApiController — API 키 목록
- [x] 제주/안양/나라장터 Mock 시뮬레이션 (개발 완료 후 제거)

## API 수집 관리 화면 [Frontend]
- [x] /api-collect 목록 페이지
- [x] /api-collect/[id] 상세 (4탭: 기본정보/매핑/스케줄/이력)
- [x] 등록 페이지 (인라인 테스트 → 매핑 → 저장)
- [x] Next.js proxy (/collector-api/* → localhost:8084)
- [x] InfoTab — 적재 설정 섹션 제거 + isActive 토글 (4/29)
- [x] MappingTab — UPSERT 토글 통합 (등록 화면과 양식 일관, 4/29)
- [x] InfoTab ✓/✗ 분기 컬럼 수 표시 — 처음 렌더 시 보였다가 덮임 패턴 fix (4/30)

---

## 범용 실행기 등록 [등록]
- [x] 나라장터 공사 입찰 (target: tm_gd014000)
- [x] 나라장터 용역 입찰 (target: tm_gd014000)
- [x] 나라장터 외자 입찰 (target: tm_gd014000)
- [x] 나라장터 물품 입찰 (target: tm_gd014000)
- [x] 네이버 뉴스 수집 (target: tm_gd014001)

## 커스텀 실행기 등록 [등록]
- [x] 제주 관측점 제원 수집 (target: tb_jeju_jewon)
- [x] 제주 수위 관측 데이터 수집 (target: tb_jeju)
- [x] 제주 이용시설 수집 — 자동/수동 (target: rgetnpmms01, rgetstgms01)
- [x] 제주 수질검사 수집 (target: rgetnwavi05, rgetnwavi06)
- [x] 안양 이용량 수집 (target: anyang_api_fac, anyang_api_data, use_legacy_data)
- [x] 약수터 제원 수집 (4/29, target: tm_gd010310, B-1 자연키 UK + DO UPDATE)
- [x] 약수터 수질 수집 (4/29, target: td_gd010310, 67 매핑 자동 등록, 4키 dedup)

## 후속 파이프라인 연결 [등록]
- [x] 제주 제원 → 내부망 GIMS 적재 (TM_GD970001 외 5개)
- [x] 제주 수위 관측 → 내부망 GIMS 적재 (PM_GD970201, PM_GD970202)
- [x] 제주 이용시설 → 내부망 GIMS 적재 (TM_GD111010)
- [x] 이용량 데이터 → 내부망 GIMS 적재 (PM_GD111021/22, TM_GD111024/25)
- [x] 제주 일집계 → SND 전송
- [x] 나라장터/네이버 뉴스 → 내부망 GIMS 적재 (TM_GD014000, TM_GD014001)
- [x] 약수터 → 내부망 GIMS 적재 (4/30, TM_GD010310, TD_GD010310 — 제원 1026 + 수질 1000 row E2E)
