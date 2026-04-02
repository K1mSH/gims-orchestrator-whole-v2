-- Orchestrator: 새올 Internal Agent 등록

-- Agent: internal-saeol-rcv
INSERT INTO agent (agent_code, agent_name, agent_type, endpoint_url, source_datasource_id, target_datasource_id, zone, is_active, status, created_at)
SELECT 'internal-saeol-rcv', 'internal-saeol-rcv', 'RCV', 'http://localhost:8092', 'saeol-oracle', 'internal', 'INTERNAL', true, 'ONLINE', NOW()
WHERE NOT EXISTS (SELECT 1 FROM agent WHERE agent_code = 'internal-saeol-rcv');

-- Agent: internal-saeol-loader
INSERT INTO agent (agent_code, agent_name, agent_type, endpoint_url, source_datasource_id, target_datasource_id, zone, is_active, status, created_at)
SELECT 'internal-saeol-loader', 'internal-saeol-loader', 'LOADER', 'http://localhost:8092', 'internal', 'internal', 'INTERNAL', true, 'ONLINE', NOW()
WHERE NOT EXISTS (SELECT 1 FROM agent WHERE agent_code = 'internal-saeol-loader');

-- Internal datasource 테이블 등록 (IF_RSV 16개 + GIMS 타겟 16개)
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETSTGMS01', 'IF_RSV 이용실태', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETSTGMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNPMMS01', 'IF_RSV 허가신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNPMMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNWAVI05', 'IF_RSV 수질검사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNWAVI05');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNWAVI06', 'IF_RSV 수질검사내역', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNWAVI06');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNMNFE01', 'IF_RSV 인력장비', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNMNFE01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNOPMS01', 'IF_RSV 이용신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNOPMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNTGMS02', 'IF_RSV 지표수수질', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNTGMS02');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNKCNO01', 'IF_RSV 케이싱', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNKCNO01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNWAMS01', 'IF_RSV 지표수환경기준', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNWAMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNSIMS01', 'IF_RSV 심화조사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNSIMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNYYMS01', 'IF_RSV 용년', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNYYMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNJHMS01', 'IF_RSV 정화조', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNJHMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNSCKT01', 'IF_RSV 스케치', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNSCKT01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNKMTB01', 'IF_RSV 공간매체', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNKMTB01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETHKMIR01', 'IF_RSV 현황', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETHKMIR01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'IF_RSV_RGETNYCSG01', 'IF_RSV 연락처', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='IF_RSV_RGETNYCSG01');

-- GIMS 타겟 (원본 이름)
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETSTGMS01', 'GIMS 이용실태', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETSTGMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNPMMS01', 'GIMS 허가신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNPMMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNWAVI05', 'GIMS 수질검사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNWAVI05');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNWAVI06', 'GIMS 수질검사내역', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNWAVI06');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNMNFE01', 'GIMS 인력장비', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNMNFE01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNOPMS01', 'GIMS 이용신고', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNOPMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNTGMS02', 'GIMS 지표수수질', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNTGMS02');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNKCNO01', 'GIMS 케이싱', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNKCNO01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNWAMS01', 'GIMS 지표수환경기준', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNWAMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNSIMS01', 'GIMS 심화조사', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNSIMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNYYMS01', 'GIMS 용년', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNYYMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNJHMS01', 'GIMS 정화조', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNJHMS01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNSCKT01', 'GIMS 스케치', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNSCKT01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNKMTB01', 'GIMS 공간매체', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNKMTB01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETHKMIR01', 'GIMS 현황', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETHKMIR01');
INSERT INTO datasource_table (datasource_id, table_name, description, created_at) SELECT 'internal', 'RGETNYCSG01', 'GIMS 연락처', NOW() WHERE NOT EXISTS (SELECT 1 FROM datasource_table WHERE datasource_id='internal' AND table_name='RGETNYCSG01');
