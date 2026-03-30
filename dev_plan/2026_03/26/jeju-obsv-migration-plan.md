# 제주도 보조망 (관측) 이관 계획서

## 개요
제주도 보조지하수관측망 데이터를 수집하여 내부망 GIMS에 적재하는 파이프라인.
2단계 구조: DMZ에서 API 수집 → 내부망에서 DB 이관.

## 1단계: DMZ (제주 API → DMZ DB)
API Collector 커스텀 실행기로 구현

| 프로그램 | 역할 | API | 적재 테이블 |
|---------|------|-----|-----------|
| InsertJeju | 수위 관측 실시간 수집 | selectObsvData.json | insertJejuOb |
| InsetTb_jeju_jewon | 관측점 마스터 초기 로드 | selectObsv.json | insetTb_jeju_jewon |

### 특이사항
- InsetTb_jeju_jewon: 좌표변환 EPSG:5186→4326, 용도코드/지역코드 매핑
- InsertJeju: site_code별 일일 수집

## 2단계: 내부망 (DMZ DB → GIMS)
Agent(bojo-int) 확장 또는 별도 배치

| 프로그램 | 역할 | 타겟 테이블 | 비고 |
|---------|------|-----------|------|
| JewonDB | 관측점 메타 저장 | TM_GD60001 외 7개 | 1소스→7타겟 분산 |
| ObsvrdataDB | 관측데이터 이관 | Pm60201/Pm60202 (6개) | 센서별 분기, RID 증분 |

### 특이사항
- JewonDB: 보조지하수관측망 고정값 세팅 (V1~V10)
- ObsvrdataDB: 센서타입별 분기 (S11 vs S2x), lc_sn 추출

## 미결 사항
- [ ] IF 테이블 구조 (팀장님 논의)
- [ ] 제주 API DMZ 접근 가능 여부
- [ ] 제주 API 인증 방식 (현재 코드상 인증 없이 POST)
- [ ] 2단계 Source DB = 1단계 적재 DB 맞는지 확인
