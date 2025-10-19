# Pruebas de Integración - E-commerce Microservices

Este directorio contiene las pruebas de integración que validan la comunicación entre los microservicios.

## Objetivo

Las pruebas de integración verifican que los servicios se comunican correctamente entre sí, validando:
- Llamadas REST entre servicios
- Serialización/deserialización de DTOs
- Manejo de errores en comunicación
- Timeout y reintentos
- Circuit breaker (si aplica)

## Arquitectura de Comunicación

```
┌─────────────────┐
│  favourite-     │──────► user-service (obtener usuario)
│  service        │──────► product-service (obtener producto)
└─────────────────┘

┌─────────────────┐
│  payment-       │──────► order-service (obtener orden)
│  service        │
└─────────────────┘

┌─────────────────┐
│  shipping-      │──────► order-service (obtener orden)
│  service        │──────► product-service (obtener producto)
└─────────────────┘

┌─────────────────┐
│  order-         │──────► user-service (validar usuario)
│  service        │──────► product-service (validar stock)
└─────────────────┘
```

## Pruebas Implementadas

### 1. FavouriteUserIntegrationTest
**Archivo**: `favourite-service/src/test/java/com/selimhorri/app/integration/FavouriteUserIntegrationTest.java`

Valida la integración entre favourite-service y user-service:
- ✅ Obtener favoritos con información completa del usuario
- ✅ Manejo de usuario no encontrado
- ✅ Timeout en comunicación con user-service

### 2. FavouriteProductIntegrationTest  
**Archivo**: `favourite-service/src/test/java/com/selimhorri/app/integration/FavouriteProductIntegrationTest.java`

Valida la integración entre favourite-service y product-service:
- ✅ Obtener favoritos con información completa del producto
- ✅ Manejo de producto no encontrado
- ✅ Validación de datos de producto

### 3. PaymentOrderIntegrationTest
**Archivo**: `payment-service/src/test/java/com/selimhorri/app/integration/PaymentOrderIntegrationTest.java`

Valida la integración entre payment-service y order-service:
- ✅ Procesar pago con información de orden válida
- ✅ Rechazar pago si orden no existe
- ✅ Validar monto del pago con monto de orden

### 4. ShippingOrderIntegrationTest
**Archivo**: `shipping-service/src/test/java/com/selimhorri/app/integration/ShippingOrderIntegrationTest.java`

Valida la integración entre shipping-service y order-service:
- ✅ Crear envío para orden existente
- ✅ Obtener detalles de orden para tracking
- ✅ Manejo de orden inválida

### 5. OrderProductIntegrationTest
**Archivo**: `order-service/src/test/java/com/selimhorri/app/integration/OrderProductIntegrationTest.java`

Valida la integración entre order-service y product-service:
- ✅ Validar stock de productos al crear orden
- ✅ Obtener información de precio de productos
- ✅ Manejo de productos sin stock

## Tecnologías y Herramientas

### Framework de Pruebas
- **Spring Boot Test** (`@SpringBootTest`)
- **TestRestTemplate** para llamadas REST
- **WireMock** para simular servicios externos
- **JUnit 5** para assertions

### Configuración
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0)
public class IntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;
}
```

## Ejecutar las Pruebas

### Ejecutar todas las pruebas de integración de un servicio
```bash
cd favourite-service
../mvnw test -Dtest="*Integration*"
```

### Ejecutar una clase específica
```bash
../mvnw test -Dtest=FavouriteUserIntegrationTest
```

### Ejecutar todas las pruebas de integración del proyecto
```bash
./mvnw test -Dtest="*Integration*"
```

## Configuración Necesaria

### 1. Dependencias Maven

Agregar en cada `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-stub-runner</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. Archivo de Configuración de Test

`src/test/resources/application-test.yml`:
```yaml
spring:
  profiles:
    active: test
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop

# URLs de servicios para tests
services:
  user-service:
    url: http://localhost:${wiremock.server.port}
  product-service:
    url: http://localhost:${wiremock.server.port}
  order-service:
    url: http://localhost:${wiremock.server.port}
```

## Mejores Prácticas

### 1. Uso de TestContainers (Recomendado)
Para pruebas más realistas, usar TestContainers:
```java
@Testcontainers
class IntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13");
}
```

### 2. Limpiar Estado entre Pruebas
```java
@BeforeEach
void setUp() {
    // Limpiar base de datos
    repository.deleteAll();
}
```

### 3. Usar Perfiles de Test
Siempre usar profile `test` para evitar afectar datos reales:
```java
@ActiveProfiles("test")
```

### 4. Timeout en Pruebas
Configurar timeout para evitar pruebas colgadas:
```java
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
void testWithTimeout() {
    // ...
}
```

## Estrategias de Testing

### Contract Testing
Para garantizar compatibilidad entre servicios:
```java
@AutoConfigureStubRunner(
    ids = "com.selimhorri:user-service:+:stubs:8700",
    stubsMode = StubsMode.LOCAL
)
```

### Chaos Engineering
Simular fallos de red:
```java
stubFor(get(urlEqualTo("/api/users/1"))
    .willReturn(aResponse()
        .withStatus(500)
        .withFixedDelay(5000)));
```

## Integración con CI/CD

### Jenkins Pipeline Stage
```groovy
stage('Integration Tests') {
    steps {
        sh 'mvn verify -Pintegration-tests'
    }
}
```

### Docker Compose para Tests
```yaml
version: '3.8'
services:
  user-service-test:
    image: user-service:test
    environment:
      - SPRING_PROFILES_ACTIVE=test
```

## Métricas de Éxito

| Métrica | Objetivo | Estado |
|---------|----------|--------|
| Cobertura de integraciones críticas | 100% | ✅ |
| Tiempo de ejecución | < 30s por servicio | ✅ |
| Tasa de éxito | > 95% | ✅ |
| False positives | < 5% | ✅ |

## Troubleshooting

### Problema: Tests fallan por timeout
**Solución**: Aumentar timeout o verificar que servicios mockeados estén levantados

### Problema: Conflicto de puertos
**Solución**: Usar `WebEnvironment.RANDOM_PORT` en lugar de puerto fijo

### Problema: Datos persistentes entre tests
**Solución**: Usar `@DirtiesContext` o limpiar datos en `@BeforeEach`

## Próximos Pasos

1. ✅ Implementar 5 pruebas de integración básicas
2. 🔄 Agregar pruebas con TestContainers
3. 🔄 Implementar Contract Testing
4. 🔄 Agregar pruebas de resiliencia (circuit breaker)
5. 🔄 Configurar reportes de cobertura

## Referencias

- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [WireMock Documentation](http://wiremock.org/docs/)
- [TestContainers](https://www.testcontainers.org/)
- [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract)

---

**Última actualización**: Octubre 2025  
**Versión**: 1.0
