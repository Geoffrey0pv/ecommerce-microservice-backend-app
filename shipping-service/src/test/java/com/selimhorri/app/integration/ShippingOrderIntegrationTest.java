package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.domain.OrderItem;
import com.selimhorri.app.domain.id.OrderItemId;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.OrderItemDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.repository.OrderItemRepository;
import com.selimhorri.app.service.OrderItemService;

/**
 * Prueba de Integración #4: Shipping-Service ↔ Order-Service
 * 
 * Esta prueba valida la comunicación entre el servicio de envíos
 * y el servicio de órdenes, verificando que:
 * - Se obtenga información de órdenes para envíos
 * - Se validen items de órdenes
 * - Se gestionen envíos correctamente
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Test: Shipping-Service <-> Order-Service")
class ShippingOrderIntegrationTest {

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @MockBean
    private RestTemplate restTemplate;

    private OrderItem orderItem;
    private OrderDto orderDto;
    private ProductDto productDto;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();

        orderDto = new OrderDto();
        orderDto.setOrderId(1);
        orderDto.setOrderDate(LocalDateTime.now());
        orderDto.setOrderDesc("Laptop order for shipping");
        orderDto.setOrderFee(1299.99);

        productDto = new ProductDto();
        productDto.setProductId(1);
        productDto.setProductTitle("Laptop Dell XPS 15");
        productDto.setPriceUnit(1299.99);
        productDto.setQuantity(50);

        orderItem = new OrderItem();
        orderItem.setOrderId(1);
        orderItem.setProductId(1);
        orderItem.setOrderedQuantity(2);
    }

    @Test
    @DisplayName("Test 1: Should create shipping for valid order")
    void testCreateShipping_WithValidOrder_ShouldSucceed() {
        // Given
        OrderItem savedOrderItem = orderItemRepository.save(orderItem);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        OrderItemId orderItemId = new OrderItemId(savedOrderItem.getOrderId(), savedOrderItem.getProductId());
        OrderItemDto result = orderItemService.findById(orderItemId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getOrderDto(), "Order DTO should not be null");
        assertEquals(orderDto.getOrderId(), result.getOrderDto().getOrderId());

        verify(restTemplate, atLeastOnce()).getForObject(anyString(), eq(OrderDto.class));
    }

    @Test
    @DisplayName("Test 2: Should retrieve order details for tracking")
    void testGetShipping_ShouldIncludeOrderDetails() {
        // Given
        OrderItem savedOrderItem = orderItemRepository.save(orderItem);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        OrderItemId orderItemId = new OrderItemId(savedOrderItem.getOrderId(), savedOrderItem.getProductId());
        OrderItemDto result = orderItemService.findById(orderItemId);

        // Then
        assertNotNull(result.getOrderDto());
        assertNotNull(result.getOrderDto().getOrderDesc());
        assertEquals("Laptop order for shipping", result.getOrderDto().getOrderDesc());
    }

    @Test
    @DisplayName("Test 3: Should handle when order does not exist")
    void testCreateShipping_WhenOrderNotFound_ShouldReturnNull() {
        // Given
        OrderItem savedOrderItem = orderItemRepository.save(orderItem);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(null); // Orden no encontrada
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        OrderItemId orderItemId = new OrderItemId(savedOrderItem.getOrderId(), savedOrderItem.getProductId());
        OrderItemDto result = orderItemService.findById(orderItemId);

        // Then
        assertNotNull(result);
        assertNull(result.getOrderDto(), "Order DTO should be null when order not found");
    }

    @Test
    @DisplayName("Test 4: Should validate order fee for shipping cost calculation")
    void testShipping_ShouldValidateOrderFee() {
        // Given
        OrderItem savedOrderItem = orderItemRepository.save(orderItem);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        OrderItemId orderItemId = new OrderItemId(savedOrderItem.getOrderId(), savedOrderItem.getProductId());
        OrderItemDto result = orderItemService.findById(orderItemId);

        // Then
        assertNotNull(result.getOrderDto());
        assertTrue(result.getOrderDto().getOrderFee() > 0,
                   "Order fee should be positive for shipping cost calculation");
        assertEquals(1299.99, result.getOrderDto().getOrderFee(), 0.01);
    }

    @Test
    @DisplayName("Test 5: Should verify order date for shipping scheduling")
    void testShipping_ShouldIncludeOrderDate() {
        // Given
        OrderItem savedOrderItem = orderItemRepository.save(orderItem);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        OrderItemId orderItemId = new OrderItemId(savedOrderItem.getOrderId(), savedOrderItem.getProductId());
        OrderItemDto result = orderItemService.findById(orderItemId);

        // Then
        assertNotNull(result.getOrderDto());
        assertNotNull(result.getOrderDto().getOrderDate(),
                      "Order date should be present for shipping scheduling");
        assertFalse(result.getOrderDto().getOrderDate().isAfter(LocalDateTime.now()),
                    "Order date should not be in the future");
    }
}






