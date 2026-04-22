-- ──────────────────────────────────────────────────────────
-- provide Agent 소스 스키마 초기화
-- 실행 주체: SYSTEM 또는 SYS
-- 실행 위치: Oracle 29004 (gims_orchestrator_inner_oracle)
-- 배경: 실운영 스키마 구조 재현 (dev_plan/2026_04/22/provide-source-table-strategy.md)
--
-- NGW 스키마: 기본 (개발 환경에서는 k1m 계정이 NGW 역할을 겸함)
-- SDE_NGWS:   가뭄119 (WT_DREAM_PERMWELL_PUBLIC_21033) 용 GIS 공간 스키마
-- DBLINKUSR:  DB Link 경유 수위/우량관측소 등 외부 DB 재현용 로컬 스키마
-- ──────────────────────────────────────────────────────────

-- SDE_NGWS 스키마 (GIS 공간 데이터)
CREATE USER SDE_NGWS IDENTIFIED BY "1111";
GRANT CONNECT, RESOURCE TO SDE_NGWS;
GRANT UNLIMITED TABLESPACE TO SDE_NGWS;

-- DBLINKUSR 스키마 (외부 DB 재현)
CREATE USER DBLINKUSR IDENTIFIED BY "1111";
GRANT CONNECT, RESOURCE TO DBLINKUSR;
GRANT UNLIMITED TABLESPACE TO DBLINKUSR;

-- 확인
SELECT username FROM dba_users
WHERE username IN ('SDE_NGWS', 'DBLINKUSR', 'K1M')
ORDER BY username;

-- 주의: 각 스키마에 테이블 생성 후, k1m 계정에
--       GRANT SELECT, INSERT, UPDATE, DELETE ON {schema}.{table} TO K1M
--       을 개별 테이블마다 부여해야 provide Agent(k1m datasource)가 접근 가능.
