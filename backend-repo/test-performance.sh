#!/bin/bash
# ============================================================================
# Performance Testing Script
# ============================================================================
# Usage: ./test-performance.sh [light|medium|heavy|all]
# Default: all
# ============================================================================

set -e

BASE_URL="${BASE_URL:-http://localhost:18045}"
TEST_TYPE="${1:-all}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Performance Testing Script${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Function to run a pressure test
run_test() {
    local name=$1
    local concurrency=$2
    local requests=$3
    local timeout=$4
    
    echo -e "${YELLOW}Running: $name${NC}"
    echo "  Concurrency: $concurrency"
    echo "  Total Requests: $requests"
    echo "  Timeout: ${timeout}ms"
    echo ""
    
    RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/monitoring/pressure-tests/run" \
        -H "Content-Type: application/json" \
        -d "{
          \"name\": \"$name\",
          \"targetUrl\": \"${BASE_URL}/api/v1/system/info\",
          \"method\": \"GET\",
          \"concurrency\": $concurrency,
          \"totalRequests\": $requests,
          \"timeoutMillis\": $timeout
        }")
    
    # Pretty print JSON if jq is available
    if command -v jq &> /dev/null; then
        echo "$RESPONSE" | jq '{
            name: .name,
            successRate: .successRate,
            avgLatencyMs: .avgLatencyMs,
            p95LatencyMs: .p95LatencyMs,
            requestsPerSecond: .requestsPerSecond,
            totalRequests: .totalRequests,
            successCount: .successCount,
            failureCount: .failureCount
        }'
    else
        echo "$RESPONSE"
    fi
    
    echo ""
}

# Check if application is running
echo "Checking application health..."
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo -e "${GREEN}✓ Application is running${NC}"
else
    echo -e "${RED}✗ Application is not responding at ${BASE_URL}${NC}"
    echo "Please start the application first:"
    echo "  mvn spring-boot:run"
    exit 1
fi

echo ""

# Run tests based on type
case $TEST_TYPE in
    light)
        run_test "light-load-test" 10 50 5000
        ;;
    medium)
        run_test "medium-load-test" 20 100 5000
        ;;
    heavy)
        run_test "heavy-load-test" 50 200 10000
        ;;
    all)
        run_test "light-load-test" 10 50 5000
        echo -e "${YELLOW}----------------------------------------${NC}"
        echo ""
        run_test "medium-load-test" 20 100 5000
        echo -e "${YELLOW}----------------------------------------${NC}"
        echo ""
        run_test "heavy-load-test" 50 200 10000
        ;;
    *)
        echo -e "${RED}Invalid test type: $TEST_TYPE${NC}"
        echo "Usage: $0 [light|medium|heavy|all]"
        exit 1
        ;;
esac

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All tests completed!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "View detailed results at:"
echo "  ${BASE_URL}/api/v1/monitoring/pressure-tests/history"
