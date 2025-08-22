#!/bin/bash

echo "=== QUICK PATTERN ANALYSIS VALIDATION ==="
echo ""

PIPELINE_ID=1
BASE_URL="http://localhost:8080"

echo "🔍 Quick test of pattern recognition endpoints..."
echo ""

echo "1. Testing basic API connectivity..."
if curl -s "$BASE_URL/api/pipelines" > /dev/null; then
    echo "✅ API server is running"
else
    echo "❌ API server not responding"
    exit 1
fi

echo ""
echo "2. Testing pattern analysis endpoint..."
RESPONSE=$(curl -s "$BASE_URL/api/analysis/patterns?pipelineId=$PIPELINE_ID&days=7")
if echo "$RESPONSE" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
    echo "✅ Pattern analysis endpoint working"
    echo "$RESPONSE" | python3 -m json.tool | head -15
else
    echo "❌ Pattern analysis endpoint failed"
    echo "Response: $RESPONSE"
fi

echo ""
echo "3. Testing success prediction..."
PRED_RESPONSE=$(curl -s "$BASE_URL/api/predictions/success?pipelineId=$PIPELINE_ID")
if echo "$PRED_RESPONSE" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
    echo "✅ Success prediction endpoint working"
    PROB=$(echo "$PRED_RESPONSE" | python3 -c "import sys, json; data = json.load(sys.stdin); print(data.get('probability', 'N/A'))" 2>/dev/null)
    echo "📊 Success probability: $PROB%"
else
    echo "❌ Success prediction endpoint failed"
    echo "Response: $PRED_RESPONSE"
fi

echo ""
echo "4. Testing flaky test detection..."
FLAKY_RESPONSE=$(curl -s "$BASE_URL/api/analysis/flaky-tests?pipelineId=$PIPELINE_ID")
if echo "$FLAKY_RESPONSE" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
    echo "✅ Flaky test detection working"
    FLAKY_COUNT=$(echo "$FLAKY_RESPONSE" | python3 -c "import sys, json; data = json.load(sys.stdin); print(data.get('flaky_tests_found', 0))" 2>/dev/null)
    echo "📊 Flaky tests found: $FLAKY_COUNT"
else
    echo "❌ Flaky test detection failed"
fi

echo ""
echo "5. Testing correlation analysis..."
CORR_RESPONSE=$(curl -s "$BASE_URL/api/analysis/correlations?pipelineId=$PIPELINE_ID&type=time")
if echo "$CORR_RESPONSE" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
    echo "✅ Correlation analysis working"
else
    echo "❌ Correlation analysis failed"
fi

echo ""
echo "6. Testing analysis summary..."
SUMMARY_RESPONSE=$(curl -s "$BASE_URL/api/analysis/summary?pipelineId=$PIPELINE_ID")
if echo "$SUMMARY_RESPONSE" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
    echo "✅ Analysis summary working"
    echo "$SUMMARY_RESPONSE" | python3 -m json.tool | head -10
else
    echo "❌ Analysis summary failed"
fi

echo ""
echo "🎯 Quick validation complete!"
echo ""
echo "If you see mostly ✅ marks, your pattern recognition system is working!"
echo "If you see ❌ marks, check:"
echo "  - Spring Boot application is running"
echo "  - Database has build data (run webhook tests first)"
echo "  - No compilation errors in logs"
echo "  - All pattern analysis classes are properly created"