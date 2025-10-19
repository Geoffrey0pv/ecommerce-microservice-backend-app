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

import com.selimhorri.app.domain.User;
import com.selimhorri.app.domain.Credential;
import com.selimhorri.app.domain.RoleBasedAuthority;
import com.selimhorri.app.dto.UserDto;
import com.selimhorri.app.dto.CredentialDto;
import com.selimhorri.app.exception.wrapper.UserObjectNotFoundException;
import com.selimhorri.app.repository.UserRepository;
import com.selimhorri.app.service.impl.UserServiceImpl;

/**
 * Pruebas unitarias para UserServiceImpl
 * 
 * Estas pruebas validan las operaciones CRUD del servicio de usuarios
 * y el manejo de excepciones para casos no encontrados.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("User Service Unit Tests")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        // Configurar credencial de prueba
        Credential credential = new Credential();
        credential.setCredentialId(1);
        credential.setUsername("johndoe");
        credential.setPassword("password123");
        credential.setRoleBasedAuthority(RoleBasedAuthority.ROLE_USER);
        
        // Configurar datos de prueba del usuario
        user = new User();
        user.setUserId(1);
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");
        user.setPhone("1234567890");
        user.setCredential(credential);
        
        // Configurar credencial DTO de prueba
        CredentialDto credentialDto = new CredentialDto();
        credentialDto.setCredentialId(1);
        credentialDto.setUsername("johndoe");
        credentialDto.setPassword("password123");
        credentialDto.setRoleBasedAuthority(RoleBasedAuthority.ROLE_USER);
        
        userDto = new UserDto();
        userDto.setUserId(1);
        userDto.setFirstName("John");
        userDto.setLastName("Doe");
        userDto.setEmail("john.doe@example.com");
        userDto.setPhone("1234567890");
        userDto.setCredentialDto(credentialDto);
    }

    @Test
    @DisplayName("Test 1: Find all users - should return list of users")
    void testFindAll_ShouldReturnUserList() {
        // Given
        Credential credential2 = new Credential();
        credential2.setCredentialId(2);
        credential2.setUsername("janesmith");
        credential2.setPassword("password456");
        credential2.setRoleBasedAuthority(RoleBasedAuthority.ROLE_USER);
        
        User user2 = new User();
        user2.setUserId(2);
        user2.setFirstName("Jane");
        user2.setLastName("Smith");
        user2.setEmail("jane.smith@example.com");
        user2.setCredential(credential2);
        
        List<User> users = Arrays.asList(user, user2);
        when(userRepository.findAll()).thenReturn(users);

        // When
        List<UserDto> result = userService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Test 2: Find user by ID - should return user when found")
    void testFindById_WhenUserExists_ShouldReturnUser() {
        // Given
        when(userRepository.findById(anyInt())).thenReturn(Optional.of(user));

        // When
        UserDto result = userService.findById(1);

        // Then
        assertNotNull(result);
        assertEquals(user.getUserId(), result.getUserId());
        assertEquals(user.getFirstName(), result.getFirstName());
        assertEquals(user.getLastName(), result.getLastName());
        verify(userRepository, times(1)).findById(1);
    }

    @Test
    @DisplayName("Test 3: Find user by ID - should throw exception when not found")
    void testFindById_WhenUserDoesNotExist_ShouldThrowException() {
        // Given
        when(userRepository.findById(anyInt())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserObjectNotFoundException.class, () -> {
            userService.findById(999);
        });
        verify(userRepository, times(1)).findById(999);
    }

    @Test
    @DisplayName("Test 4: Save user - should persist and return saved user")
    void testSave_ShouldPersistAndReturnUser() {
        // Given
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        UserDto result = userService.save(userDto);

        // Then
        assertNotNull(result);
        assertEquals(user.getUserId(), result.getUserId());
        assertEquals(user.getEmail(), result.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Test 5: Delete user by ID - should invoke repository delete")
    void testDeleteById_ShouldInvokeRepositoryDelete() {
        // Given
        doNothing().when(userRepository).deleteById(anyInt());

        // When
        userService.deleteById(1);

        // Then
        verify(userRepository, times(1)).deleteById(1);
    }

    @Test
    @DisplayName("Test 6: Find user by username - should return user when found")
    void testFindByUsername_WhenUserExists_ShouldReturnUser() {
        // Given
        String username = "johndoe";
        when(userRepository.findByCredentialUsername(anyString())).thenReturn(Optional.of(user));

        // When
        UserDto result = userService.findByUsername(username);

        // Then
        assertNotNull(result);
        assertEquals(user.getUserId(), result.getUserId());
        verify(userRepository, times(1)).findByCredentialUsername(username);
    }

    @Test
    @DisplayName("Test 7: Find user by username - should throw exception when not found")
    void testFindByUsername_WhenUserDoesNotExist_ShouldThrowException() {
        // Given
        String username = "nonexistent";
        when(userRepository.findByCredentialUsername(anyString())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserObjectNotFoundException.class, () -> {
            userService.findByUsername(username);
        });
        verify(userRepository, times(1)).findByCredentialUsername(username);
    }
}

