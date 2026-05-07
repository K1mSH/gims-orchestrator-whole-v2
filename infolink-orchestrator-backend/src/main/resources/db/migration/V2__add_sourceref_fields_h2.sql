-- =====================================================
-- Migration: sourceRef 간소화를 위한 필드 추가 (H2용)
-- 버전: V2
-- 설명:
--   - datasource 테이블에 id (숫자 PK) 추가
--   - zone_config 테이블에 short_code 추가
-- =====================================================

-- =====================================================
-- 1. datasource 테이블에 id 컬럼 추가
-- =====================================================

-- H2: 컬럼 존재 여부 확인 후 추가
ALTER TABLE datasource ADD COLUMN IF NOT EXISTS id BIGINT AUTO_INCREMENT;


-- =====================================================
-- 2. zone_config 테이블에 short_code 컬럼 추가
-- =====================================================

-- H2
ALTER TABLE zone_config ADD COLUMN IF NOT EXISTS short_code VARCHAR(5);

-- short_code 초기값 설정
UPDATE zone_config SET short_code = 'E' WHERE zone = 'EXTERNAL' AND short_code IS NULL;
UPDATE zone_config SET short_code = 'D' WHERE zone = 'DMZ' AND short_code IS NULL;
UPDATE zone_config SET short_code = 'IC' WHERE zone = 'INTERNAL_COMMON' AND short_code IS NULL;
UPDATE zone_config SET short_code = 'IS' WHERE zone = 'INTERNAL_SERVICE' AND short_code IS NULL;

-- NOT NULL 제약조건 추가
ALTER TABLE zone_config ALTER COLUMN short_code SET NOT NULL;
