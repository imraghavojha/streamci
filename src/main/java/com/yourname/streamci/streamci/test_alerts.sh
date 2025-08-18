#!/bin/bash

echo "=== ALERT SYSTEM TESTING ==="
echo ""

# check current metrics
echo "1. Current metrics for pipeline 1:"
curl -s http://localhost:8080/api/metrics/1 | python3 -m json.tool | grep -E '"successRate"|"consecutiveFailures"|"avgDurationSeconds"'

echo ""
echo "2. Checking alert configurations:"
curl -s http://localhost:8080/api/alerts/config | python3 -m json.tool | head -20

echo ""
echo "3. Creating some failing builds to trigger alerts..."
for i in {1..4}; do
    curl -X POST http://localhost:8080/api/webhooks/github \
        -H "Content-Type: application/json" \
        -H "X-GitHub-Event: workflow_run" \
        -d "{
            \"workflow_run\": {
                \"status\": \"completed\",
                \"conclusion\": \"failure\",
                \"created_at\": \"2025-08-17T14:0$i:00Z\",
                \"updated_at\": \"2025-08-17T14:0$i:30Z\",
                \"head_sha\": \"fail$i\",
                \"head_branch\": \"main\",
                \"actor\": {\"login\": \"testuser\"}
            },
            \"repository\": {\"name\": \"Enigma-Machine\"}
        }" \
        -s -o /dev/null
    echo "Created failing build $i"
done

echo ""
echo "4. Waiting for async processing..."
sleep 3

echo ""
echo "5. Trigger metrics calculation to check alerts:"
curl -X POST http://localhost:8080/api/metrics/1/calculate -s -o /dev/null
echo "Metrics calculated"

echo ""
echo "6. Check active alerts:"
curl -s http://localhost:8080/api/alerts | python3 -m json.tool

echo ""
echo "7. Get alert statistics:"
curl -s http://localhost:8080/api/alerts/stats | python3 -m json.tool

echo ""
echo "8. Get alerts for pipeline 1:"
curl -s http://localhost:8080/api/alerts/pipeline/1 | python3 -m json.tool | head -30

echo ""
echo "9. Acknowledge first alert (if exists):"
ALERT_ID=$(curl -s http://localhost:8080/api/alerts | python3 -c "import sys, json; alerts = json.load(sys.stdin); print(alerts[0]['id'] if alerts else '0')")
if [ "$ALERT_ID" != "0" ]; then
    echo "Acknowledging alert $ALERT_ID..."
    curl -X POST http://localhost:8080/api/alerts/$ALERT_ID/acknowledge -s | python3 -m json.tool | grep -E '"status"|"acknowledgedAt"'
else
    echo "No alerts to acknowledge"
fi

echo ""
echo "=== ALERT TESTING COMPLETE ==="
echo ""
echo "Check your Spring Boot logs for alert notifications!"
echo "Look for: '==================== ALERT'"