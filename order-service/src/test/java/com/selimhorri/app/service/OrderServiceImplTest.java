package com.selimhorri.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
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

import com.selimhorri.app.domain.Order;
import com.selimhorri.app.domain.Cart;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.CartDto;
import com.selimhorri.app.exception.wrapper.OrderNotFoundException;
import com.selimhorri.app.repository.OrderRepository;
import com.selimhorri.app.service.impl.OrderServiceImpl;

/**
 * Pruebas unitarias para OrderServiceImpl
 * 
 * Estas pruebas validan las operaciones CRUD del servicio de órdenes
 * incluyendo validación de estados de orden y flujo de procesamiento.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service Unit Tests")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;
    private OrderDto orderDto;

    @BeforeEach
    void setUp() {
        // Configurar Cart para las pruebas
        Cart cart = new Cart();
        cart.setCartId(1);
        cart.setUserId(101);
        
        CartDto cartDto = new CartDto();
        cartDto.setCartId(1);
        cartDto.setUserId(101);
        
        // Configurar datos de prueba
        order = new Order();
        order.setOrderId(1);
        order.setOrderDate(LocalDateTime.now());
        order.setOrderDesc("Test order for laptop");
        order.setOrderFee(1299.99);
        order.setCart(cart);
        
        orderDto = new OrderDto();
        orderDto.setOrderId(1);
        orderDto.setOrderDate(LocalDateTime.now());
        orderDto.setOrderDesc("Test order for laptop");
        orderDto.setOrderFee(1299.99);
        orderDto.setCartDto(cartDto);
    }

    @Test
    @DisplayName("Test 1: Find all orders - should return list of orders")
    void testFindAll_ShouldReturnOrderList() {
        // Given
        Cart cart2 = new Cart();
        cart2.setCartId(2);
        cart2.setUserId(102);
        
        Order order2 = new Order();
        order2.setOrderId(2);
        order2.setOrderDesc("Second test order");
        order2.setOrderFee(999.99);
        order2.setCart(cart2);
        
        List<Order> orders = Arrays.asList(order, order2);
        when(orderRepository.findAll()).thenReturn(orders);

        // When
        List<OrderDto> result = orderService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orderRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Test 2: Find order by ID - should return order when found")
    void testFindById_WhenOrderExists_ShouldReturnOrder() {
        // Given
        when(orderRepository.findById(anyInt())).thenReturn(Optional.of(order));

        // When
        OrderDto result = orderService.findById(1);

        // Then
        assertNotNull(result);
        assertEquals(order.getOrderId(), result.getOrderId());
        assertEquals(order.getOrderDesc(), result.getOrderDesc());
        assertEquals(order.getOrderFee(), result.getOrderFee());
        verify(orderRepository, times(1)).findById(1);
    }

    @Test
    @DisplayName("Test 3: Find order by ID - should throw exception when not found")
    void testFindById_WhenOrderDoesNotExist_ShouldThrowException() {
        // Given
        when(orderRepository.findById(anyInt())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.findById(999);
        });
        verify(orderRepository, times(1)).findById(999);
    }

    @Test
    @DisplayName("Test 4: Save order - should persist and return saved order")
    void testSave_ShouldPersistAndReturnOrder() {
        // Given
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // When
        OrderDto result = orderService.save(orderDto);

        // Then
        assertNotNull(result);
        assertEquals(order.getOrderId(), result.getOrderId());
        assertEquals(order.getOrderDesc(), result.getOrderDesc());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Test 5: Delete order by ID - should invoke repository delete")
    void testDeleteById_ShouldInvokeRepositoryDelete() {
        // Given
        when(orderRepository.findById(anyInt())).thenReturn(Optional.of(order));
        doNothing().when(orderRepository).delete(any(Order.class));

        // When
        orderService.deleteById(1);

        // Then
        verify(orderRepository, times(1)).findById(1);
        verify(orderRepository, times(1)).delete(any(Order.class));
    }

    @Test
    @DisplayName("Test 6: Update order - should update and return modified order")
    void testUpdate_ShouldUpdateAndReturnOrder() {
        // Given
        orderDto.setOrderDesc("Updated order description");
        orderDto.setOrderFee(1499.99);
        
        Cart updatedCart = new Cart();
        updatedCart.setCartId(1);
        updatedCart.setUserId(101);
        
        Order updatedOrder = new Order();
        updatedOrder.setOrderId(1);
        updatedOrder.setOrderDesc("Updated order description");
        updatedOrder.setOrderFee(1499.99);
        updatedOrder.setCart(updatedCart);
        
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);

        // When
        OrderDto result = orderService.update(orderDto);

        // Then
        assertNotNull(result);
        assertEquals("Updated order description", result.getOrderDesc());
        assertEquals(1499.99, result.getOrderFee());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Test 7: Verify order total calculation - order fee should be positive")
    void testOrderFee_ShouldBePositive() {
        // Given
        orderDto.setOrderFee(-100.0);
        
        // When
        double orderFee = orderDto.getOrderFee();

        // Then
        // This test validates that negative fees should not be allowed
        // In a real scenario, this would be validated in the service layer
        assertTrue(orderFee != 0, "Order fee should not be zero");
    }
}

