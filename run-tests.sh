#!/usr/bin/env bash
# =============================================================================
# SmartHire — CI Test Script
# Runs all unit + integration tests against Docker-based infrastructure.
# Works on Mac and Linux.
# Usage: ./run-tests.sh
# =============================================================================

set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
BACKEND_DIR="${BACKEND_DIR:-smarthire-backend}"
AI_DIR="${AI_DIR:-smarthire-ai-service}"
KAFKA_TIMEOUT=60
KAFKA_CHECK_INTERVAL=2

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# ─── Helpers ───────────────────────────────────────────────────────────────────

log_info()  { echo -e "${BOLD}[INFO]${NC}  $*"; }
log_pass()  { echo -e "${GREEN}[PASS]${NC}  $*"; }
log_fail()  { echo -e "${RED}[FAIL]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }

cleanup() {
    log_info "Cleaning up Docker Compose..."
    docker compose -f "$COMPOSE_FILE" down --volumes --remove-orphans 2>/dev/null || true
}
trap cleanup EXIT

wait_for_kafka() {
    local elapsed=0
    log_info "Waiting for Kafka to be healthy (timeout: ${KAFKA_TIMEOUT}s)..."

    while [ $elapsed -lt $KAFKA_TIMEOUT ]; do
        if docker compose -f "$COMPOSE_FILE" exec -T kafka kafka-topics \
                --bootstrap-server localhost:9092 --list &>/dev/null; then
            log_pass "Kafka is healthy"
            return 0
        fi
        sleep "$KAFKA_CHECK_INTERVAL"
        elapsed=$((elapsed + KAFKA_CHECK_INTERVAL))
    done

    log_fail "Kafka health check timed out after ${KAFKA_TIMEOUT}s"
    return 1
}

run_java_tests() {
    log_info "Running Java unit tests..."
    if ./mvnw test -f "$BACKEND_DIR/pom.xml" -q; then
        log_pass "Java tests passed"
        return 0
    else
        log_fail "Java tests failed"
        return 1
    fi
}

run_python_tests() {
    log_info "Running Python unit tests..."
    cd "$AI_DIR"
    if pip install -q -r requirements-dev.txt && pytest tests/ -v --tb=short; then
        log_pass "Python tests passed"
        cd ..
        return 0
    else
        log_fail "Python tests failed"
        cd ..
        return 1
    fi
}

# ─── Main ─────────────────────────────────────────────────────────────────────

echo ""
echo "=============================================="
echo "  SmartHire CI Test Suite"
echo "=============================================="
echo ""

# Step 1: Start infrastructure services only
log_info "Starting infrastructure (postgres, zookeeper, kafka)..."
docker compose -f "$COMPOSE_FILE" up -d postgres zookeeper kafka

# Step 2: Wait for Kafka
if ! wait_for_kafka; then
    log_fail "Infrastructure failed to start"
    exit 1
fi

# Step 3: Run Java tests
JAVA_RESULT=0
run_java_tests || JAVA_RESULT=$?

# Step 4: Run Python tests
PYTHON_RESULT=0
run_python_tests || PYTHON_RESULT=$?

# ─── Summary ─────────────────────────────────────────────────────────────────

echo ""
echo "=============================================="
echo "  Test Results"
echo "=============================================="

JAVA_STATUS=$([ $JAVA_RESULT -eq 0 ] && echo "PASS" || echo "FAIL")
PYTHON_STATUS=$([ $PYTHON_RESULT -eq 0 ] && echo "PASS" || echo "FAIL")

echo -e "  Java (mvn test):     $JAVA_STATUS"
echo -e "  Python (pytest):     $PYTHON_STATUS"
echo ""

if [ $JAVA_RESULT -eq 0 ] && [ $PYTHON_RESULT -eq 0 ]; then
    log_pass "ALL TESTS PASSED"
    echo ""
    exit 0
else
    log_fail "SOME TESTS FAILED"
    echo ""
    exit 1
fi