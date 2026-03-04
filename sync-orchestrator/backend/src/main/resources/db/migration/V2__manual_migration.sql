-- =====================================================
-- 수동 마이그레이션 SQL
-- sourceRef 간소화를 위한 필드 추가
--
-- 사용법:
--   PostgreSQL: psql -d orchestrator -f V2__manual_migration.sql
--   또는 DBeaver 등에서 직접 실행
-- =====================================================

-- =====================================================
-- PostgreSQL 버전
-- =====================================================

-- 1. datasource 테이블에 id 컬럼 추가
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'datasource' AND column_name = 'id'
    ) THEN
        ALTER TABLE datasource ADD COLUMN id BIGSERIAL;
    END IF;
END $$;

-- 2. zone_config 테이블에 short_code 컬럼 추가
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'zone_config' AND column_name = 'short_code'
    ) THEN
        ALTER TABLE zone_config ADD COLUMN short_code VARCHAR(5);
    END IF;
END $$;

-- 3. short_code 초기값 설정
UPDATE zone_config SET short_code = 'E' WHERE zone = 'EXTERNAL' AND (short_code IS NULL OR short_code = '');
UPDATE zone_config SET short_code = 'D' WHERE zone = 'DMZ' AND (short_code IS NULL OR short_code = '');
UPDATE zone_config SET short_code = 'IC' WHERE zone = 'INTERNAL_COMMON' AND (short_code IS NULL OR short_code = '');
UPDATE zone_config SET short_code = 'IS' WHERE zone = 'INTERNAL_SERVICE' AND (short_code IS NULL OR short_code = '');

-- 4. NOT NULL 제약조건 추가
ALTER TABLE zone_config ALTER COLUMN short_code SET NOT NULL;

-- =====================================================
-- 검증
-- =====================================================
SELECT 'datasource 테이블 확인:' AS info;
SELECT datasource_id, id, datasource_name FROM datasource ORDER BY id;

SELECT 'zone_config 테이블 확인:' AS info;
SELECT zone, short_code, proxy_agent_url FROM zone_config ORDER BY zone;


-- =====================================================
-- 롤백 (필요시)
-- =====================================================
-- ALTER TABLE datasource DROP COLUMN IF EXISTS id;
-- ALTER TABLE zone_config DROP COLUMN IF EXISTS short_code;
