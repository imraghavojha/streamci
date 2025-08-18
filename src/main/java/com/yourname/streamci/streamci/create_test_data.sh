#!/bin/bash

echo "=== CREATING TEST DATA FOR ALERTS ==="
echo ""

# First, create a pipeline using webhook
echo "1. Creating test pipeline via webhook..."
curl -X POST http://localhost:8080/api/webhooks/github \
    -H "Content-Type: application/json" \
    -H "X-GitHub-Event: workflow_run" \
    -d '{
        "workflow_run": {
            "status": "completed",
            "conclusion": "success",
            "created_at": "2025-08-18T10:00:00Z",
            "updated_at": "2025-08-18T10:05:00Z",
            "head_sha": "initial123",
            "head_branch": "main",
            "actor": {"login": "testuser"}
        },
        "repository": {"name": "Test-Pipeline"}
    }' \
    -s | python3 -m json.tool

echo ""
echo "2. Waiting for pipeline creation..."
sleep 2

echo ""
echo "3. Creating multiple failing builds to trigger alerts..."
for i in {1..6}; do
    curl -X POST http://localhost:8080/api/webhooks/github \
        -H "Content-Type: application/json" \
        -H "X-GitHub-Event: workflow_run" \
        -d "{
            \"workflow_run\": {
                \"status\": \"completed\",
                \"conclusion\": \"failure\",
                \"created_at\": \"2025-08-18T10:$(printf %02d $i):00Z\",
                \"updated_at\": \"2025-08-18T10:$(printf %02d $((i+5))):00Z\",
                \"head_sha\": \"fail${i}abc\",
                \"head_branch\": \"main\",
                \"actor\": {\"login\": \"testuser\"}
            },
            \"repository\": {\"name\": \"Test-Pipeline\"}
        }" \
        -s -o /dev/null
    echo "Created failing build $i"
done

echo ""
echo "4. Waiting for webhook processing..."
sleep 3

echo ""
echo "5. Check pipelines..."
curl -s http://localhost:8080/api/pipelines | python3 -m json.tool | head -20

echo ""
echo "6. Trigger metrics calculation for pipeline 1..."
curl -X POST http://localhost:8080/api/metrics/1/calculate \
    -s | python3 -m json.tool | grep -E '"successRate"|"consecutiveFailures"|"totalBuilds"'

echo ""
echo "7. Check for alerts..."
curl -s http://localhost:8080/api/alerts | python3 -m json.tool | head -50

echo ""
echo "=== CHECK YOUR CONSOLE FOR ALERT NOTIFICATIONS! ==="
echo "Look for: '==================== ALERT'"