-- PRV 응답 alias v3 호환 정렬 — ApiPrvOperationColumn 일괄 갱신
-- 정책: 내부 DB = 표준화 / 외부 응답 = v3 레거시 alias
-- 작성일: 2026-04-28
--
-- 11 핸들러 × column_name + alias_name 동시 갱신
-- handler_key 로 operation.id 식별 후 column_name 일치 row UPDATE

-- ========== B5 (id=24) — 17 컬럼 모두 소문자 ==========
UPDATE api_prv_operation_column SET column_name='gennum',         alias_name='gennum'         WHERE operation_id=24 AND column_name='GENNUM';
UPDATE api_prv_operation_column SET column_name='jiguname',       alias_name='jiguname'       WHERE operation_id=24 AND column_name='JIGUNAME';
UPDATE api_prv_operation_column SET column_name='obsv_code',      alias_name='obsv_code'      WHERE operation_id=24 AND column_name='OBSV_CODE';
UPDATE api_prv_operation_column SET column_name='inperm_no',      alias_name='inperm_no'      WHERE operation_id=24 AND column_name='INPERM_NO';
UPDATE api_prv_operation_column SET column_name='addr',           alias_name='addr'           WHERE operation_id=24 AND column_name='ADDR';
UPDATE api_prv_operation_column SET column_name='pyogo',          alias_name='pyogo'          WHERE operation_id=24 AND column_name='PYOGO';
UPDATE api_prv_operation_column SET column_name='sour_gov',       alias_name='sour_gov'       WHERE operation_id=24 AND column_name='SOUR_GOV';
UPDATE api_prv_operation_column SET column_name='insdate',        alias_name='insdate'        WHERE operation_id=24 AND column_name='INSDATE';
UPDATE api_prv_operation_column SET column_name='obsv_type',      alias_name='obsv_type'      WHERE operation_id=24 AND column_name='OBSV_TYPE';
UPDATE api_prv_operation_column SET column_name='well',           alias_name='well'           WHERE operation_id=24 AND column_name='WELL';
UPDATE api_prv_operation_column SET column_name='casing_height',  alias_name='casing_height'  WHERE operation_id=24 AND column_name='CASING_HEIGHT';
UPDATE api_prv_operation_column SET column_name='guldep',         alias_name='guldep'         WHERE operation_id=24 AND column_name='GULDEP';
UPDATE api_prv_operation_column SET column_name='guldia',         alias_name='guldia'         WHERE operation_id=24 AND column_name='GULDIA';
UPDATE api_prv_operation_column SET column_name='gigwanmethod',   alias_name='gigwanmethod'   WHERE operation_id=24 AND column_name='GIGWANMETHOD';
UPDATE api_prv_operation_column SET column_name='gigwanitem',     alias_name='gigwanitem'     WHERE operation_id=24 AND column_name='GIGWANITEM';
UPDATE api_prv_operation_column SET column_name='grounduse',      alias_name='grounduse'      WHERE operation_id=24 AND column_name='GROUNDUSE';
UPDATE api_prv_operation_column SET column_name='uwater_pota_yn', alias_name='uwater_pota_yn' WHERE operation_id=24 AND column_name='UWATER_POTA_YN';

-- ========== B6 (id=23) — 6 컬럼 소문자 ==========
UPDATE api_prv_operation_column SET column_name='addr',      alias_name='addr'      WHERE operation_id=23 AND column_name='ADDR';
UPDATE api_prv_operation_column SET column_name='jiguname',  alias_name='jiguname'  WHERE operation_id=23 AND column_name='JIGUNAME';
UPDATE api_prv_operation_column SET column_name='wellnum',   alias_name='wellnum'   WHERE operation_id=23 AND column_name='WELLNUM';
UPDATE api_prv_operation_column SET column_name='grounduse', alias_name='grounduse' WHERE operation_id=23 AND column_name='GROUNDUSE';
UPDATE api_prv_operation_column SET column_name='drinkox',   alias_name='drinkox'   WHERE operation_id=23 AND column_name='DRINKOX';
UPDATE api_prv_operation_column SET column_name='gubun',     alias_name='gubun'     WHERE operation_id=23 AND column_name='GUBUN';

-- ========== B9 (id=26) — 6 컬럼 소문자 ==========
UPDATE api_prv_operation_column SET column_name='gennum', alias_name='gennum' WHERE operation_id=26 AND column_name='GENNUM';
UPDATE api_prv_operation_column SET column_name='ymd',    alias_name='ymd'    WHERE operation_id=26 AND column_name='YMD';
UPDATE api_prv_operation_column SET column_name='elev',   alias_name='elev'   WHERE operation_id=26 AND column_name='ELEV';
UPDATE api_prv_operation_column SET column_name='wtemp',  alias_name='wtemp'  WHERE operation_id=26 AND column_name='WTEMP';
UPDATE api_prv_operation_column SET column_name='lev',    alias_name='lev'    WHERE operation_id=26 AND column_name='LEV';
UPDATE api_prv_operation_column SET column_name='ec',     alias_name='ec'     WHERE operation_id=26 AND column_name='EC';

-- ========== B10 (id=27) — 6 컬럼 소문자 ==========
UPDATE api_prv_operation_column SET column_name='gennum', alias_name='gennum' WHERE operation_id=27 AND column_name='GENNUM';
UPDATE api_prv_operation_column SET column_name='ymd',    alias_name='ymd'    WHERE operation_id=27 AND column_name='YMD';
UPDATE api_prv_operation_column SET column_name='elev',   alias_name='elev'   WHERE operation_id=27 AND column_name='ELEV';
UPDATE api_prv_operation_column SET column_name='wtemp',  alias_name='wtemp'  WHERE operation_id=27 AND column_name='WTEMP';
UPDATE api_prv_operation_column SET column_name='lev',    alias_name='lev'    WHERE operation_id=27 AND column_name='LEV';
UPDATE api_prv_operation_column SET column_name='ec',     alias_name='ec'     WHERE operation_id=27 AND column_name='EC';

-- ========== B14 (id=15) — 4 컬럼 camelCase ==========
UPDATE api_prv_operation_column SET column_name='josacode',             alias_name='josacode'             WHERE operation_id=15 AND column_name='JOSACODE';
UPDATE api_prv_operation_column SET column_name='dtaStdrYear',          alias_name='dtaStdrYear'          WHERE operation_id=15 AND column_name='DTA_STDR_YEAR';
UPDATE api_prv_operation_column SET column_name='qltwtrInspctIemCode',  alias_name='qltwtrInspctIemCode'  WHERE operation_id=15 AND column_name='QLTWTR_INSPCT_IEM_CODE';
UPDATE api_prv_operation_column SET column_name='remarkCtnt',           alias_name='remarkCtnt'           WHERE operation_id=15 AND column_name='CD_CN';

-- ========== B15 (id=16) — 2 컬럼 camelCase ==========
UPDATE api_prv_operation_column SET column_name='qltwtrInspctIemCode', alias_name='qltwtrInspctIemCode' WHERE operation_id=16 AND column_name='QLTWTR_INSPCT_IEM_CODE';
UPDATE api_prv_operation_column SET column_name='remarkCtnt',          alias_name='remarkCtnt'          WHERE operation_id=16 AND column_name='CD_CN';

-- ========== B16-DJ (id=19) — 4 컬럼 소문자 + YN 유지 ==========
UPDATE api_prv_operation_column SET column_name='sigungu', alias_name='sigungu' WHERE operation_id=19 AND column_name='SIGUNGU';
UPDATE api_prv_operation_column SET column_name='year',    alias_name='year'    WHERE operation_id=19 AND column_name='YEAR';
UPDATE api_prv_operation_column SET column_name='depart',  alias_name='depart'  WHERE operation_id=19 AND column_name='DEPART';
UPDATE api_prv_operation_column SET column_name='ymd',     alias_name='ymd'     WHERE operation_id=19 AND column_name='YMD';
-- YN 그대로

-- ========== B16-KB (id=20) — 동일 ==========
UPDATE api_prv_operation_column SET column_name='sigungu', alias_name='sigungu' WHERE operation_id=20 AND column_name='SIGUNGU';
UPDATE api_prv_operation_column SET column_name='year',    alias_name='year'    WHERE operation_id=20 AND column_name='YEAR';
UPDATE api_prv_operation_column SET column_name='depart',  alias_name='depart'  WHERE operation_id=20 AND column_name='DEPART';
UPDATE api_prv_operation_column SET column_name='ymd',     alias_name='ymd'     WHERE operation_id=20 AND column_name='YMD';

-- ========== B17 (id=18) — 10 컬럼 소문자 ==========
UPDATE api_prv_operation_column SET column_name='sido',       alias_name='sido'       WHERE operation_id=18 AND column_name='SIDO';
UPDATE api_prv_operation_column SET column_name='sigungu',    alias_name='sigungu'    WHERE operation_id=18 AND column_name='SIGUNGU';
UPDATE api_prv_operation_column SET column_name='total',      alias_name='total'      WHERE operation_id=18 AND column_name='TOTAL';
UPDATE api_prv_operation_column SET column_name='used',       alias_name='used'       WHERE operation_id=18 AND column_name='USED';
UPDATE api_prv_operation_column SET column_name='unused',     alias_name='unused'     WHERE operation_id=18 AND column_name='UNUSED';
UPDATE api_prv_operation_column SET column_name='undefined',  alias_name='undefined'  WHERE operation_id=18 AND column_name='UNDEFINED';
UPDATE api_prv_operation_column SET column_name='permission', alias_name='permission' WHERE operation_id=18 AND column_name='PERMISSION';
UPDATE api_prv_operation_column SET column_name='register',   alias_name='register'   WHERE operation_id=18 AND column_name='REGISTER';
UPDATE api_prv_operation_column SET column_name='restore',    alias_name='restore'    WHERE operation_id=18 AND column_name='RESTORE';
UPDATE api_prv_operation_column SET column_name='none',       alias_name='none'       WHERE operation_id=18 AND column_name='NONE';

-- ========== B18 (id=25) — 7 컬럼 소문자 ==========
UPDATE api_prv_operation_column SET column_name='sido',    alias_name='sido'    WHERE operation_id=25 AND column_name='SIDO';
UPDATE api_prv_operation_column SET column_name='sigungu', alias_name='sigungu' WHERE operation_id=25 AND column_name='SIGUNGU';
UPDATE api_prv_operation_column SET column_name='year',    alias_name='year'    WHERE operation_id=25 AND column_name='YEAR';
UPDATE api_prv_operation_column SET column_name='odr',     alias_name='odr'     WHERE operation_id=25 AND column_name='ODR';
UPDATE api_prv_operation_column SET column_name='total',   alias_name='total'   WHERE operation_id=25 AND column_name='TOTAL';
UPDATE api_prv_operation_column SET column_name='complt',  alias_name='complt'  WHERE operation_id=25 AND column_name='COMPLT';
UPDATE api_prv_operation_column SET column_name='ncomplt', alias_name='ncomplt' WHERE operation_id=25 AND column_name='NCOMPLT';

-- ========== B13 (id=28) — 19 고정 컬럼은 이미 camelCase OK. 동적 컬럼은 호출 시점 가변이므로 등록 안 됨 ==========
-- skip

-- 검증
SELECT o.id, o.handler_key, c.column_name, c.alias_name
FROM api_prv_operation_column c
JOIN api_prv_operation o ON o.id = c.operation_id
WHERE o.operation_type='CUSTOM'
ORDER BY o.id, c.display_order;
