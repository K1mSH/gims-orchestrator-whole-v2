# Production Deployment — 마스터 체크리스트

배포 D-day 기준 순서대로 진행. 각 체크 완료 시 체크박스 + 확인자 + 일시 기록.

---

## Phase 1. 사전 준비 (D-7 ~ D-1)

### 1.1 코드 / 빌드
- [ ] main 브랜치 최신 상태
- [ ] 모든 모듈 `gradlew clean build -x test` 성공
- [ ] common JAR 최신본이 의존 모듈 `libs/` 에 복사됨
- [ ] 회귀 체크리스트 전 항목 통과 (`verify/checklists/`)
- [ ] invariant 위반 0 건 (`verify/_invariants/`)

### 1.2 설정 정리
- [ ] `config-replacement.md` 모든 dev 값 → 실배포 값 매핑 확정
- [ ] 임시값(tmp/dummy/placeholder/TODO) grep 스캔 결과 0 건
- [ ] credentials 외부화 완료 (yml 에 암호 하드코딩 없음)
- [ ] 경로 하드코딩 없음 (Windows 경로 / `D:/dev` 등 제거)

### 1.3 DB 준비
- [ ] 운영 Oracle / Tibero / PG DDL 배포
- [ ] 초기 데이터 INSERT (Orchestrator DB 등록 / Agent 설정)
- [ ] 인덱스 생성 확인 (특히 executionId 인덱스 6 개)
- [ ] GRANT 권한 부여
- [ ] 백업 정책 확인

### 1.4 네트워크 / 보안
- [ ] 방화벽 규칙 확정 (`environment-setup.md`)
- [ ] HTTPS 인증서 발급
- [ ] API 키 발급 / 교체 (`credentials-rotation.md`)
- [ ] IP 화이트리스트 확정

### 1.5 모니터링 준비
- [ ] 로그 수집 설정 (`monitoring-setup.md`)
- [ ] 알람 채널 테스트
- [ ] 헬스체크 엔드포인트 등록
- [ ] 대시보드 정상 표시

---

## Phase 2. 배포 D-day

### 2.1 환경 전환
- [ ] Spring profile = `prod`
- [ ] 로그 레벨 조정 (`info` / `warn` — `debug` 아님)
- [ ] `ddl-auto = validate` (create / update 금지)
- [ ] 타임존 (`TZ=Asia/Seoul`) / 인코딩 (UTF-8) 명시

### 2.2 치환 실행
- [ ] `config-replacement.md` 전 항목 치환 완료
- [ ] 치환 후 yml / env / Orchestrator DB 등록 전수 재검토

### 2.3 배포 순서
1. [ ] Orchestrator backend (8080)
2. [ ] API Provider (8095)
3. [ ] Proxy Internal (8093)
4. [ ] Proxy DMZ (8083)
5. [ ] infolink-agent-bojo-dmz (DMZ, 8082)
6. [ ] infolink-agent-others-dmz (DMZ, 8085)
7. [ ] infolink-agent-bojo-internal (Internal, 8092)
8. [ ] infolink-agent-provide-dmz (Internal, 8096)
9. [ ] API Collector DMZ (8084)
10. [ ] API Collector Internal (8094)
11. [ ] Frontend (3000)

> 순서 근거: **의존 역방향**. 중앙(Orchestrator) → 경계(Proxy) → 실행(Agent) → UI.
> 실배포 호스트 / 포트는 `environment-setup.md` 에서 확정.

### 2.4 헬스 확인
- [ ] 각 서비스 `/actuator/health` 응답 200
- [ ] Orchestrator UI 접근
- [ ] DMZ ↔ Internal ↔ GIMS 각 망 간 통신 ping 성공

---

## Phase 3. 배포 후 검증 (post-deploy)

- [ ] `post-deploy-smoke.md` 시나리오 전체 통과
- [ ] 모니터링 대시보드 정상 데이터 흐름 확인
- [ ] 첫 24h 모니터링 — 알람 없음 확인
- [ ] 운영자 인수인계 완료

---

## Phase 4. 문제 발생 시

- [ ] `rollback.md` 절차 즉시 실행
- [ ] 장애 로그 보존
- [ ] 원인 기록 → 다음 배포 때 재발 방지 항목으로 추가

---

## 확인자 서명란

| Phase | 완료 시각 | 확인자 | 비고 |
|-------|---------|--------|------|
| 1 | | | |
| 2 | | | |
| 3 | | | |
| 4 | | | (장애 발생 시) |
