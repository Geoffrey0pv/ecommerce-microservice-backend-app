# Pruebas End-to-End (E2E) - E-commerce Microservices

Este directorio contiene las pruebas E2E que validan flujos completos de usuario a través de múltiples microservicios.

## Objetivo

Las pruebas E2E verifican que los flujos de negocio funcionan correctamente de extremo a extremo, simulando acciones reales de usuarios y validando que todos los servicios involucrados respondan correctamente.

## Flujos de Usuario Implementados

### 1. User Registration and Authentication Flow
**Flujo**: Usuario se registra → Verifica email → Inicia sesión → Accede al sistema

**Servicios Involucrados**:
- user-service
- api-gateway
- proxy-client (autenticación)

### 2. Product Browse and Favorite Flow
**Flujo**: Usuario busca productos → Ve detalles → Agrega a favoritos → Consulta lista de favoritos

**Servicios Involucrados**:
- user-service
- product-service
- favourite-service
- api-gateway

### 3. Complete Purchase Flow
**Flujo**: Usuario añade productos al carrito → Crea orden → Procesa pago → Confirma compra

**Servicios Involucrados**:
- user-service
- product-service
- order-service
- payment-service
- api-gateway

### 4. Order Shipping and Tracking Flow
**Flujo**: Orden creada → Pago confirmado → Envío generado → Usuario rastrea envío

**Servicios Involucrados**:
- order-service
- payment-service
- shipping-service
- api-gateway

### 5. Complete E-commerce Journey
**Flujo**: Registro → Búsqueda de productos → Favoritos → Compra → Pago → Envío → Tracking

**Servicios Involucrados**:
- **Todos los 6 microservicios**

## Estructura de Pruebas E2E

```
tests/e2e/
├── README.md
├── UserRegistrationE2ETest.java (Test 1)
├── ProductBrowseFavouriteE2ETest.java (Test 2)
├── CompletePurchaseE2ETest.java (Test 3)
├── OrderShippingTrackingE2ETest.java (Test 4)
└── CompleteEcommerceJourneyE2ETest.java (Test 5)
```

## Pruebas Implementadas

| # | Prueba | Servicios | Descripción | Estado |
|---|--------|-----------|-------------|---------|
| 1 | UserRegistrationE2ETest | user-service, proxy-client | Registro y autenticación completa | ✅ |
| 2 | ProductBrowseFavouriteE2ETest | product-service, favourite-service, user-service | Navegación y favoritos | ✅ |
| 3 | CompletePurchaseE2ETest | order-service, payment-service, product-service, user-service | Flujo de compra completo | ✅ |
| 4 | OrderShippingTrackingE2ETest | order-service, payment-service, shipping-service | Envío y seguimiento | ✅ |
| 5 | CompleteEcommerceJourneyE2ETest | Todos | Viaje completo del usuario | ✅ |

## Tecnologías y Herramientas

### Framework de Pruebas
- **Spring Boot Test** con `@SpringBootTest`
- **TestRestTemplate** para llamadas HTTP
- **JUnit 5** para assertions y organización
- **Testcontainers** (opcional) para servicios reales

### Configuración de Ambiente E2E
```java
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("e2e")
public class E2ETest {
    @Autowired
    private TestRestTemplate restTemplate;
    
    private String baseUrl = "http://localhost:8080/api";
}
```

## Ejecutar las Pruebas E2E

### Prerrequisitos
1. Todos los microservicios deben estar corriendo
2. Base de datos de prueba configurada
3. API Gateway accesible

### Levantar Servicios para E2E
```bash
# Opción 1: Con Docker Compose
docker-compose -f compose.yml up -d

# Opción 2: Individualmente
cd user-service && ./mvnw spring-boot:run -Dspring.profiles.active=e2e &
cd product-service && ./mvnw spring-boot:run -Dspring.profiles.active=e2e &
# ... repetir para todos los servicios
```

### Ejecutar Pruebas E2E
```bash
# Ejecutar todas las pruebas E2E
./mvnw test -Dtest="*E2E*" -Pe2e

# Ejecutar una prueba específica
./mvnw test -Dtest=UserRegistrationE2ETest -Pe2e

# Con reporte detallado
./mvnw test -Dtest="*E2E*" -Pe2e -Dmaven.test.failure.ignore=true
```

## Configuración del Perfil E2E

### application-e2e.yml
```yaml
spring:
  profiles:
    active: e2e
  datasource:
    url: jdbc:h2:mem:e2edb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop

server:
  port: ${E2E_PORT:8080}

# Configuración de servicios
services:
  api-gateway:
    url: http://localhost:8080
  user-service:
    url: http://localhost:8700
  product-service:
    url: http://localhost:8500
  order-service:
    url: http://localhost:8300
  payment-service:
    url: http://localhost:8400
  shipping-service:
    url: http://localhost:8600
  favourite-service:
    url: http://localhost:8800
```

## Mejores Prácticas

### 1. Orden de Ejecución
Usar `@TestMethodOrder` para garantizar orden:
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E2ETest {
    @Test
    @Order(1)
    void step1_RegisterUser() { }
    
    @Test
    @Order(2)
    void step2_Login() { }
}
```

### 2. Limpieza de Estado
```java
@AfterEach
void cleanUp() {
    // Limpiar datos creados durante la prueba
    testDataCleaner.cleanAll();
}
```

### 3. Timeouts y Esperas
```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
void testWithTimeout() {
    // Esperar a que el servicio esté listo
    await().atMost(10, SECONDS)
           .until(() -> isServiceReady());
}
```

### 4. Datos de Prueba Realistas
```java
@BeforeAll
static void setupTestData() {
    // Crear datos de prueba realistas
    TestDataBuilder.createUsers(10);
    TestDataBuilder.createProducts(50);
}
```

## Patrón de Prueba E2E

### Estructura Given-When-Then
```java
@Test
@DisplayName("Complete purchase flow should succeed")
void testCompletePurchaseFlow() {
    // Given - Usuario autenticado y producto disponible
    String authToken = authenticateUser("john@example.com", "password");
    Integer productId = getAvailableProduct();
    
    // When - Usuario realiza compra
    OrderDto order = createOrder(authToken, productId);
    PaymentDto payment = processPayment(authToken, order.getOrderId());
    
    // Then - Orden y pago exitosos
    assertNotNull(order.getOrderId());
    assertTrue(payment.getIsPayed());
    assertEquals(order.getOrderId(), payment.getOrderDto().getOrderId());
}
```

## Estrategias de Testing

### 1. Smoke Tests
Pruebas rápidas para verificar que el sistema está operativo:
```java
@Test
@Tag("smoke")
void allServicesAreUp() {
    assertServiceIsUp("user-service");
    assertServiceIsUp("product-service");
    // ...
}
```

### 2. Happy Path
Flujos principales sin errores:
```java
@Test
@Tag("happy-path")
void userCanCompletePurchaseSuccessfully() {
    // Flujo completo sin errores
}
```

### 3. Error Scenarios
Manejo de errores y casos límite:
```java
@Test
@Tag("error-scenario")
void purchaseFailsWhenProductOutOfStock() {
    // Validar manejo de errores
}
```

## Integración con CI/CD

### Jenkins Pipeline Stage
```groovy
stage('E2E Tests') {
    steps {
        sh '''
            docker-compose up -d
            sleep 30 # Esperar que servicios estén listos
            mvn test -Dtest="*E2E*" -Pe2e
            docker-compose down
        '''
    }
    post {
        always {
            junit '**/target/surefire-reports/*.xml'
        }
    }
}
```

### GitHub Actions Workflow
```yaml
- name: Run E2E Tests
  run: |
    docker-compose up -d
    sleep 30
    mvn test -Dtest="*E2E*" -Pe2e
    docker-compose down
```

## Métricas de Calidad

| Métrica | Objetivo | Estado |
|---------|----------|--------|
| Cobertura de flujos críticos | 100% | ✅ |
| Tiempo de ejecución | < 5 min | ✅ |
| Tasa de éxito | > 90% | ✅ |
| Flakiness | < 10% | ✅ |

## Troubleshooting

### Problema: Servicios no están listos
**Solución**: Aumentar tiempo de espera o implementar health checks

### Problema: Datos inconsistentes entre pruebas
**Solución**: Implementar limpieza robusta en `@AfterEach`

### Problema: Timeouts aleatorios
**Solución**: Aumentar timeouts o investigar cuellos de botella

### Problema: Fallos en CI pero no en local
**Solución**: Verificar configuración de red y recursos en CI

## Herramientas Adicionales

### Para Pruebas E2E Avanzadas
- **Selenium WebDriver**: Para pruebas de UI
- **Cucumber**: Para BDD (Behavior Driven Development)
- **Rest Assured**: Para API testing más expresivo
- **WireMock**: Para simular servicios externos

## Ejemplo de Uso de Rest Assured
```java
given()
    .auth().oauth2(token)
    .contentType(ContentType.JSON)
    .body(orderRequest)
.when()
    .post("/api/orders")
.then()
    .statusCode(201)
    .body("orderId", notNullValue())
    .body("orderFee", equalTo(1299.99f));
```

## Próximos Pasos

1. ✅ Implementar 5 pruebas E2E básicas
2. 🔄 Agregar pruebas con Selenium para UI
3. 🔄 Implementar BDD con Cucumber
4. 🔄 Agregar pruebas de carga ligera
5. 🔄 Configurar reportes visuales (Allure)

## Referencias

- [Spring Boot Testing Best Practices](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Rest Assured](https://rest-assured.io/)
- [Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/)

---

**Última actualización**: Octubre 2025  
**Versión**: 1.0
