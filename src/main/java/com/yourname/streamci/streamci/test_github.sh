#!/bin/bash

echo "=== GITHUB INTEGRATION TESTING ==="
echo "Make sure your Spring Boot app is running on port 8080"
echo ""

# 1. Test GitHub API Connection
echo "1. Testing GitHub API Connection..."
curl -X GET "http://localhost:8080/api/github/test" \
  -H "Content-Type: application/json" \
  -w "\nStatus Code: %{http_code}\n"

echo -e "\n--- Expected: success status with timestamp ---\n"

# 2. Verify existing pipelines (before sync)
echo "2. Checking existing pipelines (before sync)..."
curl -X GET "http://localhost:8080/api/pipelines" \
  -H "Content-Type: application/json" \
  -w "\nStatus Code: %{http_code}\n"

echo -e "\n--- Note: Record the count of existing pipelines ---\n"

# 3. Test sync with your repo that has GitHub Actions
echo "3. Syncing GitHub repository: imraghavojha/Enigma-Machine..."
curl -X GET "http://localhost:8080/api/sync/github/imraghavojha/Enigma-Machine" \
  -H "Content-Type: application/json" \
  -w "\nStatus Code: %{http_code}\n"

echo -e "\n--- Expected: success with pipelines_synced: 1, builds_synced: >0 ---\n"

# 3b. Also test with a known active repo for comparison
echo "3b. Also syncing actions/checkout (known active repo)..."
curl -X GET "http://localhost:8080/api/sync/github/actions/checkout" \
  -H "Content-Type: application/json" \
  -w "\nStatus Code: %{http_code}\n"

echo -e "\n--- This should definitely show builds > 0 ---\n"

# 4. Verify pipelines after sync
echo "4. Checking pipelines after sync..."
curl -X GET "http://localhost:8080/api/pipelines" \
  -H "Content-Type: application/json" \
  -w "\nStatus Code: %{http_code}\n"

echo -e "\n--- Expected: Should see 'Hello-World' pipeline ---\n"

# 5. Check all builds
echo "5. Checking all builds..."
curl -X GET "http://localhost:8080/api/builds" \
  -H "Content-Type: application/json" \
  -w "\nStatus Code: %{http_code}\n"

echo -e "\n--- Expected: Should see builds from GitHub ---\n"

echo "=== TESTING COMPLETE ==="