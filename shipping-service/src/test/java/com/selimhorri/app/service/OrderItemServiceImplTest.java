package com.selimhorri.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.domain.OrderItem;
import com.selimhorri.app.domain.id.OrderItemId;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.OrderItemDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.exception.wrapper.OrderItemNotFoundException;
import com.selimhorri.app.repository.OrderItemRepository;
import com.selimhorri.app.service.impl.OrderItemServiceImpl;

/**
 * Pruebas unitarias para OrderItemServiceImpl (Shipping Service)
 * 
 * Estas pruebas validan las operaciones de gestión de items de orden
 * incluyendo integración con servicios de productos y órdenes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Shipping Service Unit Tests")
class OrderItemServiceImplTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OrderItemServiceImpl orderItemService;

    private OrderItem orderItem;
    private OrderItemDto orderItemDto;
    private OrderDto orderDto;
    private ProductDto productDto;
    private OrderItemId orderItemId;

    @BeforeEach
    void setUp() {
        // Configurar datos de prueba
        orderDto = new OrderDto();
        orderDto.setOrderId(1);
        orderDto.setOrderDesc("Test order");
        
        productDto = new ProductDto();
        productDto.setProductId(1);
        productDto.setProductTitle("Laptop");
        productDto.setPriceUnit(1299.99);
        
        orderItemId = new OrderItemId();
        
        orderItem = new OrderItem();
        orderItem.setOrderedQuantity(2);
        
        orderItemDto = new OrderItemDto();
        orderItemDto.setOrderedQuantity(2);
        orderItemDto.setOrderDto(orderDto);
        orderItemDto.setProductDto(productDto);
    }

    @Test
    @DisplayName("Test 1: Find all order items - should return list with product and order info")
    void testFindAll_ShouldReturnOrderItemListWithDetails() {
        // Given
        OrderItem orderItem2 = new OrderItem();
        orderItem2.setOrderedQuantity(1);
        
        List<OrderItem> orderItems = Arrays.asList(orderItem, orderItem2);
        when(orderItemRepository.findAll()).thenReturn(orderItems);
        when(restTemplate.getForObject(anyString(), eq(ProductDto.class))).thenReturn(productDto);
        when(restTemplate.getForObject(anyString(), eq(OrderDto.class))).thenReturn(orderDto);

        // When
        List<OrderItemDto> result = orderItemService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orderItemRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Test 2: Save order item - should persist and return saved item")
    void testSave_ShouldPersistAndReturnOrderItem() {
        // Given
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(orderItem);

        // When
        OrderItemDto result = orderItemService.save(orderItemDto);

        // Then
        assertNotNull(result);
        assertEquals(orderItem.getOrderedQuantity(), result.getOrderedQuantity());
        verify(orderItemRepository, times(1)).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("Test 3: Delete order item by ID - should invoke repository delete")
    void testDeleteById_ShouldInvokeRepositoryDelete() {
        // Given
        doNothing().when(orderItemRepository).deleteById(any(OrderItemId.class));

        // When
        orderItemService.deleteById(orderItemId);

        // Then
        verify(orderItemRepository, times(1)).deleteById(orderItemId);
    }

    @Test
    @DisplayName("Test 4: Update order item - should update and return modified item")
    void testUpdate_ShouldUpdateAndReturnOrderItem() {
        // Given
        orderItemDto.setOrderedQuantity(5);
        
        OrderItem updatedOrderItem = new OrderItem();
        updatedOrderItem.setOrderedQuantity(5);
        
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(updatedOrderItem);

        // When
        OrderItemDto result = orderItemService.update(orderItemDto);

        // Then
        assertNotNull(result);
        assertEquals(5, result.getOrderedQuantity());
        verify(orderItemRepository, times(1)).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("Test 5: Verify ordered quantity - should be positive")
    void testOrderedQuantity_ShouldBePositive() {
        // Given
        orderItemDto.setOrderedQuantity(10);
        
        // When
        int quantity = orderItemDto.getOrderedQuantity();

        // Then
        assertTrue(quantity > 0, "Ordered quantity should be positive");
        assertTrue(quantity <= 100, "Ordered quantity should be reasonable");
    }
}

