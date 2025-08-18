#!/bin/bash

echo "=== METRICS TESTING ==="
echo ""

# First, create some test data
echo "1. Creating test builds for metrics calculation..."
echo ""

# Create a few test builds with different statuses
for i in {1..5}; do
    STATUS="success"
    if [ $((i % 3)) -eq 0 ]; then
        STATUS="failure"
    fi

    curl -X POST http://localhost:8080/api/builds \
        -H "Content-Type: application/json" \
        -d "{
            \"pipeline\": {\"id\": 1},
            \"status\": \"$STATUS\",
            \"duration\": $((300 + i * 50)),
            \"commitHash\": \"test$i\",
            \"committer\": \"tester\",
            \"branch\": \"main\"
        }" \
        -s -o /dev/null

    echo "Created build $i with status: $STATUS"
done

echo ""
echo "2. Manually trigger metrics calculation for pipeline 1..."
curl -X POST http://localhost:8080/api/metrics/1/calculate \
    -H "Content-Type: application/json" \
    | python3 -m json.tool

echo ""
echo "3. Get latest metrics for pipeline 1..."
curl -X GET http://localhost:8080/api/metrics/1 \
    -H "Content-Type: application/json" \
    | python3 -m json.tool

echo ""
echo "4. Get metrics history (last 7 days)..."
curl -X GET "http://localhost:8080/api/metrics/1/history?days=7" \
    -H "Content-Type: application/json" \
    | python3 -m json.tool

echo ""
echo "5. Check all pipelines to see updated stats..."
curl -X GET http://localhost:8080/api/pipelines \
    -H "Content-Type: application/json" \
    | python3 -m json.tool | grep -E '"name"|"status"|"duration"'

echo ""
echo "=== METRICS TESTING COMPLETE ==="
echo ""
echo "Note: Metrics will auto-calculate every 5 minutes."
echo "Check logs to see: 'Starting scheduled metrics calculation'"