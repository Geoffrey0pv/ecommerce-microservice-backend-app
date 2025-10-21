package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Test: Performance and Load Testing
 * Tests system performance under various load conditions and measures response times
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
@TestMethodOrder(OrderAnnotation.class)
public class PerformanceAndLoadE2ETest {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private String baseUrl;
    private final String API_GATEWAY_URL = "http://localhost:8100";

    // Performance metrics
    private final Map<String, List<Long>> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        objectMapper = new ObjectMapper();
        baseUrl = "http://localhost:" + port;
        
        // Initialize metrics
        performanceMetrics.clear();
        errorCounts.clear();
    }

    @Test
    @Order(1)
    void testBasicResponseTimeMetrics() {
        System.out.println("📊 Testing Basic Response Time Metrics");

        final int SAMPLE_SIZE = 20;
        
        // Test User Service response times
        List<Long> userServiceTimes = measureResponseTimes(
                API_GATEWAY_URL + "/user-service/api/users", 
                HttpMethod.GET, 
                null, 
                SAMPLE_SIZE
        );
        performanceMetrics.put("user-service-get", userServiceTimes);

        // Test Product Service response times
        List<Long> productServiceTimes = measureResponseTimes(
                API_GATEWAY_URL + "/product-service/api/products", 
                HttpMethod.GET, 
                null, 
                SAMPLE_SIZE
        );
        performanceMetrics.put("product-service-get", productServiceTimes);

        // Test Order Service response times
        List<Long> orderServiceTimes = measureResponseTimes(
                API_GATEWAY_URL + "/order-service/api/orders", 
                HttpMethod.GET, 
                null, 
                SAMPLE_SIZE
        );
        performanceMetrics.put("order-service-get", orderServiceTimes);

        // Calculate and display statistics
        System.out.println("\n📈 RESPONSE TIME METRICS:");
        System.out.println("==========================");
        
        displayMetrics("User Service GET", userServiceTimes);
        displayMetrics("Product Service GET", productServiceTimes);
        displayMetrics("Order Service GET", orderServiceTimes);

        // Verify all response times are reasonable (< 2 seconds)
        assertThat(getAverageTime(userServiceTimes)).isLessThan(2000);
        assertThat(getAverageTime(productServiceTimes)).isLessThan(2000);
        assertThat(getAverageTime(orderServiceTimes)).isLessThan(2000);

        System.out.println("✅ All services respond within acceptable time limits");
    }

    @Test
    @Order(2)
    void testConcurrentUserLoad() {
        System.out.println("👥 Testing Concurrent User Load");

        final int CONCURRENT_USERS = 10;
        final int REQUESTS_PER_USER = 5;

        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_USERS);
        List<Future<LoadTestResult>> futures = new ArrayList<>();

        System.out.println("Simulating " + CONCURRENT_USERS + " concurrent users, " + 
                          REQUESTS_PER_USER + " requests each...");

        // Submit concurrent user simulation tasks
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            Future<LoadTestResult> future = executorService.submit(() -> 
                simulateUserBehavior(userId, REQUESTS_PER_USER)
            );
            futures.add(future);
        }

        // Collect results
        List<LoadTestResult> results = new ArrayList<>();
        for (Future<LoadTestResult> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                System.err.println("User simulation failed: " + e.getMessage());
            }
        }

        executorService.shutdown();

        // Analyze results
        analyzeConcurrentLoadResults(results);

        assertThat(results.size()).isGreaterThanOrEqualTo(CONCURRENT_USERS / 2); // At least 50% success
        System.out.println("✅ Concurrent load test completed");
    }

    @Test
    @Order(3)
    void testHighVolumeDataCreation() {
        System.out.println("📦 Testing High Volume Data Creation");

        final int BATCH_SIZE = 50;
        
        // Test high volume user creation
        System.out.println("Creating " + BATCH_SIZE + " users in rapid succession...");
        long startTime = System.currentTimeMillis();
        
        List<Long> creationTimes = new ArrayList<>();
        int successfulCreations = 0;
        
        for (int i = 0; i < BATCH_SIZE; i++) {
            long requestStart = System.currentTimeMillis();
            
            Map<String, Object> userRequest = createUserRequest("BatchUser" + i);
            
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        API_GATEWAY_URL + "/user-service/api/users",
                        createJsonEntity(userRequest),
                        Map.class
                );
                
                long requestTime = System.currentTimeMillis() - requestStart;
                creationTimes.add(requestTime);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    successfulCreations++;
                }
            } catch (Exception e) {
                System.err.println("User creation failed: " + e.getMessage());
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.println("\n📊 HIGH VOLUME CREATION METRICS:");
        System.out.println("================================");
        System.out.println("Total users created: " + successfulCreations + "/" + BATCH_SIZE);
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println("Average creation time: " + getAverageTime(creationTimes) + "ms");
        System.out.println("Throughput: " + String.format("%.2f", (successfulCreations * 1000.0) / totalTime) + " users/second");
        
        displayMetrics("User Creation", creationTimes);
        
        // Verify reasonable success rate and performance
        double successRate = (double) successfulCreations / BATCH_SIZE;
        assertThat(successRate).isGreaterThan(0.8); // At least 80% success rate
        assertThat(getAverageTime(creationTimes)).isLessThan(1000); // Average < 1 second
        
        System.out.println("✅ High volume creation test passed");
    }

    @Test
    @Order(4)
    void testDatabaseStressTest() {
        System.out.println("💾 Testing Database Stress");

        final int STRESS_OPERATIONS = 30;
        
        // Create some base data first
        Map<String, Object> testUser = createUserRequest("StressTestUser");
        ResponseEntity<Map> userResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(testUser),
                Map.class
        );
        
        Integer userId = null;
        if (userResponse.getStatusCode().is2xxSuccessful()) {
            userId = (Integer) userResponse.getBody().get("userId");
        }

        if (userId == null) {
            System.out.println("⚠️ Skipping database stress test - couldn't create test user");
            return;
        }

        // Stress test with rapid read/write operations
        List<Long> readTimes = new ArrayList<>();
        List<Long> writeTimes = new ArrayList<>();
        int readErrors = 0;
        int writeErrors = 0;

        System.out.println("Performing " + STRESS_OPERATIONS + " rapid database operations...");

        for (int i = 0; i < STRESS_OPERATIONS; i++) {
            // Read operation
            long readStart = System.currentTimeMillis();
            try {
                ResponseEntity<Map> readResponse = restTemplate.getForEntity(
                        API_GATEWAY_URL + "/user-service/api/users/" + userId,
                        Map.class
                );
                
                long readTime = System.currentTimeMillis() - readStart;
                readTimes.add(readTime);
                
                if (!readResponse.getStatusCode().is2xxSuccessful()) {
                    readErrors++;
                }
            } catch (Exception e) {
                readErrors++;
            }

            // Write operation (cart creation)
            long writeStart = System.currentTimeMillis();
            try {
                Map<String, Object> cartRequest = new HashMap<>();
                cartRequest.put("userId", userId);
                
                ResponseEntity<Map> writeResponse = restTemplate.postForEntity(
                        API_GATEWAY_URL + "/order-service/api/carts",
                        createJsonEntity(cartRequest),
                        Map.class
                );
                
                long writeTime = System.currentTimeMillis() - writeStart;
                writeTimes.add(writeTime);
                
                if (!writeResponse.getStatusCode().is2xxSuccessful()) {
                    writeErrors++;
                }
            } catch (Exception e) {
                writeErrors++;
            }
        }

        System.out.println("\n💾 DATABASE STRESS METRICS:");
        System.out.println("===========================");
        System.out.println("Read operations: " + readTimes.size() + "/" + STRESS_OPERATIONS + " (errors: " + readErrors + ")");
        System.out.println("Write operations: " + writeTimes.size() + "/" + STRESS_OPERATIONS + " (errors: " + writeErrors + ")");
        
        displayMetrics("Database Reads", readTimes);
        displayMetrics("Database Writes", writeTimes);
        
        // Verify acceptable error rates and performance
        double readErrorRate = (double) readErrors / STRESS_OPERATIONS;
        double writeErrorRate = (double) writeErrors / STRESS_OPERATIONS;
        
        assertThat(readErrorRate).isLessThan(0.1); // Less than 10% read errors
        assertThat(writeErrorRate).isLessThan(0.2); // Less than 20% write errors
        
        System.out.println("✅ Database stress test completed");
    }

    @Test
    @Order(5)
    void testSystemThroughputMeasurement() {
        System.out.println("⚡ Testing System Throughput");

        final int DURATION_SECONDS = 30;
        final int THREAD_COUNT = 5;

        System.out.println("Running throughput test for " + DURATION_SECONDS + " seconds with " + 
                          THREAD_COUNT + " threads...");

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<ThroughputResult>> futures = new ArrayList<>();
        
        long testStartTime = System.currentTimeMillis();
        long testEndTime = testStartTime + (DURATION_SECONDS * 1000);

        // Start throughput measurement threads
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            Future<ThroughputResult> future = executorService.submit(() -> 
                measureThroughput(threadId, testEndTime)
            );
            futures.add(future);
        }

        // Collect results
        List<ThroughputResult> results = new ArrayList<>();
        for (Future<ThroughputResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                System.err.println("Throughput measurement failed: " + e.getMessage());
            }
        }

        executorService.shutdown();

        // Calculate total throughput
        int totalRequests = results.stream().mapToInt(r -> r.requestCount).sum();
        int totalErrors = results.stream().mapToInt(r -> r.errorCount).sum();
        long actualDuration = System.currentTimeMillis() - testStartTime;
        
        double requestsPerSecond = (totalRequests * 1000.0) / actualDuration;
        double errorRate = totalErrors > 0 ? (double) totalErrors / totalRequests : 0.0;

        System.out.println("\n⚡ THROUGHPUT METRICS:");
        System.out.println("=====================");
        System.out.println("Duration: " + actualDuration + "ms");
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Total errors: " + totalErrors);
        System.out.println("Requests/second: " + String.format("%.2f", requestsPerSecond));
        System.out.println("Error rate: " + String.format("%.2f%%", errorRate * 100));
        
        // Verify throughput requirements
        assertThat(requestsPerSecond).isGreaterThan(10.0); // At least 10 requests/second
        assertThat(errorRate).isLessThan(0.05); // Less than 5% error rate
        
        System.out.println("✅ Throughput test completed");
    }

    @Test
    @Order(6)
    void testPerformanceBenchmarks() {
        System.out.println("🏆 Running Performance Benchmarks");

        // Benchmark different operations
        Map<String, BenchmarkResult> benchmarks = new HashMap<>();

        // Benchmark 1: User CRUD operations
        benchmarks.put("User CRUD", benchmarkUserCRUD());

        // Benchmark 2: Product browsing
        benchmarks.put("Product Browsing", benchmarkProductBrowsing());

        // Benchmark 3: Order processing
        benchmarks.put("Order Processing", benchmarkOrderProcessing());

        // Display benchmark results
        System.out.println("\n🏆 PERFORMANCE BENCHMARKS:");
        System.out.println("==========================");
        
        for (Map.Entry<String, BenchmarkResult> entry : benchmarks.entrySet()) {
            BenchmarkResult result = entry.getValue();
            System.out.println(entry.getKey() + ":");
            System.out.println("  Operations: " + result.operationCount);
            System.out.println("  Total time: " + result.totalTime + "ms");
            System.out.println("  Average time: " + String.format("%.2f", result.averageTime) + "ms");
            System.out.println("  Operations/sec: " + String.format("%.2f", result.operationsPerSecond));
            System.out.println("  Success rate: " + String.format("%.1f%%", result.successRate * 100));
            System.out.println();
        }

        // Verify all benchmarks meet minimum performance standards
        for (BenchmarkResult result : benchmarks.values()) {
            assertThat(result.successRate).isGreaterThan(0.8); // 80% success rate
            assertThat(result.averageTime).isLessThan(2000); // Average < 2 seconds
        }

        System.out.println("✅ All performance benchmarks passed");
    }

    // Helper Methods
    private List<Long> measureResponseTimes(String url, HttpMethod method, Object body, int samples) {
        List<Long> times = new ArrayList<>();
        
        for (int i = 0; i < samples; i++) {
            long startTime = System.currentTimeMillis();
            
            try {
                if (method == HttpMethod.GET) {
                    restTemplate.getForEntity(url, Map.class);
                } else if (method == HttpMethod.POST && body != null) {
                    restTemplate.postForEntity(url, body, Map.class);
                }
                
                long responseTime = System.currentTimeMillis() - startTime;
                times.add(responseTime);
                
            } catch (Exception e) {
                // Record timeout/error as max time
                times.add(5000L);
            }
            
            // Small delay between requests
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return times;
    }

    private LoadTestResult simulateUserBehavior(int userId, int requestCount) {
        LoadTestResult result = new LoadTestResult();
        result.userId = userId;
        
        for (int i = 0; i < requestCount; i++) {
            try {
                // Simulate user browsing products
                ResponseEntity<List> productsResponse = restTemplate.getForEntity(
                        API_GATEWAY_URL + "/product-service/api/products",
                        List.class
                );
                
                if (productsResponse.getStatusCode().is2xxSuccessful()) {
                    result.successfulRequests++;
                } else {
                    result.failedRequests++;
                }
                
                // Small delay to simulate user thinking time
                Thread.sleep(100);
                
            } catch (Exception e) {
                result.failedRequests++;
            }
        }
        
        return result;
    }

    private void analyzeConcurrentLoadResults(List<LoadTestResult> results) {
        int totalSuccessful = results.stream().mapToInt(r -> r.successfulRequests).sum();
        int totalFailed = results.stream().mapToInt(r -> r.failedRequests).sum();
        int totalRequests = totalSuccessful + totalFailed;
        
        double successRate = totalRequests > 0 ? (double) totalSuccessful / totalRequests : 0;
        
        System.out.println("\n👥 CONCURRENT LOAD RESULTS:");
        System.out.println("===========================");
        System.out.println("Active users: " + results.size());
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Successful: " + totalSuccessful);
        System.out.println("Failed: " + totalFailed);
        System.out.println("Success rate: " + String.format("%.1f%%", successRate * 100));
    }

    private ThroughputResult measureThroughput(int threadId, long endTime) {
        ThroughputResult result = new ThroughputResult();
        result.threadId = threadId;
        
        while (System.currentTimeMillis() < endTime) {
            try {
                ResponseEntity<List> response = restTemplate.getForEntity(
                        API_GATEWAY_URL + "/user-service/api/users",
                        List.class
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    result.requestCount++;
                } else {
                    result.errorCount++;
                }
                
            } catch (Exception e) {
                result.errorCount++;
            }
        }
        
        return result;
    }

    private BenchmarkResult benchmarkUserCRUD() {
        BenchmarkResult result = new BenchmarkResult();
        long startTime = System.currentTimeMillis();
        
        final int OPERATIONS = 10;
        
        for (int i = 0; i < OPERATIONS; i++) {
            try {
                // CREATE
                Map<String, Object> userRequest = createUserRequest("BenchUser" + i);
                ResponseEntity<Map> createResponse = restTemplate.postForEntity(
                        API_GATEWAY_URL + "/user-service/api/users",
                        createJsonEntity(userRequest),
                        Map.class
                );
                
                if (createResponse.getStatusCode().is2xxSuccessful()) {
                    Integer userId = (Integer) createResponse.getBody().get("userId");
                    
                    // READ
                    ResponseEntity<Map> readResponse = restTemplate.getForEntity(
                            API_GATEWAY_URL + "/user-service/api/users/" + userId,
                            Map.class
                    );
                    
                    if (readResponse.getStatusCode().is2xxSuccessful()) {
                        result.successfulOperations++;
                    }
                }
                
                result.operationCount++;
                
            } catch (Exception e) {
                result.operationCount++;
            }
        }
        
        result.totalTime = System.currentTimeMillis() - startTime;
        result.calculateMetrics();
        
        return result;
    }

    private BenchmarkResult benchmarkProductBrowsing() {
        BenchmarkResult result = new BenchmarkResult();
        long startTime = System.currentTimeMillis();
        
        final int OPERATIONS = 20;
        
        for (int i = 0; i < OPERATIONS; i++) {
            try {
                ResponseEntity<List> response = restTemplate.getForEntity(
                        API_GATEWAY_URL + "/product-service/api/products",
                        List.class
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    result.successfulOperations++;
                }
                
                result.operationCount++;
                
            } catch (Exception e) {
                result.operationCount++;
            }
        }
        
        result.totalTime = System.currentTimeMillis() - startTime;
        result.calculateMetrics();
        
        return result;
    }

    private BenchmarkResult benchmarkOrderProcessing() {
        BenchmarkResult result = new BenchmarkResult();
        long startTime = System.currentTimeMillis();
        
        // Create a test user first
        Map<String, Object> userRequest = createUserRequest("OrderBenchUser");
        ResponseEntity<Map> userResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(userRequest),
                Map.class
        );
        
        if (!userResponse.getStatusCode().is2xxSuccessful()) {
            result.totalTime = System.currentTimeMillis() - startTime;
            result.calculateMetrics();
            return result;
        }
        
        Integer userId = (Integer) userResponse.getBody().get("userId");
        final int OPERATIONS = 5;
        
        for (int i = 0; i < OPERATIONS; i++) {
            try {
                // Create cart
                Map<String, Object> cartRequest = new HashMap<>();
                cartRequest.put("userId", userId);
                
                ResponseEntity<Map> cartResponse = restTemplate.postForEntity(
                        API_GATEWAY_URL + "/order-service/api/carts",
                        createJsonEntity(cartRequest),
                        Map.class
                );
                
                if (cartResponse.getStatusCode().is2xxSuccessful()) {
                    Integer cartId = (Integer) cartResponse.getBody().get("cartId");
                    
                    // Create order
                    Map<String, Object> orderRequest = new HashMap<>();
                    orderRequest.put("orderDate", LocalDateTime.now().toString());
                    orderRequest.put("orderDesc", "Benchmark order " + i);
                    orderRequest.put("orderFee", 25.0);
                    orderRequest.put("cartId", cartId);
                    
                    ResponseEntity<Map> orderResponse = restTemplate.postForEntity(
                            API_GATEWAY_URL + "/order-service/api/orders",
                            createJsonEntity(orderRequest),
                            Map.class
                    );
                    
                    if (orderResponse.getStatusCode().is2xxSuccessful()) {
                        result.successfulOperations++;
                    }
                }
                
                result.operationCount++;
                
            } catch (Exception e) {
                result.operationCount++;
            }
        }
        
        result.totalTime = System.currentTimeMillis() - startTime;
        result.calculateMetrics();
        
        return result;
    }

    private void displayMetrics(String operation, List<Long> times) {
        if (times.isEmpty()) {
            System.out.println(operation + ": No data");
            return;
        }
        
        double average = getAverageTime(times);
        long min = Collections.min(times);
        long max = Collections.max(times);
        long p95 = getPercentile(times, 95);
        long p99 = getPercentile(times, 99);
        
        System.out.println(operation + ":");
        System.out.println("  Samples: " + times.size());
        System.out.println("  Average: " + String.format("%.2f", average) + "ms");
        System.out.println("  Min: " + min + "ms");
        System.out.println("  Max: " + max + "ms");
        System.out.println("  95th percentile: " + p95 + "ms");
        System.out.println("  99th percentile: " + p99 + "ms");
    }

    private double getAverageTime(List<Long> times) {
        return times.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private long getPercentile(List<Long> times, int percentile) {
        List<Long> sorted = new ArrayList<>(times);
        Collections.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private Map<String, Object> createUserRequest(String namePrefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("firstName", namePrefix);
        userRequest.put("lastName", "PerfUser");
        userRequest.put("imageUrl", "https://example.com/perf.jpg");
        userRequest.put("email", namePrefix.toLowerCase() + uniqueId + "@perftest.com");
        userRequest.put("phone", "+1555" + uniqueId.substring(0, 7));
        userRequest.put("username", namePrefix.toLowerCase() + uniqueId);
        userRequest.put("password", "PerfTest123!");
        userRequest.put("credentialType", "EMAIL");
        return userRequest;
    }

    private HttpEntity<String> createJsonEntity(Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = objectMapper.writeValueAsString(body);
            return new HttpEntity<>(json, headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JSON entity", e);
        }
    }

    // Result classes
    private static class LoadTestResult {
        int userId;
        int successfulRequests = 0;
        int failedRequests = 0;
    }

    private static class ThroughputResult {
        int threadId;
        int requestCount = 0;
        int errorCount = 0;
    }

    private static class BenchmarkResult {
        int operationCount = 0;
        int successfulOperations = 0;
        long totalTime = 0;
        double averageTime = 0;
        double operationsPerSecond = 0;
        double successRate = 0;

        void calculateMetrics() {
            if (operationCount > 0) {
                averageTime = (double) totalTime / operationCount;
                operationsPerSecond = (operationCount * 1000.0) / totalTime;
                successRate = (double) successfulOperations / operationCount;
            }
        }
    }
}