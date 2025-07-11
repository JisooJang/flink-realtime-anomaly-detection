# Flink 실시간 이상행동 감지 시스템

Apache Flink를 활용한 실시간 스트림 처리 시스템으로, 사용자의 이상행동을 감지하고 알람을 발생시키는 예제 프로젝트입니다.

## 🚀 프로젝트 개요

### 주요 기능
- **실시간 이벤트 생성**: 3개의 병렬 Source Task로 사용자 이벤트 시뮬레이션
- **상태 기반 처리**: Keyed State를 활용한 유저별 이상행동 카운트
- **체크포인트**: 장애 복구를 위한 상태 스냅샷 메커니즘
- **실시간 모니터링**: 웹 대시보드를 통한 클러스터 상태 추적

### 기술 스택
- **Apache Flink 1.17.2**: 스트림 처리 엔진
- **Java 17**: 개발 언어
- **Gradle**: 빌드 도구
- **HTML/CSS/JavaScript**: 모니터링 대시보드

## 🏗️ 아키텍처

### Flink 클러스터 구성
```
JobManager (마스터 노드)
├── TaskManager 1 (워커 노드)
│   ├── Source Task (user1 이벤트 생성)
│   ├── Process Task (user1 이상행동 카운트)
│   └── Sink Task (로그 출력)
├── TaskManager 2 (워커 노드)
│   ├── Source Task (user2 이벤트 생성)
│   ├── Process Task (user2 이상행동 카운트)
│   └── Sink Task (로그 출력)
└── TaskManager 3 (워커 노드)
    ├── Source Task (user3 이벤트 생성)
    ├── Process Task (user3 이상행동 카운트)
    └── Sink Task (로그 출력)
```

### 스트림 처리 파이프라인
```
Source → KeyBy (userId) → Process Function → Sink
  ↓         ↓              ↓              ↓
이벤트 생성 → 키별 분산 → 상태 기반 처리 → 결과 출력
```

### Flink 클러스터 아키텍처 다이어그램
```
┌─────────────────────────────────────────────────────────────────┐
│                    Flink 클러스터 아키텍처                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐ │
│  │   JobManager    │    │   JobManager    │    │   JobManager    │ │
│  │   (마스터 노드)   │    │   (마스터 노드)   │    │   (마스터 노드)   │ │
│  │                 │    │                 │    │                 │ │
│  │ • Job 스케줄링   │    │ • Checkpoint    │    │ • 리소스 관리    │ │
│  │ • 리소스 관리    │    │   Coordinator   │    │ • 장애 감지      │ │
│  │ • 장애 감지      │    │ • JobGraph 배포 │    │ • 복구 조정      │ │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘ │
│           │                       │                       │         │
│           └───────────────────────┼───────────────────────┘         │
│                                   │                               │
│  ┌─────────────────────────────────┼─────────────────────────────────┐ │
│  │                    TaskManager (워커 노드)                        │ │
│  │                                                                 │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │ │
│  │  │   Slot 1    │  │   Slot 2    │  │   Slot 3    │            │ │
│  │  │             │  │             │  │             │            │ │
│  │  │ Source Task │  │ Process     │  │ Sink Task   │            │ │
│  │  │ (user1)     │  │ Task        │  │ (로그 출력)  │            │ │
│  │  │             │  │ (user1)     │  │             │            │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │ │
│  │                                                                 │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │ │
│  │  │   Slot 4    │  │   Slot 5    │  │   Slot 6    │            │ │
│  │  │             │  │             │  │             │            │ │
│  │  │ Source Task │  │ Process     │  │ Sink Task   │            │ │
│  │  │ (user2)     │  │ Task        │  │ (로그 출력)  │            │ │
│  │  │             │  │ (user2)     │  │             │            │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │ │
│  │                                                                 │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │ │
│  │  │   Slot 7    │  │   Slot 8    │  │   Slot 9    │            │ │
│  │  │             │  │             │  │             │            │ │
│  │  │ Source Task │  │ Process     │  │ Sink Task   │            │ │
│  │  │ (user3)     │  │ Task        │  │ (로그 출력)  │            │ │
│  │  │             │  │ (user3)     │  │             │            │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 스트림 처리 파이프라인 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                    실시간 스트림 처리 파이프라인                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│  │    Source   │    │    Source   │    │    Source   │        │
│  │   (user1)   │    │   (user2)   │    │   (user3)   │        │
│  │             │    │             │    │             │        │
│  │ • 5초마다    │    │ • 5초마다    │    │ • 5초마다    │        │
│  │   이벤트 생성 │    │   이벤트 생성 │    │   이벤트 생성 │        │
│  │ • 30% 확률   │    │ • 30% 확률   │    │ • 30% 확률   │        │
│  │   이상행동    │    │   이상행동    │    │   이상행동    │        │
│  └─────────────┘    └─────────────┘    └─────────────┘        │
│           │                   │                   │            │
│           └───────────────────┼───────────────────┘            │
│                               │                                │
│  ┌─────────────────────────────┼─────────────────────────────┐  │
│  │                    KeyBy (userId)                        │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              Hash-based Partitioning               │  │  │
│  │  │  user1 → Task 1  |  user2 → Task 2  |  user3 → Task 3  │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                               │                                │
│  ┌─────────────────────────────┼─────────────────────────────┐  │
│  │              Process Function (Keyed State)              │  │
│  │                                                           │  │
│  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │  │
│  │  │   Task 1    │    │   Task 2    │    │   Task 3    │  │  │
│  │  │ (user1)     │    │ (user2)     │    │ (user3)     │  │  │
│  │  │             │    │             │    │             │  │  │
│  │  │ • ValueState │    │ • ValueState │    │ • ValueState │  │  │
│  │  │   (count)   │    │   (count)   │    │   (count)   │  │  │
│  │  │ • 3회 이상    │    │ • 3회 이상    │    │ • 3회 이상    │  │  │
│  │  │   시 알람     │    │   시 알람     │    │   시 알람     │  │  │
│  │  └─────────────┘    └─────────────┘    └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                               │                                │
│  ┌─────────────────────────────┼─────────────────────────────┐  │
│  │                        Sink                              │  │
│  │                                                           │  │
│  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │  │
│  │  │   Task 1    │    │   Task 2    │    │   Task 3    │  │  │
│  │  │ (로그 출력)  │    │ (로그 출력)  │    │ (로그 출력)  │  │  │
│  │  │             │    │             │    │             │  │  │
│  │  │ • 알람 메시지 │    │ • 알람 메시지 │    │ • 알람 메시지 │  │  │
│  │  │ • 처리 로그   │    │ • 처리 로그   │    │ • 처리 로그   │  │  │
│  │  └─────────────┘    └─────────────┘    └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 3. 체크포인트 동작 과정 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                    체크포인트 동작 과정                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐                                           │
│  │   JobManager    │                                           │
│  │ (Checkpoint     │                                           │
│  │  Coordinator)   │                                           │
│  └─────────────────┘                                           │
│           │                                                     │
│           │ 1. Checkpoint Barrier 전송                          │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              Barrier Alignment (정렬)                      │ │
│  │                                                             │ │
│  │  Source Task 1: [Event1] [Event2] [Barrier] [Event3]      │ │
│  │  Source Task 2: [Event1] [Barrier] [Event2] [Event3]      │ │
│  │  Source Task 3: [Event1] [Event2] [Event3] [Barrier]      │ │
│  │                                                             │ │
│  │  모든 Task가 Barrier를 받을 때까지 대기                        │ │
│  └─────────────────────────────────────────────────────────────┘ │
│           │                                                     │
│           │ 2. 상태 스냅샷 생성                                  │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    상태 스냅샷                              │ │
│  │                                                             │ │
│  │  {                                                          │ │
│  │    "user1": {"abnormalCount": 2},                          │ │
│  │    "user2": {"abnormalCount": 0},                          │ │
│  │    "user3": {"abnormalCount": 1}                           │ │
│  │  }                                                          │ │
│  └─────────────────────────────────────────────────────────────┘ │
│           │                                                     │
│           │ 3. 외부 저장소에 저장                                │
│           ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              외부 저장소 (/tmp/flink-checkpoints)          │ │
│  │                                                             │ │
│  │  /tmp/flink-checkpoints/                                   │ │
│  │  └── {job-id}/                                             │ │
│  │      └── chk-{checkpoint-id}/                              │ │
│  │          └── _metadata (바이너리 상태 데이터)                │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 4. 장애 복구 과정 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                    장애 복구 과정                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐                                           │
│  │   정상 상태      │                                           │
│  │                 │                                           │
│  │  TaskManager 1  │  TaskManager 2  │  TaskManager 3          │
│  │  [정상]         │  [정상]         │  [정상]                 │
│  └─────────────────┘                                           │
│           │                                                     │
│           │ 장애 발생!                                           │
│           ▼                                                     │
│  ┌─────────────────┐                                           │
│  │   장애 상태      │                                           │
│  │                 │                                           │
│  │  TaskManager 1  │  TaskManager 2  │  TaskManager 3          │
│  │  [정상]         │  [장애]         │  [정상]                 │
│  └─────────────────┘                                           │
│           │                                                     │
│           │ JobManager 장애 감지                                 │
│           ▼                                                     │
│  ┌─────────────────┐                                           │
│  │   복구 시작      │                                           │
│  │                 │                                           │
│  │  1. 최신 체크포인트 ID 확인                                   │
│  │  2. 모든 TaskManager에 복구 명령 전송                        │
│  │  3. 체크포인트 파일에서 상태 복원                             │
│  └─────────────────┘                                           │
│           │                                                     │
│           │ 복구 완료                                            │
│           ▼                                                     │
│  ┌─────────────────┐                                           │
│  │   복구된 상태    │                                           │
│  │                 │                                           │
│  │  TaskManager 1  │  TaskManager 2  │  TaskManager 3          │
│  │  [복구됨]       │  [새로 시작]    │  [복구됨]               │
│  │                 │  (체크포인트     │                         │
│  │                 │   상태 복원)     │                         │
│  └─────────────────┘                                           │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              복구 후 처리 재개                              │ │
│  │                                                             │ │
│  │  • Source Task: 체크포인트 이후 데이터부터 재처리              │ │
│  │  • Process Function: 복원된 상태로 처리 재개                 │ │
│  │  • Sink Task: 정상 로그 출력 재개                           │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```


## 📋 실행 방법

### 1. 환경 준비
```bash
# Java 17 설치 (macOS)
brew install openjdk@17

# Java 17 환경 설정
export JAVA_HOME=/usr/local/opt/openjdk@17
export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
```

### 2. Flink 클러스터 시작
```bash
# Flink 다운로드 및 설정
wget https://archive.apache.org/dist/flink/flink-1.17.2/flink-1.17.2-bin-scala_2.12.tgz
tar -xzf flink-1.17.2-bin-scala_2.12.tgz
cd flink-1.17.2

# 클러스터 시작
./bin/start-cluster.sh
```

### 3. 애플리케이션 실행
```bash
# 프로젝트 디렉토리로 이동
cd flink-demo

# 애플리케이션 실행
./gradlew run
```

### 4. 모니터링 대시보드
```bash
# 대시보드 열기
open flink-dashboard.html
```

## 🔧 주요 설정

### 체크포인트 설정
```java
// 10초마다 체크포인트 생성
env.enableCheckpointing(10000L, CheckpointingMode.EXACTLY_ONCE);

// 메모리 기반 상태 백엔드
env.setStateBackend(new HashMapStateBackend());

// 체크포인트 저장 경로
env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink-checkpoints");

// Unaligned Checkpoint 활성화
env.getCheckpointConfig().enableUnalignedCheckpoints(true);
```

### 병렬 처리 설정
```java
// 기본 병렬도 설정
env.setParallelism(3);

// Source 병렬도 설정
.addSource(new RandomUserEventSource())
.setParallelism(3);

// Process Function 병렬도 설정
.process(new AbnormalCountProcessFunction(3))
.setParallelism(3);
```

## 📊 모니터링

### 대시보드 기능
- **클러스터 개요**: TaskManager 수, 슬롯 상태, Job 상태
- **실시간 태스크 상태**: 각 Task의 처리량, 체크포인트 크기
- **체크포인트 히스토리**: 완료/실패 체크포인트, 소요 시간
- **실시간 로그**: Source 이벤트, Process 처리, 알람 발생
- **시각적 다이어그램**: 클러스터 아키텍처와 스트림 처리 파이프라인

### 모니터링 지표
```
체크포인트 통계:
- 총 체크포인트: 165개
- 완료된 체크포인트: 165개
- 평균 체크포인트 크기: 6KB
- 평균 체크포인트 시간: 61ms
```

## 🎯 핵심 학습 포인트

### 1. Flink 클러스터 아키텍처
- **JobManager**: 중앙 집중식 스케줄링과 체크포인트 조정
- **TaskManager**: 분산 데이터 처리와 상태 관리
- **Task Slot**: 리소스 격리와 병렬 처리 단위

### 2. 스트림 처리 파이프라인
- **Source**: 외부 데이터 소스 연결
- **Process Function**: 상태 기반 비즈니스 로직
- **Sink**: 결과 데이터 전송

### 3. 체크포인트 메커니즘
- **Barrier Alignment**: 정확한 상태 스냅샷 보장
- **상태 백엔드**: 메모리/디스크 기반 상태 저장
- **장애 복구**: 체크포인트 기반 자동 복구

## 🔍 코드 구조

### 주요 클래스
- **Main**: 애플리케이션 진입점 및 설정
- **UserEvent**: 사용자 이벤트 POJO
- **RandomUserEventSource**: 무한 스트림 이벤트 생성
- **AbnormalCountProcessFunction**: 이상행동 감지 및 상태 관리

### 핵심 로직
```java
// 유저별 이상행동 카운트
public void processElement(UserEvent value, Context ctx, Collector<String> out) {
    int count = abnormalCountState.value();
    if (value.isAbnormal) {
        count++;
        abnormalCountState.update(count);
        if (count >= maxCount) {
            sendAlert(value.userId, count);
            out.collect("[ALERT] userId=" + value.userId + " 이상행동 " + count + "회 감지!");
        }
    } else {
        abnormalCountState.update(0);
    }
}
```

## 🚨 실무 적용 시 고려사항

### 성능 최적화
- **상태 백엔드**: RocksDB 사용으로 메모리 효율성 증대
- **체크포인트 간격**: 처리량과 복구 시간의 트레이드오프
- **Unaligned Checkpoint**: 네트워크 병목 시 처리량 향상

### 운영 고려사항
- **모니터링**: 체크포인트 성공률, 처리 지연시간 추적
- **알람 설정**: 체크포인트 실패, Job 실패 시 즉시 알림
- **리소스 관리**: TaskManager 메모리, CPU 사용량 모니터링

## 📚 참고 자료

- [Apache Flink 공식 문서](https://flink.apache.org/docs/)
- [Flink Checkpointing 가이드](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/state/checkpoints/)
- [Flink State Backends](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/state/state_backends/)


## 플링크 클러스터 모니터링하기
### 전반적인 클러스터 모니터링
http://localhost:8081/
http://localhost:8081/overview

### 체크포인트 관련 모니터링
http://localhost:8081/#/job/running/{job_id}/checkpoints


### 클러스터 실시간 모니터링 웹 UI 확인
<img width="1428" height="750" alt="image" src="https://github.com/user-attachments/assets/c1061eee-fa99-4f1f-9005-8d7b310617a4" />
<img width="1421" height="948" alt="image" src="https://github.com/user-attachments/assets/c11a06cb-0aa7-466d-ad77-eac77ec57a1c" />
<img width="1391" height="765" alt="image" src="https://github.com/user-attachments/assets/d4ec52a8-1f4d-49e1-a315-1f436a14af40" />

open flink-demo/flink-dashboard.html

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 📞 문의

프로젝트에 대한 질문이나 제안사항이 있으시면 이슈를 생성해 주세요.

---

**Flink 실시간 이상행동 감지 시스템** - Apache Flink를 활용한 실시간 스트림 처리 학습 프로젝트 🚀 
