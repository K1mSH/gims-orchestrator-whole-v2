# API Collector DB 이슈 분석

> 작성일: 2026-04-03

## 문제 요약

API Collector(8084)의 endpoint를 **psql로 직접 INSERT** 했더니, 앱에서 조회하면 **다른 endpoint가 반환**되는 현상.

## 원인

### API Collector에는 DB가 2개 있음

| DB | 용도 | 테이블 |
|----|------|--------|
| `api_collector` (PG 29001) | API Collector 자체 DB — endpoint, 실행이력, 스케줄 등 JPA 관리 | api_endpoint, api_execution_history, api_param 등 |
| `dev` (PG 29001) | DMZ 데이터 DB — API가 수집한 실 데이터 | rgetstgms01, rgetnpmms01, tb_jeju_jewon, tb_jeju 등 |

### endpoint ID 불일치

프론트 UI(`/api-collect`)에서 등록한 endpoint들은 **JPA가 `api_collector` DB에 자동 시퀀스로 생성**합니다.

```
api_collector DB (JPA 관리):
  id=15  네이버 뉴스 수집
  id=16  나라장터 #1
  id=17  안양시 이용량
  id=18  나라장터 #2
  id=19  나라장터 #3
  id=20  나라장터 #4
  id=21  제주 제원 입력 (jeju-jewon)
  ← 여기까지 프론트 UI에서 등록한 것

  id=22  제주_3 이용시설 (jeju-facility) ← psql로 직접 INSERT
```

**문제**: psql INSERT는 JPA 영속성 컨텍스트를 거치지 않음.
- 앱 기동 시 JPA가 캐시에 올린 데이터와 psql INSERT 데이터가 **시퀀스 충돌**
- `api/endpoints/22` 요청 시 JPA 캐시에서 다른 데이터를 반환할 수 있음

### 실제 확인된 현상

```
psql 조회 (api_collector DB):  id=22 → 제주_3 이용시설 (jeju-facility)  ✓
API  조회 (8084 앱):           id=22 → 제주_2 수위관측 (jeju-obsv-data) ✗
```

**결론**: 프론트 UI로 이전에 등록했던 D2~D5 endpoint가 앱 재기동 또는 DB 초기화 과정에서 **`api_collector` DB에서는 사라졌지만, 앱 내부에서는 JPA ddl-auto:update와 시퀀스가 다른 상태**가 된 것으로 추정.

## 해결 방안

### A안: 프론트 UI로 등록 (권장)

1. http://localhost:3000/api-collect 에서 D2~D5 endpoint를 다시 등록
2. 각 endpoint 설정:
   - `executorType`: 커스텀 실행기 드롭다운에서 선택
   - `targetDatasourceId`: `dmz` 선택 (← 이전에 `dmz_api_collector`였던 것)
   - `url`: Mock API URL

| # | 이름 | executorType | url |
|---|------|-------------|-----|
| D1 | 제주 제원 | jeju-jewon | http://localhost:8084/mock/jeju/obsv |
| D2 | 제주 수위관측 | jeju-obsv-data | http://localhost:8084/mock/jeju/obsv-data |
| D3 | 제주 이용시설 | jeju-facility | http://localhost:8084/mock/jeju/facility |
| D4 | 제주 수질검사 | jeju-water-quality | http://localhost:8084/mock/jeju/water-quality |

3. D1은 이미 등록(id=21), targetDatasourceId만 `dmz`로 수정 필요

### B안: psql로 시퀀스 맞춰서 INSERT

1. 기존 psql INSERT 삭제
2. 현재 JPA 시퀀스 값 확인 → 다음 값으로 INSERT
3. 앱 재기동 필요
4. **비권장** — JPA와 raw SQL 혼용은 계속 이슈 발생 가능

## 추가 이슈: `targetDatasourceId` 정리

### 이전
- endpoint의 `target_datasource_id`가 `dmz_api_collector`로 설정
- Orchestrator에 `dmz_api_collector` datasource가 없어서 500 에러

### 현재 (해결 필요)
- `dmz` datasource = PG 29001 dev DB → 여기에 수집 데이터 적재
- 기존 endpoint(id=15~21)의 target_datasource_id를 `dmz`로 변경 완료 (psql)
- **하지만 앱 캐시에는 아직 반영 안 됨** → 앱 재기동 또는 프론트에서 수정 필요

### 정리 필요 사항
1. id=21 (제주 제원): `targetDatasourceId`가 비어있음 → `dmz`로 설정
2. D2~D4: 프론트에서 등록 시 `targetDatasourceId`를 `dmz`로 설정
3. 기존 endpoint(id=15~20): 앱 재기동하면 psql UPDATE 반영됨

## 작업 순서

1. API Collector 앱 **재기동** (psql로 변경한 target_datasource_id 반영)
2. 프론트 `/api-collect`에서 **D2, D3, D4 신규 등록** (targetDatasourceId=`dmz`)
3. 프론트에서 **D1(id=21) targetDatasourceId를 `dmz`로 수정**
4. D3 실행 테스트 → rgetstgms01에 코드변환된 데이터 확인
5. SND → RCV 전구간 테스트
