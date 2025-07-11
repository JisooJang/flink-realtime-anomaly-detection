# Flink 스터디 발표 스크립트
## "실시간 이상행동 감지 시스템 - Flink 클러스터 아키텍처와 체크포인트 동작 원리"

---

## 1. 발표 개요 (2분)

### 안녕하세요! 오늘은 Apache Flink를 활용한 실시간 이상행동 감지 시스템을 통해 Flink 클러스터 아키텍처와 체크포인트 동작 원리를 살펴보겠습니다.

**발표 목표:**
- Flink 클러스터의 핵심 구성요소 이해
- 실시간 스트림 처리 파이프라인 구조 파악
- 체크포인트를 통한 장애 복구 메커니즘 학습

---

## 2. Flink 클러스터 아키텍처 개요 (3분)

### 2.1 Flink 클러스터 구성요소

**JobManager (마스터 노드)**
- 전체 Job의 스케줄링과 리소스 관리
- Checkpoint Coordinator 역할
- JobGraph를 TaskManager에 분산 배포

**TaskManager (워커 노드)**
- 실제 데이터 처리 작업 수행
- 각 TaskManager는 여러 Task Slot 보유
- 우리 예제에서는 3개의 병렬 Task 실행

### 2.2 우리 시스템의 클러스터 구조
```
JobManager (포트 8081)
├── TaskManager 1 (Slot 1-3)
│   ├── Source Task 1 (user1 이벤트 생성)
│   ├── Process Task 1 (user1 이상행동 카운트)
│   └── Sink Task 1 (로그 출력)
├── TaskManager 2 (Slot 4-6)
│   ├── Source Task 2 (user2 이벤트 생성)
│   ├── Process Task 2 (user2 이상행동 카운트)
│   └── Sink Task 2 (로그 출력)
└── TaskManager 3 (Slot 7-9)
    ├── Source Task 3 (user3 이벤트 생성)
    ├── Process Task 3 (user3 이상행동 카운트)
    └── Sink Task 3 (로그 출력)
```

---

## 3. 실시간 스트림 처리 파이프라인 구조 (5분)

### 3.1 Source (이벤트 생성)
```java
// RandomUserEventSource - 무한 스트림 이벤트 생성
public class RandomUserEventSource extends RichParallelSourceFunction<UserEvent> {
    // 3개의 병렬 Source Task
    // 각 Task는 5초마다 랜덤 이벤트 생성
    // 30% 확률로 이상행동 이벤트 생성
}
```

**Source의 역할:**
- 외부 시스템에서 데이터를 읽어오는 역할
- 우리 예제에서는 시뮬레이션된 사용자 이벤트 생성
- 3개의 병렬 Task로 분산 처리

### 3.2 Process Function (이상행동 감지)
```java
// AbnormalCountProcessFunction - Keyed State 기반 처리
public class AbnormalCountProcessFunction extends KeyedProcessFunction<String, UserEvent, String> {
    private ValueState<Integer> abnormalCountState; // 유저별 상태 저장
    
    // 유저 ID로 키 지정하여 분산 처리
    // 각 유저별로 이상행동 카운트 누적
    // 3회 이상 시 알람 발생
}
```

**Process Function의 역할:**
- Keyed State를 활용한 상태 기반 처리
- 유저별로 독립적인 상태 관리
- Hash 기반으로 Key Group 분산

### 3.3 Sink (결과 출력)
```java
// .print() - 표준 출력으로 결과 전송
// 실제 운영에서는 Kafka, Database, 외부 API 등으로 전송
```

---

## 4. 체크포인트 동작 원리 (8분)

### 4.1 체크포인트란?
**정의:** Flink가 스트림 처리 중 특정 시점의 상태를 외부 저장소에 저장하는 메커니즘

**목적:**
- 장애 발생 시 이전 상태로 복구
- Exactly-once 처리 보장
- 상태 백엔드와 연동하여 안정성 확보

### 4.2 체크포인트 설정
```java
// 10초마다 체크포인트 생성
env.enableCheckpointing(10000L, CheckpointingMode.EXACTLY_ONCE);

// 메모리 기반 상태 백엔드 (실무에서는 RocksDB 권장)
env.setStateBackend(new HashMapStateBackend());

// 체크포인트 저장 경로
env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink-checkpoints");

// 체크포인트 간 최소 간격 5초
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000L);

// 체크포인트 타임아웃 60초
env.getCheckpointConfig().setCheckpointTimeout(60000L);

// 동시 체크포인트 1개만 허용
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

// Unaligned Checkpoint 활성화 (네트워크 병목 시)
env.getCheckpointConfig().enableUnalignedCheckpoints(true);
```

### 4.3 체크포인트 동작 과정

**1단계: Checkpoint Coordinator가 체크포인트 트리거**
```
JobManager → 모든 TaskManager에 Checkpoint Barrier 전송
```

**2단계: Barrier Alignment (정렬)**
```
Source Task 1: [Event1] [Event2] [Barrier] [Event3] [Event4]
Source Task 2: [Event1] [Barrier] [Event2] [Event3] [Event4]
Source Task 3: [Event1] [Event2] [Event3] [Barrier] [Event4]
```

**3단계: 상태 스냅샷 생성**
```java
// ValueState 스냅샷 예시
{
  "user1": {"abnormalCount": 2},
  "user2": {"abnormalCount": 0},
  "user3": {"abnormalCount": 1}
}
```

**4단계: 외부 저장소에 저장**
```
파일 경로: /tmp/flink-checkpoints/{job-id}/chk-{checkpoint-id}/
파일 내용: _metadata (바이너리 상태 데이터)
```

### 4.4 장애 복구 과정

**장애 발생 시나리오:**
1. TaskManager 2번이 장애 발생
2. JobManager가 장애 감지
3. 최신 체크포인트에서 복구 시작
4. 모든 Task가 체크포인트 상태로 복원
5. 정상 처리 재개

**복구 과정:**
```
1. JobManager가 최신 체크포인트 ID 확인
2. 모든 TaskManager에 복구 명령 전송
3. 각 Task가 체크포인트 파일에서 상태 복원
4. Source Task가 체크포인트 이후 데이터부터 재처리
5. Process Function이 복원된 상태로 처리 재개
```

---

## 5. 실시간 모니터링 대시보드 (3분)

### 5.1 대시보드 구성
- **클러스터 개요:** TaskManager 수, 슬롯 상태, Job 상태
- **실시간 태스크 상태:** 각 Task의 처리량, 체크포인트 크기
- **체크포인트 히스토리:** 완료/실패 체크포인트, 소요 시간
- **실시간 로그:** Source 이벤트, Process 처리, 알람 발생

### 5.2 모니터링 지표
```
체크포인트 통계:
- 총 체크포인트: 165개
- 완료된 체크포인트: 165개
- 평균 체크포인트 크기: 6KB
- 평균 체크포인트 시간: 61ms
```

---

## 6. 핵심 학습 포인트 (2분)

### 6.1 Flink 클러스터 아키텍처
- **JobManager:** 중앙 집중식 스케줄링과 체크포인트 조정
- **TaskManager:** 분산 데이터 처리와 상태 관리
- **Task Slot:** 리소스 격리와 병렬 처리 단위

### 6.2 스트림 처리 파이프라인
- **Source:** 외부 데이터 소스 연결
- **Process Function:** 상태 기반 비즈니스 로직
- **Sink:** 결과 데이터 전송

### 6.3 체크포인트 메커니즘
- **Barrier Alignment:** 정확한 상태 스냅샷 보장
- **상태 백엔드:** 메모리/디스크 기반 상태 저장
- **장애 복구:** 체크포인트 기반 자동 복구

---

## 7. 실무 적용 시 고려사항 (2분)

### 7.1 성능 최적화
- **상태 백엔드:** RocksDB 사용으로 메모리 효율성 증대
- **체크포인트 간격:** 처리량과 복구 시간의 트레이드오프
- **Unaligned Checkpoint:** 네트워크 병목 시 처리량 향상

### 7.2 운영 고려사항
- **모니터링:** 체크포인트 성공률, 처리 지연시간 추적
- **알람 설정:** 체크포인트 실패, Job 실패 시 즉시 알림
- **리소스 관리:** TaskManager 메모리, CPU 사용량 모니터링

---

## 8. 마무리 (1분)

### 오늘 살펴본 내용:
1. **Flink 클러스터 아키텍처** - JobManager와 TaskManager의 역할
2. **실시간 스트림 처리 파이프라인** - Source, Process, Sink 구조
3. **체크포인트 동작 원리** - 장애 복구와 Exactly-once 보장
4. **실시간 모니터링** - 클러스터 상태와 체크포인트 추적

### 다음 스터디 주제 제안:
- Flink의 다양한 상태 백엔드 비교 (Memory vs RocksDB)
- 네트워크 병목 상황에서의 Unaligned Checkpoint 효과
- 대용량 데이터 처리 시 성능 최적화 기법

**질문과 토론 시간을 가져보겠습니다!**

---

## 발표 시 주의사항

### 1. 시연 준비사항
- Flink 클러스터가 실행 중인지 확인
- 대시보드가 정상 작동하는지 확인
- 체크포인트 파일 경로 준비

### 2. 발표 팁
- 코드와 아키텍처 다이어그램을 번갈아가며 설명
- 실시간 대시보드를 보여주며 현재 상태 설명
- 체크포인트 파일 구조를 실제로 확인해보기

### 3. 예상 질문과 답변
**Q: 왜 HashMapStateBackend를 사용했나요?**
A: 학습 목적으로 메모리 기반 백엔드를 사용했습니다. 실무에서는 RocksDB를 권장합니다.

**Q: 체크포인트 간격을 10초로 설정한 이유는?**
A: 빠른 복구와 처리량의 균형을 고려했습니다. 실무에서는 데이터 특성에 따라 조정합니다.

**Q: Unaligned Checkpoint의 장점은?**
A: 네트워크 병목 시에도 체크포인트가 완료되어 처리량을 향상시킵니다. 