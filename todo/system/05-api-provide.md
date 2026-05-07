# 5. API 제공

> **요구사항**: GIMS DB에 적재된 데이터를 외부 시스템에 API로 제공한다.
> UI에서 오퍼레이션을 등록하면 동적 SELECT 쿼리가 생성되어 API로 노출된다.
> 테이블 데이터는 이미 가공된 상태로 존재하며, 조인 등 복잡한 쿼리는 범위 밖.

## 상태: 개발진행중 (테스트 전)

---

## 동적 쿼리 범위 [참고]
- SELECT 컬럼: 제공할 컬럼 선택
- WHERE 조건: 필터 조건 설정
- ORDER BY: 정렬 기준
- LIMIT: 응답 건수 제한
- 범위 밖: JOIN, INSERT/UPDATE/DELETE, 서브쿼리, 집계함수

## 오퍼레이션 관리 [Backend]
- [x] 오퍼레이션 등록 (Datasource + 테이블 선택)
- [x] 제공 컬럼 선택
- [x] WHERE 조건 설정
- [x] 정렬/LIMIT 설정
- [x] 응답 포맷 (JSON)
- [x] 컬럼 가공 기능 (ROUND / DATE_FORMAT / COALESCE / SUBSTRING)
- [x] 파라미터 옵션 (필수/숨김/기본값)
- [x] 페이징 지원 (page/pageSize → LIMIT/OFFSET)

## 테스트 호출 [Backend]
- [x] 등록한 오퍼레이션 미리보기 (실제 쿼리 실행 → 결과 샘플 표시)
- [x] 결과 확인 후 활성화 — 비활성 시 외부 노출 안 됨
- [x] 활성/비활성 상태 관리
- [x] 생성 SQL 하이라이팅 표시 (디버깅용)

## API 엔드포인트 생성 [Backend]
- [x] 활성 오퍼레이션 → 동적 SELECT 쿼리 생성
- [x] REST API 자동 노출 (오퍼레이션 ID 기반, `/api/provide/{operationId}`)
- [x] 인증/인가 (API Key, IP 제한 등)
- [x] 동적 쿼리 보안 (화이트리스트 정규식 + PreparedStatement 바인딩)
- [x] 10종 WHERE 연산자 (=, >, >=, <, <=, LIKE 포함/시작/끝, IN, BETWEEN)
- [x] Proxy 경유 DataSource 획득·캐싱 (ProviderDataSourceService, bojo-internal 패턴)
- [x] Mock API Key 검증기 (운영 시 외부 API URL만 교체하면 전환 가능)
- [x] 슬래시 operationId 지원 (4/24, `megokrApi/ngw08` 레거시 URL 그대로 재현)
- [x] 응답 alias 대소문자 보존 (4/24, PG identifier lowercase 회피, 쌍따옴표 alias)
- [x] 응답 alias v3 호환 정렬 (4/28, 11종 핸들러 일괄 + frontend fix)
- [x] PRV 응답 키 = v3 레거시 alias 유지 메모리 룰 박힘 (`feedback_provide_response_v3_compat.md`)
- [x] A7 RNUM + B2 RNUM/JOSACODE 자바 후처리 (4/29, DynamicQueryService 화이트리스트, ROWNUM_PREPEND_OPS)
- [x] B2 josacode column 1행 INSERT (display_order=22) — v3 환경 등록 정합 (4/29)

## 실행/모니터링 [Backend]
- [x] 호출 이력 저장 (ApiPrvCallHistory)
- [ ] 호출량 통계
- [ ] 에러 모니터링
- [x] 비활성 오퍼레이션 차단 (404 응답)

## 오퍼레이션 관리 화면 [Frontend]
- [x] 오퍼레이션 목록/등록/수정
- [x] 컬럼 선택 UI
- [x] WHERE 조건 빌더
- [x] 테스트 결과 미리보기
- [x] 활성/비활성 토글
- [x] API 명세서 자동 생성 탭 (SpecTab)
- [x] 호출 이력 탭 (HistoryTab, 페이징)
- [x] 상세 6탭 UI 통합 (기본정보/컬럼/파라미터/테스트/명세서/이력)

## 인프라 [api-provider]
- [x] DB 구조 전환 — Oracle 단일 → PG 전용 컨테이너 (api_provider, 29006)
- [x] DB 컬럼 comment 관리 (comment.sql 기동 시 자동 실행)
- [x] Jasypt 암호화 (민감정보 ENC() 래핑)

## E2E 검증 [테스트]
- [x] 동적 SELECT 엔진 E2E 테스트 — Type A 12종 200 OK (4/24, Python 일괄 등록 + 호출 검증)
- [x] alias 변경 전후 회귀 검증 (4/28, 2회 Type A 12종 200 OK)
- [x] 호출 이력 저장 로직 검증 (ApiPrvCallHistory finally 패턴, 외부 호출만 / test 제외)
- [x] RNUM/JOSACODE 후처리 회귀 검증 (4/29, 다른 10종 응답 키 변화 없음)
- [ ] API Key 검증 외부화 시나리오 테스트 (Mock → 실제 검증 API 전환)
- [ ] 등록 플로우 브라우저 전체 재테스트
