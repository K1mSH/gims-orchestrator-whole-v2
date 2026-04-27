-- ============================================================
-- B7/B8 DBLINK 소스 진단 쿼리 모음
-- 작성일: 2026-04-24
-- 배경: Type B 범위 결정 위해 레거시 `DBLINKUSR.*` 테이블이 현행 DB 에
--      실제로 존재하는지 / 갱신 주기 / 실사용 여부를 확인해야 함.
--
-- 사용법: 현행 운영 Oracle DB 접속 후 섹션별로 하나씩 실행.
--        결과를 dev_plan/2026_04/24/type-b-migration.md §9 또는
--        별도 메모에 기록.
-- ============================================================


-- ============================================================
-- 1. DBLINKUSR 스키마의 객체 전수 목록
-- ============================================================
-- 목적: 레거시가 참조하는 DBLINKUSR 의 실제 테이블/뷰 이름 파악
-- 레거시 참조 대상 (copySource/v3 기준):
--   DUBWLOBSIF, DUBMMWL, DUBRFOBSIF, DUBMMRF,
--   V_WP_WKSDAMSBSN, V_WR_HACHEON_MST
--
-- 2026-04-24 1차 확인 결과 (사용자): DUADTDAMIF, DUADTRF, DUADTWL,
--   DUBBOSPC, DUBMMWL, DUBWLOBSIF — 6건만 보임. 나머지 누락.
-- ============================================================
SELECT object_type, object_name
  FROM all_objects
 WHERE owner = 'DBLINKUSR'
 ORDER BY object_type, object_name;


-- ============================================================
-- 2. 누락된 객체가 다른 Owner 에 있는지 전역 검색
-- ============================================================
-- 목적: V_WP_WKSDAMSBSN, V_WR_HACHEON_MST, DUBRFOBSIF, DUBMMRF 가
--      다른 스키마/owner 로 이동했는지 확인
-- ============================================================
SELECT owner, object_type, object_name
  FROM all_objects
 WHERE object_name IN (
          'DUBRFOBSIF', 'DUBMMRF',
          'V_WP_WKSDAMSBSN', 'V_WR_HACHEON_MST'
       )
 ORDER BY owner, object_name;


-- ============================================================
-- 3. 우량(Rainfall) 관련 객체 키워드 검색
-- ============================================================
-- 목적: DUBRFOBSIF / DUBMMRF 가 다른 이름으로 있는지
-- ============================================================
SELECT owner, object_type, object_name
  FROM all_objects
 WHERE (object_name LIKE '%RF%'
         OR object_name LIKE '%RAIN%'
         OR object_name LIKE '%RFOBS%')
   AND owner IN ('DBLINKUSR')  -- 범위 좁히려면 유지, 전체 보려면 이 AND 줄 제거
 ORDER BY owner, object_type, object_name;


-- ============================================================
-- 4. 10분 자료 특성 컬럼 가진 객체
-- ============================================================
-- 목적: 이름이 바뀌어도 OBSDHM+TRMDV+WLOBSCD/RFOBSCD 조합은 유지될 가능성
-- ============================================================
SELECT owner, table_name, LISTAGG(column_name, ', ') WITHIN GROUP (ORDER BY column_name) cols
  FROM all_tab_columns
 WHERE column_name IN ('OBSDHM','TRMDV','WLOBSCD','RFOBSCD','WL','RF','FLW','ACURF')
 GROUP BY owner, table_name
HAVING COUNT(*) >= 2
 ORDER BY owner, table_name;


-- ============================================================
-- 5. Synonym 확인 (DUBRFOBSIF 등이 synonym 으로만 남아있을 가능성)
-- ============================================================
SELECT owner, synonym_name, table_owner, table_name, db_link
  FROM all_synonyms
 WHERE synonym_name LIKE 'DUB%'
    OR table_name LIKE 'DUB%'
    OR synonym_name LIKE 'V_W%'
 ORDER BY owner, synonym_name;


-- ============================================================
-- 6. DUBMMWL 갱신 주기 실측 (수위 10분자료)
-- ============================================================
-- 목적: 외부 DB 에 실제로 몇 분 지연으로 데이터가 들어오는지 측정
-- 방법: 아래 쿼리를 5~10분 간격으로 반복 실행 → MAX(OBSDHM) 증가 관찰
-- ============================================================

-- 6.1 현재 최신 OBSDHM vs SYSDATE 비교
SELECT MAX(OBSDHM) AS latest_obsdhm,
       TO_CHAR(SYSDATE, 'YYYYMMDDHH24MI') AS sysdate_str,
       TRMDV
  FROM DBLINKUSR.DUBMMWL
 WHERE TRMDV = '10'
 GROUP BY TRMDV;

-- 6.2 특정 관측소 1시간치 자료 개수 (10분 단위 완결성 체크 — 6건이 정상)
SELECT WLOBSCD,
       COUNT(*) cnt,
       MIN(OBSDHM) oldest,
       MAX(OBSDHM) latest
  FROM DBLINKUSR.DUBMMWL
 WHERE TRMDV = '10'
   AND OBSDHM >= TO_CHAR(SYSDATE - 1/24, 'YYYYMMDDHH24MI')
 GROUP BY WLOBSCD
 ORDER BY cnt DESC
 FETCH FIRST 10 ROWS ONLY;

-- 6.3 가장 최근 24시간 내 10분 단위별 레코드 수 (지연/공백 탐지)
SELECT SUBSTR(OBSDHM, 1, 12) ten_min_slot,
       COUNT(*) station_cnt
  FROM DBLINKUSR.DUBMMWL
 WHERE TRMDV = '10'
   AND OBSDHM >= TO_CHAR(SYSDATE - 1, 'YYYYMMDDHH24MI')
 GROUP BY SUBSTR(OBSDHM, 1, 12)
 ORDER BY ten_min_slot DESC
 FETCH FIRST 30 ROWS ONLY;


-- ============================================================
-- 7. 공개 API 카탈로그 등록 여부 확인
-- ============================================================
-- 목적: B7(getWaterLevelObservationStation) / B8(getRainfallStation) 이
--      실제 공개 API 로 노출되고 있는지 확인 — 비활성이면 이식 불필요
-- 레거시 관리 테이블 이름 추정 (확실한 건 아님):
-- ============================================================

-- 7.1 레거시 API 관리 테이블 후보 찾기
SELECT owner, table_name
  FROM all_tables
 WHERE table_name LIKE '%OPENAPI%'
    OR table_name LIKE '%API_LIST%'
    OR table_name LIKE '%TM_GD21%'   -- OPN 카탈로그 추정 prefix
    OR table_name LIKE '%TC_GD21%'
 ORDER BY owner, table_name;

-- 7.2 API 호출 이력 테이블 후보
SELECT owner, table_name
  FROM all_tables
 WHERE table_name LIKE '%TH_GD%'
    OR table_name LIKE '%API_LOG%'
    OR table_name LIKE '%API_HIST%'
 ORDER BY owner, table_name;

-- 7.3 최근 30일 OPN 관련 호출 이력 (7.2 결과로 테이블명 확정 후 실행)
-- SELECT CREAT_DT, SUCCES_AT, UGRWTR__API_KND_CODE, COUNT(*)
--   FROM TH_GD21301
--  WHERE CREAT_DT > SYSDATE - 30
--    AND (UGRWTR__API_KND_CODE LIKE '%observation%'
--         OR UGRWTR__API_KND_CODE LIKE '%Station%')
--  GROUP BY CREAT_DT, SUCCES_AT, UGRWTR__API_KND_CODE
--  ORDER BY CREAT_DT DESC;


-- ============================================================
-- 8. 참고 — sql_igis_analy.xml 에서 쓰이는 DBLINKUSR 테이블 전수
-- ============================================================
-- B7/B8 외 다른 곳에서 참조하는 객체도 참고용으로 함께 확인:
--
--  DBLINKUSR.DUADTWL            -- 일 수위 (있음 확인)
--  DBLINKUSR.DUADTRF            -- 일 우량 (있음 확인)
--  DBLINKUSR.DUADTDAMIF         -- 일 댐 (있음 확인)
--  DBLINKUSR.V_V_WR_AGRICDAMCGGSPC  -- 농업용 댐 제원 뷰
--  DBLINKUSR.V_WR_DAMSPEC_INFO  -- 댐 제원 뷰
--  DBLINKUSR.V_WP_WKSDAMSBSN    -- 댐 유역 (B7 에서도 참조, 현재 미발견)
--  DBLINKUSR.V_WR_ESTUARYBANKINFO -- 하구언 정보
--  DBLINKUSR.DUBBOSPC           -- 보 제원 (있음 확인)
--  DBLINKUSR.DUBWLOBSIF         -- 수위 관측소 (있음 확인, B7)
--  DBLINKUSR.DUBMMWL            -- 수위 10분자료 (있음 확인, B7)
--  DBLINKUSR.V_WR_HACHEON_MST   -- 하천 마스터 (B7, 현재 미발견)


-- ============================================================
-- 9. 결과 기록 위치
-- ============================================================
-- 확인 결과는 다음 중 한 곳에 기록:
--   - dev_plan/2026_04/24/type-b-migration.md §9 리스크 섹션
--   - docs/provide/LEGACY_API_MIGRATION_MAP.md 개정 이력
--   - 별도 dev_logs 항목
--
-- 핵심 확인 포인트:
--  (a) DUBRFOBSIF / DUBMMRF 진짜 없는가? 있으면 어디?
--  (b) 6.1~6.3 결과로 DUBMMWL 갱신 지연 시간 (분 단위)
--  (c) 7.3 결과로 B7/B8 실사용 빈도 (최근 30일 호출 0건이면 폐기 의심)
--
-- 위 3개 답에 따라 Type B 재개 조건 재검토.
