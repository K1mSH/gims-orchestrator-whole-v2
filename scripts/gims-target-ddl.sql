-- ============================================================
-- GIMS Target 테이블 DDL (내부 PG: orchestrator DB)
-- 내부망 Loader가 적재하는 대상 테이블
-- ============================================================

-- 1. 관측소 (제원)
CREATE TABLE IF NOT EXISTS tm_gd970001 (
    spot_id BIGSERIAL PRIMARY KEY,
    obsrvt_id VARCHAR(50) NOT NULL,
    spot_ty_mng_word_nm VARCHAR(100),
    obsrvt_nm VARCHAR(200),
    well_no INTEGER,
    ctprvn_nm VARCHAR(50),           -- 시도
    sgg_nm VARCHAR(50),              -- 시군구
    emd_nm VARCHAR(50),              -- 읍면동
    bunji VARCHAR(50),               -- 번지
    ri VARCHAR(50),                  -- 리
    lo_crd VARCHAR(50),              -- 경도 (X)
    la_crd VARCHAR(50),              -- 위도 (Y)
    grnd_elev NUMERIC,               -- 표고
    instl_de DATE,                   -- 설치일
    drlg_dpth NUMERIC,               -- 굴착깊이
    drlg_diam NUMERIC,               -- 굴착지름
    reg_de DATE,                     -- 등록일
    csng_hght NUMERIC,               -- 케이싱높이
    UNIQUE (obsrvt_id, spot_ty_mng_word_nm)
);

CREATE INDEX IF NOT EXISTS idx_tm_gd970001_obsrvt_id ON tm_gd970001 (obsrvt_id);
CREATE INDEX IF NOT EXISTS idx_tm_gd970001_spot_ty ON tm_gd970001 (spot_ty_mng_word_nm);

-- 2. 결과 매핑 (참조)
CREATE TABLE IF NOT EXISTS tm_gd970101 (
    result_id BIGSERIAL PRIMARY KEY,
    time_unit_id INTEGER NOT NULL,
    obsrvn_iem_id INTEGER NOT NULL,
    spot_id BIGINT REFERENCES tm_gd970001(spot_id) ON DELETE CASCADE,
    UNIQUE (time_unit_id, obsrvn_iem_id, spot_id)
);

CREATE INDEX IF NOT EXISTS idx_tm_gd970101_spot_id ON tm_gd970101 (spot_id);

-- 3. 관측자료 (EAV)
CREATE TABLE IF NOT EXISTS pm_gd970201 (
    obsrvn_dta_id BIGSERIAL PRIMARY KEY,
    result_id BIGINT REFERENCES tm_gd970101(result_id) ON DELETE CASCADE,
    obsrvn_dta_value NUMERIC,
    obsrvn_dt TIMESTAMP NOT NULL,
    qlt_id INTEGER DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_pm_gd970201_result_id ON pm_gd970201 (result_id);
CREATE INDEX IF NOT EXISTS idx_pm_gd970201_obsrvn_dt ON pm_gd970201 (obsrvn_dt);

-- 4. Link 증분 추적
CREATE TABLE IF NOT EXISTS tm_gd980002 (
    spot_id BIGINT,
    obsrvt_id VARCHAR(50) NOT NULL,
    last_obsrvn_de VARCHAR(8),
    last_obsrvn_time VARCHAR(6),
    change_dt TIMESTAMP,
    frst_date VARCHAR(8),
    frst_time VARCHAR(6),
    PRIMARY KEY (obsrvt_id)
);

-- ============================================================
-- Orchestrator 등록 SQL
-- ============================================================

-- Agent 등록 (internal-bojo-loader)
-- ※ datasource 'internal'은 이미 등록되어 있으므로 별도 등록 불필요
--   (source/target 모두 internal — localhost:29002/dev)
INSERT INTO agent (id, agent_code, agent_name, agent_type, zone, endpoint_url,
                   source_datasource_id, target_datasource_id,
                   status, is_active, created_at)
VALUES (
    nextval('agent_id_seq'),
    'internal-bojo-loader',
    '내부망-보조-LOADER',
    'LOADER',
    'INTERNAL',
    'http://localhost:8092',
    'internal',
    'internal',
    'ONLINE',
    true,
    NOW()
) ON CONFLICT (agent_code) DO NOTHING;

-- Step Definition 등록
INSERT INTO agent_step_definition (id, agent_id, step_id, step_name, description, display_order, enabled_by_default, created_at)
VALUES (
    nextval('agent_step_definition_id_seq'),
    (SELECT id FROM agent WHERE agent_code = 'internal-bojo-loader'),
    'internal-load',
    '내부망 GIMS 적재',
    'IF_RSV 관측데이터를 EAV 확장하여 GIMS Target(pm_gd970201)에 적재, Link(tm_gd980002) 갱신',
    1,
    true,
    NOW()
) ON CONFLICT (agent_id, step_id) DO NOTHING;

-- Execution Param 등록: 시간 범위
INSERT INTO agent_execution_param (id, agent_id, param_id, label, description, data_type, default_value, is_enabled, display_order, created_at)
VALUES (
    nextval('agent_execution_param_id_seq'),
    (SELECT id FROM agent WHERE agent_code = 'internal-bojo-loader'),
    'startTime',
    '시작 시간',
    '지정 시 해당 시간 이후 데이터만 동기화 (미지정 시 PENDING 기반 증분)',
    'DATETIME',
    NULL,
    true,
    1,
    NOW()
) ON CONFLICT (agent_id, param_id) DO NOTHING;

INSERT INTO agent_execution_param (id, agent_id, param_id, label, description, data_type, default_value, is_enabled, display_order, created_at)
VALUES (
    nextval('agent_execution_param_id_seq'),
    (SELECT id FROM agent WHERE agent_code = 'internal-bojo-loader'),
    'endTime',
    '종료 시간',
    '지정 시 해당 시간 이전 데이터만 동기화 (미지정 시 현재 시각까지)',
    'DATETIME',
    NULL,
    true,
    2,
    NOW()
) ON CONFLICT (agent_id, param_id) DO NOTHING;

-- ============================================================
-- Agent Execution Mode 테이블
-- 실행 방식(모드) 메타데이터 — Agent 소스코드에 정의된 모드를 DB에 캐시
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_execution_mode (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    mode_id VARCHAR(50) NOT NULL,
    mode_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER DEFAULT 0,
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (agent_id, mode_id)
);

-- internal-bojo-loader 기본 모드 등록
INSERT INTO agent_execution_mode (id, agent_id, mode_id, mode_name, description, display_order, is_default, created_at)
VALUES (
    nextval('agent_execution_mode_id_seq'),
    (SELECT id FROM agent WHERE agent_code = 'internal-bojo-loader'),
    'incremental',
    '증분 적재',
    'PENDING 상태 데이터만 적재 (기본 동작)',
    1,
    true,
    NOW()
) ON CONFLICT (agent_id, mode_id) DO NOTHING;

INSERT INTO agent_execution_mode (id, agent_id, mode_id, mode_name, description, display_order, is_default, created_at)
VALUES (
    nextval('agent_execution_mode_id_seq'),
    (SELECT id FROM agent WHERE agent_code = 'internal-bojo-loader'),
    'full-reload',
    '전체 재적재',
    '모든 IF_RSV 데이터를 Target에 재적재 (시간 범위 무시)',
    2,
    false,
    NOW()
) ON CONFLICT (agent_id, mode_id) DO NOTHING;
