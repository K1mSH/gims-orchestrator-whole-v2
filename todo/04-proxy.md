# sync-proxy (DMZ / Internal)

## [E1] 신규 모듈 구축
- [x] sync-proxy-dmz 생성 (port 8083)
- [x] sync-proxy-internal 생성 (port 8093)
- [x] ProxyDataSourceService (캐시 + Orchestrator fallback)
- [x] HealthController (/health, type: DB_CON_PROXY)

## [E2] 보안
- [x] ConnectionInfoController (Orchestrator 패스스루)
- [x] ApiKeyFilter 통합 (common 이동)
- [x] /debug/datasources 제거
- [x] HikariCP 풀 하드닝
- [x] application.yml Jasypt 암호화
- [x] controller/filter 조건부 활성화

**진행도: 10/10 = 100%**
