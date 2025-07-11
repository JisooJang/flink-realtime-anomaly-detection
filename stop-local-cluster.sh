#!/bin/bash

# Flink 버전 설정
FLINK_VERSION="1.17.2"
FLINK_HOME="./flink-${FLINK_VERSION}"

# 로컬 클러스터 중지
echo "Flink 로컬 클러스터 중지 중..."
$FLINK_HOME/bin/stop-cluster.sh

echo "Flink 클러스터가 중지되었습니다!" 