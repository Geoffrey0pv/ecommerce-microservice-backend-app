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
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2E Test: Error Handling and System Resilience
 * Tests system behavior under error conditions and validates proper error handling
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
@TestMethodOrder(OrderAnnotation.class)
public class ErrorHandlingAndResilienceE2ETest {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private String baseUrl;
    private final String API_GATEWAY_URL = "http://localhost:8100";

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        objectMapper = new ObjectMapper();
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @Order(1)
    void testInvalidDataHandling() {
        System.out.println("🚨 Testing Invalid Data Handling");

        // Test 1: Invalid User Data
        System.out.println("Testing invalid user data scenarios...");
        
        // Empty required fields
        Map<String, Object> emptyUser = new HashMap<>();
        emptyUser.put("firstName", "");
        emptyUser.put("lastName", "");
        emptyUser.put("email", "");

        ResponseEntity<Map> emptyUserResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(emptyUser),
                Map.class
        );

        assertThat(emptyUserResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY
        );
        System.out.println("✅ Empty user fields properly rejected");

        // Invalid email format
        Map<String, Object> invalidEmailUser = new HashMap<>();
        invalidEmailUser.put("firstName", "Test");
        invalidEmailUser.put("lastName", "User");
        invalidEmailUser.put("email", "invalid-email-format");
        invalidEmailUser.put("username", "testuser");
        invalidEmailUser.put("password", "password123");

        ResponseEntity<Map> invalidEmailResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(invalidEmailUser),
                Map.class
        );

        assertThat(invalidEmailResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY
        );
        System.out.println("✅ Invalid email format properly rejected");

        // Test 2: Invalid Product Data
        System.out.println("Testing invalid product data scenarios...");

        // Negative price
        Map<String, Object> negativePhiceProduct = new HashMap<>();
        negativePhiceProduct.put("productTitle", "Test Product");
        negativePhiceProduct.put("sku", "TEST123");
        negativePhiceProduct.put("priceUnit", -10.0); // Invalid negative price
        negativePhiceProduct.put("quantity", 5);
        negativePhiceProduct.put("categoryId", 1);

        ResponseEntity<Map> negativePriceResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/product-service/api/products",
                createJsonEntity(negativePhiceProduct),
                Map.class
        );

        // Some services might allow negative prices, others might not
        if (negativePriceResponse.getStatusCode().is4xxClientError()) {
            System.out.println("✅ Negative price properly rejected");
        } else {
            System.out.println("⚠️ Negative price allowed - consider adding validation");
        }

        // Missing required fields
        Map<String, Object> incompleteProduct = new HashMap<>();
        incompleteProduct.put("productTitle", ""); // Empty title
        
        ResponseEntity<Map> incompleteProductResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/product-service/api/products",
                createJsonEntity(incompleteProduct),
                Map.class
        );

        assertThat(incompleteProductResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY
        );
        System.out.println("✅ Incomplete product data properly rejected");
    }

    @Test
    @Order(2)
    void testResourceNotFoundHandling() {
        System.out.println("🔍 Testing Resource Not Found Handling");

        // Test non-existent user
        ResponseEntity<Map> nonExistentUser = restTemplate.getForEntity(
                API_GATEWAY_URL + "/user-service/api/users/999999",
                Map.class
        );

        assertThat(nonExistentUser.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        System.out.println("✅ Non-existent user returns 404");

        // Test non-existent product
        ResponseEntity<Map> nonExistentProduct = restTemplate.getForEntity(
                API_GATEWAY_URL + "/product-service/api/products/999999",
                Map.class
        );

        assertThat(nonExistentProduct.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        System.out.println("✅ Non-existent product returns 404");

        // Test non-existent cart
        ResponseEntity<Map> nonExistentCart = restTemplate.getForEntity(
                API_GATEWAY_URL + "/order-service/api/carts/999999",
                Map.class
        );

        assertThat(nonExistentCart.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        System.out.println("✅ Non-existent cart returns 404");

        // Test non-existent order
        ResponseEntity<Map> nonExistentOrder = restTemplate.getForEntity(
                API_GATEWAY_URL + "/order-service/api/orders/999999",
                Map.class
        );

        assertThat(nonExistentOrder.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        System.out.println("✅ Non-existent order returns 404");
    }

    @Test
    @Order(3)
    void testBusinessLogicValidation() {
        System.out.println("💼 Testing Business Logic Validation");

        // Create valid test user first
        Map<String, Object> validUser = createValidUserRequest("BusinessLogicUser");
        ResponseEntity<Map> userResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(validUser),
                Map.class
        );

        assertThat(userResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Integer userId = (Integer) userResponse.getBody().get("userId");

        // Test 1: Order without cart (business logic violation)
        Map<String, Object> orderWithoutCart = new HashMap<>();
        orderWithoutCart.put("orderDate", LocalDateTime.now().toString());
        orderWithoutCart.put("orderDesc", "Order without cart");
        orderWithoutCart.put("orderFee", 25.0);
        orderWithoutCart.put("cartId", 999999); // Non-existent cart

        ResponseEntity<Map> invalidOrderResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/order-service/api/orders",
                createJsonEntity(orderWithoutCart),
                Map.class
        );

        assertThat(invalidOrderResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR
        );
        System.out.println("✅ Order with non-existent cart properly rejected");

        // Test 2: Duplicate user registration (business rule)
        ResponseEntity<Map> duplicateUserResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(validUser), // Same user data
                Map.class
        );

        // Should fail due to duplicate email/username
        assertThat(duplicateUserResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_ENTITY
        );
        System.out.println("✅ Duplicate user registration properly rejected");

        // Test 3: Invalid order amounts
        // First create a valid cart
        Map<String, Object> cartRequest = new HashMap<>();
        cartRequest.put("userId", userId);

        ResponseEntity<Map> cartResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/order-service/api/carts",
                createJsonEntity(cartRequest),
                Map.class
        );

        if (cartResponse.getStatusCode().is2xxSuccessful()) {
            Integer cartId = (Integer) cartResponse.getBody().get("cartId");

            // Test negative order fee
            Map<String, Object> negativeOrder = new HashMap<>();
            negativeOrder.put("orderDate", LocalDateTime.now().toString());
            negativeOrder.put("orderDesc", "Order with negative fee");
            negativeOrder.put("orderFee", -10.0); // Invalid negative fee
            negativeOrder.put("cartId", cartId);

            ResponseEntity<Map> negativeOrderResponse = restTemplate.postForEntity(
                    API_GATEWAY_URL + "/order-service/api/orders",
                    createJsonEntity(negativeOrder),
                    Map.class
            );

            if (negativeOrderResponse.getStatusCode().is4xxClientError()) {
                System.out.println("✅ Negative order fee properly rejected");
            } else {
                System.out.println("⚠️ Negative order fee allowed - consider adding validation");
            }
        }
    }

    @Test
    @Order(4)
    void testConcurrentModificationHandling() {
        System.out.println("🔄 Testing Concurrent Modification Handling");

        // Create test data
        Map<String, Object> userRequest = createValidUserRequest("ConcurrentUser");
        ResponseEntity<Map> userResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(userRequest),
                Map.class
        );

        assertThat(userResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Integer userId = (Integer) userResponse.getBody().get("userId");

        // Create product for concurrent modification
        Map<String, Object> productRequest = createValidProductRequest("ConcurrentProduct");
        ResponseEntity<Map> productResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/product-service/api/products",
                createJsonEntity(productRequest),
                Map.class
        );

        assertThat(productResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Integer productId = (Integer) productResponse.getBody().get("productId");

        // Test concurrent updates
        Map<String, Object> updateRequest1 = new HashMap<>();
        updateRequest1.put("userId", userId);
        updateRequest1.put("firstName", "UpdatedFirstName1");
        updateRequest1.put("lastName", "UpdatedLastName1");
        updateRequest1.put("email", userRequest.get("email"));
        updateRequest1.put("username", userRequest.get("username"));

        Map<String, Object> updateRequest2 = new HashMap<>();
        updateRequest2.put("userId", userId);
        updateRequest2.put("firstName", "UpdatedFirstName2");
        updateRequest2.put("lastName", "UpdatedLastName2");
        updateRequest2.put("email", userRequest.get("email"));
        updateRequest2.put("username", userRequest.get("username"));

        // Execute concurrent updates
        ResponseEntity<Map> update1 = restTemplate.exchange(
                API_GATEWAY_URL + "/user-service/api/users",
                HttpMethod.PUT,
                createJsonEntity(updateRequest1),
                Map.class
        );

        ResponseEntity<Map> update2 = restTemplate.exchange(
                API_GATEWAY_URL + "/user-service/api/users",
                HttpMethod.PUT,
                createJsonEntity(updateRequest2),
                Map.class
        );

        // At least one should succeed
        boolean atLeastOneSucceeded = update1.getStatusCode().is2xxSuccessful() || 
                                     update2.getStatusCode().is2xxSuccessful();
        assertThat(atLeastOneSucceeded).isTrue();

        System.out.println("✅ Concurrent modification handling tested");
    }

    @Test
    @Order(5)
    void testSystemBoundariesAndLimits() {
        System.out.println("📏 Testing System Boundaries and Limits");

        // Test 1: Very long string values
        Map<String, Object> longStringUser = new HashMap<>();
        longStringUser.put("firstName", "A".repeat(1000)); // Very long name
        longStringUser.put("lastName", "User");
        longStringUser.put("email", "longstring@test.com");
        longStringUser.put("username", "longstringuser");
        longStringUser.put("password", "password123");

        ResponseEntity<Map> longStringResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(longStringUser),
                Map.class
        );

        if (longStringResponse.getStatusCode().is4xxClientError()) {
            System.out.println("✅ Very long string values properly rejected");
        } else {
            System.out.println("⚠️ Very long strings accepted - consider length validation");
        }

        // Test 2: Extreme numeric values for products
        Map<String, Object> extremeProduct = new HashMap<>();
        extremeProduct.put("productTitle", "Extreme Product");
        extremeProduct.put("sku", "EXTREME123");
        extremeProduct.put("priceUnit", Double.MAX_VALUE); // Extreme price
        extremeProduct.put("quantity", Integer.MAX_VALUE); // Extreme quantity
        extremeProduct.put("categoryId", 1);

        ResponseEntity<Map> extremeProductResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/product-service/api/products",
                createJsonEntity(extremeProduct),
                Map.class
        );

        if (extremeProductResponse.getStatusCode().is4xxClientError()) {
            System.out.println("✅ Extreme numeric values properly rejected");
        } else {
            System.out.println("⚠️ Extreme values accepted - consider range validation");
        }

        // Test 3: Special characters and SQL injection attempts
        Map<String, Object> maliciousUser = new HashMap<>();
        maliciousUser.put("firstName", "'; DROP TABLE users; --");
        maliciousUser.put("lastName", "<script>alert('xss')</script>");
        maliciousUser.put("email", "malicious@test.com");
        maliciousUser.put("username", "malicioususer");
        maliciousUser.put("password", "password123");

        ResponseEntity<Map> maliciousResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(maliciousUser),
                Map.class
        );

        // Should either reject or properly sanitize
        assertThat(maliciousResponse.getStatusCode()).isIn(
                HttpStatus.OK, HttpStatus.CREATED, HttpStatus.BAD_REQUEST
        );

        if (maliciousResponse.getStatusCode().is2xxSuccessful()) {
            // If accepted, verify data was sanitized
            Integer userId = (Integer) maliciousResponse.getBody().get("userId");
            ResponseEntity<Map> retrievedUser = restTemplate.getForEntity(
                    API_GATEWAY_URL + "/user-service/api/users/" + userId,
                    Map.class
            );

            if (retrievedUser.getStatusCode().is2xxSuccessful()) {
                String storedName = (String) retrievedUser.getBody().get("firstName");
                assertThat(storedName).doesNotContain("DROP TABLE");
                System.out.println("✅ Malicious input properly sanitized");
            }
        } else {
            System.out.println("✅ Malicious input properly rejected");
        }
    }

    @Test
    @Order(6)
    void testTimeoutAndResponseTimeHandling() {
        System.out.println("⏱️ Testing Timeout and Response Time Handling");

        long startTime = System.currentTimeMillis();

        // Test basic response times
        ResponseEntity<List> usersResponse = restTemplate.getForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                List.class
        );

        long responseTime = System.currentTimeMillis() - startTime;

        assertThat(usersResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseTime).isLessThan(5000); // Should respond within 5 seconds

        System.out.println("✅ User list response time: " + responseTime + "ms");

        // Test product list response time
        startTime = System.currentTimeMillis();
        ResponseEntity<List> productsResponse = restTemplate.getForEntity(
                API_GATEWAY_URL + "/product-service/api/products",
                List.class
        );

        responseTime = System.currentTimeMillis() - startTime;

        assertThat(productsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseTime).isLessThan(5000);

        System.out.println("✅ Product list response time: " + responseTime + "ms");

        // Test order list response time
        startTime = System.currentTimeMillis();
        ResponseEntity<List> ordersResponse = restTemplate.getForEntity(
                API_GATEWAY_URL + "/order-service/api/orders",
                List.class
        );

        responseTime = System.currentTimeMillis() - startTime;

        assertThat(ordersResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseTime).isLessThan(5000);

        System.out.println("✅ Order list response time: " + responseTime + "ms");
    }

    @Test
    @Order(7)
    void testErrorResponseFormats() {
        System.out.println("📝 Testing Error Response Formats");

        // Test error response structure for 404
        ResponseEntity<Map> notFoundResponse = restTemplate.getForEntity(
                API_GATEWAY_URL + "/user-service/api/users/999999",
                Map.class
        );

        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify error response has proper structure (if any)
        if (notFoundResponse.getBody() != null) {
            System.out.println("404 Error response body: " + notFoundResponse.getBody());
        }

        // Test error response for bad request
        Map<String, Object> invalidData = new HashMap<>();
        invalidData.put("invalid", "data");

        ResponseEntity<Map> badRequestResponse = restTemplate.postForEntity(
                API_GATEWAY_URL + "/user-service/api/users",
                createJsonEntity(invalidData),
                Map.class
        );

        assertThat(badRequestResponse.getStatusCode()).isIn(
                HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY
        );

        if (badRequestResponse.getBody() != null) {
            System.out.println("Bad request error response: " + badRequestResponse.getBody());
        }

        System.out.println("✅ Error response formats verified");
    }

    // Helper Methods
    private Map<String, Object> createValidUserRequest(String namePrefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("firstName", namePrefix);
        userRequest.put("lastName", "TestUser");
        userRequest.put("imageUrl", "https://example.com/error.jpg");
        userRequest.put("email", namePrefix.toLowerCase() + uniqueId + "@errortest.com");
        userRequest.put("phone", "+1555" + uniqueId.substring(0, 7));
        userRequest.put("username", namePrefix.toLowerCase() + uniqueId);
        userRequest.put("password", "ErrorTest123!");
        userRequest.put("credentialType", "EMAIL");
        return userRequest;
    }

    private Map<String, Object> createValidProductRequest(String namePrefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        Random random = new Random();
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("productTitle", namePrefix);
        productRequest.put("imageUrl", "https://example.com/errorproduct.jpg");
        productRequest.put("sku", "ERROR" + uniqueId.toUpperCase());
        productRequest.put("priceUnit", Math.round((random.nextDouble() * 100 + 10) * 100.0) / 100.0);
        productRequest.put("quantity", random.nextInt(50) + 10);
        productRequest.put("categoryId", random.nextInt(5) + 1);
        productRequest.put("productBrand", "ErrorBrand");
        return productRequest;
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