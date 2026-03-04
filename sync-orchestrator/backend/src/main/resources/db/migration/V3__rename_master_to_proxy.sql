-- =====================================================
-- 수동 마이그레이션 SQL
-- zone_config.master_agent_url → proxy_agent_url 컬럼 이름 변경
-- 프록시 Agent 분리에 따른 용어 정리
--
-- 사용법:
--   PostgreSQL: psql -d orchestrator -f V3__rename_master_to_proxy.sql
--   또는 DBeaver 등에서 직접 실행
-- =====================================================

-- 1. 컬럼 이름 변경
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'zone_config' AND column_name = 'master_agent_url'
    ) THEN
        ALTER TABLE zone_config RENAME COLUMN master_agent_url TO proxy_agent_url;
        RAISE NOTICE 'Column renamed: master_agent_url → proxy_agent_url';
    ELSE
        RAISE NOTICE 'Column master_agent_url does not exist (already renamed or new install)';
    END IF;
END $$;

-- 2. INTERNAL zone 추가 (기존에 없을 수 있음)
INSERT INTO zone_config (zone, short_code, proxy_agent_url, description, is_active, created_at, updated_at)
SELECT 'INTERNAL', 'I', 'http://localhost:8093', 'Internal zone proxy agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'INTERNAL');

-- 3. 기존 zone의 프록시 URL 업데이트 (dev 환경 기준)
UPDATE zone_config SET proxy_agent_url = 'http://localhost:8083' WHERE zone = 'DMZ';
UPDATE zone_config SET proxy_agent_url = 'http://localhost:8083' WHERE zone = 'EXTERNAL';
UPDATE zone_config SET proxy_agent_url = 'http://localhost:8093' WHERE zone IN ('INTERNAL', 'INTERNAL_COMMON', 'INTERNAL_SERVICE');

-- =====================================================
-- 검증
-- =====================================================
SELECT 'zone_config 테이블 확인:' AS info;
SELECT zone, short_code, proxy_agent_url, is_active FROM zone_config ORDER BY zone;
