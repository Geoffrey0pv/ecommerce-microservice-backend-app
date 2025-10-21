package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E Test: User Registration and Authentication Flow
 * Tests complete user journey from registration to profile management
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("User Registration Flow E2E Tests")
public class UserRegistrationFlowE2ETest {
    
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private String apiGatewayUrl;
    private String uniqueId;
    private Map<String, Object> testUser;
    
    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(java.time.Duration.ofSeconds(10))
                .setReadTimeout(java.time.Duration.ofSeconds(30))
                .build();
        
        objectMapper = new ObjectMapper();
        apiGatewayUrl = System.getProperty("api.gateway.url", "http://localhost:8100");
        uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        // Setup test user data
        testUser = new HashMap<>();
        testUser.put("firstName", "E2ETest" + uniqueId);
        testUser.put("lastName", "User");
        testUser.put("imageUrl", "https://example.com/e2e.jpg");
        testUser.put("email", "e2etest" + uniqueId + "@example.com");
        testUser.put("phone", "+1555" + uniqueId.substring(0, 7));
        testUser.put("username", "e2etest" + uniqueId);
        testUser.put("password", "E2ETest123!");
        testUser.put("credentialType", "EMAIL");
    }
    
    @Test
    @DisplayName("Complete User Registration Flow")
    void testCompleteUserRegistrationFlow() {
        System.out.println("🚀 Starting Complete User Registration Flow Test");
        
        // STEP 1: Register new user
        System.out.println("📝 Step 1: User Registration");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> registrationRequest = new HttpEntity<>(testUser, headers);
        
        ResponseEntity<String> registrationResponse = restTemplate.postForEntity(
                apiGatewayUrl + "/user-service/api/users",
                registrationRequest,
                String.class
        );
        
        // Assert registration was successful
        assertThat(registrationResponse.getStatusCode())
                .as("User registration should be successful")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        
        // Parse response and extract user ID
        JsonNode registrationData = parseJsonResponse(registrationResponse.getBody());
        assertThat(registrationData.has("userId"))
                .as("Registration response should contain userId")
                .isTrue();
        
        String userId = registrationData.get("userId").asText();
        assertThat(userId).as("User ID should not be null or empty").isNotBlank();
        
        assertThat(registrationData.get("firstName").asText())
                .as("Returned firstName should match input")
                .isEqualTo(testUser.get("firstName"));
        
        assertThat(registrationData.get("email").asText())
                .as("Returned email should match input")
                .isEqualTo(testUser.get("email"));
        
        System.out.println("✅ User registered successfully with ID: " + userId);
        
        // STEP 2: Verify user can be retrieved
        System.out.println("🔍 Step 2: User Retrieval Verification");
        
        ResponseEntity<String> getUserResponse = restTemplate.getForEntity(
                apiGatewayUrl + "/user-service/api/users/" + userId,
                String.class
        );
        
        assertThat(getUserResponse.getStatusCode())
                .as("User retrieval should be successful")
                .isEqualTo(HttpStatus.OK);
        
        JsonNode retrievedUser = parseJsonResponse(getUserResponse.getBody());
        assertThat(retrievedUser.get("userId").asText())
                .as("Retrieved user ID should match")
                .isEqualTo(userId);
        
        assertThat(retrievedUser.get("email").asText())
                .as("Retrieved email should match")
                .isEqualTo(testUser.get("email"));
        
        System.out.println("✅ User retrieval verified successfully");
        
        // STEP 3: Test user profile update
        System.out.println("📝 Step 3: User Profile Update");
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("userId", userId);
        updateData.put("firstName", "Updated" + uniqueId);
        updateData.put("lastName", "UpdatedUser");
        updateData.put("email", testUser.get("email")); // Keep original email
        updateData.put("phone", "+1999" + uniqueId.substring(0, 7));
        updateData.put("username", testUser.get("username"));
        updateData.put("imageUrl", "https://example.com/updated_avatar.jpg");
        
        HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updateData, headers);
        
        ResponseEntity<String> updateResponse = restTemplate.exchange(
                apiGatewayUrl + "/user-service/api/users",
                HttpMethod.PUT,
                updateRequest,
                String.class
        );
        
        assertThat(updateResponse.getStatusCode())
                .as("User update should be successful")
                .isEqualTo(HttpStatus.OK);
        
        // STEP 4: Verify updates were applied
        System.out.println("🔍 Step 4: Update Verification");
        
        ResponseEntity<String> getUpdatedResponse = restTemplate.getForEntity(
                apiGatewayUrl + "/user-service/api/users/" + userId,
                String.class
        );
        
        assertThat(getUpdatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        JsonNode updatedUser = parseJsonResponse(getUpdatedResponse.getBody());
        assertThat(updatedUser.get("firstName").asText())
                .as("Updated firstName should be applied")
                .isEqualTo(updateData.get("firstName"));
        
        assertThat(updatedUser.get("phone").asText())
                .as("Updated phone should be applied")
                .isEqualTo(updateData.get("phone"));
        
        assertThat(updatedUser.get("imageUrl").asText())
                .as("Updated imageUrl should be applied")
                .isEqualTo(updateData.get("imageUrl"));
        
        System.out.println("✅ User profile update verified successfully");
        
        System.out.println("🎉 Complete User Registration Flow Test PASSED!");
        System.out.println("   User ID: " + userId);
    }
    
    @Test
    @DisplayName("Duplicate User Registration Prevention")
    void testDuplicateUserRegistrationPrevention() {
        System.out.println("🛡️ Testing Duplicate Registration Prevention");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(testUser, headers);
        
        // First registration should succeed
        ResponseEntity<String> firstResponse = restTemplate.postForEntity(
                apiGatewayUrl + "/user-service/api/users",
                request,
                String.class
        );
        
        assertThat(firstResponse.getStatusCode())
                .as("First registration should succeed")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        
        System.out.println("✅ First registration successful");
        
        // Second registration with same data should fail
        try {
            ResponseEntity<String> duplicateResponse = restTemplate.postForEntity(
                    apiGatewayUrl + "/user-service/api/users",
                    request,
                    String.class
            );
            
            // If no exception, check that it's an error status
            assertThat(duplicateResponse.getStatusCode())
                    .as("Duplicate registration should fail with error status")
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_ENTITY);
            
        } catch (HttpClientErrorException e) {
            // Expected - duplicate should cause client error
            assertThat(e.getStatusCode())
                    .as("Duplicate registration should cause client error")
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_ENTITY);
            
            System.out.println("✅ Duplicate registration properly rejected: " + e.getStatusCode());
        }
        
        System.out.println("🎉 Duplicate Registration Prevention Test PASSED!");
    }
    
    @Test
    @DisplayName("User Data Validation")
    void testUserDataValidation() {
        System.out.println("✅ Testing User Data Validation");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Test with invalid email
        Map<String, Object> invalidUser = new HashMap<>(testUser);
        invalidUser.put("email", "invalid-email-format");
        
        HttpEntity<Map<String, Object>> invalidRequest = new HttpEntity<>(invalidUser, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    apiGatewayUrl + "/user-service/api/users",
                    invalidRequest,
                    String.class
            );
            
            // If no exception, should be an error status
            assertThat(response.getStatusCode())
                    .as("Invalid email should be rejected")
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
            
        } catch (HttpClientErrorException e) {
            // Expected for validation errors
            assertThat(e.getStatusCode())
                    .as("Invalid data should cause validation error")
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
            
            System.out.println("✅ Invalid email properly rejected: " + e.getStatusCode());
        }
        
        // Test with missing required field
        Map<String, Object> incompleteUser = new HashMap<>();
        incompleteUser.put("firstName", "Incomplete");
        // Missing other required fields
        
        HttpEntity<Map<String, Object>> incompleteRequest = new HttpEntity<>(incompleteUser, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    apiGatewayUrl + "/user-service/api/users",
                    incompleteRequest,
                    String.class
            );
            
            assertThat(response.getStatusCode())
                    .as("Incomplete user data should be rejected")
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
            
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode())
                    .as("Incomplete data should cause validation error")
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
            
            System.out.println("✅ Incomplete data properly rejected: " + e.getStatusCode());
        }
        
        System.out.println("🎉 User Data Validation Test PASSED!");
    }
    
    private JsonNode parseJsonResponse(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            fail("Failed to parse JSON response: " + responseBody, e);
            return null;
        }
    }
}