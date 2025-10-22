# 🧪 Guía de Ejecución de Pruebas E2E

## 📋 Resumen de las 5 Pruebas E2E Creadas

### ✅ Pruebas Implementadas
1. **UserRegistrationFlowE2ETest** - Flujo completo de registro de usuarios
2. **ECommerceShoppingFlowE2ETest** - Journey completo de compras 
3. **MultiServiceIntegrationE2ETest** - Integración entre múltiples servicios
4. **ErrorHandlingAndResilienceE2ETest** - Manejo de errores y resiliencia
5. **PerformanceAndLoadE2ETest** - Métricas de rendimiento y carga

---

## 🚀 **PASO A PASO: Ejecución de Pruebas E2E**

### **Prerequisitos**
- ✅ **5 pruebas E2E** ya están creadas en `/tests/e2e/src/test/java/com/selimhorri/app/e2e/`
- ✅ **Maven configurado** en `/tests/e2e/pom.xml` 
- ⚠️ **RAM suficiente** para levantar 6 microservicios + bases de datos

---

### **Paso 1: Preparar el Entorno**
```bash
# Navegar al directorio raíz del proyecto
cd /home/geoffrey0pv/Projects/2025-2/Ingeosft5/ecommerce-microservice-backend-app

# Verificar que docker-compose.yml existe
ls -la compose.yml

# Verificar memoria disponible
free -h
```

**💡 Resultado Esperado:**
- Archivo `compose.yml` presente
- Al menos 4-6 GB de RAM libre para los microservicios

---

### **Paso 2: Levantar los Microservicios**
```bash
# Levantar todos los servicios en background
docker-compose up -d

# Verificar que todos los servicios están corriendo
docker-compose ps

# Monitorear logs (opcional)
docker-compose logs -f
```

**💡 Resultados Esperados:**
```
✅ api-gateway        : 8100 (UP)
✅ user-service       : 8200 (UP) 
✅ order-service      : 8300 (UP)
✅ product-service    : 8400 (UP)
✅ payment-service    : 8500 (UP)
✅ shipping-service   : 8600 (UP)
✅ service-discovery  : 8761 (UP)
✅ cloud-config       : 8888 (UP)
✅ MySQL/PostgreSQL   : Bases de datos (UP)
```

---

### **Paso 3: Verificar Conectividad de Servicios**
```bash
# Verificar API Gateway
curl http://localhost:8100/actuator/health

# Verificar User Service a través del Gateway
curl http://localhost:8100/user-service/api/users

# Verificar Product Service 
curl http://localhost:8100/product-service/api/products

# Verificar Order Service
curl http://localhost:8100/order-service/api/orders
```

**💡 Resultados Esperados:**
- ✅ Respuestas HTTP 200 o datos JSON válidos
- ✅ No errores de conexión

---

### **Paso 4: Ejecutar las Pruebas E2E**
```bash
# Navegar al módulo de pruebas E2E
cd tests/e2e

# Ejecutar TODAS las pruebas E2E
mvn test

# O ejecutar pruebas individuales:
mvn test -Dtest=UserRegistrationFlowE2ETest
mvn test -Dtest=ECommerceShoppingFlowE2ETest  
mvn test -Dtest=MultiServiceIntegrationE2ETest
mvn test -Dtest=ErrorHandlingAndResilienceE2ETest
mvn test -Dtest=PerformanceAndLoadE2ETest
```

**💡 Resultados Esperados:**
```
[INFO] Tests run: 25+, Failures: 0, Errors: 0, Skipped: 0

✅ UserRegistrationFlowE2ETest: 
   - testCompleteUserRegistrationFlow() ✅
   - testDuplicateUserPrevention() ✅  
   - testUserProfileUpdateFlow() ✅

✅ ECommerceShoppingFlowE2ETest:
   - testCompleteShoppingJourney() ✅
   - testCartAbandonmentRecovery() ✅
   - testInventoryManagement() ✅

✅ MultiServiceIntegrationE2ETest:
   - testCompleteSystemIntegrationWorkflow() ✅
   - testConcurrentMultiServiceOperations() ✅
   - testDataIntegrityAcrossServices() ✅

✅ ErrorHandlingAndResilienceE2ETest:
   - testInvalidDataHandling() ✅
   - testResourceNotFoundHandling() ✅
   - testBusinessLogicValidation() ✅
   - testConcurrentModificationHandling() ✅

✅ PerformanceAndLoadE2ETest:
   - testBasicResponseTimeMetrics() ✅
   - testConcurrentUserLoad() ✅  
   - testHighVolumeDataCreation() ✅
   - testDatabaseStressTest() ✅
   - testSystemThroughputMeasurement() ✅
   - testPerformanceBenchmarks() ✅
```

---

### **Paso 5: Analizar Métricas de Rendimiento**
Las pruebas mostrarán métricas detalladas:

```
📊 RESPONSE TIME METRICS:
==========================
User Service GET:
  Samples: 20
  Average: 145.30ms
  Min: 89ms
  Max: 287ms
  95th percentile: 245ms
  99th percentile: 278ms

👥 CONCURRENT LOAD RESULTS:
===========================
Active users: 10
Total requests: 50
Successful: 48
Failed: 2
Success rate: 96.0%

⚡ THROUGHPUT METRICS:
=====================
Duration: 30012ms
Total requests: 487
Total errors: 3
Requests/second: 16.23
Error rate: 0.62%

🏆 PERFORMANCE BENCHMARKS:
==========================
User CRUD:
  Operations: 10
  Total time: 1847ms
  Average time: 184.70ms
  Operations/sec: 5.41
  Success rate: 90.0%
```

---

## 🎯 **CRITERIOS DE ÉXITO**

### ✅ **Pruebas Funcionales (Tests 1-4)**
- **Success Rate**: > 90% de pruebas pasan
- **Flujos E2E**: Todos los workflows funcionan correctamente
- **Integración**: Servicios se comunican sin errores

### ✅ **Pruebas de Rendimiento (Test 5)**
- **Response Time**: Promedio < 2 segundos
- **Throughput**: > 10 requests/segundo
- **Error Rate**: < 5% de errores
- **Concurrent Load**: Maneja 10+ usuarios simultáneos

---

## 🚨 **Posibles Problemas y Soluciones**

### **Problema 1: Servicios no responden**
```bash
# Verificar estado
docker-compose ps

# Reiniciar servicio específico
docker-compose restart user-service

# Ver logs de errores
docker-compose logs user-service
```

### **Problema 2: Tests fallan por conectividad**
```bash
# Verificar puertos
netstat -tlnp | grep :810

# Verificar health endpoints
curl http://localhost:8100/actuator/health
```

### **Problema 3: OutOfMemory**
```bash
# Verificar memoria
docker stats

# Aumentar heap size
export MAVEN_OPTS="-Xmx2g -Xms1g"
```

---

## 🔄 **QUÉ SIGUE DESPUÉS**

### **Cuando las Pruebas E2E Pasen ✅**

#### **Siguiente Fase: Implementar Locust**
1. **📊 Crear scripts de Locust** para performance testing avanzado
2. **🐍 Python Locust setup** para simulación de carga real
3. **📈 Métricas avanzadas**: Percentiles, concurrencia, estrés prolongado

#### **Casos de Uso de Locust:**
```python
# Ejemplo de lo que haremos:
- 🧪 Load Testing: 100 usuarios por 10 minutos
- 🔥 Stress Testing: Ramp up hasta 500 usuarios  
- 📊 Spike Testing: Picos súbitos de tráfico
- ⏰ Endurance Testing: Carga sostenida por 1 hora
```

#### **Métricas Objetivos con Locust:**
- **👥 Usuarios Concurrentes**: 100-500 usuarios
- **⚡ Requests/segundo**: 50-200 RPS
- **📊 Response Time**: P95 < 1s, P99 < 2s  
- **🎯 Error Rate**: < 1% under normal load
- **💾 Resource Utilization**: CPU < 80%, RAM < 80%

### **Configuración del Entorno Completo**
1. **🐳 Docker Profiles** para diferentes entornos
2. **📋 Scripts de automatización** para CI/CD
3. **📊 Dashboards de métricas** con Grafana/Prometheus
4. **📝 Reportes automatizados** de rendimiento

---

## 📝 **Checklist de Ejecución**

- [ ] Verificar RAM disponible (6+ GB)
- [ ] Ejecutar `docker-compose up -d`
- [ ] Verificar servicios con `docker-compose ps`
- [ ] Probar conectividad con `curl` 
- [ ] Ejecutar `mvn test` en `/tests/e2e/`
- [ ] Revisar métricas de rendimiento
- [ ] Documentar resultados obtenidos
- [ ] ✅ **¡Listo para Locust!**

---

## 💡 **Comandos de Limpieza**

```bash
# Parar todos los servicios
docker-compose down

# Limpiar recursos
docker-compose down -v --remove-orphans

# Limpiar cache de Maven
mvn clean -f tests/e2e/pom.xml
```

---

**📞 ¿Problemas?** Esta guía cubre los escenarios más comunes. ¡Las pruebas están diseñadas para ser robustas y mostrar métricas detalladas!