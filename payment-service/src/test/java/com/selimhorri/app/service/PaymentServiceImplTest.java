package com.selimhorri.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.selimhorri.app.domain.Payment;
import com.selimhorri.app.dto.OrderDto;
import com.selimhorri.app.dto.PaymentDto;
import com.selimhorri.app.exception.wrapper.PaymentNotFoundException;
import com.selimhorri.app.repository.PaymentRepository;
import com.selimhorri.app.service.impl.PaymentServiceImpl;

/**
 * Pruebas unitarias para PaymentServiceImpl
 * 
 * Estas pruebas validan las operaciones de procesamiento de pagos
 * incluyendo integración con el servicio de órdenes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Service Unit Tests")
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Payment payment;
    private PaymentDto paymentDto;
    private OrderDto orderDto;

    @BeforeEach
    void setUp() {
        // Configurar datos de prueba
        orderDto = new OrderDto();
        orderDto.setOrderId(1);
        orderDto.setOrderDesc("Test order");
        orderDto.setOrderFee(1299.99);
        
        payment = new Payment();
        payment.setPaymentId(1);
        payment.setIsPayed(true);
        
        paymentDto = new PaymentDto();
        paymentDto.setPaymentId(1);
        paymentDto.setIsPayed(true);
        paymentDto.setOrderDto(orderDto);
    }

    @Test
    @DisplayName("Test 1: Find all payments - should return list of payments with order info")
    void testFindAll_ShouldReturnPaymentListWithOrderInfo() {
        // Given
        Payment payment2 = new Payment();
        payment2.setPaymentId(2);
        payment2.setIsPayed(false);
        
        List<Payment> payments = Arrays.asList(payment, payment2);
        when(paymentRepository.findAll()).thenReturn(payments);
        when(restTemplate.getForObject(anyString(), eq(OrderDto.class))).thenReturn(orderDto);

        // When
        List<PaymentDto> result = paymentService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(paymentRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Test 2: Find payment by ID - should return payment when found")
    void testFindById_WhenPaymentExists_ShouldReturnPayment() {
        // Given
        when(paymentRepository.findById(anyInt())).thenReturn(Optional.of(payment));
        when(restTemplate.getForObject(anyString(), eq(OrderDto.class))).thenReturn(orderDto);

        // When
        PaymentDto result = paymentService.findById(1);

        // Then
        assertNotNull(result);
        assertEquals(payment.getPaymentId(), result.getPaymentId());
        assertTrue(result.getIsPayed());
        assertNotNull(result.getOrderDto());
        verify(paymentRepository, times(1)).findById(1);
    }

    @Test
    @DisplayName("Test 3: Find payment by ID - should throw exception when not found")
    void testFindById_WhenPaymentDoesNotExist_ShouldThrowException() {
        // Given
        when(paymentRepository.findById(anyInt())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PaymentNotFoundException.class, () -> {
            paymentService.findById(999);
        });
        verify(paymentRepository, times(1)).findById(999);
    }

    @Test
    @DisplayName("Test 4: Save payment - should persist and return saved payment")
    void testSave_ShouldPersistAndReturnPayment() {
        // Given
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        // When
        PaymentDto result = paymentService.save(paymentDto);

        // Then
        assertNotNull(result);
        assertEquals(payment.getPaymentId(), result.getPaymentId());
        assertTrue(result.getIsPayed());
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Test 5: Delete payment by ID - should invoke repository delete")
    void testDeleteById_ShouldInvokeRepositoryDelete() {
        // Given
        doNothing().when(paymentRepository).deleteById(anyInt());

        // When
        paymentService.deleteById(1);

        // Then
        verify(paymentRepository, times(1)).deleteById(1);
    }

    @Test
    @DisplayName("Test 6: Update payment status - should update and return modified payment")
    void testUpdate_ShouldUpdateAndReturnPayment() {
        // Given
        paymentDto.setIsPayed(false);
        
        Payment updatedPayment = new Payment();
        updatedPayment.setPaymentId(1);
        updatedPayment.setIsPayed(false);
        
        when(paymentRepository.save(any(Payment.class))).thenReturn(updatedPayment);

        // When
        PaymentDto result = paymentService.update(paymentDto);

        // Then
        assertNotNull(result);
        assertFalse(result.getIsPayed());
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }
}

