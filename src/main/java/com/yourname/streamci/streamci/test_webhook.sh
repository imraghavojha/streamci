#!/bin/bash

echo "=== WEBHOOK TESTING ==="
echo ""
echo "Note: Using no signature for testing (set github.webhook.secret=default-secret in application.properties)"
echo ""

# Test 1: Basic webhook endpoint
echo "Test 1: Basic webhook accepts JSON"
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: ping" \
  -H "X-Hub-Signature-256: test" \
  -d '{"test":"data"}' \
  -w "\nStatus: %{http_code}\n"

echo -e "\n---\n"

# Test 2: Push event (should be ignored)
echo "Test 2: Push event (should be ignored)"
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -d '{
    "ref": "refs/heads/main",
    "repository": {"name": "test-repo"}
  }' \
  -w "\nStatus: %{http_code}\n"

echo -e "\n---\n"

# Test 3: Workflow run event (should be processed)
echo "Test 3: Workflow run event (should create build)"
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: workflow_run" \
  -d '{
    "action": "completed",
    "workflow_run": {
      "id": 12345,
      "status": "completed",
      "conclusion": "success",
      "created_at": "2024-01-15T10:00:00Z",
      "updated_at": "2024-01-15T10:05:00Z",
      "head_sha": "abc123def456",
      "head_branch": "main",
      "actor": {
        "login": "testuser"
      }
    },
    "repository": {
      "name": "webhook-test-repo",
      "owner": {
        "login": "testowner"
      }
    }
  }' \
  -w "\nStatus: %{http_code}\n"

echo -e "\n---\n"

# Test 4: Check if build was created
echo "Test 4: Verify build was created"
sleep 2  # wait for async processing
curl -X GET http://localhost:8080/api/builds \
  -H "Content-Type: application/json" \
  | python3 -m json.tool | grep -A5 "webhook-test-repo"

echo -e "\n---\n"

# Test 5: Failed workflow
echo "Test 5: Failed workflow event"
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: workflow_run" \
  -d '{
    "workflow_run": {
      "status": "completed",
      "conclusion": "failure",
      "created_at": "2024-01-15T11:00:00Z",
      "updated_at": "2024-01-15T11:10:00Z",
      "head_sha": "fail123",
      "head_branch": "feature-branch",
      "actor": {"login": "developer"}
    },
    "repository": {"name": "webhook-test-repo"}
  }' \
  -w "\nStatus: %{http_code}\n"

echo -e "\n---\n"

# Test 6: Response time test
echo "Test 6: Testing async response time"
time curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: workflow_run" \
  -d '{
    "workflow_run": {
      "status": "in_progress",
      "created_at": "2024-01-15T12:00:00Z",
      "head_sha": "timing123"
    },
    "repository": {"name": "performance-test"}
  }' \
  -w "\nTotal time: %{time_total}s\n" \
  -o /dev/null -s

echo -e "\n=== TESTING COMPLETE ===\n"