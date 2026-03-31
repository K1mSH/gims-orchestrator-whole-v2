# 인프라 / 보안

## DB 마이그레이션
- [x] V3__rename_master_to_proxy.sql
- [x] pm_gd970201 DDL (execution_id, source_refs)
- [x] api_collector DB 5개 테이블

## Jasypt 암호화
- [x] 5개 모듈 application.yml ENC() 암호화
- [x] encrypt.sh / encrypt.java 도구
- [x] JASYPT_PASSWORD 환경변수 설정

## 문서
- [x] PIPELINE_EXECUTION_FLOW.md
- [x] test_plan/bojo-test.md (API Key/보안, Proxy 섹션 추가)
- [x] dev_plan 계획 문서 (3/3~3/24)

## 네이밍 정리
- [ ] IF 테이블 prefix `if_rsv_` → `if_rcv_` 일괄 변경 (Agent 유형명 `RCV`와 통일)
  - 대상: YAML 설정, DB 테이블명, 엔티티, ARCHITECTURE.md, service-name-mapping.md 등
  - 현재 `if_rsv_`로 전체 사용 중 — 기능 안정화 후 일괄 리네이밍 권장

**진행도: 9/10**
