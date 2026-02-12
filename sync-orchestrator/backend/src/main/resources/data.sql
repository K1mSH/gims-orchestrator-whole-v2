-- ZoneConfig 초기 데이터
-- 각 zone의 master agent URL을 설정합니다.
-- 실제 환경에 맞게 URL을 수정하세요.

-- DMZ zone master agent
INSERT INTO zone_config (zone, short_code, master_agent_url, description, is_active, created_at, updated_at)
SELECT 'DMZ', 'D', 'http://localhost:8081', 'DMZ zone master agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'DMZ');

-- EXTERNAL zone master agent
INSERT INTO zone_config (zone, short_code, master_agent_url, description, is_active, created_at, updated_at)
SELECT 'EXTERNAL', 'E', 'http://localhost:8082', 'External zone master agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'EXTERNAL');

-- INTERNAL_COMMON zone master agent
INSERT INTO zone_config (zone, short_code, master_agent_url, description, is_active, created_at, updated_at)
SELECT 'INTERNAL_COMMON', 'IC', 'http://localhost:8083', 'Internal common zone master agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'INTERNAL_COMMON');

-- INTERNAL_SERVICE zone master agent
INSERT INTO zone_config (zone, short_code, master_agent_url, description, is_active, created_at, updated_at)
SELECT 'INTERNAL_SERVICE', 'IS', 'http://localhost:8084', 'Internal service zone master agent', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zone_config WHERE zone = 'INTERNAL_SERVICE');
