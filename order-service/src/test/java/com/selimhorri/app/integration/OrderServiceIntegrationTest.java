package com.selimhorri.app.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.selimhorri.app.dto.CartDto;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.exception.wrapper.OrderNotFoundException;
import com.selimhorri.app.service.CartService;
import com.selimhorri.app.service.OrderService;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.config.import-check.enabled=false",
    "spring.cloud.config.enabled=false"
})
public class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Test
    void testOrderServiceCRUDOperations() {
        // Given
        UserDto userDto = UserDto.builder()
                .userId(1)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        CartDto cartDto = CartDto.builder()
                .userDto(userDto)
                .build();

        // First save the cart to avoid Hibernate transient instance error
        CartDto savedCart = cartService.save(cartDto);

        OrderDto orderDto = OrderDto.builder()
                .orderDate(LocalDateTime.now())
                .orderDesc("Test order description")
                .orderFee(29.99)
                .cartDto(savedCart)
                .build();

        // When - Create
        OrderDto savedOrder = orderService.save(orderDto);

        // Then - Verify creation
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getOrderId()).isNotNull();
        assertThat(savedOrder.getOrderDesc()).isEqualTo("Test order description");
        assertThat(savedOrder.getOrderFee()).isEqualTo(29.99);
        assertThat(savedOrder.getCartDto()).isNotNull();

        // When - Read by ID
        OrderDto retrievedOrder = orderService.findById(savedOrder.getOrderId());

        // Then - Verify retrieval
        assertThat(retrievedOrder).isNotNull();
        assertThat(retrievedOrder.getOrderId()).isEqualTo(savedOrder.getOrderId());
        assertThat(retrievedOrder.getOrderDesc()).isEqualTo("Test order description");

        // When - Update
        retrievedOrder.setOrderDesc("Updated order description");
        retrievedOrder.setOrderFee(39.99);
        OrderDto updatedOrder = orderService.update(retrievedOrder);

        // Then - Verify update
        assertThat(updatedOrder).isNotNull();
        assertThat(updatedOrder.getOrderDesc()).isEqualTo("Updated order description");
        assertThat(updatedOrder.getOrderFee()).isEqualTo(39.99);

        // When - Find all orders
        List<OrderDto> allOrders = orderService.findAll();

        // Then - Verify the order is in the list
        assertThat(allOrders).isNotEmpty();
        boolean orderExists = allOrders.stream()
                .anyMatch(o -> o.getOrderId().equals(updatedOrder.getOrderId()));
        assertThat(orderExists).isTrue();

        // When - Delete
        orderService.deleteById(updatedOrder.getOrderId());

        // Then - Verify deletion
        assertThatThrownBy(() -> orderService.findById(updatedOrder.getOrderId()))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order with id: " + updatedOrder.getOrderId() + " not found");
    }

    @Test
    void testOrderServiceUpdateWithId() {
        // Given
        UserDto userDto = UserDto.builder()
                .userId(2)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .build();

        CartDto cartDto = CartDto.builder()
                .userDto(userDto)
                .build();

        // First save the cart to avoid Hibernate transient instance error
        CartDto savedCart = cartService.save(cartDto);

        OrderDto orderDto = OrderDto.builder()
                .orderDate(LocalDateTime.now())
                .orderDesc("Original description")
                .orderFee(19.99)
                .cartDto(savedCart)
                .build();

        OrderDto savedOrder = orderService.save(orderDto);

        // When - Update using orderId parameter
        OrderDto updateDto = OrderDto.builder()
                .orderDesc("Updated via orderId")
                .orderFee(24.99)
                .cartDto(savedCart)
                .build();

        OrderDto updatedOrder = orderService.update(savedOrder.getOrderId(), updateDto);

        // Then - Verify update
        assertThat(updatedOrder).isNotNull();
        assertThat(updatedOrder.getOrderId()).isEqualTo(savedOrder.getOrderId());
        
        // Clean up
        orderService.deleteById(updatedOrder.getOrderId());
    }

    @Test
    void testOrderNotFoundScenario() {
        // When & Then - Try to find non-existent order
        assertThatThrownBy(() -> orderService.findById(99999))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order with id: 99999 not found");
    }

    @Test
    void testFindAllOrdersWhenEmpty() {
        // When
        List<OrderDto> orders = orderService.findAll();

        // Then - Should return empty list or list with existing orders
        assertThat(orders).isNotNull();
        // Note: We can't assert empty because other tests might have created orders
    }
}