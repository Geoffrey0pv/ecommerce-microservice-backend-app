package com.selimhorri.app.e2e.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class for JWT authentication in E2E tests
 * Handles user registration and authentication to obtain JWT tokens
 */
public class JwtAuthHelper {

    private final TestRestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private String jwtToken;
    private String testUsername;
    private String testPassword;

    public JwtAuthHelper(String baseUrl) {
        this.restTemplate = new TestRestTemplate();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.testPassword = "TestSecure123!";
    }

    /**
     * Creates a test user and authenticates to get JWT token
     * Returns the JWT token to be used in subsequent requests
     */
    public String authenticateAndGetToken() {
        if (jwtToken != null) {
            return jwtToken; // Return cached token if already authenticated
        }

        // 1. Create a unique test user
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        testUsername = "e2etest" + uniqueId;
        
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("credentialType", "EMAIL");
        userRequest.put("firstName", "E2ETest");
        userRequest.put("lastName", "User");
        userRequest.put("username", testUsername);
        userRequest.put("password", testPassword);
        userRequest.put("email", testUsername + "@e2etest.com");
        userRequest.put("phone", "+1555" + uniqueId);
        userRequest.put("imageUrl", "https://example.com/e2e.jpg");

        try {
            // Register user (now permitAll in SecurityConfig)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> userEntity = new HttpEntity<>(userRequest, headers);
            
            ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                baseUrl + "/app/user-service/api/users",
                userEntity,
                String.class
            );

            if (!registerResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to register test user: " + registerResponse.getStatusCode() 
                    + " - " + registerResponse.getBody());
            }

            System.out.println("✅ Test user created: " + testUsername);

            // 2. Authenticate to get JWT token
            Map<String, String> authRequest = new HashMap<>();
            authRequest.put("username", testUsername);
            authRequest.put("password", testPassword);

            HttpEntity<Map<String, String>> authEntity = new HttpEntity<>(authRequest, headers);
            
            ResponseEntity<String> authResponse = restTemplate.postForEntity(
                baseUrl + "/app/api/authenticate",
                authEntity,
                String.class
            );

            if (!authResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to authenticate test user: " + authResponse.getStatusCode()
                    + " - " + authResponse.getBody());
            }

            // 3. Extract JWT token from response
            JsonNode jsonNode = objectMapper.readTree(authResponse.getBody());
            jwtToken = jsonNode.get("jwtToken").asText();
            
            System.out.println("✅ JWT token obtained for user: " + testUsername);
            return jwtToken;

        } catch (Exception e) {
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates HttpHeaders with JWT authentication
     */
    public HttpHeaders createAuthHeaders() {
        if (jwtToken == null) {
            authenticateAndGetToken();
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + jwtToken);
        return headers;
    }

    /**
     * Creates HttpEntity with JSON body and JWT authentication
     */
    public <T> HttpEntity<T> createAuthEntity(T body) {
        return new HttpEntity<>(body, createAuthHeaders());
    }

    /**
     * Creates HttpEntity with only JWT authentication headers (no body)
     */
    public HttpEntity<Void> createAuthEntity() {
        return new HttpEntity<>(createAuthHeaders());
    }

    public String getTestUsername() {
        return testUsername;
    }

    public String getJwtToken() {
        return jwtToken;
    }
}
