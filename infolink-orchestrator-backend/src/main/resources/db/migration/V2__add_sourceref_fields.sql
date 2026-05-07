-- =====================================================
-- Migration: sourceRef 간소화를 위한 필드 추가
-- 버전: V2
-- 설명:
--   - datasource 테이블에 id (숫자 PK) 추가
--   - zone_config 테이블에 short_code 추가
-- =====================================================

-- =====================================================
-- 1. datasource 테이블에 id 컬럼 추가
-- =====================================================

-- PostgreSQL
ALTER TABLE datasource ADD COLUMN IF NOT EXISTS id BIGSERIAL;

-- 기존 데이터에 id 값 부여 (이미 SERIAL이므로 자동 생성됨)
-- 만약 기존 데이터가 있다면 아래 주석 해제
-- UPDATE datasource SET id = nextval('datasource_id_seq') WHERE id IS NULL;


-- =====================================================
-- 2. zone_config 테이블에 short_code 컬럼 추가
-- =====================================================

-- PostgreSQL
ALTER TABLE zone_config ADD COLUMN IF NOT EXISTS short_code VARCHAR(5);

-- short_code 초기값 설정
UPDATE zone_config SET short_code = 'E' WHERE zone = 'EXTERNAL' AND (short_code IS NULL OR short_code = '');
UPDATE zone_config SET short_code = 'D' WHERE zone = 'DMZ' AND (short_code IS NULL OR short_code = '');
UPDATE zone_config SET short_code = 'IC' WHERE zone = 'INTERNAL_COMMON' AND (short_code IS NULL OR short_code = '');
UPDATE zone_config SET short_code = 'IS' WHERE zone = 'INTERNAL_SERVICE' AND (short_code IS NULL OR short_code = '');

-- NOT NULL 제약조건 추가 (기존 데이터 업데이트 후)
ALTER TABLE zone_config ALTER COLUMN short_code SET NOT NULL;


-- =====================================================
-- 검증 쿼리
-- =====================================================
-- SELECT datasource_id, id FROM datasource;
-- SELECT zone, short_code FROM zone_config;
