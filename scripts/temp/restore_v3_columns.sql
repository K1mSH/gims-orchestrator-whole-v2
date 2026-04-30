-- v3 응답 컬럼 호환 보정 — B2 JOSACODE 누락분 추가
-- 작성일: 2026-04-29
-- 정책: 외부 응답 = v3 레거시 alias (feedback_provide_response_v3_compat)
-- 원자료: copySource/v3/src/egovframework/sqlmap/com/gims/sql_megokrapi.xml:44-83
--
-- v3 selectNgw03_01 외부 SELECT 의 컬럼 목록 중 JOSACODE 가 현재 등록 누락.
-- api_prv_tmp_megokr_api 는 표준화 매핑 없는 테이블 (룰 B) 이라 PG 컬럼명도 v3 명 'josacode' 그대로.
-- alias_name 만 v3 응답 명 'JOSACODE' 로 정렬.
--
-- (RNUM 은 코드 측 자바 후처리로 처리 — DynamicQueryService.prependRownum, DB 변경 없음)

INSERT INTO api_prv_operation_column
  (operation_id, column_name, alias_name, display_order, transform_type)
SELECT id, 'josacode', 'JOSACODE', 22, 'NONE'
FROM api_prv_operation
WHERE operation_id = 'megokrApi/ngw03_01';
