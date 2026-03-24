# 스케줄링 (Scheduling)

> 이 문서는 GIMS 동기화 시스템에서 **작업을 예약하고 주기적으로 실행하는 기술**을 설명합니다.
> 코드를 모르더라도 "이 시스템이 어떻게 정해진 시간에 알아서 돌아가는지"를 이해할 수 있도록 작성했습니다.

---

## 전체 그림

```
사용자가 UI에서 "매일 새벽 2시에 동기화 실행" 설정
    ↓
Spring TaskScheduler가 cron 표현식 해석 → 내부 스레드 풀에 작업 등록
    ↓
매일 새벽 2시가 되면 스레드 풀에서 스레드 하나가 깨어나 작업 실행
    ↓
실행 결과는 DB 이력 테이블에 기록
```

---

## 핵심 개념

### 1. Cron 표현식

> Unix/Linux의 crontab에서 유래한 표준적인 시간 표현 방식이다. Spring은 이를 자체적으로 구현하여 `CronTrigger` 클래스로 제공한다.

"언제 실행할지"를 문자열 하나로 표현하는 규칙이다. 리눅스 crontab과 비슷하지만, Spring은 **6자리**를 쓴다.

```
초  분  시  일  월  요일
 0   0   2   *   *    *     → 매일 새벽 2시 0분 0초
 0   0   *   *   *    *     → 매시 정각
 0  30   9   *   *  MON-FRI → 평일 오전 9시 30분
```

- `*` = 매번 (every)
- `0` = 정확히 0
- `MON-FRI` = 월~금

이 문자열은 DB의 `cron_expression` 컬럼에 저장되고, `CronTrigger`라는 객체가 해석한다.

```
DB: cron_expression = "0 0 2 * * *"
    ↓
코드: new CronTrigger("0 0 2 * * *")  → "다음 실행 시각은 언제?" 계산기
```

> **연관 파일**
> - `sync-orchestrator/backend/.../schedule/Schedule.java` — cron_expression 컬럼 정의
> - `infolink-api-collector/.../entity/ApiSchedule.java` — API Collector 스케줄 엔티티

---

### 2. TaskScheduler와 스레드 풀

> `TaskScheduler`는 Spring Framework가 제공하는 스케줄링 인터페이스다. 내부 구현체인 `ThreadPoolTaskScheduler`는 Java 표준의 `ScheduledExecutorService`를 감싼 것이다.

Spring의 `TaskScheduler`는 **작업 예약 접수 창구**다. 내부에 스레드 풀(일꾼 팀)을 갖고 있다.

```
TaskScheduler (접수 창구)
  └─ ThreadPoolTaskScheduler (내부 구현)
       └─ 스레드 풀: [스레드1] [스레드2] [스레드3] [스레드4] [스레드5]
                     (일꾼 5명이 대기 중)
```

**스레드 풀이 필요한 이유**: 스케줄이 여러 개 등록되면 동시에 실행될 수 있다. 일꾼이 1명이면 "매시 정각 A 실행"과 "매시 정각 B 실행"이 겹칠 때 하나가 끝날 때까지 다른 하나가 기다려야 한다. 일꾼이 여러 명이면 동시에 처리 가능하다.

우리 시스템의 스레드 풀 크기:

| 모듈 | 풀 크기 | 스레드 이름 |
|------|---------|------------|
| Orchestrator (Agent 동기화) | 5개 | `schedule-1`, `schedule-2`, ... |
| API Collector (API 수집) | 4개 | `api-schedule-1`, `api-schedule-2`, ... |

스레드 이름은 로그에서 "어떤 스케줄 풀에서 실행됐는지" 구분할 때 쓰인다.

> **연관 파일**
> - `sync-orchestrator/backend/.../config/SchedulerConfig.java` — poolSize=5, prefix=`schedule-`
> - `infolink-api-collector/.../config/SchedulerConfig.java` — poolSize=4, prefix=`api-schedule-`

---

### 3. ScheduledFuture — 등록된 작업의 제어 핸들

> `ScheduledFuture`는 Java 표준 라이브러리(`java.util.concurrent`)가 제공하는 인터페이스다.

`TaskScheduler`에 작업을 등록하면 `ScheduledFuture`라는 객체를 돌려준다. 이 객체가 **그 등록된 작업에 대한 유일한 제어 수단**이다.

```java
ScheduledFuture<?> future = taskScheduler.schedule(작업, 언제실행할지);
```

`TaskScheduler`는 등록만 받고, "3번 스케줄 취소해줘" 같은 기능을 제공하지 않는다. 따라서 나중에 특정 스케줄을 취소하려면 이 `future` 객체를 직접 들고 있어야 한다.

```java
future.cancel(false);    // 이 스케줄의 다음 실행부터 취소
future.isCancelled();    // 취소됐는지 확인
future.getDelay(단위);   // 다음 실행까지 남은 시간
```

`<?>` 부분: `Future<T>`의 T는 작업 반환값 타입인데, cron 작업은 반환값이 없으므로 `<?>` = "반환값 신경 안 씀".

> **연관 파일**
> - `sync-orchestrator/backend/.../schedule/ScheduleExecutor.java` — registerSchedule(), unregisterSchedule()
> - `infolink-api-collector/.../service/ApiScheduleExecutor.java` — 동일 패턴

---

### 4. ConcurrentHashMap — 스케줄 보관함

> `ConcurrentHashMap`은 Java 표준 라이브러리(`java.util.concurrent`)가 제공하는 스레드 안전 Map이다.

여러 스케줄의 `ScheduledFuture`를 scheduleId로 찾을 수 있도록 Map에 저장한다.

```
scheduledTasks (보관함)
┌──────────────┬─────────────────────────────────────┐
│ scheduleId=1 │ future_A (매시 정각 daejeon 동기화)  │
│ scheduleId=2 │ future_B (매일 자정 bytek 동기화)    │
│ scheduleId=3 │ future_C (매주 월 chungnam 동기화)   │
└──────────────┴─────────────────────────────────────┘
```

**왜 일반 HashMap이 아니라 ConcurrentHashMap인가?**

여러 스레드가 동시에 이 Map에 접근할 수 있기 때문이다:
- 스레드 A: 새 스케줄 등록 (put)
- 스레드 B: 기존 스케줄 삭제 (remove)
- 스레드 C: 스케줄 실행 중 상태 확인 (get)

일반 HashMap은 동시 접근 시 데이터가 깨질 수 있다. ConcurrentHashMap은 내부적으로 잠금 처리를 해서 여러 스레드가 동시에 읽고 써도 안전하다. 개발자가 직접 `synchronized` 같은 잠금 코드를 쓸 필요가 없다.

> **연관 파일**
> - `sync-orchestrator/backend/.../schedule/ScheduleExecutor.java` — `scheduledTasks` 필드
> - `infolink-api-collector/.../service/ApiScheduleExecutor.java` — `scheduledTasks` 필드

---

## 스케줄 생명주기

### 등록 → 실행 → 해제 흐름

```
1. 사용자가 UI에서 스케줄 생성
   └→ DB에 저장 (schedule 테이블)
   └→ registerSchedule() 호출

2. registerSchedule()
   ├→ 기존 future 있으면 cancel (중복 방지)
   ├→ isEnabled 체크 (비활성이면 등록 안 함)
   ├→ taskScheduler.schedule(작업, CronTrigger) → future 반환
   └→ scheduledTasks.put(id, future) → Map에 저장

3. cron 시각 도달 → 스레드 풀에서 스레드가 작업 실행
   └→ executeAgent() 또는 executionService.run()

4. 스케줄 수정 시
   └→ unregisterSchedule() → 기존 future 꺼내서 cancel
   └→ registerSchedule() → 새 cron으로 재등록

5. 스케줄 삭제 시
   └→ unregisterSchedule() → cancel
   └→ DB에서 삭제
```

### 앱 재시작 시 복원

스케줄은 DB에 영구 저장되지만, `ScheduledFuture`는 메모리에만 존재한다. 따라서 앱이 재시작되면 TaskScheduler에 등록된 작업은 모두 사라진다.

이를 해결하기 위해 앱 시작 시 DB에서 활성 스케줄을 전부 읽어서 다시 등록한다:

```
앱 시작 → ContextRefreshedEvent 발생
  → onApplicationStart()
    → DB에서 isEnabled=true인 스케줄 전부 조회
    → 하나씩 registerSchedule() 호출
    → 로그: "스케줄 3건 등록 시작"
```

`ContextRefreshedEvent`는 Spring이 모든 빈(bean) 초기화를 마친 직후 발생하는 이벤트다. "앱이 준비 완료됐으니 이제 스케줄 등록해도 된다"는 신호.

> **연관 파일 (생명주기 전체)**
> - `sync-orchestrator/backend/.../schedule/ScheduleExecutor.java` — 등록/해제/앱시작 복원
> - `sync-orchestrator/backend/.../schedule/ScheduleService.java` — CRUD + Executor 호출
> - `sync-orchestrator/backend/.../schedule/ScheduleController.java` — REST API
> - `sync-orchestrator/backend/.../schedule/ScheduleRepository.java` — DB 조회
> - `infolink-api-collector/.../service/ApiScheduleExecutor.java` — 등록/해제/앱시작 복원
> - `infolink-api-collector/.../service/ApiScheduleService.java` — CRUD + Executor 호출
> - `infolink-api-collector/.../controller/ApiScheduleController.java` — REST API

---

## 두 모듈의 스케줄 비교

시스템에는 **두 개의 독립적인 스케줄 엔진**이 있다:

### Orchestrator의 ScheduleExecutor [sync-orchestrator/backend]

Agent 파이프라인(데이터 동기화)을 주기적으로 실행한다.

```
스케줄 → Agent 실행 요청 (HTTP)
         └→ Agent가 실제 동기화 수행
```

- 스케줄은 cron 표현식 + 활성화 여부만 설정 (조건 필터 없이 전체 증분 동기화 실행)

### API Collector의 ApiScheduleExecutor [infolink-api-collector]

외부 API 데이터 수집을 주기적으로 실행한다.

```
스케줄 → ApiExecutionService.run() 직접 호출
         └→ API 호출 → JSON 파싱 → DB 적재
```

- 더 단순한 구조 (필터 없이 Endpoint 전체 실행)

### 공통점

| 항목 | 동일한 패턴 |
|------|------------|
| 스케줄 저장 | DB 테이블 (JPA Entity) |
| 작업 등록 | TaskScheduler + CronTrigger |
| 핸들 보관 | ConcurrentHashMap<Long, ScheduledFuture<?>> |
| 앱 시작 복원 | ContextRefreshedEvent → DB 조회 → 재등록 |
| 취소 방식 | future.cancel(false) |
| 에러 처리 | catch로 감싸서 스케줄러 스레드 죽지 않게 보호 |

---

## 고정 스케줄 (시스템 내부용)

사용자가 등록하는 동적 스케줄 외에, 시스템 자체적으로 고정 주기로 도는 작업도 있다:

### AgentHealthScheduler [sync-orchestrator/backend] — 30초마다 Agent 상태 확인

```
@Scheduled(fixedDelay = 30000, initialDelay = 10000)

앱 시작 10초 후 첫 실행, 이후 30초 간격으로 반복
  → 모든 Agent에 /health 호출 (병렬)
  → 응답에 따라 Agent 상태 업데이트: ONLINE / RUNNING / OFFLINE
  → RUNNING으로 남아있지만 실제론 안 도는 작업 자동 복구
```

> **연관 파일**: `sync-orchestrator/backend/.../agent/AgentHealthScheduler.java`

`@Scheduled`는 Spring이 제공하는 간편 애노테이션이다. 동적 스케줄처럼 CronTrigger/ScheduledFuture를 직접 다룰 필요 없이, 메서드에 붙이기만 하면 된다. 대신 **실행 중 취소나 변경이 불가능**하다.

### DataRetentionScheduler [sync-orchestrator/backend] — 매일 새벽 2시 오래된 데이터 삭제

```
@Scheduled(cron = "${retention.cron:0 0 2 * * *}")

매일 새벽 2시
  → retention_config가 설정된 Agent에 cleanup 요청
  → Agent가 오래된 데이터 삭제
```

`${retention.cron:0 0 2 * * *}` 의미: application.yml에 `retention.cron` 값이 있으면 그걸 쓰고, 없으면 `0 0 2 * * *`(매일 새벽 2시)를 기본값으로 쓴다.

> **연관 파일**: `sync-orchestrator/backend/.../agent/DataRetentionScheduler.java`

---

## 에러 안전장치

스케줄 실행 중 에러가 발생하면 **스케줄러 스레드 자체가 죽을 수 있다**. 스레드가 죽으면 그 스케줄은 다시는 실행되지 않는다. 이를 방지하기 위해 실행 코드 전체를 try-catch로 감싼다:

```java
private void executeAgent(Long scheduleId, Long agentId, String agentCode) {
    try {
        // 실제 실행 로직
        executionService.triggerExecution(agentId, "SCHEDULE");
    } catch (Exception e) {
        // 로그만 남기고 예외를 삼킨다
        // → 스레드는 살아남아서 다음 cron 시각에 다시 실행
        log.error("스케줄 실행 실패: agent={}, error={}", agentCode, e.getMessage());
    }
}
```

이 패턴이 없으면: 한 번 실패 → 스레드 사망 → 영영 실행 안 됨.
이 패턴이 있으면: 한 번 실패 → 로그에 기록 → 다음 주기에 정상 재시도.

---

## 우아한 종료 (Graceful Shutdown)

앱이 종료될 때 실행 중인 스케줄 작업이 갑자기 끊기면 데이터가 깨질 수 있다. 이를 방지하는 설정:

```java
scheduler.setWaitForTasksToCompleteOnShutdown(true);  // 실행 중인 작업 완료 대기
scheduler.setAwaitTerminationSeconds(30);               // 최대 30초까지 기다림
```

- 앱 종료 신호 수신 → 새 작업 접수 중단
- 현재 실행 중인 작업이 있으면 최대 30초 대기
- 30초 내 완료되면 정상 종료, 못 끝나면 강제 종료
