# 실행옵션 패널 간소화 + WHERE 조건 UX 개선

## 목적
- 사용자에게 불필요한 내부 구현 정보 제거
- WHERE 조건 입력 시 테이블/컬럼 드롭다운으로 편의성 + 안정성(대소문자 문제 방지)

## 변경 대상
- `sync-orchestrator/frontend/app/agents/[id]/page.tsx`

## 변경 내용

### 1. 실행옵션 패널에서 제거
- **실행 방식** (증분적재/전체재적재 라디오) → 제거
- **실행 Step 선택** (체크박스 목록) → 제거

### 2. 실행옵션 패널에 남기는 것
- **시간 범위 지정** (체크박스 + 시작/종료 날짜)
- **WHERE 조건** (개선된 UI)

### 3. WHERE 조건 UX 개선
**현재**: 컬럼명 수동 입력 (텍스트 필드)
**변경**: 테이블 선택 → 컬럼 선택 (드롭다운)

```
[테이블 ▾] [컬럼 ▾] [연산자 ▾] [값 입력] [+ 추가] [X 삭제]
```

#### 데이터 소스
- Agent의 `sourceTableIds` → 해당 DatasourceTable의 컬럼 목록 사용
- sourceTableIds가 비어있으면 `sourceDatasourceId`로 전체 테이블 목록 조회
- API: `GET /api/datasources/{dsId}/tables` (이미 존재)

#### 동작 흐름
1. 실행옵션 열릴 때 Agent의 소스 테이블 + 컬럼 정보 로드
2. 테이블 드롭다운: 등록된 소스 테이블 목록
3. 컬럼 드롭다운: 선택된 테이블의 컬럼 목록 (alias 있으면 표시)
4. 조건 추가 시: 테이블+컬럼+연산자+값 → conditions 배열에 추가
5. 실행 시: conditions를 기존 형식 `{column, operator, value, value2}`으로 변환하여 API 전송

#### 주의
- conditions의 column은 **컬럼명**(테이블명 아님) — Agent가 해당 컬럼으로 WHERE 절 생성
- 테이블 선택은 UX용 (어떤 테이블의 컬럼인지 안내), 실제 API에는 column만 전달
- 동일 컬럼명이 여러 테이블에 있을 수 있지만, ConditionBuilder가 모든 step에 동일 conditions 적용하므로 문제없음

## 백엔드 변경
- 없음 (프론트엔드만)

## 영향 범위
- executionModeId / selectedStepIds를 전송하지 않으면 Agent는 디폴트 동작
- 기존 API 호환성 유지
