# Pruebas Unitarias - E-commerce Microservices

Este documento detalla las pruebas unitarias implementadas para los 6 microservicios seleccionados.

## Resumen de Pruebas Unitarias

Se han implementado un total de **37 pruebas unitarias** distribuidas en los 6 microservicios:

| Microservicio | Archivo de Pruebas | Número de Pruebas | Estado |
|--------------|-------------------|-------------------|---------|
| user-service | UserServiceImplTest.java | 7 pruebas | ✅ Completado |
| product-service | ProductServiceImplTest.java | 6 pruebas | ✅ Completado |
| order-service | OrderServiceImplTest.java | 7 pruebas | ✅ Completado |
| payment-service | PaymentServiceImplTest.java | 6 pruebas | ✅ Completado |
| shipping-service | OrderItemServiceImplTest.java | 5 pruebas | ✅ Completado |
| favourite-service | FavouriteServiceImplTest.java | 6 pruebas | ✅ Completado |

## Detalles de Pruebas por Microservicio

### 1. User Service (user-service)

**Archivo**: `user-service/src/test/java/com/selimhorri/app/service/UserServiceImplTest.java`

**Pruebas Implementadas**:
1. **testFindAll_ShouldReturnUserList**: Valida que se retorne la lista completa de usuarios
2. **testFindById_WhenUserExists_ShouldReturnUser**: Valida búsqueda exitosa de usuario por ID
3. **testFindById_WhenUserDoesNotExist_ShouldThrowException**: Valida excepción cuando usuario no existe
4. **testSave_ShouldPersistAndReturnUser**: Valida persistencia correcta de nuevo usuario
5. **testDeleteById_ShouldInvokeRepositoryDelete**: Valida eliminación de usuario
6. **testFindByUsername_WhenUserExists_ShouldReturnUser**: Valida búsqueda por username
7. **testFindByUsername_WhenUserDoesNotExist_ShouldThrowException**: Valida excepción cuando username no existe

**Cobertura**:
- ✅ Operaciones CRUD completas
- ✅ Manejo de excepciones
- ✅ Búsqueda por username
- ✅ Validación de datos

### 2. Product Service (product-service)

**Archivo**: `product-service/src/test/java/com/selimhorri/app/service/ProductServiceImplTest.java`

**Pruebas Implementadas**:
1. **testFindAll_ShouldReturnProductList**: Valida que se retorne la lista completa de productos
2. **testFindById_WhenProductExists_ShouldReturnProduct**: Valida búsqueda exitosa de producto por ID
3. **testFindById_WhenProductDoesNotExist_ShouldThrowException**: Valida excepción cuando producto no existe
4. **testSave_ShouldPersistAndReturnProduct**: Valida persistencia correcta de nuevo producto
5. **testDeleteById_ShouldInvokeRepositoryDelete**: Valida eliminación de producto
6. **testUpdate_ShouldUpdateAndReturnProduct**: Valida actualización de información de producto

**Cobertura**:
- ✅ Operaciones CRUD completas
- ✅ Validación de precios
- ✅ Gestión de stock
- ✅ Manejo de excepciones

### 3. Order Service (order-service)

**Archivo**: `order-service/src/test/java/com/selimhorri/app/service/OrderServiceImplTest.java`

**Pruebas Implementadas**:
1. **testFindAll_ShouldReturnOrderList**: Valida que se retorne la lista completa de órdenes
2. **testFindById_WhenOrderExists_ShouldReturnOrder**: Valida búsqueda exitosa de orden por ID
3. **testFindById_WhenOrderDoesNotExist_ShouldThrowException**: Valida excepción cuando orden no existe
4. **testSave_ShouldPersistAndReturnOrder**: Valida persistencia correcta de nueva orden
5. **testDeleteById_ShouldInvokeRepositoryDelete**: Valida eliminación de orden
6. **testUpdate_ShouldUpdateAndReturnOrder**: Valida actualización de información de orden
7. **testOrderFee_ShouldBePositive**: Valida que el monto de la orden sea válido

**Cobertura**:
- ✅ Operaciones CRUD completas
- ✅ Validación de montos
- ✅ Estados de orden
- ✅ Flujo de procesamiento

### 4. Payment Service (payment-service)

**Archivo**: `payment-service/src/test/java/com/selimhorri/app/service/PaymentServiceImplTest.java`

**Pruebas Implementadas**:
1. **testFindAll_ShouldReturnPaymentListWithOrderInfo**: Valida lista de pagos con información de órdenes
2. **testFindById_WhenPaymentExists_ShouldReturnPayment**: Valida búsqueda exitosa de pago por ID
3. **testFindById_WhenPaymentDoesNotExist_ShouldThrowException**: Valida excepción cuando pago no existe
4. **testSave_ShouldPersistAndReturnPayment**: Valida persistencia correcta de nuevo pago
5. **testDeleteById_ShouldInvokeRepositoryDelete**: Valida eliminación de pago
6. **testUpdate_ShouldUpdateAndReturnPayment**: Valida actualización de estado de pago

**Cobertura**:
- ✅ Operaciones CRUD completas
- ✅ Integración con order-service
- ✅ Estados de pago
- ✅ Validación de transacciones

### 5. Shipping Service (shipping-service)

**Archivo**: `shipping-service/src/test/java/com/selimhorri/app/service/OrderItemServiceImplTest.java`

**Pruebas Implementadas**:
1. **testFindAll_ShouldReturnOrderItemListWithDetails**: Valida lista de items con detalles
2. **testSave_ShouldPersistAndReturnOrderItem**: Valida persistencia correcta de item
3. **testDeleteById_ShouldInvokeRepositoryDelete**: Valida eliminación de item
4. **testUpdate_ShouldUpdateAndReturnOrderItem**: Valida actualización de item
5. **testOrderedQuantity_ShouldBePositive**: Valida cantidades válidas

**Cobertura**:
- ✅ Gestión de items de orden
- ✅ Integración con product-service y order-service
- ✅ Validación de cantidades
- ✅ Procesamiento de envíos

### 6. Favourite Service (favourite-service)

**Archivo**: `favourite-service/src/test/java/com/selimhorri/app/service/FavouriteServiceImplTest.java`

**Pruebas Implementadas**:
1. **testFindAll_ShouldReturnFavouriteListWithDetails**: Valida lista de favoritos con detalles
2. **testFindById_WhenFavouriteExists_ShouldReturnFavourite**: Valida búsqueda exitosa de favorito
3. **testFindById_WhenFavouriteDoesNotExist_ShouldThrowException**: Valida excepción cuando favorito no existe
4. **testSave_ShouldPersistAndReturnFavourite**: Valida persistencia correcta de favorito
5. **testDeleteById_ShouldInvokeRepositoryDelete**: Valida eliminación de favorito
6. **testLikeDate_ShouldNotBeInFuture**: Valida que la fecha de "me gusta" sea válida

**Cobertura**:
- ✅ Gestión de favoritos de usuario
- ✅ Integración con user-service y product-service
- ✅ Validación de fechas
- ✅ Relaciones compuestas

## Tecnologías Utilizadas

- **JUnit 5** (Jupiter): Framework de pruebas
- **Mockito**: Framework de mocking
- **Spring Boot Test**: Soporte para pruebas de Spring Boot

## Convenciones y Mejores Prácticas

### Nomenclatura
- Nombres descriptivos en formato: `test[Método]_[Escenario]_[ResultadoEsperado]`
- Uso de `@DisplayName` para descripciones legibles

### Estructura de Pruebas (Given-When-Then)
```java
@Test
void testMetodo_Escenario_ResultadoEsperado() {
    // Given - Configuración de datos y mocks
    when(mockRepository.method()).thenReturn(expectedValue);
    
    // When - Ejecución del método bajo prueba
    Result result = service.method();
    
    // Then - Verificaciones y assertions
    assertNotNull(result);
    verify(mockRepository, times(1)).method();
}
```

### Cobertura de Pruebas
Cada servicio incluye pruebas para:
1. **Happy Path**: Flujos exitosos de operaciones
2. **Edge Cases**: Casos límite y valores especiales
3. **Exception Handling**: Manejo correcto de excepciones
4. **Business Logic**: Validaciones de reglas de negocio
5. **Integration Points**: Interacciones con otros servicios

## Ejecutar las Pruebas

### Ejecutar todas las pruebas de un microservicio
```bash
cd user-service
../mvnw test
```

### Ejecutar una clase de prueba específica
```bash
../mvnw test -Dtest=UserServiceImplTest
```

### Ejecutar una prueba específica
```bash
../mvnw test -Dtest=UserServiceImplTest#testFindById_WhenUserExists_ShouldReturnUser
```

### Ejecutar todas las pruebas del proyecto
```bash
./mvnw clean test
```

### Generar reporte de cobertura (con JaCoCo)
```bash
./mvnw clean test jacoco:report
```

## Integración con Jenkins

Las pruebas unitarias están integradas en los pipelines de Jenkins:

1. **Stage: Unit Testing** - Se ejecutan automáticamente en cada build
2. **Requisito**: Todas las pruebas deben pasar para continuar el pipeline
3. **Reportes**: Los resultados se publican en Jenkins para visualización

```groovy
stage('Unit Testing') {
    steps {
        dir("${SERVICE_DIR}") {
            script {
                docker.image('maven:3.8.4-openjdk-11').inside {
                    sh 'mvn test -Dspring.profiles.active=dev'
                }
            }
        }
    }
}
```

## Métricas de Calidad

### Objetivos de Cobertura
- **Líneas de código**: > 80%
- **Branches**: > 75%
- **Métodos**: > 85%

### Tipos de Pruebas Implementadas
- ✅ **37 pruebas unitarias** (componentes individuales)
- ⏳ Pruebas de integración (en desarrollo)
- ⏳ Pruebas E2E (en desarrollo)
- ⏳ Pruebas de rendimiento (en desarrollo)

## Próximos Pasos

1. ✅ Implementar pruebas unitarias (COMPLETADO)
2. 🔄 Implementar pruebas de integración entre servicios
3. 🔄 Implementar pruebas E2E para flujos completos
4. 🔄 Implementar pruebas de rendimiento con Locust
5. 🔄 Configurar reportes de cobertura automáticos
6. 🔄 Integrar análisis estático de código (SonarQube)

## Mantenimiento

### Agregar Nuevas Pruebas
1. Crear clase de prueba en `src/test/java/` con el mismo package que la clase a probar
2. Usar `@ExtendWith(MockitoExtension.class)` para habilitar Mockito
3. Seguir convenciones de nomenclatura y estructura Given-When-Then
4. Documentar con `@DisplayName` descriptivo

### Actualizar Pruebas Existentes
1. Mantener sincronizadas con cambios en el código de producción
2. Actualizar mocks y datos de prueba según sea necesario
3. Revisar y actualizar assertions

## Recursos Adicionales

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Test-Driven Development (TDD)](https://martinfowler.com/bliki/TestDrivenDevelopment.html)

## Contacto y Soporte

Para preguntas o problemas relacionados con las pruebas:
- Crear issue en el repositorio
- Consultar con el equipo de DevOps
- Revisar documentación del proyecto

---

**Última actualización**: Octubre 2025  
**Versión**: 1.0  
**Autor**: Equipo de DevOps
