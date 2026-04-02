-- Orchestrator: 새올 SND 등록
-- Datasource + Agent + 32 tables

-- 1. Datasource
INSERT INTO datasource (datasource_id, datasource_name, db_type, host, port, database_name, username, password, zone, is_active, created_at, updated_at)
SELECT 'saeol-oracle', '새올 Oracle', 'ORACLE', 'localhost', 29005, 'XEPDB1', 'k1m', '1111', 'DMZ', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM datasource WHERE datasource_id = 'saeol-oracle');

-- 2. Agent
INSERT INTO agent (agent_code, agent_name, agent_type, endpoint_url, source_datasource_id, target_datasource_id, zone, is_active, status, created_at)
SELECT 'dmz-others-snd-saeol', 'dmz-others-snd-saeol', 'SND', 'http://localhost:8085', 'saeol-oracle', 'saeol-oracle', 'DMZ', true, 'ONLINE', NOW()
WHERE NOT EXISTS (SELECT 1 FROM agent WHERE agent_code = 'dmz-others-snd-saeol');

-- 3. Source tables (16)
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETSTGMS01', '이용실태', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETSTGMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNPMMS01', '허가신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNPMMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNWAVI05', '수질검사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNWAVI05');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNWAVI06', '수질검사내역', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNWAVI06');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNMNFE01', '인력장비', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNMNFE01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNOPMS01', '이용신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNOPMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNTGMS02', '지표수수질', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNTGMS02');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNKCNO01', '케이싱', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNKCNO01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNWAMS01', '지표수환경기준', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNWAMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNSIMS01', '심화조사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNSIMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNYYMS01', '용년', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNYYMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNJHMS01', '정화조', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNJHMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNSCKT01', '스케치', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNSCKT01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNKMTB01', '공간매체', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNKMTB01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETHKMIR01', '현황', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETHKMIR01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'RGETNYCSG01', '연락처', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='RGETNYCSG01');

-- 4. IF_SND tables (16)
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETSTGMS01', 'IF_SND 이용실태', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETSTGMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNPMMS01', 'IF_SND 허가신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNPMMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNWAVI05', 'IF_SND 수질검사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNWAVI05');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNWAVI06', 'IF_SND 수질검사내역', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNWAVI06');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNMNFE01', 'IF_SND 인력장비', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNMNFE01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNOPMS01', 'IF_SND 이용신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNOPMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNTGMS02', 'IF_SND 지표수수질', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNTGMS02');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNKCNO01', 'IF_SND 케이싱', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNKCNO01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNWAMS01', 'IF_SND 지표수환경기준', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNWAMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNSIMS01', 'IF_SND 심화조사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNSIMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNYYMS01', 'IF_SND 용년', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNYYMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNJHMS01', 'IF_SND 정화조', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNJHMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNSCKT01', 'IF_SND 스케치', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNSCKT01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNKMTB01', 'IF_SND 공간매체', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNKMTB01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETHKMIR01', 'IF_SND 현황', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETHKMIR01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'saeol-oracle', 'IF_SND_RGETNYCSG01', 'IF_SND 연락처', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='saeol-oracle' AND table_name='IF_SND_RGETNYCSG01');
