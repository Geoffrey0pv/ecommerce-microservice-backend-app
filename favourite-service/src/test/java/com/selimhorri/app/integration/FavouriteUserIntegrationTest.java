package com.selimhorri.app.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.domain.Favourite;
import com.selimhorri.app.domain.id.FavouriteId;
import com.selimhorri.app.dto.FavouriteDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.repository.FavouriteRepository;
import com.selimhorri.app.service.FavouriteService;

/**
 * Prueba de Integración #1: Favourite-Service ↔ User-Service
 * 
 * Esta prueba valida la comunicación entre el servicio de favoritos
 * y el servicio de usuarios, verificando que:
 * - Se puedan obtener datos de usuario correctamente
 * - Se maneje apropiadamente cuando un usuario no existe
 * - Se gestionen errores de comunicación (timeout, etc.)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Test: Favourite-Service <-> User-Service")
class FavouriteUserIntegrationTest {

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
        // Limpiar repositorio antes de cada prueba
        favouriteRepository.deleteAll();

        // Configurar datos de prueba
        userDto = new UserDto();
        userDto.setUserId(1);
        userDto.setFirstName("John");
        userDto.setLastName("Doe");
        userDto.setEmail("john.doe@example.com");
        userDto.setPhone("1234567890");

        productDto = new ProductDto();
        productDto.setProductId(1);
        productDto.setProductTitle("Laptop Dell XPS 15");
        productDto.setPriceUnit(1299.99);

        favourite = new Favourite();
        favourite.setUserId(1);
        favourite.setProductId(1);
        favourite.setLikeDate(LocalDateTime.now());
    }

    @Test
    @DisplayName("Test 1: Should retrieve favourite with complete user information")
    void testGetFavourite_ShouldIncludeUserData() {
        // Given - Guardar favorito en base de datos
        Favourite savedFavourite = favouriteRepository.save(favourite);

        // Mock de la llamada REST a user-service
        when(restTemplate.getForObject(
            contains("/user-service/"),
            eq(UserDto.class)))
            .thenReturn(userDto);

        // Mock de la llamada REST a product-service
        when(restTemplate.getForObject(
            contains("/product-service/"),
            eq(ProductDto.class)))
            .thenReturn(productDto);

        // When - Obtener favorito por ID
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then - Verificar que se obtuvo información del usuario
        assertNotNull(result);
        assertNotNull(result.getUserDto(), "User DTO should not be null");
        assertEquals(userDto.getUserId(), result.getUserDto().getUserId());
        assertEquals(userDto.getFirstName(), result.getUserDto().getFirstName());
        assertEquals(userDto.getLastName(), result.getUserDto().getLastName());
        assertEquals(userDto.getEmail(), result.getUserDto().getEmail());

        // Verificar que se hizo la llamada REST
        verify(restTemplate, atLeastOnce()).getForObject(
            anyString(),
            eq(UserDto.class));
    }

    @Test
    @DisplayName("Test 2: Should handle when user service is unavailable")
    void testGetFavourite_WhenUserServiceDown_ShouldThrowException() {
        // Given - Guardar favorito
        Favourite savedFavourite = favouriteRepository.save(favourite);

        // Simular que user-service no está disponible
        when(restTemplate.getForObject(
            contains("/user-service/"),
            eq(UserDto.class)))
            .thenThrow(new RestClientException("Connection refused"));

        // When & Then - Debe lanzar excepción
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        
        assertThrows(RestClientException.class, () -> {
            favouriteService.findById(favouriteId);
        });

        verify(restTemplate, times(1)).getForObject(
            anyString(),
            eq(UserDto.class));
    }

    @Test
    @DisplayName("Test 3: Should retrieve all favourites with user information")
    void testGetAllFavourites_ShouldIncludeUserDataForAll() {
        // Given - Guardar múltiples favoritos
        Favourite favourite1 = new Favourite();
        favourite1.setUserId(1);
        favourite1.setProductId(1);
        favourite1.setLikeDate(LocalDateTime.now());

        Favourite favourite2 = new Favourite();
        favourite2.setUserId(1);
        favourite2.setProductId(2);
        favourite2.setLikeDate(LocalDateTime.now().minusDays(1));

        favouriteRepository.save(favourite1);
        favouriteRepository.save(favourite2);

        // Mock de llamadas REST
        when(restTemplate.getForObject(
            contains("/user-service/"),
            eq(UserDto.class)))
            .thenReturn(userDto);

        when(restTemplate.getForObject(
            contains("/product-service/"),
            eq(ProductDto.class)))
            .thenReturn(productDto);

        // When - Obtener todos los favoritos
        List<FavouriteDto> results = favouriteService.findAll();

        // Then - Verificar que todos tienen información de usuario
        assertNotNull(results);
        assertFalse(results.isEmpty());
        
        for (FavouriteDto fav : results) {
            assertNotNull(fav.getUserDto(), "Every favourite should have user data");
            assertEquals(userDto.getUserId(), fav.getUserDto().getUserId());
        }

        // Verificar que se hicieron múltiples llamadas
        verify(restTemplate, atLeast(2)).getForObject(
            anyString(),
            eq(UserDto.class));
    }

    @Test
    @DisplayName("Test 4: Should handle user not found scenario")
    void testGetFavourite_WhenUserNotFound_ShouldReturnNull() {
        // Given - Guardar favorito con userId que no existe
        Favourite savedFavourite = favouriteRepository.save(favourite);

        // Simular que el usuario no existe (retorna null)
        when(restTemplate.getForObject(
            contains("/user-service/"),
            eq(UserDto.class)))
            .thenReturn(null);

        when(restTemplate.getForObject(
            contains("/product-service/"),
            eq(ProductDto.class)))
            .thenReturn(productDto);

        // When - Obtener favorito
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then - El favorito existe pero sin datos de usuario
        assertNotNull(result);
        assertNull(result.getUserDto(), "User DTO should be null when user not found");

        verify(restTemplate, times(1)).getForObject(
            anyString(),
            eq(UserDto.class));
    }

    @Test
    @DisplayName("Test 5: Should verify user data consistency across multiple calls")
    void testUserDataConsistency_AcrossMultipleCalls() {
        // Given - Mismo favorito consultado múltiples veces
        Favourite savedFavourite = favouriteRepository.save(favourite);
        FavouriteId favouriteId = new FavouriteId(savedFavourite.getUserId(), savedFavourite.getProductId());

        when(restTemplate.getForObject(
            contains("/user-service/"),
            eq(UserDto.class)))
            .thenReturn(userDto);

        when(restTemplate.getForObject(
            contains("/product-service/"),
            eq(ProductDto.class)))
            .thenReturn(productDto);

        // When - Múltiples llamadas al mismo favorito
        FavouriteDto result1 = favouriteService.findById(favouriteId);
        FavouriteDto result2 = favouriteService.findById(favouriteId);

        // Then - Los datos del usuario deben ser consistentes
        assertNotNull(result1.getUserDto());
        assertNotNull(result2.getUserDto());
        assertEquals(result1.getUserDto().getUserId(), result2.getUserDto().getUserId());
        assertEquals(result1.getUserDto().getEmail(), result2.getUserDto().getEmail());
        assertEquals(result1.getUserDto().getFirstName(), result2.getUserDto().getFirstName());
    }
}

