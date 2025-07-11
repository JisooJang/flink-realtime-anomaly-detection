package com.kakao.ticket.application;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.util.Collector;
import java.time.Duration;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws Exception {
        // 로컬 클러스터에 연결 (포트 충돌 방지를 위해 REST 설정 제거)
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(3);
        // TODO 별도로 병렬도를 지정하지 않은 모든 연산자(Operator, Source, Sink 등)에 대해 기본 병렬도를 3으로 적용한다.




        // TODO Checkpoint 설정 (실무 적정값)
        env.enableCheckpointing(10000L, CheckpointingMode.EXACTLY_ONCE); // 10초마다 체크포인트 생성
        env.setStateBackend(new HashMapStateBackend()); // 메모리 기반 상태 백엔드 설정 (실무 권장: RocksDBStateBackend)
        env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink-checkpoints"); // 체크포인트 외부 스토리지 저장 경로 (실무 권장: HDFS, S3 등)
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000L); // 체크포인트 . 최소 간격을 5초로 설정
        env.getCheckpointConfig().setCheckpointTimeout(60000L); // 60초 내 체크포인트 생성 완료되어야 함. 넘어가면 실패로 간주
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1); // 동시 1개
        env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // Barrier alignment timeout (실무 권장: 1~5초)
        env.getCheckpointConfig().setAlignmentTimeout(Duration.ofMillis(5000)); // 정렬 타임아웃을 5초로 설정
        // Unaligned checkpoint 활성화 (네트워크 병목 시)
        env.getCheckpointConfig().enableUnalignedCheckpoints(true); // Unaligned Checkpoint 활성화





        // TODO 무한 스트림처럼 동작하는 랜덤 유저 이벤트 SourceFunction 사용. 3개의 source task가 병렬로 실행됨.
        DataStream<UserEvent> input = env
            .addSource(new RandomUserEventSource())
            .setParallelism(3);



        // TODO Keyed State로 유저별 이상 count 누적, 3회 이상이면 알람/로그 출력
        /***
         * /유저 ID로 키 지정.
         * 3개의 AbnormalCountProcessFunction task가 병렬로 수행됨.
         * 각 키는 hash(key) % maxParallelism 값을 기준으로 Key Group에 할당된다.
         */
        input
            .keyBy(event -> event.userId)
            .process(new AbnormalCountProcessFunction(3))
            .setParallelism(3)
            .name("AbnormalCountProcess")
            .print();

        env.execute("Internal Keyed State Abnormal Count Example");
    }

    // 유저 이벤트 POJO
    public static class UserEvent {
        public String userId;
        public boolean isAbnormal;
        public UserEvent() {}
        public UserEvent(String userId, boolean isAbnormal) {
            this.userId = userId;
            this.isAbnormal = isAbnormal;
        }
        @Override
        public String toString() {
            return "UserEvent{" +
                    "userId='" + userId + '\'' +
                    ", isAbnormal=" + isAbnormal +
                    '}';
        }
    }

    // KeyedProcessFunction: 유저별 이상 count 누적, maxCount 도달 시 알람/로그 출력
    public static class AbnormalCountProcessFunction extends KeyedProcessFunction<String, UserEvent, String> {
        private final int maxCount;
        private transient ValueState<Integer> abnormalCountState;

        public AbnormalCountProcessFunction(int maxCount) {
            this.maxCount = maxCount;
        }

        @Override
        public void open(Configuration parameters) {
            ValueStateDescriptor<Integer> descriptor = new ValueStateDescriptor<>(
                    "abnormalCount", Integer.class, 0);
            abnormalCountState = getRuntimeContext().getState(descriptor);
        }

        @Override
        public void processElement(UserEvent value, Context ctx, Collector<String> out) throws Exception {
            int count = abnormalCountState.value();
            if (value.isAbnormal) {
                count++;
                abnormalCountState.update(count);
                if (count >= maxCount) {
                    sendAlert(value.userId, count);
                    out.collect("[ALERT] userId=" + value.userId + " 이상행동 " + count + "회 감지! (알람 전송)");
                    Thread.sleep(3000); // alert 로그 후 1초 대기
                }
            } else {
                abnormalCountState.update(0);
            }
        }

        // 알람 시스템 연동 예시 (실제 구현은 외부 API 호출 등으로 대체)
        private void sendAlert(String userId, int count) {
            System.out.println("[ALERT SYSTEM] userId=" + userId + ", 이상행동 " + count + "회 감지! 알람 전송");
        }
    }

    // 무한 랜덤 유저 이벤트 SourceFunction
    public static class RandomUserEventSource extends RichParallelSourceFunction<UserEvent> {
        private volatile boolean running = true;
        private String[] userIds;
        private final Random random = new Random();

        @Override
        public void open(Configuration parameters) {
            int idx = getRuntimeContext().getIndexOfThisSubtask();
            System.out.println("[Source Open] Subtask: " + idx);
            // 병렬 인스턴스별로 userId를 다르게 할당 (user1, user2, user3)
            userIds = new String[] { "user" + (idx + 1) };
        }

        @Override
        public void run(SourceContext<UserEvent> ctx) throws Exception {
            while (running) {
                String userId = userIds[random.nextInt(userIds.length)];
                boolean isAbnormal = random.nextDouble() < 0.3; // 30% 확률로 이상행동
                UserEvent event = new UserEvent(userId, isAbnormal);
                System.out.println("[Source Emit] " + event);
                ctx.collect(event);
                Thread.sleep(5000); // 5초마다 이벤트 생성
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}