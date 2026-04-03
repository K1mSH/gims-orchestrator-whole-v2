-- ============================================================
-- 제주/이용량 내부망 RCV Agent + 테이블 등록
-- 실행: PGPASSWORD=1111 psql -h localhost -p 29001 -U k1m -d orchestrator < orchestrator-internal-jeju-use-register.sql
-- ============================================================

-- Agent 등록
INSERT INTO agent (agent_code, agent_name, agent_type, zone, source_datasource_id, target_datasource_id, endpoint_url, is_active)
VALUES ('internal-jeju-rcv', '제주 내부망 RCV', 'RCV', 'INTERNAL', 'dmz', 'internal', 'http://localhost:8092', true)
ON CONFLICT (agent_code) DO NOTHING;

INSERT INTO agent (agent_code, agent_name, agent_type, zone, source_datasource_id, target_datasource_id, endpoint_url, is_active)
VALUES ('internal-use-rcv', '이용량 내부망 RCV', 'RCV', 'INTERNAL', 'dmz', 'internal', 'http://localhost:8092', true)
ON CONFLICT (agent_code) DO NOTHING;

-- 제주 IF_SND 소스 테이블 (dmz datasource)
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('dmz', 'if_snd_tb_jeju_jewon', '제주 관측점 SND') ON CONFLICT DO NOTHING;
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('dmz', 'if_snd_tb_jeju', '제주 수위 SND') ON CONFLICT DO NOTHING;
-- if_snd_rgetstgms01은 이미 등록되어 있을 수 있음
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('dmz', 'if_snd_rgetstgms01', '이용실태 SND') ON CONFLICT DO NOTHING;

-- 이용량 IF_SND 소스 테이블 (dmz datasource)
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('dmz', 'if_snd_use_legacy_data', '이용량 레거시 SND') ON CONFLICT DO NOTHING;
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('dmz', 'if_snd_use_status_data', '이용량 상태 SND') ON CONFLICT DO NOTHING;
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('dmz', 'if_snd_use_jeju_day', '제주 일일이용량 SND') ON CONFLICT DO NOTHING;

-- 제주 IF_RSV 타겟 테이블 (internal datasource)
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('internal', 'IF_RSV_TB_JEJU_JEWON', '제주 관측점 RSV') ON CONFLICT DO NOTHING;
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('internal', 'IF_RSV_TB_JEJU', '제주 수위 RSV') ON CONFLICT DO NOTHING;
-- IF_RSV_RGETSTGMS01은 새올에서 이미 등록됨

-- 이용량 IF_RSV 타겟 테이블 (internal datasource)
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('internal', 'IF_RSV_USE_LEGACY_DATA', '이용량 레거시 RSV') ON CONFLICT DO NOTHING;
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('internal', 'IF_RSV_USE_STATUS_DATA', '이용량 상태 RSV') ON CONFLICT DO NOTHING;
INSERT INTO datasource_table (datasource_id, table_name, table_alias) VALUES ('internal', 'IF_RSV_USE_JEJU_DAY', '제주 일일이용량 RSV') ON CONFLICT DO NOTHING;
