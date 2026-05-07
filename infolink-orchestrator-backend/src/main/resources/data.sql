-- ZoneConfig 초기 데이터
-- 각 zone의 프록시 Agent URL을 설정합니다.
-- 실제 환경에 맞게 URL을 수정하세요.

-- DMZ zone 프록시 Agent (sync-proxy-dmz, port 8083)
INSERT INTO zone_config (zone, short_code, proxy_agent_url, description, is_active, created_at, updated_at)
SELECT 'DMZ', 'D', 'http://localhost:8083', 'DMZ zone proxy agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'DMZ');

-- EXTERNAL zone 프록시 Agent
INSERT INTO zone_config (zone, short_code, proxy_agent_url, description, is_active, created_at, updated_at)
SELECT 'EXTERNAL', 'E', 'http://localhost:8083', 'External zone proxy agent (DMZ 공유)', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'EXTERNAL');

-- INTERNAL zone 프록시 Agent (sync-proxy-internal, port 8093)
INSERT INTO zone_config (zone, short_code, proxy_agent_url, description, is_active, created_at, updated_at)
SELECT 'INTERNAL', 'I', 'http://localhost:8093', 'Internal zone proxy agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'INTERNAL');

-- INTERNAL_COMMON zone 프록시 Agent
INSERT INTO zone_config (zone, short_code, proxy_agent_url, description, is_active, created_at, updated_at)
SELECT 'INTERNAL_COMMON', 'IC', 'http://localhost:8093', 'Internal common zone proxy agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'INTERNAL_COMMON');

-- INTERNAL_SERVICE zone 프록시 Agent
INSERT INTO zone_config (zone, short_code, proxy_agent_url, description, is_active, created_at, updated_at)
SELECT 'INTERNAL_SERVICE', 'IS', 'http://localhost:8093', 'Internal service zone proxy agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'INTERNAL_SERVICE');
