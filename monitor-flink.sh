#!/bin/bash

echo "=== Flink 클러스터 모니터링 ==="
echo ""

# 클러스터 상태 확인
echo "1. 클러스터 상태:"
curl -s http://localhost:8081/overview

echo ""
echo "2. 실행 중인 Job 목록:"
curl -s http://localhost:8081/jobs

echo ""
echo "3. TaskManager 정보:"
curl -s http://localhost:8081/taskmanagers

echo ""
echo "4. Job 세부 정보:"
JOB_ID=$(curl -s http://localhost:8081/jobs | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ ! -z "$JOB_ID" ]; then
    echo "Job ID: $JOB_ID"
    curl -s http://localhost:8081/jobs/$JOB_ID
else
    echo "실행 중인 Job이 없습니다."
fi

echo ""
echo "5. 웹 UI 접속 정보:"
echo "Flink Dashboard: http://localhost:8081"
echo "REST API: http://localhost:8081"
echo ""
echo "6. 유용한 REST API 엔드포인트:"
echo "- 클러스터 개요: http://localhost:8081/overview"
echo "- Job 목록: http://localhost:8081/jobs"
echo "- TaskManager: http://localhost:8081/taskmanagers"
echo "- Config: http://localhost:8081/config" 