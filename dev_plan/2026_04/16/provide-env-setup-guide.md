# 전처리 Agent 개발 환경 구축 매뉴얼

> 작성일: 2026-04-16
> 전제: Docker 설치, Java 17+, Git

---

## 1. 원클릭 셋업

```bash
cd orchestrator_v2
bash scripts/provide-env-setup.sh
```

이 스크립트가 **전부** 해줍니다:
- Docker 컨테이너 3개 생성/시작 (Oracle, PG Orchestrator, PG Provider)
- Oracle에 관리 테이블 생성 (sync_log, execution)
- Oracle에 GIMS 원본 테이블 15개 DDL + 샘플 데이터
- PG에 데이터베이스 생성
- JASYPT 환경 변수 설정
- sync-agent-common 빌드
- JAR 복사 (common → bojo-int/libs/)
- sync-agent-bojo-int 빌드

## 2. Agent 기동

```bash
cd sync-agent-bojo-int
./gradlew bootRun
```

로그에서 확인:
```
[BojoInt] Agent 설정 로드: internal-provide-preprocessor (type=LOADER, steps=...)
```

## 3. 테스트 실행

```bash
curl -X POST http://localhost:8092/api/pipeline/execute \
  -H "Content-Type: application/json" \
  -d '{"agentCode":"internal-provide-preprocessor"}'
```

## 4. 결과 확인

```bash
# PG 제공 테이블
docker exec -it gims_api_provider_pg psql -U k1m -c "SELECT * FROM tm_provide_ngw04;"

# Oracle SyncLog
docker exec -it gims_orchestrator_inner_oracle sqlplus -s k1m/1111@//localhost:1521/XEPDB1 \
  <<< "SELECT STEP_ID, READ_COUNT, WRITE_COUNT FROM SYNC_LOG;"

# Oracle 원본 LINK_STATUS
docker exec -it gims_orchestrator_inner_oracle sqlplus -s k1m/1111@//localhost:1521/XEPDB1 \
  <<< "SELECT LINK_STATUS, COUNT(*) FROM TM_GD30302 GROUP BY LINK_STATUS;"
```

---

## 트러블슈팅

| 증상 | 해결 |
|------|------|
| `ORA-01017: invalid username/password` | `docker restart gims_orchestrator_inner_oracle` 후 1~2분 대기 |
| `Cannot determine embedded database driver` | `export JASYPT_PASSWORD=sync-pipeline-secret-key-2024` |
| `Connection refused: 29004` | `docker start gims_orchestrator_inner_oracle` |
| Agent 설정 로드 로그 없음 | `config/agents/*.yml` 파일 확인 |
| 빌드 실패 | Java 17+ 확인: `java -version` |

---

## 환경 정보

| 컨테이너 | 포트 | 용도 | 계정 |
|---------|------|------|------|
| gims_orchestrator_inner_oracle | 29004 | Oracle 소스 + Agent 관리 | k1m / 1111 |
| gims_orchestrator_dmz | 29001 | PG Orchestrator 중앙 관리 | k1m / 1111 |
| gims_api_provider_pg | 29006 | PG API Provider 전용 | k1m / 1111 |

| 환경 변수 | 값 |
|----------|-----|
| JASYPT_PASSWORD | sync-pipeline-secret-key-2024 |

---

## 관련 문서

| 문서 | 경로 |
|------|------|
| 전처리 개발 가이드 | `dev_plan/2026_04/16/preprocess-loader-guide.md` |
| 표준화 전체매핑 | `docs/Standardizedtable/표준화_전체매핑.tsv` |
| 기존 Oracle DDL | `scripts/ddl/internal-oracle/` |
