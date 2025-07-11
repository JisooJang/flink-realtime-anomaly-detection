#!/bin/bash

# Flink 버전 설정
FLINK_VERSION="1.17.2"
FLINK_HOME="./flink-${FLINK_VERSION}"

echo "=== Flink 클러스터 상태 확인 ==="
echo ""

# JobManager 상태 확인
echo "JobManager 상태:"
$FLINK_HOME/bin/flink list

echo ""
echo "TaskManager 상태:"
$FLINK_HOME/bin/flink info

echo ""
echo "웹 UI 접속 정보:"
echo "Flink Dashboard: http://localhost:8081"
echo "JobManager: http://localhost:8081"
echo "TaskManager: http://localhost:8082"

echo ""
echo "로그 확인:"
echo "JobManager 로그: $FLINK_HOME/log/flink-*-standalonesession-*.log"
echo "TaskManager 로그: $FLINK_HOME/log/flink-*-taskexecutor-*.log" 