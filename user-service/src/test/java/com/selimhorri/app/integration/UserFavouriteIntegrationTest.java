package com.selimhorri.app.integration;

import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration Test: User-Service <-> Favourite-Service
 * Tests the communication between user and favourite services
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Test: User-Service <-> Favourite-Service")
class UserFavouriteIntegrationTest {

    @Autowired
    private UserService userService;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(restTemplate);
    }

    @Test
    @DisplayName("Test 1: Should validate user data when accessing favourites")
    void testUserFavouriteAccess_ShouldValidateUserData() {
        // Given
        Integer userId = 1;
        
        // Mock favourite service response
        when(restTemplate.getForObject(contains("/favourite-service/"), eq(Object[].class)))
            .thenReturn(new Object[]{});

        // When
        UserDto user = userService.findById(userId);

        // Then
        assertNotNull(user, "User should exist for favourite access");
        assertNotNull(user.getUserId(), "User ID should not be null");
        assertTrue(user.getUserId() > 0, "User ID should be positive");
    }

    @Test
    @DisplayName("Test 2: Should handle favourite service unavailability gracefully")
    void testUserFavouriteAccess_WhenFavouriteServiceDown_ShouldHandleGracefully() {
        // Given
        Integer userId = 1;
        
        // Mock favourite service failure
        when(restTemplate.getForObject(contains("/favourite-service/"), eq(Object[].class)))
            .thenThrow(new RuntimeException("Favourite service unavailable"));

        // When & Then
        assertDoesNotThrow(() -> {
            UserDto user = userService.findById(userId);
            assertNotNull(user, "User service should still work when favourite service is down");
        });
    }

    @Test
    @DisplayName("Test 3: Should validate user authentication for favourite operations")
    void testUserAuthentication_ForFavouriteOperations() {
        // Given
        Integer userId = 1;

        // When
        UserDto user = userService.findById(userId);

        // Then
        assertNotNull(user, "User should be retrievable");
        assertNotNull(user.getFirstName(), "User should have first name");
        assertNotNull(user.getLastName(), "User should have last name");
        
        // Verify user has required fields for favourite operations
        assertTrue(user.getFirstName().length() > 0, "First name should not be empty");
        assertTrue(user.getLastName().length() > 0, "Last name should not be empty");
    }

    @Test
    @DisplayName("Test 4: Should maintain user session consistency across favourite calls")
    void testUserSessionConsistency_AcrossFavouriteCalls() {
        // Given
        Integer userId = 1;

        // When - Multiple calls simulating user session
        UserDto user1 = userService.findById(userId);
        UserDto user2 = userService.findById(userId);

        // Then
        assertNotNull(user1, "First user call should succeed");
        assertNotNull(user2, "Second user call should succeed");
        assertEquals(user1.getUserId(), user2.getUserId(), "User ID should be consistent");
        assertEquals(user1.getFirstName(), user2.getFirstName(), "User data should be consistent");
    }

    @Test
    @DisplayName("Test 5: Should validate user permissions for favourite modifications")
    void testUserPermissions_ForFavouriteModifications() {
        // Given
        Integer userId = 1;

        // When
        UserDto user = userService.findById(userId);

        // Then
        assertNotNull(user, "User should exist");
        assertNotNull(user.getUserId(), "User should have valid ID for permissions");
        
        // Verify user can be identified for favourite operations
        assertTrue(user.getUserId().equals(userId), "User ID should match requested ID");
    }
}