# 제주도 이용량 이관 계획서

## 개요
제주도 지하수 이용시설/수질검사 데이터를 수집하여 내부망 GIMS에 적재하는 파이프라인.
2단계 구조: DMZ에서 API 수집 → 내부망에서 DB 이관.

## 1단계: DMZ (제주 API → DMZ DB)
API Collector 커스텀 실행기로 구현

| 프로그램 | 역할 | API | 적재 테이블 |
|---------|------|-----|-----------|
| RgetstgmsProgram | 지하수이용시설 수집 (자동) | selectJejuUse.json | insetRgetnpmms01, insetRgetstgms01 |
| yearProgram | 지하수이용시설 수집 (수동/연도) | selectJejuUse.json | insetRgetstgms01 |
| RgetnwaviProgram | 수질검사 수집 | selectSujil.json | insetRgetnwavi05/06 |

### 특이사항
- RgetstgmsProgram: 좌표변환 + 용도코드(11종) + 지역코드 + 허가형태 + 상태코드 변환, 1000건 페이징
- yearProgram: RgetstgmsProgram의 수동 버전 (연도 지정) → 통합 가능
- RgetnwaviProgram: 수질검사 항목명 한→영 매핑 (탁도→Turbidity 등), 용도구분(A/D)

## 2단계: 내부망 (DMZ DB → GIMS)
Agent(bojo-int) 확장 또는 별도 배치

| 프로그램 | 역할 | 타겟 테이블 | 비고 |
|---------|------|-----------|------|
| JejuInToDB | 수자원 사용량 이관 | TmGd31010Gms, PmGd31022 | 연도별 처리 |
| RgetnDB | 수질시설 이관 | insetRgetstgms01 | 단순 이관 |

## 미결 사항
- [ ] IF 테이블 구조 (팀장님 논의)
- [ ] RgetstgmsProgram + yearProgram 통합 여부
- [ ] 2단계 Source DB = 1단계 적재 DB 맞는지 확인
