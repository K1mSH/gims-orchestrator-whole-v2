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

---

## 범용 실행기 등록 [등록]
- [x] 나라장터 #1 공사 (ID:16, target: tm_gd014000)
- [x] 나라장터 #2 용역 (ID:18, target: tm_gd014000)
- [x] 나라장터 #3 외자 (ID:19, target: tm_gd014000)
- [x] 나라장터 #4 물품 (ID:20, target: tm_gd014000)
- [x] 네이버 뉴스 수집 (ID:15, target: tm_gd014001)

## 커스텀 실행기 등록 [등록]
- [x] D1 jeju-jewon — 제주 관측점 마스터 (ID:21, target: tb_jeju_jewon)
- [x] D2 jeju-obsv-data — 제주 수위 관측 (ID:22, target: tb_jeju)
- [x] D3 jeju-facility — 제주 이용시설 (ID:23, target: rgetstgms01)
- [x] D3 jeju-facility 수동 — 별도 오퍼레이션 (ID:24, target: rgetstgms01)
- [x] D4 jeju-water-quality — 제주 수질검사 (ID:25, target: rgetnwavi05/06)
- [x] D5 anyang-usage — 안양 이용량 (ID:17, target: anyang_api_fac/data, use_legacy_data)

## 후속 파이프라인 연결 [등록]
- [x] tb_jeju_jewon → dmz-others-snd-jeju → TM_GD970001 외 5개
- [x] tb_jeju → dmz-others-snd-jeju → PM_GD970201, PM_GD970202
- [x] rgetstgms01 → dmz-others-snd-jeju → TM_GD111010
- [x] use_legacy/status_data → dmz-others-snd-use → PM_GD111021/22, TM_GD111024/25
- [x] use_jeju_day → dmz-others-snd-use → (외부 시스템 적재, SND만 담당)
- [ ] tm_gd014000/014001 → dmz-others-snd-api-collect → TM_GD014000/014001 **(RCV/Loader 예정)**
