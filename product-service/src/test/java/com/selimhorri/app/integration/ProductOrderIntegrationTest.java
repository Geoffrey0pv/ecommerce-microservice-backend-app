package com.selimhorri.app.integration;

import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration Test: Product-Service <-> Order-Service
 * Tests the communication between product and order services
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Test: Product-Service <-> Order-Service")
class ProductOrderIntegrationTest {

    @Autowired
    private ProductService productService;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(restTemplate);
    }

    @Test
    @DisplayName("Test 1: Should validate product availability for order placement")
    void testProductAvailability_ForOrderPlacement() {
        // Given
        Integer productId = 1;
        
        // Mock order service response
        when(restTemplate.getForObject(contains("/order-service/"), eq(Object[].class)))
            .thenReturn(new Object[]{});

        // When
        ProductDto product = productService.findById(productId);

        // Then
        assertNotNull(product, "Product should exist for order placement");
        assertNotNull(product.getProductId(), "Product ID should not be null");
        assertTrue(product.getQuantity() >= 0, "Product quantity should be non-negative");
    }

    @Test
    @DisplayName("Test 2: Should handle order service communication failures")
    void testOrderServiceCommunication_WhenServiceDown_ShouldHandleGracefully() {
        // Given
        Integer productId = 1;
        
        // Mock order service failure
        when(restTemplate.getForObject(contains("/order-service/"), eq(Object[].class)))
            .thenThrow(new RuntimeException("Order service unavailable"));

        // When & Then
        assertDoesNotThrow(() -> {
            ProductDto product = productService.findById(productId);
            assertNotNull(product, "Product service should still work when order service is down");
        });
    }

    @Test
    @DisplayName("Test 3: Should validate product data consistency for orders")
    void testProductDataConsistency_ForOrders() {
        // Given
        Integer productId = 1;

        // When - Multiple calls simulating order validation
        ProductDto product1 = productService.findById(productId);
        ProductDto product2 = productService.findById(productId);

        // Then
        assertNotNull(product1, "First product call should succeed");
        assertNotNull(product2, "Second product call should succeed");
        assertEquals(product1.getProductId(), product2.getProductId(), "Product ID should be consistent");
        assertEquals(product1.getPriceUnit(), product2.getPriceUnit(), "Product price should be consistent");
        assertEquals(product1.getQuantity(), product2.getQuantity(), "Product quantity should be consistent");
    }

    @Test
    @DisplayName("Test 4: Should validate product stock levels for order processing")
    void testProductStock_ForOrderProcessing() {
        // Given
        Integer productId = 1;

        // When
        ProductDto product = productService.findById(productId);

        // Then
        assertNotNull(product, "Product should exist");
        assertNotNull(product.getQuantity(), "Product should have quantity information");
        
        // Validate stock information for order processing
        assertTrue(product.getQuantity() >= 0, "Stock should be non-negative");
        assertNotNull(product.getPriceUnit(), "Product should have price for order calculation");
        assertTrue(product.getPriceUnit() > 0, "Product price should be positive");
    }

    @Test
    @DisplayName("Test 5: Should validate product categories for order organization")
    void testProductCategories_ForOrderOrganization() {
        // Given - Get all products to test category consistency
        
        // When
        List<ProductDto> products = productService.findAll();

        // Then
        assertNotNull(products, "Products list should not be null");
        assertFalse(products.isEmpty(), "Should have products available");
        
        // Validate each product has proper category data for order organization
        for (ProductDto product : products) {
            assertNotNull(product.getProductId(), "Each product should have ID");
            assertNotNull(product.getProductTitle(), "Each product should have title");
            
            // Products should have sufficient data for order processing
            assertTrue(product.getProductTitle().length() > 0, "Product title should not be empty");
        }
    }
}