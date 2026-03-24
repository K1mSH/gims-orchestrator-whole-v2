# API Collector Docker 배포 테스트

> **상태**: 대기
> **목적**: infolink-api-collector를 Docker로 빌드·배포하고, JASYPT_PASSWORD 환경변수 주입이 정상 작동하는지 검증

---

## 배경

- 현재 로컬에서 `gradlew bootRun`으로만 실행 중
- docker-compose.yaml, .env.example은 존재하나 실제 Docker 배포 테스트는 미진행
- JASYPT_PASSWORD가 docker-compose.yaml → 컨테이너 → Spring → Jasypt 복호화까지 정상 흐르는지 확인 필요

## 확인 항목

1. **Docker 이미지 빌드**
   - Dockerfile 존재 여부 확인 (없으면 작성)
   - `docker build` 정상 완료

2. **docker-compose 기동**
   - JASYPT_PASSWORD 환경변수 주입
   - 앱 정상 기동 (healthcheck 통과)

3. **ENC() 복호화 검증**
   - DB 연결 성공 (암호화된 datasource 정보 복호화)
   - `/api/endpoints` 정상 응답

4. **외부 연동 확인**
   - Orchestrator 연결 (DataSource 정보 조회)
   - 프론트엔드 CORS 정상 동작

## 참고

- docker-compose.yaml: `infolink-api-collector/docker-compose.yaml`
- .env.example: `infolink-api-collector/.env.example`
- 암호화 문서: `docs/tech/dynamic-datasource.md`
