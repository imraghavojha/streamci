#!/bin/bash

echo "=== QUEUE ANALYSIS TESTING V2 ==="
echo ""

# first check if pipeline exists
echo "0. checking pipelines..."
curl -s http://localhost:8080/api/pipelines | python3 -m json.tool | grep -E '"id"|"name"' | head -10

PIPELINE_ID=1

echo ""
echo "1. simulating 5 queued builds..."
for i in {1..5}; do
    RESPONSE=$(curl -X POST http://localhost:8080/api/webhooks/github \
        -H "Content-Type: application/json" \
        -H "X-GitHub-Event: workflow_run" \
        -d "{
            \"workflow_run\": {
                \"id\": \"queue_test_$i\",
                \"status\": \"queued\",
                \"created_at\": \"2025-08-18T10:0$i:00Z\"
            },
            \"repository\": {\"name\": \"Test-Pipeline\"}
        }" \
        -s)
    echo "  queued build $i - response: $RESPONSE"
done

echo ""
echo "2. wait for async processing..."
sleep 3

echo ""
echo "3. check queue tracker table directly..."
echo "   (if you have access to database, run: SELECT * FROM queue_tracker;)"

echo ""
echo "4. calculate queue metrics now..."
METRICS=$(curl -X POST http://localhost:8080/api/queue/$PIPELINE_ID/calculate -s)
echo "$METRICS" | python3 -m json.tool | grep -E '"currentQueueDepth"|"runningBuilds"|"waitingBuilds"' || echo "Response: $METRICS"

echo ""
echo "5. simulate 3 builds starting..."
for i in {1..3}; do
    RESPONSE=$(curl -X POST http://localhost:8080/api/webhooks/github \
        -H "Content-Type: application/json" \
        -H "X-GitHub-Event: workflow_run" \
        -d "{
            \"workflow_run\": {
                \"id\": \"queue_test_$i\",
                \"status\": \"in_progress\",
                \"updated_at\": \"2025-08-18T10:10:00Z\"
            },
            \"repository\": {\"name\": \"Test-Pipeline\"}
        }" \
        -s)
    echo "  started build $i"
done

echo ""
echo "6. wait and recalculate..."
sleep 3
curl -X POST http://localhost:8080/api/queue/$PIPELINE_ID/calculate -s | python3 -m json.tool | grep -E '"currentQueueDepth"|"runningBuilds"|"waitingBuilds"'

echo ""
echo "7. get predictions..."
curl -X GET "http://localhost:8080/api/predictions/queue?pipelineId=$PIPELINE_ID" -s | python3 -m json.tool

echo ""
echo "8. complete the builds..."
for i in {1..3}; do
    curl -X POST http://localhost:8080/api/webhooks/github \
        -H "Content-Type: application/json" \
        -H "X-GitHub-Event: workflow_run" \
        -d "{
            \"workflow_run\": {
                \"id\": \"queue_test_$i\",
                \"status\": \"completed\",
                \"conclusion\": \"success\",
                \"created_at\": \"2025-08-18T10:0$i:00Z\",
                \"updated_at\": \"2025-08-18T10:15:00Z\",
                \"head_sha\": \"abc$i\",
                \"head_branch\": \"main\"
            },
            \"repository\": {\"name\": \"Test-Pipeline\"}
        }" \
        -s -o /dev/null
    echo "  completed build $i"
done

echo ""
echo "9. final check..."
sleep 3
curl -X POST http://localhost:8080/api/queue/$PIPELINE_ID/calculate -s | python3 -m json.tool

echo ""
echo "10. check spring boot logs for:"
echo "    - 'tracking queued build'"
echo "    - 'tracking build started'"
echo "    - 'tracking build completed'"
echo ""
echo "=== TESTING COMPLETE ==="