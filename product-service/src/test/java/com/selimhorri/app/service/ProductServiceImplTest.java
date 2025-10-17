package com.selimhorri.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com.selimhorri.app.domain.Product;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.exception.wrapper.ProductNotFoundException;
import com.selimhorri.app.repository.ProductRepository;
import com.selimhorri.app.service.impl.ProductServiceImpl;

/**
 * Pruebas unitarias para ProductServiceImpl
 * 
 * Estas pruebas validan las operaciones CRUD del servicio de productos
 * incluyendo validación de precios y disponibilidad de stock.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Product Service Unit Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;
    private ProductDto productDto;

    @BeforeEach
    void setUp() {
        // Configurar datos de prueba
        product = new Product();
        product.setProductId(1);
        product.setProductTitle("Laptop Dell XPS 15");
        product.setImageUrl("https://example.com/laptop.jpg");
        product.setSku("DELL-XPS-15-001");
        product.setPriceUnit(1299.99);
        product.setQuantity(50);
        
        productDto = new ProductDto();
        productDto.setProductId(1);
        productDto.setProductTitle("Laptop Dell XPS 15");
        productDto.setImageUrl("https://example.com/laptop.jpg");
        productDto.setSku("DELL-XPS-15-001");
        productDto.setPriceUnit(1299.99);
        productDto.setQuantity(50);
    }

    @Test
    @DisplayName("Test 1: Find all products - should return list of products")
    void testFindAll_ShouldReturnProductList() {
        // Given
        Product product2 = new Product();
        product2.setProductId(2);
        product2.setProductTitle("MacBook Pro");
        product2.setPriceUnit(1999.99);
        
        List<Product> products = Arrays.asList(product, product2);
        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ProductDto> result = productService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Test 2: Find product by ID - should return product when found")
    void testFindById_WhenProductExists_ShouldReturnProduct() {
        // Given
        when(productRepository.findById(anyInt())).thenReturn(Optional.of(product));

        // When
        ProductDto result = productService.findById(1);

        // Then
        assertNotNull(result);
        assertEquals(product.getProductId(), result.getProductId());
        assertEquals(product.getProductTitle(), result.getProductTitle());
        assertEquals(product.getPriceUnit(), result.getPriceUnit());
        verify(productRepository, times(1)).findById(1);
    }

    @Test
    @DisplayName("Test 3: Find product by ID - should throw exception when not found")
    void testFindById_WhenProductDoesNotExist_ShouldThrowException() {
        // Given
        when(productRepository.findById(anyInt())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ProductNotFoundException.class, () -> {
            productService.findById(999);
        });
        verify(productRepository, times(1)).findById(999);
    }

    @Test
    @DisplayName("Test 4: Save product - should persist and return saved product")
    void testSave_ShouldPersistAndReturnProduct() {
        // Given
        when(productRepository.save(any(Product.class))).thenReturn(product);

        // When
        ProductDto result = productService.save(productDto);

        // Then
        assertNotNull(result);
        assertEquals(product.getProductId(), result.getProductId());
        assertEquals(product.getProductTitle(), result.getProductTitle());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("Test 5: Delete product by ID - should invoke repository delete")
    void testDeleteById_ShouldInvokeRepositoryDelete() {
        // Given
        when(productRepository.findById(anyInt())).thenReturn(Optional.of(product));
        doNothing().when(productRepository).delete(any(Product.class));

        // When
        productService.deleteById(1);

        // Then
        verify(productRepository, times(1)).findById(1);
        verify(productRepository, times(1)).delete(any(Product.class));
    }

    @Test
    @DisplayName("Test 6: Update product - should update and return modified product")
    void testUpdate_ShouldUpdateAndReturnProduct() {
        // Given
        productDto.setProductTitle("Updated Laptop Dell XPS 15");
        productDto.setPriceUnit(1399.99);
        
        Product updatedProduct = new Product();
        updatedProduct.setProductId(1);
        updatedProduct.setProductTitle("Updated Laptop Dell XPS 15");
        updatedProduct.setPriceUnit(1399.99);
        
        when(productRepository.save(any(Product.class))).thenReturn(updatedProduct);

        // When
        ProductDto result = productService.update(productDto);

        // Then
        assertNotNull(result);
        assertEquals("Updated Laptop Dell XPS 15", result.getProductTitle());
        assertEquals(1399.99, result.getPriceUnit());
        verify(productRepository, times(1)).save(any(Product.class));
    }
}

