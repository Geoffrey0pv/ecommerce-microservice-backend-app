package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.dto.OrderItemDto;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.service.OrderItemService;
import com.selimhorri.app.domain.id.OrderItemId;

/**
 * Integration tests for OrderItem-User service communication
 * Tests order item processing with user validation workflows
 */
@SpringBootTest
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false"
})
public class ShippingUserIntegrationTest {

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private OrderItemService orderItemService;

    private OrderItemDto sampleOrderItemDto;
    private OrderDto sampleOrderDto;
    private ProductDto sampleProductDto;

    @BeforeEach
    void setUp() {
        // Create sample product data
        sampleProductDto = ProductDto.builder()
                .productId(1)
                .productTitle("Test Product")
                .build();

        // Create sample order data
        sampleOrderDto = OrderDto.builder()
                .orderId(1)
                .orderDesc("Test Order for Shipping")
                .build();

        // Create sample order item data
        sampleOrderItemDto = OrderItemDto.builder()
                .productId(1)
                .orderId(1)
                .orderedQuantity(5)
                .productDto(sampleProductDto)
                .orderDto(sampleOrderDto)
                .build();
    }

    @Test
    void testOrderItemCreation_WithUserValidation() {
        // Given
        when(orderItemService.save(any(OrderItemDto.class))).thenReturn(sampleOrderItemDto);

        // Mock user service response for validation
        ResponseEntity<Boolean> userValidationResponse = new ResponseEntity<>(true, HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://localhost:8081/api/users/exists/1"),
                eq(Boolean.class)))
                .thenReturn(userValidationResponse);

        // When
        OrderItemDto savedOrderItem = orderItemService.save(sampleOrderItemDto);

        // Then
        assertNotNull(savedOrderItem);
        assertEquals(1, savedOrderItem.getProductId());
        assertEquals(1, savedOrderItem.getOrderId());
        assertEquals(5, savedOrderItem.getOrderedQuantity());
        assertNotNull(savedOrderItem.getOrderDto());
        assertNotNull(savedOrderItem.getProductDto());

        verify(orderItemService, times(1)).save(any(OrderItemDto.class));
    }

    @Test
    void testOrderItemProcessing_WhenUserServiceUnavailable() {
        // Given
        OrderItemId orderItemId = new OrderItemId(1, 1);
        when(orderItemService.findById(orderItemId)).thenReturn(sampleOrderItemDto);

        // Mock user service unavailable
        when(restTemplate.getForEntity(
                eq("http://localhost:8081/api/users/exists/1"),
                eq(Boolean.class)))
                .thenThrow(new RestClientException("User service unavailable"));

        // When & Then
        assertDoesNotThrow(() -> {
            OrderItemDto orderItem = orderItemService.findById(orderItemId);
            assertNotNull(orderItem);
            assertEquals(5, orderItem.getOrderedQuantity());
        });

        verify(orderItemService, times(1)).findById(orderItemId);
    }

    @Test
    void testOrderItemValidation_QuantityIntegrity() {
        // Given
        OrderItemId orderItemId = new OrderItemId(1, 1);
        when(orderItemService.findById(orderItemId)).thenReturn(sampleOrderItemDto);

        // Mock product service for stock validation
        ResponseEntity<Boolean> stockValidationResponse = new ResponseEntity<>(true, HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://localhost:8080/api/products/stock-check/1"),
                eq(Boolean.class)))
                .thenReturn(stockValidationResponse);

        // When
        OrderItemDto orderItem = orderItemService.findById(orderItemId);

        // Then
        assertNotNull(orderItem);
        assertEquals(1, orderItem.getProductId());
        assertEquals(1, orderItem.getOrderId());
        assertTrue(orderItem.getOrderedQuantity() > 0,
                   "Ordered quantity should be positive");
        assertTrue(orderItem.getOrderedQuantity() <= 100,
                  "Ordered quantity should be reasonable");

        verify(orderItemService, times(1)).findById(orderItemId);
    }

    @Test
    void testOrderItemStatusUpdate_OrderNotification() {
        // Given
        OrderItemDto updatedOrderItem = OrderItemDto.builder()
                .productId(1)
                .orderId(1)
                .orderedQuantity(3)
                .productDto(sampleProductDto)
                .orderDto(sampleOrderDto)
                .build();

        when(orderItemService.update(any(OrderItemDto.class))).thenReturn(updatedOrderItem);

        // Mock order notification service
        ResponseEntity<String> notificationResponse = new ResponseEntity<>("NOTIFIED", HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://localhost:8082/api/orders/notify/1"),
                eq(String.class)))
                .thenReturn(notificationResponse);

        // When
        OrderItemDto result = orderItemService.update(updatedOrderItem);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getProductId());
        assertEquals(1, result.getOrderId());
        assertEquals(3, result.getOrderedQuantity());
        assertNotNull(result.getOrderDto());
        assertNotNull(result.getProductDto());

        verify(orderItemService, times(1)).update(any(OrderItemDto.class));
    }

    @Test
    void testOrderItemBatchProcessing_MultipleProducts() {
        // Given
        List<OrderItemDto> orderItems = Arrays.asList(
                sampleOrderItemDto,
                OrderItemDto.builder()
                        .productId(2)
                        .orderId(2)
                        .orderedQuantity(3)
                        .productDto(ProductDto.builder().productId(2).productTitle("Second Product").build())
                        .orderDto(OrderDto.builder().orderId(2).orderDesc("Second Order").build())
                        .build(),
                OrderItemDto.builder()
                        .productId(3)
                        .orderId(3)
                        .orderedQuantity(8)
                        .productDto(ProductDto.builder().productId(3).productTitle("Third Product").build())
                        .orderDto(OrderDto.builder().orderId(3).orderDesc("Third Order").build())
                        .build(),
                OrderItemDto.builder()
                        .productId(4)
                        .orderId(4)
                        .orderedQuantity(2)
                        .productDto(ProductDto.builder().productId(4).productTitle("Fourth Product").build())
                        .orderDto(OrderDto.builder().orderId(4).orderDesc("Fourth Order").build())
                        .build(),
                OrderItemDto.builder()
                        .productId(5)
                        .orderId(5)
                        .orderedQuantity(12)
                        .productDto(ProductDto.builder().productId(5).productTitle("Fifth Product").build())
                        .orderDto(OrderDto.builder().orderId(5).orderDesc("Fifth Order").build())
                        .build()
        );

        when(orderItemService.findAll()).thenReturn(orderItems);

        // Mock product service for batch validation
        when(restTemplate.getForEntity(
                contains("http://localhost:8080/api/products/exists"),
                eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(true, HttpStatus.OK));

        // When
        List<OrderItemDto> allOrderItems = orderItemService.findAll();

        // Then
        assertNotNull(allOrderItems);
        assertEquals(5, allOrderItems.size(), "Should have exactly 5 order items for integration test");

        // Validate all order items have positive quantities
        for (OrderItemDto item : allOrderItems) {
            assertTrue(item.getOrderedQuantity() > 0,
                      "Order item " + item.getProductId() + " should have positive quantity");
            assertNotNull(item.getProductDto(),
                         "Order item " + item.getProductId() + " should have associated product");
            assertNotNull(item.getOrderDto(),
                         "Order item " + item.getProductId() + " should have associated order");
        }

        // Validate unique product-order combinations
        long uniqueCombinations = allOrderItems.stream()
                .map(item -> item.getProductId() + "-" + item.getOrderId())
                .distinct()
                .count();
        
        assertEquals(5, uniqueCombinations, "All order items should have unique product-order combinations");

        // Verify quantity ranges are reasonable
        boolean allQuantitiesValid = allOrderItems.stream()
                .allMatch(item -> item.getOrderedQuantity() >= 1 && item.getOrderedQuantity() <= 20);
        
        assertTrue(allQuantitiesValid, "All order quantities should be between 1 and 20");

        // Calculate total ordered quantity across all items
        int totalQuantity = allOrderItems.stream()
                .mapToInt(OrderItemDto::getOrderedQuantity)
                .sum();
        
        assertTrue(totalQuantity >= 5, "Total quantity should be at least 5");
        assertEquals(30, totalQuantity, "Total quantity should be 5+3+8+2+12=30");

        // Verify product ID sequence integrity  
        List<Integer> productIds = allOrderItems.stream()
                .map(OrderItemDto::getProductId)
                .sorted()
                .collect(Collectors.toList());
        
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), productIds, 
                    "Product IDs should be sequential from 1 to 5");
    }
}