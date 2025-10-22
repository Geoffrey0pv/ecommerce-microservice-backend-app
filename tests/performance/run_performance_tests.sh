#!/bin/bash

# 🚀 Performance Testing Runner Script
# Automates execution of all Locust performance tests

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
HOST="http://localhost:8100"
RESULTS_DIR="./results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Create results directory
mkdir -p $RESULTS_DIR

echo -e "${BLUE}🐍 E-commerce Performance Testing Suite${NC}"
echo -e "${BLUE}======================================${NC}"
echo "Host: $HOST"
echo "Results Directory: $RESULTS_DIR"
echo "Timestamp: $TIMESTAMP"
echo ""

# Function to check if services are running
check_services() {
    echo -e "${YELLOW}🔍 Checking if services are running...${NC}"
    
    if curl -s "$HOST/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ API Gateway is running${NC}"
    else
        echo -e "${RED}❌ API Gateway is not accessible at $HOST${NC}"
        echo "Please start your microservices with: docker-compose up -d"
        exit 1
    fi
    
    # Check individual services
    services=("user-service" "product-service" "order-service")
    for service in "${services[@]}"; do
        if curl -s "$HOST/$service/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ $service is running${NC}"
        else
            echo -e "${YELLOW}⚠️ $service health check failed (may be normal)${NC}"
        fi
    done
    echo ""
}

# Function to run a specific test
run_test() {
    local test_name=$1
    local test_file=$2
    local users=$3
    local spawn_rate=$4
    local duration=$5
    local description=$6
    
    echo -e "${BLUE}🧪 Running $test_name${NC}"
    echo "Description: $description"
    echo "Configuration: $users users, spawn rate $spawn_rate/sec, duration $duration"
    echo ""
    
    local output_prefix="$RESULTS_DIR/${test_name,,}_${TIMESTAMP}"
    
    # Run Locust test
    locust -f "$test_file" \
           --host="$HOST" \
           --users="$users" \
           --spawn-rate="$spawn_rate" \
           --run-time="$duration" \
           --headless \
           --html="$output_prefix.html" \
           --csv="$output_prefix" \
           --logfile="$output_prefix.log" \
           --loglevel INFO
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $test_name completed successfully${NC}"
        echo "Results: $output_prefix.html"
    else
        echo -e "${RED}❌ $test_name failed${NC}"
    fi
    echo ""
}

# Function to run quick smoke test
run_smoke_test() {
    echo -e "${YELLOW}💨 Running Smoke Test (Quick validation)${NC}"
    
    locust -f "ecommerce_load_test.py" \
           --host="$HOST" \
           --users=5 \
           --spawn-rate=2 \
           --run-time=30s \
           --headless \
           --html="$RESULTS_DIR/smoke_test_${TIMESTAMP}.html"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Smoke test passed - system is ready for performance testing${NC}"
        return 0
    else
        echo -e "${RED}❌ Smoke test failed - check system status before running full tests${NC}"
        return 1
    fi
}

# Function to run all tests
run_all_tests() {
    echo -e "${BLUE}🚀 Running Complete Performance Test Suite${NC}"
    echo ""
    
    # Load Testing
    run_test "Load_Test" \
             "ecommerce_load_test.py" \
             100 \
             10 \
             "10m" \
             "Standard load testing with 100 concurrent users"
    
    sleep 30  # Cool down period
    
    # Stress Testing
    run_test "Stress_Test" \
             "ecommerce_stress_test.py" \
             500 \
             20 \
             "15m" \
             "Stress testing ramping up to 500 users"
    
    sleep 60  # Longer cool down after stress test
    
    # Spike Testing
    run_test "Spike_Test" \
             "ecommerce_spike_test.py" \
             300 \
             50 \
             "5m" \
             "Spike testing with sudden traffic bursts"
    
    sleep 30  # Cool down period
    
    # Note: Endurance test commented out by default (takes 1 hour)
    # Uncomment if you want to run it
    # run_test "Endurance_Test" \
    #          "ecommerce_endurance_test.py" \
    #          150 \
    #          5 \
    #          "1h" \
    #          "Endurance testing with sustained load for 1 hour"
}

# Function to generate summary report
generate_summary() {
    echo -e "${BLUE}📊 Generating Summary Report${NC}"
    
    summary_file="$RESULTS_DIR/performance_summary_${TIMESTAMP}.md"
    
    cat > "$summary_file" << EOF
# 🚀 Performance Testing Summary

**Test Date:** $(date)
**Target System:** $HOST
**Test Suite:** E-commerce Microservices Performance Testing

## Test Results Overview

### Load Test (100 users, 10 minutes)
- **Objective:** Validate normal operational capacity
- **Results:** See \`load_test_${TIMESTAMP}.html\`

### Stress Test (500 users, 15 minutes)  
- **Objective:** Find system breaking points
- **Results:** See \`stress_test_${TIMESTAMP}.html\`

### Spike Test (300 users, 5 minutes)
- **Objective:** Test sudden traffic spike handling
- **Results:** See \`spike_test_${TIMESTAMP}.html\`

## Key Metrics Analyzed

- **Response Time:** Average, P95, P99 percentiles
- **Throughput:** Requests per second (RPS)
- **Error Rate:** Percentage of failed requests
- **Concurrent Users:** Maximum supported users
- **System Stability:** Performance under various loads

## Performance Objectives

| Metric | Target | Load Test | Stress Test | Spike Test |
|--------|--------|-----------|-------------|------------|
| Response Time P95 | < 1s | - | - | - |
| Response Time P99 | < 2s | - | - | - |
| Error Rate | < 1% | - | - | - |
| Throughput | 50-200 RPS | - | - | - |

## Recommendations

Review individual test reports for detailed analysis and specific recommendations.

## Files Generated

EOF

    # List all generated files
    find "$RESULTS_DIR" -name "*${TIMESTAMP}*" -type f | sort >> "$summary_file"
    
    echo "Summary report created: $summary_file"
}

# Main execution logic
main() {
    case "${1:-all}" in
        "smoke")
            check_services
            run_smoke_test
            ;;
        "load")
            check_services
            run_test "Load_Test" "ecommerce_load_test.py" 100 10 "10m" "Load testing with 100 users"
            ;;
        "stress")
            check_services
            run_test "Stress_Test" "ecommerce_stress_test.py" 500 20 "15m" "Stress testing with 500 users"
            ;;
        "spike")
            check_services
            run_test "Spike_Test" "ecommerce_spike_test.py" 300 50 "5m" "Spike testing with traffic bursts"
            ;;
        "endurance")
            check_services
            run_test "Endurance_Test" "ecommerce_endurance_test.py" 150 5 "1h" "Endurance testing for 1 hour"
            ;;
        "all")
            check_services
            if run_smoke_test; then
                run_all_tests
                generate_summary
            else
                echo -e "${RED}Skipping full test suite due to smoke test failure${NC}"
                exit 1
            fi
            ;;
        "check")
            check_services
            ;;
        *)
            echo -e "${YELLOW}Usage: $0 [smoke|load|stress|spike|endurance|all|check]${NC}"
            echo ""
            echo "Test Types:"
            echo "  smoke     - Quick 30-second validation test"
            echo "  load      - Standard load test (100 users, 10 min)"
            echo "  stress    - Stress test (500 users, 15 min)"
            echo "  spike     - Spike test (300 users, 5 min)"
            echo "  endurance - Endurance test (150 users, 1 hour)"
            echo "  all       - Run complete test suite (excluding endurance)"
            echo "  check     - Check if services are running"
            echo ""
            echo "Examples:"
            echo "  $0 smoke          # Quick validation"
            echo "  $0 load           # Run only load test"
            echo "  $0 all            # Run full test suite"
            echo "  $0 check          # Check service health"
            ;;
    esac
}

# Check if we're in the right directory
if [ ! -f "ecommerce_load_test.py" ]; then
    echo -e "${RED}❌ Error: Performance test files not found${NC}"
    echo "Please run this script from the tests/performance directory"
    exit 1
fi

# Check if Locust is installed
if ! command -v locust &> /dev/null; then
    echo -e "${RED}❌ Error: Locust is not installed${NC}"
    echo "Please install requirements: pip install -r requirements.txt"
    exit 1
fi

# Run main function with all arguments
main "$@"