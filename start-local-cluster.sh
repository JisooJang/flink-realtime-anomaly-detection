#!/bin/bash

# Flink 버전 설정
FLINK_VERSION="1.17.2"
FLINK_HOME="./flink-${FLINK_VERSION}"

# Flink 다운로드 (없는 경우)
if [ ! -d "$FLINK_HOME" ]; then
    echo "Flink ${FLINK_VERSION} 다운로드 중..."
    wget https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_2.12.tgz
    tar -xzf flink-${FLINK_VERSION}-bin-scala_2.12.tgz
    mv flink-${FLINK_VERSION} $FLINK_HOME
    rm flink-${FLINK_VERSION}-bin-scala_2.12.tgz
    echo "Flink 다운로드 완료"
fi

# 로컬 클러스터 시작
echo "Flink 로컬 클러스터 시작 중..."
$FLINK_HOME/bin/start-cluster.sh

echo "Flink 클러스터가 시작되었습니다!"
echo "웹 UI: http://localhost:8081"
echo "JobManager: localhost:8081"
echo "TaskManager: localhost:8082"

echo ""
echo "애플리케이션을 실행하려면 다음 명령어를 사용하세요:"
echo "./gradlew run" 