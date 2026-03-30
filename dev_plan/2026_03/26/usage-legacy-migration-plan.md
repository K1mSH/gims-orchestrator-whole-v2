# 이용량 레거시 이관 계획서

## 개요
이용량 레거시 데이터를 Source DB에서 내부망 GIMS Target DB로 이관.
제주 외 일반 이용량 데이터. 내부망에서만 실행.

## 프로그램

| 프로그램 | 역할 | 소스 | 타겟 테이블 |
|---------|------|------|-----------|
| UseToIn | 사용량 레거시 이관 | Source DB (select_use_legacy_data, select_use_status_data) | PM_GD31021, PM_GD31022, TM_GD31025 |

### 특이사항
- SN 기반 증분 처리 (마지막 처리 SN 추적)
- 음수값 보정 (last_measure_value < 0 → "0")
- 사용량 데이터 + 상태 데이터 2종 처리
- 임시 테이블 업데이트 (tmpUpdateImsi, tmpUpdateImsi2)
- 예외 시 마지막 성공 SN 기록

## 구현 방향
- Agent(bojo-int) 편입 또는 별도 배치
- Source DB가 DMZ인지 내부망 다른 시스템인지에 따라 달라짐

## 미결 사항
- [ ] Source DB 위치 확인 (DMZ? 내부망?)
- [ ] 안양시 이용량(AnyangUsageExecutor)과의 관계/패턴 유사성
- [ ] IF 테이블 경유 여부 (팀장님 논의)
