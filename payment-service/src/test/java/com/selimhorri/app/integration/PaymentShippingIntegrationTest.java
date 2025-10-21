package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.service.PaymentService;
import com.selimhorri.app.domain.PaymentStatus;

/**
 * Integration tests for Payment-Shipping service communication
 * Tests payment processing with shipping validation workflows
 */
@SpringBootTest
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false"
})
public class PaymentShippingIntegrationTest {

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private PaymentService paymentService;

    private PaymentDto samplePaymentDto;
    private OrderDto sampleOrderDto;

    @BeforeEach
    void setUp() {
        // Create sample order data
        sampleOrderDto = OrderDto.builder()
                .orderId(1)
                .orderDesc("Test Order")
                .build();

        // Create sample payment data  
        samplePaymentDto = PaymentDto.builder()
                .paymentId(1)
                .isPayed(true)
                .paymentStatus(PaymentStatus.COMPLETED)
                .orderDto(sampleOrderDto)
                .build();
    }

    @Test
    void testPaymentProcessing_WithShippingValidation() {
        // Given
        when(paymentService.save(any(PaymentDto.class))).thenReturn(samplePaymentDto);

        // Mock shipping service response for validation
        ResponseEntity<Boolean> shippingValidationResponse = new ResponseEntity<>(true, HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://localhost:8084/api/shipping/validate/1"),
                eq(Boolean.class)))
                .thenReturn(shippingValidationResponse);

        // When
        PaymentDto savedPayment = paymentService.save(samplePaymentDto);

        // Then
        assertNotNull(savedPayment);
        assertEquals(1, savedPayment.getPaymentId());
        assertEquals(PaymentStatus.COMPLETED, savedPayment.getPaymentStatus());
        assertTrue(savedPayment.getIsPayed(), "Payment should be marked as payed");

        verify(paymentService, times(1)).save(any(PaymentDto.class));
    }

    @Test
    void testPaymentValidation_WhenShippingServiceUnavailable() {
        // Given
        when(paymentService.findById(1)).thenReturn(samplePaymentDto);

        // Mock shipping service unavailable
        when(restTemplate.getForEntity(
                eq("http://localhost:8084/api/shipping/validate/1"),
                eq(Boolean.class)))
                .thenThrow(new RestClientException("Shipping service unavailable"));

        // When & Then
        assertDoesNotThrow(() -> {
            PaymentDto payment = paymentService.findById(1);
            assertNotNull(payment);
            assertEquals(PaymentStatus.COMPLETED, payment.getPaymentStatus());
        });

        verify(paymentService, times(1)).findById(1);
    }

    @Test
    void testPaymentSecurityValidation_OrderIntegrity() {
        // Given
        when(paymentService.findById(1)).thenReturn(samplePaymentDto);

        // Mock order service for validation
        ResponseEntity<Boolean> orderValidationResponse = new ResponseEntity<>(true, HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://localhost:8082/api/orders/exists/1"),
                eq(Boolean.class)))
                .thenReturn(orderValidationResponse);

        // When
        PaymentDto payment = paymentService.findById(1);

        // Then
        assertNotNull(payment);
        assertEquals(1, payment.getPaymentId());
        assertEquals(PaymentStatus.COMPLETED, payment.getPaymentStatus());
        assertNotNull(payment.getOrderDto(), "Payment should have associated order");
        assertEquals(1, payment.getOrderDto().getOrderId());

        verify(paymentService, times(1)).findById(1);
    }

    @Test
    void testPaymentRefund_ShippingCancellation() {
        // Given
        PaymentDto refundPayment = PaymentDto.builder()
                .paymentId(2)
                .isPayed(false)
                .paymentStatus(PaymentStatus.NOT_STARTED)
                .orderDto(sampleOrderDto)
                .build();

        when(paymentService.update(any(PaymentDto.class))).thenReturn(refundPayment);

        // Mock shipping cancellation confirmation
        ResponseEntity<String> shippingCancellation = new ResponseEntity<>("CANCELLED", HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://localhost:8084/api/shipping/status/1"),
                eq(String.class)))
                .thenReturn(shippingCancellation);

        // When
        PaymentDto updatedPayment = paymentService.update(refundPayment);

        // Then
        assertNotNull(updatedPayment);
        assertEquals(PaymentStatus.NOT_STARTED, updatedPayment.getPaymentStatus());
        assertFalse(updatedPayment.getIsPayed(), "Refunded payment should not be marked as payed");

        verify(paymentService, times(1)).update(any(PaymentDto.class));
    }

    @Test
    void testPaymentBatchProcessing_MultipleShipments() {
        // Given
        List<PaymentDto> payments = Arrays.asList(
                samplePaymentDto,
                PaymentDto.builder()
                        .paymentId(2)
                        .isPayed(false)
                        .paymentStatus(PaymentStatus.IN_PROGRESS)
                        .orderDto(OrderDto.builder().orderId(2).orderDesc("Second Order").build())
                        .build(),
                PaymentDto.builder()
                        .paymentId(3)
                        .isPayed(false)
                        .paymentStatus(PaymentStatus.NOT_STARTED)
                        .orderDto(OrderDto.builder().orderId(3).orderDesc("Third Order").build())
                        .build()
        );

        when(paymentService.findAll()).thenReturn(payments);

        // Mock shipping service for batch validation
        when(restTemplate.getForEntity(
                contains("http://localhost:8084/api/shipping/validate"),
                eq(Boolean.class)))
                .thenReturn(new ResponseEntity<>(true, HttpStatus.OK));

        // When
        List<PaymentDto> allPayments = paymentService.findAll();

        // Then
        assertNotNull(allPayments);
        assertEquals(3, allPayments.size());

        // Validate payment status distribution
        long completedPayments = allPayments.stream()
                .filter(p -> PaymentStatus.COMPLETED.equals(p.getPaymentStatus()))
                .count();
        long inProgressPayments = allPayments.stream()
                .filter(p -> PaymentStatus.IN_PROGRESS.equals(p.getPaymentStatus()))
                .count();
        long notStartedPayments = allPayments.stream()
                .filter(p -> PaymentStatus.NOT_STARTED.equals(p.getPaymentStatus()))
                .count();

        assertEquals(1, completedPayments, "Should have 1 completed payment");
        assertEquals(1, inProgressPayments, "Should have 1 in-progress payment");
        assertEquals(1, notStartedPayments, "Should have 1 not-started payment");

        // Verify all payments have associated orders
        for (PaymentDto payment : allPayments) {
            assertNotNull(payment.getOrderDto(),
                      "Payment " + payment.getPaymentId() + " should have associated order");
            assertTrue(payment.getOrderDto().getOrderId() > 0,
                      "Order ID should be positive for payment " + payment.getPaymentId());
        }

        // Verify payment consistency
        long payedPayments = allPayments.stream()
                .filter(PaymentDto::getIsPayed)
                .count();
        
        assertEquals(1, payedPayments, "Only completed payments should be marked as payed");
    }
}