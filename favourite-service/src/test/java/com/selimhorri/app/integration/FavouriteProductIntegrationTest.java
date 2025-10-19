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

import com.selimhorri.app.domain.Favourite;
import com.selimhorri.app.domain.id.FavouriteId;
import com.selimhorri.app.dto.FavouriteDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.repository.FavouriteRepository;
import com.selimhorri.app.service.FavouriteService;

/**
 * Prueba de Integración #2: Favourite-Service ↔ Product-Service
 * 
 * Esta prueba valida la comunicación entre el servicio de favoritos
 * y el servicio de productos, verificando que:
 * - Se obtengan datos completos de productos
 * - Se validen precios y disponibilidad
 * - Se manejen productos no disponibles
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Test: Favourite-Service <-> Product-Service")
class FavouriteProductIntegrationTest {

    @Autowired
    private FavouriteService favouriteService;

    @Autowired
    private FavouriteRepository favouriteRepository;

    @MockBean
    private RestTemplate restTemplate;

    private Favourite favourite;
    private UserDto userDto;
    private ProductDto productDto;

    @BeforeEach
    void setUp() {
        favouriteRepository.deleteAll();

        userDto = new UserDto();
        userDto.setUserId(1);
        userDto.setFirstName("John");
        userDto.setLastName("Doe");

        productDto = new ProductDto();
        productDto.setProductId(1);
        productDto.setProductTitle("Laptop Dell XPS 15");
        productDto.setImageUrl("https://example.com/laptop.jpg");
        productDto.setSku("DELL-XPS-15-001");
        productDto.setPriceUnit(1299.99);
        productDto.setQuantity(50);

        favourite = new Favourite();
        favourite.setUserId(1);
        favourite.setProductId(1);
        favourite.setLikeDate(LocalDateTime.now());
    }

    @Test
    @DisplayName("Test 1: Should retrieve favourite with complete product information")
    void testGetFavourite_ShouldIncludeProductData() {
        // Given
        Favourite savedFavourite = favouriteRepository.save(favourite);

        when(restTemplate.getForObject(contains("/user-service/"), eq(UserDto.class)))
            .thenReturn(userDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getProductDto(), "Product DTO should not be null");
        assertEquals(productDto.getProductId(), result.getProductDto().getProductId());
        assertEquals(productDto.getProductTitle(), result.getProductDto().getProductTitle());
        assertEquals(productDto.getPriceUnit(), result.getProductDto().getPriceUnit());
        assertEquals(productDto.getSku(), result.getProductDto().getSku());

        verify(restTemplate, atLeastOnce()).getForObject(anyString(), eq(ProductDto.class));
    }

    @Test
    @DisplayName("Test 2: Should validate product price from product-service")
    void testGetFavourite_ShouldValidateProductPrice() {
        // Given
        Favourite savedFavourite = favouriteRepository.save(favourite);

        when(restTemplate.getForObject(contains("/user-service/"), eq(UserDto.class)))
            .thenReturn(userDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then
        assertNotNull(result.getProductDto());
        assertTrue(result.getProductDto().getPriceUnit() > 0, 
                   "Product price should be greater than 0");
        assertEquals(1299.99, result.getProductDto().getPriceUnit(), 0.01);
    }

    @Test
    @DisplayName("Test 3: Should handle when product is out of stock")
    void testGetFavourite_WhenProductOutOfStock_ShouldStillRetrieveInfo() {
        // Given
        Favourite savedFavourite = favouriteRepository.save(favourite);

        // Producto sin stock
        ProductDto outOfStockProduct = new ProductDto();
        outOfStockProduct.setProductId(1);
        outOfStockProduct.setProductTitle("Laptop Dell XPS 15");
        outOfStockProduct.setPriceUnit(1299.99);
        outOfStockProduct.setQuantity(0); // Sin stock

        when(restTemplate.getForObject(contains("/user-service/"), eq(UserDto.class)))
            .thenReturn(userDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(outOfStockProduct);

        // When
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then
        assertNotNull(result.getProductDto());
        assertEquals(0, result.getProductDto().getQuantity(), 
                     "Should show product with 0 quantity");
    }

    @Test
    @DisplayName("Test 4: Should handle when product not found in product-service")
    void testGetFavourite_WhenProductNotFound_ShouldReturnNull() {
        // Given
        Favourite savedFavourite = favouriteRepository.save(favourite);

        when(restTemplate.getForObject(contains("/user-service/"), eq(UserDto.class)))
            .thenReturn(userDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(null); // Producto no encontrado

        // When
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then
        assertNotNull(result);
        assertNull(result.getProductDto(), "Product DTO should be null when product not found");
    }

    @Test
    @DisplayName("Test 5: Should retrieve product with valid SKU and image URL")
    void testGetFavourite_ShouldIncludeProductDetailsLikeSKUAndImage() {
        // Given
        Favourite savedFavourite = favouriteRepository.save(favourite);

        when(restTemplate.getForObject(contains("/user-service/"), eq(UserDto.class)))
            .thenReturn(userDto);
        when(restTemplate.getForObject(contains("/product-service/"), eq(ProductDto.class)))
            .thenReturn(productDto);

        // When
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then
        assertNotNull(result.getProductDto());
        assertNotNull(result.getProductDto().getSku(), "SKU should not be null");
        assertNotNull(result.getProductDto().getImageUrl(), "Image URL should not be null");
        assertTrue(result.getProductDto().getImageUrl().startsWith("http"), 
                   "Image URL should be a valid HTTP URL");
        assertFalse(result.getProductDto().getSku().isEmpty(), "SKU should not be empty");
    }
}






