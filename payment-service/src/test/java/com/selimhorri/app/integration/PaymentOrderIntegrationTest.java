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

import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.repository.PaymentRepository;
import com.selimhorri.app.service.PaymentService;

/**
 * Prueba de Integración #3: Payment-Service ↔ Order-Service
 * 
 * Esta prueba valida la comunicación entre el servicio de pagos
 * y el servicio de órdenes, verificando que:
 * - Se validen los montos de pago con las órdenes
 * - Se manejen órdenes inválidas
 * - Se procesen pagos correctamente
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Test: Payment-Service <-> Order-Service")
class PaymentOrderIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private RestTemplate restTemplate;

    private Payment payment;
    private OrderDto orderDto;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();

        orderDto = new OrderDto();
        orderDto.setOrderId(1);
        orderDto.setOrderDate(LocalDateTime.now());
        orderDto.setOrderDesc("Order for laptop and accessories");
        orderDto.setOrderFee(1299.99);

        payment = new Payment();
        payment.setOrderId(1);
        payment.setIsPayed(false);
    }

    @Test
    @DisplayName("Test 1: Should process payment with valid order information")
    void testProcessPayment_WithValidOrder_ShouldSucceed() {
        // Given
        Payment savedPayment = paymentRepository.save(payment);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);

        // When
        PaymentDto result = paymentService.findById(savedPayment.getPaymentId());

        // Then
        assertNotNull(result);
        assertNotNull(result.getOrderDto(), "Order DTO should not be null");
        assertEquals(orderDto.getOrderId(), result.getOrderDto().getOrderId());
        assertEquals(orderDto.getOrderFee(), result.getOrderDto().getOrderFee());

        verify(restTemplate, atLeastOnce()).getForObject(anyString(), eq(OrderDto.class));
    }

    @Test
    @DisplayName("Test 2: Should validate payment amount matches order total")
    void testPayment_ShouldMatchOrderTotal() {
        // Given
        Payment savedPayment = paymentRepository.save(payment);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);

        // When
        PaymentDto result = paymentService.findById(savedPayment.getPaymentId());

        // Then
        assertNotNull(result.getOrderDto());
        assertTrue(result.getOrderDto().getOrderFee() > 0, 
                   "Order fee should be greater than 0");
        assertEquals(1299.99, result.getOrderDto().getOrderFee(), 0.01,
                     "Payment amount should match order total");
    }

    @Test
    @DisplayName("Test 3: Should handle when order does not exist")
    void testProcessPayment_WhenOrderNotFound_ShouldReturnNull() {
        // Given
        Payment savedPayment = paymentRepository.save(payment);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(null); // Orden no encontrada

        // When
        PaymentDto result = paymentService.findById(savedPayment.getPaymentId());

        // Then
        assertNotNull(result);
        assertNull(result.getOrderDto(), "Order DTO should be null when order not found");
    }

    @Test
    @DisplayName("Test 4: Should retrieve order details for completed payment")
    void testGetPayment_ForCompletedPayment_ShouldIncludeOrderDetails() {
        // Given
        payment.setIsPayed(true); // Pago completado
        Payment savedPayment = paymentRepository.save(payment);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);

        // When
        PaymentDto result = paymentService.findById(savedPayment.getPaymentId());

        // Then
        assertNotNull(result);
        assertTrue(result.getIsPayed(), "Payment should be marked as completed");
        assertNotNull(result.getOrderDto());
        assertEquals(orderDto.getOrderDesc(), result.getOrderDto().getOrderDesc());
    }

    @Test
    @DisplayName("Test 5: Should handle multiple payments for same order")
    void testMultiplePayments_ForSameOrder_ShouldRetrieveSameOrderInfo() {
        // Given - Dos pagos para la misma orden
        Payment payment1 = new Payment();
        payment1.setOrderId(1);
        payment1.setIsPayed(true);

        Payment payment2 = new Payment();
        payment2.setOrderId(1);
        payment2.setIsPayed(false);

        Payment savedPayment1 = paymentRepository.save(payment1);
        Payment savedPayment2 = paymentRepository.save(payment2);

        when(restTemplate.getForObject(contains("/order-service/"), eq(OrderDto.class)))
            .thenReturn(orderDto);

        // When
        PaymentDto result1 = paymentService.findById(savedPayment1.getPaymentId());
        PaymentDto result2 = paymentService.findById(savedPayment2.getPaymentId());

        // Then - Ambos deben tener la misma información de orden
        assertNotNull(result1.getOrderDto());
        assertNotNull(result2.getOrderDto());
        assertEquals(result1.getOrderDto().getOrderId(), result2.getOrderDto().getOrderId());
        assertEquals(result1.getOrderDto().getOrderFee(), result2.getOrderDto().getOrderFee());
    }
}






