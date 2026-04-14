# 7. 보안

> **요구사항**: 다수의 DB 자격증명과 서비스 간 통신을 안전하게 보호하고,
> 관리 화면 접근을 인가된 사용자로 제한한다.

## 상태: 부분 완료 (로그인 미개발)

---

## DB 자격증명 암호화 [Common]
- [x] Jasypt PBEWithHMACSHA512AndAES_256 + RandomIV
- [x] PasswordEncryptor 클래스 (common 모듈, 전 서비스 공유)
- [x] 암호문 형식: ENC(base64) — decrypt 시 래핑 자동 제거
- [x] 암호화 키 외부화 (JASYPT_PASSWORD 환경변수, Docker 전달)
- [x] application.yml 내 민감정보 ENC() 래핑 (5개 모듈)
- [x] CLI 암복호화 도구 (scripts/encrypt.java)

## 자격증명 전달 흐름 [Common]
- [x] Orchestrator에서 암호화 후 DB 저장
- [x] 실행 요청에 ID만 전달 (평문 자격증명 미포함)
- [x] Agent → Proxy: datasourceId로 연결정보 요청 (X-API-Key 인증)
- [x] Proxy → Agent: 암호문 그대로 반환 (패스스루)
- [x] Agent: 자체 비밀키로 복호화 → ThreadLocal에 평문 보관 → DB 직접 접속
- [x] 실행 완료 시 ThreadLocal 정리 (clearCurrentDatasources)
- [x] 연결 테스트: Orchestrator 직접(zone 없음) 또는 Proxy 경유(zone 있음), 내부망 통신+API Key 인증, HTTPS 적용 시 전송 구간 보호 가능

## API Key 인증 [Common]
- [x] ApiKeyFilter (common 모듈, @ConditionalOnProperty)
- [x] 대상: /api/** (예외: /health, /api/pipeline/info)
- [x] 헤더: X-API-Key — Orchestrator RestTemplate에 자동 추가
- [x] 빈 키면 필터 비활성화 (개발 편의)

## 엔드포인트 보안 [Common]
- [x] @ConditionalOnProperty — 모듈별 엔드포인트 선택적 활성화
- [x] Agent: 전부 활성 / Proxy: execution-data, datasource만 활성
- [x] 새 컨트롤러 추가 시 명시적으로 켜지 않으면 비활성 (구조적 방어)
- [x] /debug/datasources 제거 (호스트/포트/DB명 노출 방지)

## HikariCP 풀 하드닝 [Common]
- [x] Agent/Proxy 통일 적용 (maxPool=10, timeout=10s, leak=60s)
- [ ] api-collector 하드닝 미적용 (maxPool=5, timeout/leak 미설정)

## 로그인/인가 [Orchestrator]
- [ ] 사용자 로그인 기능 (ID/PW 인증)
- [ ] 세션 또는 JWT 기반 인증 유지
- [ ] 권한 체계 (관리자/운영자/조회자 등)
- [ ] 미인증 접근 차단 (프론트 + 백엔드)
