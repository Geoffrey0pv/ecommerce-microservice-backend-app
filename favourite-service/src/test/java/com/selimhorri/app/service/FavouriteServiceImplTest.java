package com.selimhorri.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.web.client.RestTemplate;

import com.selimhorri.app.domain.Favourite;
import com.selimhorri.app.domain.id.FavouriteId;
import com.selimhorri.app.dto.FavouriteDto;
import com.selimhorri.app.dto.ProductDto;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.exception.wrapper.FavouriteNotFoundException;
import com.selimhorri.app.repository.FavouriteRepository;
import com.selimhorri.app.service.impl.FavouriteServiceImpl;

/**
 * Pruebas unitarias para FavouriteServiceImpl
 * 
 * Estas pruebas validan las operaciones de gestión de favoritos de usuario
 * incluyendo integración con servicios de usuarios y productos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Favourite Service Unit Tests")
class FavouriteServiceImplTest {

    @Mock
    private FavouriteRepository favouriteRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FavouriteServiceImpl favouriteService;

    private Favourite favourite;
    private FavouriteDto favouriteDto;
    private UserDto userDto;
    private ProductDto productDto;
    private FavouriteId favouriteId;

    @BeforeEach
    void setUp() {
        // Configurar datos de prueba
        userDto = new UserDto();
        userDto.setUserId(1);
        userDto.setFirstName("John");
        userDto.setLastName("Doe");
        
        productDto = new ProductDto();
        productDto.setProductId(1);
        productDto.setProductTitle("Laptop Dell XPS 15");
        productDto.setPriceUnit(1299.99);
        
        favouriteId = new FavouriteId(1, 1, LocalDateTime.now());
        
        favourite = new Favourite();
        favourite.setLikeDate(LocalDateTime.now());
        
        favouriteDto = new FavouriteDto();
        favouriteDto.setUserId(1);
        favouriteDto.setProductId(1);
        favouriteDto.setLikeDate(LocalDateTime.now());
        favouriteDto.setUserDto(userDto);
        favouriteDto.setProductDto(productDto);
    }

    @Test
    @DisplayName("Test 1: Find all favourites - should return list with user and product info")
    void testFindAll_ShouldReturnFavouriteListWithDetails() {
        // Given
        Favourite favourite2 = new Favourite();
        favourite2.setLikeDate(LocalDateTime.now().minusDays(1));
        
        List<Favourite> favourites = Arrays.asList(favourite, favourite2);
        when(favouriteRepository.findAll()).thenReturn(favourites);
        when(restTemplate.getForObject(anyString(), eq(UserDto.class))).thenReturn(userDto);
        when(restTemplate.getForObject(anyString(), eq(ProductDto.class))).thenReturn(productDto);

        // When
        List<FavouriteDto> result = favouriteService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(favouriteRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Test 2: Find favourite by ID - should return favourite when found")
    void testFindById_WhenFavouriteExists_ShouldReturnFavourite() {
        // Given
        when(favouriteRepository.findById(any(FavouriteId.class))).thenReturn(Optional.of(favourite));
        when(restTemplate.getForObject(anyString(), eq(UserDto.class))).thenReturn(userDto);
        when(restTemplate.getForObject(anyString(), eq(ProductDto.class))).thenReturn(productDto);

        // When
        FavouriteDto result = favouriteService.findById(favouriteId);

        // Then
        assertNotNull(result);
        assertNotNull(result.getUserDto());
        assertNotNull(result.getProductDto());
        verify(favouriteRepository, times(1)).findById(favouriteId);
    }

    @Test
    @DisplayName("Test 3: Find favourite by ID - should throw exception when not found")
    void testFindById_WhenFavouriteDoesNotExist_ShouldThrowException() {
        // Given
        when(favouriteRepository.findById(any(FavouriteId.class))).thenReturn(Optional.empty());

        // When & Then
        assertThrows(FavouriteNotFoundException.class, () -> {
            favouriteService.findById(favouriteId);
        });
        verify(favouriteRepository, times(1)).findById(favouriteId);
    }

    @Test
    @DisplayName("Test 4: Save favourite - should persist and return saved favourite")
    void testSave_ShouldPersistAndReturnFavourite() {
        // Given
        when(favouriteRepository.save(any(Favourite.class))).thenReturn(favourite);

        // When
        FavouriteDto result = favouriteService.save(favouriteDto);

        // Then
        assertNotNull(result);
        verify(favouriteRepository, times(1)).save(any(Favourite.class));
    }

    @Test
    @DisplayName("Test 5: Delete favourite by ID - should invoke repository delete")
    void testDeleteById_ShouldInvokeRepositoryDelete() {
        // Given
        doNothing().when(favouriteRepository).deleteById(any(FavouriteId.class));

        // When
        favouriteService.deleteById(favouriteId);

        // Then
        verify(favouriteRepository, times(1)).deleteById(favouriteId);
    }

    @Test
    @DisplayName("Test 6: Verify favourite date - should not be in the future")
    void testLikeDate_ShouldNotBeInFuture() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        favouriteDto.setLikeDate(now);
        
        // When
        LocalDateTime likeDate = favouriteDto.getLikeDate();

        // Then
        assertNotNull(likeDate);
        assertFalse(likeDate.isAfter(LocalDateTime.now()), 
                    "Like date should not be in the future");
    }
}

