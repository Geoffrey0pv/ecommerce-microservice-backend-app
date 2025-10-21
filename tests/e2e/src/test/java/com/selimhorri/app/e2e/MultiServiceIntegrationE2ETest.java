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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Test: Multi-Service Integration Flow
 * Tests complex interactions between all microservices to validate system integration
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
@TestMethodOrder(OrderAnnotation.class)
public class MultiServiceIntegrationE2ETest {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private String baseUrl;
    private final String API_GATEWAY_URL = "http://localhost:8100";

    // Test data storage
    private final List<Integer> createdUserIds = new ArrayList<>();
    private final List<Integer> createdProductIds = new ArrayList<>();
    private final List<Integer> createdCartIds = new ArrayList<>();
    private final List<Integer> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        objectMapper = new ObjectMapper();
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @Order(1)
    void testCompleteSystemIntegrationWorkflow() {
        System.out.println("🚀 Starting Complete System Integration Test");

        // PHASE 1: User Service Integration
        System.out.println("📝 Phase 1: Multi-User Creation");
        List<Map<String, Object>> users = createMultipleUsers(3);
        assertThat(users).hasSize(3);
        
        for (Map<String, Object> user : users) {
            createdUserIds.add((Integer) user.get("userId"));
        }
        System.out.println("✅ Created " + users.size() + " users successfully");

        // PHASE 2: Product Service Integration
        System.out.println("📦 Phase 2: Product Catalog Setup");
        List<Map<String, Object>> products = createMultipleProducts(5);
        assertThat(products).hasSize(5);
        
        for (Map<String, Object> product : products) {
            createdProductIds.add((Integer) product.get("productId"));
        }
        System.out.println("✅ Created " + products.size() + " products successfully");

        // PHASE 3: Order Service Integration - Multiple Carts
        System.out.println("🛒 Phase 3: Multi-User Cart Management");
        for (Integer userId : createdUserIds) {
            Map<String, Object> cartRequest = new HashMap<>();
            cartRequest.put("userId", userId);

            ResponseEntity<Map> cartResponse = restTemplate.postForEntity(
                    API_GATEWAY_URL + "/order-service/api/carts",
                    createJsonEntity(cartRequest),
                    Map.class
            );

            assertThat(cartResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
            assertThat(cartResponse.getBody()).isNotNull();
            
            Integer cartId = (Integer) cartResponse.getBody().get("cartId");
            createdCartIds.add(cartId);
        }
        System.out.println("✅ Created " + createdCartIds.size() + " carts for users");

        // PHASE 4: Cross-Service Order Creation
        System.out.println("📋 Phase 4: Cross-Service Order Processing");
        for (int i = 0; i < createdUserIds.size(); i++) {
            Integer cartId = createdCartIds.get(i);
            
            Map<String, Object> orderRequest = new HashMap<>();
            orderRequest.put("orderDate", LocalDateTime.now().toString());
            orderRequest.put("orderDesc", "Integration test order for cart " + cartId);
            orderRequest.put("orderFee", 50.0 + (i * 10)); // Different fees per order
            orderRequest.put("cartId", cartId);

            ResponseEntity<Map> orderResponse = restTemplate.postForEntity(
                    API_GATEWAY_URL + "/order-service/api/orders",
                    createJsonEntity(orderRequest),
                    Map.class
            );

            assertThat(orderResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
            assertThat(orderResponse.getBody()).isNotNull();
            
            Integer orderId = (Integer) orderResponse.getBody().get("orderId");
            createdOrderIds.add(orderId);
        }
        System.out.println("✅ Created " + createdOrderIds.size() + " orders successfully");

        // PHASE 5: Data Consistency Verification
        System.out.println("🔍 Phase 5: Cross-Service Data Consistency");
        verifyDataConsistency();

        System.out.println("🎉 Complete System Integration Test PASSED!");
        printTestSummary();
    }

    @Test
    @Order(2)
    void testConcurrentMultiServiceOperations() {
        System.out.println("⚡ Testing Concurrent Multi-Service Operations");

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Concurrent user creation
        for (int i = 0; i < 3; i++) {
            final int userIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Map<String, Object> userRequest = createUserRequest("ConcurrentUser" + userIndex);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        API_GATEWAY_URL + "/user-service/api/users",
                        createJsonEntity(userRequest),
                        Map.class
                );
                
                assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
                System.out.println("✅ Concurrent user " + userIndex + " created");
            }, executorService);
            
            futures.add(future);
        }

        // Concurrent product creation
        for (int i = 0; i < 3; i++) {
            final int productIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Map<String, Object> productRequest = createProductRequest("ConcurrentProduct" + productIndex);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        API_GATEWAY_URL + "/product-service/api/products",
                        createJsonEntity(productRequest),
                        Map.class
                );
                
                assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
                System.out.println("✅ Concurrent product " + productIndex + " created");
            }, executorService);
            
            futures.add(future);
        }

        // Wait for all concurrent operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        System.out.println("✅ All concurrent operations completed successfully");
    }

    @Test
    @Order(3)
    void testServiceResilienceAndErrorHandling() {
        System.out.println("🛡️ Testing Service Resilience and Error Handling");

        // Test 1: Invalid user creation
        Map<String, Object> invalidUser = new HashMap<>();
        invalidUser.put("firstName", ""); // Invalid - empty name
        invalidUser.put("email", "invalid-email"); // Invalid email format

        ResponseEntity<Map> invalidUserResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(invalidUser),
                Map.class
        );

        assertThat(invalidUserResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY
        );
        System.out.println("✅ Invalid user creation properly rejected");

        // Test 2: Non-existent resource access
        ResponseEntity<Map> nonExistentUser = restTemplate.getForEntity(
                API_GATEWAY_URL + "/user-service/api/users/99999",
                Map.class
        );

        assertThat(nonExistentUser.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        System.out.println("✅ Non-existent user properly returns 404");

        // Test 3: Invalid order creation (non-existent cart)
        Map<String, Object> invalidOrder = new HashMap<>();
        invalidOrder.put("orderDate", LocalDateTime.now().toString());
        invalidOrder.put("orderDesc", "Test order with invalid cart");
        invalidOrder.put("orderFee", 25.0);
        invalidOrder.put("cartId", 99999); // Non-existent cart

        ResponseEntity<Map> invalidOrderResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/order-service/api/orders",
                createJsonEntity(invalidOrder),
                Map.class
        );

        assertThat(invalidOrderResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR
        );
        System.out.println("✅ Invalid order creation properly handled");

        System.out.println("✅ Service resilience tests completed");
    }

    @Test
    @Order(4)
    void testDataIntegrityAcrossServices() {
        System.out.println("🔒 Testing Data Integrity Across Services");

        // Create test data
        Map<String, Object> user = createSingleUser("IntegrityTestUser");
        Integer userId = (Integer) user.get("userId");

        Map<String, Object> product = createSingleProduct("IntegrityTestProduct");
        Integer productId = (Integer) product.get("productId");

        // Create cart and order
        Map<String, Object> cartRequest = new HashMap<>();
        cartRequest.put("userId", userId);

        ResponseEntity<Map> cartResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/order-service/api/carts",
                createJsonEntity(cartRequest),
                Map.class
        );

        assertThat(cartResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Integer cartId = (Integer) cartResponse.getBody().get("cartId");

        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("orderDate", LocalDateTime.now().toString());
        orderRequest.put("orderDesc", "Data integrity test order");
        orderRequest.put("orderFee", 75.0);
        orderRequest.put("cartId", cartId);

        ResponseEntity<Map> orderResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/order-service/api/orders",
                createJsonEntity(orderRequest),
                Map.class
        );

        assertThat(orderResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Integer orderId = (Integer) orderResponse.getBody().get("orderId");

        // Verify data integrity
        // 1. User still exists and is correct
        ResponseEntity<Map> userCheck = restTemplate.getForEntity(
                API_GATEWAY_URL + "/user-service/api/users/" + userId,
                Map.class
        );
        assertThat(userCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userCheck.getBody().get("firstName")).isEqualTo("IntegrityTestUser");

        // 2. Product still exists and is correct
        ResponseEntity<Map> productCheck = restTemplate.getForEntity(
                API_GATEWAY_URL + "/product-service/api/products/" + productId,
                Map.class
        );
        assertThat(productCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(productCheck.getBody().get("productTitle")).isEqualTo("IntegrityTestProduct");

        // 3. Cart still exists and belongs to correct user
        ResponseEntity<Map> cartCheck = restTemplate.getForEntity(
                API_GATEWAY_URL + "/order-service/api/carts/" + cartId,
                Map.class
        );
        assertThat(cartCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cartCheck.getBody().get("userId")).isEqualTo(userId);

        // 4. Order exists and references correct cart
        ResponseEntity<Map> orderCheck = restTemplate.getForEntity(
                API_GATEWAY_URL + "/order-service/api/orders/" + orderId,
                Map.class
        );
        assertThat(orderCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderCheck.getBody().get("cartId")).isEqualTo(cartId);

        System.out.println("✅ Data integrity verified across all services");
    }

    @Test
    @Order(5)
    void testHighVolumeDataOperations() {
        System.out.println("📊 Testing High Volume Data Operations");

        final int BULK_SIZE = 10;
        List<Integer> bulkUserIds = new ArrayList<>();
        List<Integer> bulkProductIds = new ArrayList<>();

        // Bulk user creation
        System.out.println("Creating " + BULK_SIZE + " users in bulk...");
        for (int i = 0; i < BULK_SIZE; i++) {
            Map<String, Object> user = createSingleUser("BulkUser" + i);
            bulkUserIds.add((Integer) user.get("userId"));
        }
        assertThat(bulkUserIds).hasSize(BULK_SIZE);

        // Bulk product creation
        System.out.println("Creating " + BULK_SIZE + " products in bulk...");
        for (int i = 0; i < BULK_SIZE; i++) {
            Map<String, Object> product = createSingleProduct("BulkProduct" + i);
            bulkProductIds.add((Integer) product.get("productId"));
        }
        assertThat(bulkProductIds).hasSize(BULK_SIZE);

        // Verify bulk retrieval operations
        System.out.println("Verifying bulk data retrieval...");
        
        ResponseEntity<List> allUsers = restTemplate.getForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                List.class
        );
        assertThat(allUsers.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allUsers.getBody()).hasSizeGreaterThanOrEqualTo(BULK_SIZE);

        ResponseEntity<List> allProducts = restTemplate.getForEntity(
                API_GATEWAY_URL + "/product-service/api/products",
                List.class
        );
        assertThat(allProducts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allProducts.getBody()).hasSizeGreaterThanOrEqualTo(BULK_SIZE);

        System.out.println("✅ High volume operations completed successfully");
        System.out.println("   - Created " + BULK_SIZE + " users");
        System.out.println("   - Created " + BULK_SIZE + " products");
        System.out.println("   - Verified bulk retrieval operations");
    }

    // Helper Methods
    private List<Map<String, Object>> createMultipleUsers(int count) {
        List<Map<String, Object>> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> user = createSingleUser("MultiUser" + i);
            users.add(user);
        }
        return users;
    }

    private List<Map<String, Object>> createMultipleProducts(int count) {
        List<Map<String, Object>> products = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> product = createSingleProduct("MultiProduct" + i);
            products.add(product);
        }
        return products;
    }

    private Map<String, Object> createSingleUser(String namePrefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> userRequest = createUserRequest(namePrefix + uniqueId);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(userRequest),
                Map.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> createSingleProduct(String namePrefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> productRequest = createProductRequest(namePrefix + uniqueId);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                API_GATEWAY_URL + "/product-service/api/products",
                createJsonEntity(productRequest),
                Map.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> createUserRequest(String namePrefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("firstName", namePrefix);
        userRequest.put("lastName", "TestUser");
        userRequest.put("imageUrl", "https://example.com/multi.jpg");
        userRequest.put("email", namePrefix.toLowerCase() + uniqueId + "@multitest.com");
        userRequest.put("phone", "+1555" + uniqueId.substring(0, 7));
        userRequest.put("username", namePrefix.toLowerCase() + uniqueId);
        userRequest.put("password", "MultiTest123!");
        userRequest.put("credentialType", "EMAIL");
        return userRequest;
    }

    private Map<String, Object> createProductRequest(String namePrefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Random random = new Random();
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("productTitle", namePrefix);
        productRequest.put("imageUrl", "https://example.com/multiproduct.jpg");
        productRequest.put("sku", "MULTI" + uniqueId.toUpperCase());
        productRequest.put("priceUnit", Math.round((random.nextDouble() * 100 + 10) * 100.0) / 100.0);
        productRequest.put("quantity", random.nextInt(50) + 10);
        productRequest.put("categoryId", random.nextInt(5) + 1);
        productRequest.put("productBrand", "MultiBrand");
        return productRequest;
    }

    private void verifyDataConsistency() {
        // Verify all created users still exist
        for (Integer userId : createdUserIds) {
            ResponseEntity<Map> userResponse = restTemplate.getForEntity(
                    API_GATEWAY_URL + "/user-service/api/users/" + userId,
                    Map.class
            );
            assertThat(userResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Verify all created products still exist
        for (Integer productId : createdProductIds) {
            ResponseEntity<Map> productResponse = restTemplate.getForEntity(
                    API_GATEWAY_URL + "/product-service/api/products/" + productId,
                    Map.class
            );
            assertThat(productResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Verify all created carts still exist
        for (Integer cartId : createdCartIds) {
            ResponseEntity<Map> cartResponse = restTemplate.getForEntity(
                    API_GATEWAY_URL + "/order-service/api/carts/" + cartId,
                    Map.class
            );
            assertThat(cartResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Verify all created orders still exist
        for (Integer orderId : createdOrderIds) {
            ResponseEntity<Map> orderResponse = restTemplate.getForEntity(
                    API_GATEWAY_URL + "/order-service/api/orders/" + orderId,
                    Map.class
            );
            assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        System.out.println("✅ Data consistency verified:");
        System.out.println("   - " + createdUserIds.size() + " users verified");
        System.out.println("   - " + createdProductIds.size() + " products verified");
        System.out.println("   - " + createdCartIds.size() + " carts verified");
        System.out.println("   - " + createdOrderIds.size() + " orders verified");
    }

    private void printTestSummary() {
        System.out.println("\n📊 INTEGRATION TEST SUMMARY:");
        System.out.println("=====================================");
        System.out.println("✅ Users created: " + createdUserIds.size());
        System.out.println("✅ Products created: " + createdProductIds.size());
        System.out.println("✅ Carts created: " + createdCartIds.size());
        System.out.println("✅ Orders created: " + createdOrderIds.size());
        System.out.println("✅ All cross-service integrations verified");
        System.out.println("✅ Data consistency maintained");
        System.out.println("=====================================");
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
}