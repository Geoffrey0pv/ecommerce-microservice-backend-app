# 🧪 Guía de Tests en Staging (GKE)

## 📋 Resumen

La pipeline de staging ahora ejecuta **tests E2E y de Performance** directamente en el cluster de GKE, generando reportes detallados con métricas clave.

---

## 🎯 Tests Implementados

### 1️⃣ **E2E Tests (End-to-End)**

#### ✨ Características:
- **Ejecutor**: Maven 3.8.6 + OpenJDK 17 en pod efímero
- **Código fuente**: Copiado desde `tests/e2e/` vía ConfigMap
- **Target**: API Gateway (`proxy-client`) en staging
- **Timeout**: 15 minutos máximo
- **Ubicación**: `tests/e2e/src/test/java/com/selimhorri/app/e2e/`

#### 📦 Tests Incluidos:
1. **UserRegistrationFlowE2ETest** - Registro de usuarios
2. **ECommerceShoppingFlowE2ETest** - Flujo completo de compras
3. **MultiServiceIntegrationE2ETest** - Integración entre servicios
4. **ErrorHandlingAndResilienceE2ETest** - Manejo de errores
5. **PerformanceAndLoadE2ETest** - Métricas de rendimiento

#### 📊 Reportes Generados:
```
test-results/e2e/
├── TEST-*.xml                    # Reportes JUnit XML
├── *.txt                         # Logs de tests
└── surefire-reports/             # Reportes Surefire completos
```

#### 🔍 Dónde Ver los Resultados:
- **Jenkins**: Tab "Test Results" (JUnit reports)
- **Artifacts**: Download completo de reportes XML/TXT
- **Console Output**: Logs en tiempo real de ejecución

---

### 2️⃣ **Performance Tests (Locust)**

#### ✨ Características:
- **Ejecutor**: Locust 2.17.0 en pod efímero
- **Usuarios**: 100 concurrentes
- **Spawn Rate**: 10 usuarios/segundo
- **Duración**: 5 minutos
- **Modo**: Headless (sin UI)
- **Target**: API Gateway en staging

#### 🎯 Escenario de Carga:
```python
# ecommerce_load_test.py ejecuta:
- LoadTestUser: Navegación y búsqueda (peso: 4)
- PeakHourUser: Tráfico de hora pico (peso: 3)
- BackgroundUser: Usuarios ocasionales (peso: 1)

Flujos simulados:
✓ Browse & Search (60% probabilidad)
✓ User Registration (nuevo usuario)
✓ Complete Purchase (25% probabilidad)
✓ Check Order Status
```

#### 📊 Reportes Generados:
```
test-results/performance/
├── load_test_report.html         # Reporte visual completo ⭐
├── load_test_stats.csv           # Estadísticas generales
├── load_test_stats_history.csv   # Historial temporal
├── load_test_failures.csv        # Fallos detectados
└── load_test_exceptions.csv      # Excepciones capturadas
```

#### 📈 Métricas Clave Analizadas:

##### 1. **Tiempo de Respuesta (Response Times)**
```
Formato: Avg=XXXms, Min=XXXms, Max=XXXms

Interpretación:
✅ Avg < 500ms   = Excelente
⚠️  Avg 500-1s   = Aceptable
❌ Avg > 1s      = Requiere optimización
```

##### 2. **Throughput (Requests per Second)**
```
Formato: XX.XX req/s

Interpretación:
✅ > 50 req/s    = Alto rendimiento
⚠️  20-50 req/s  = Rendimiento medio
❌ < 20 req/s    = Bajo rendimiento
```

##### 3. **Tasa de Errores (Failure Rate)**
```
Formato: XX.XX% (failures/total)

Interpretación:
✅ < 1%          = Sistema estable
⚠️  1-5%         = Requiere atención
❌ > 5%          = Sistema inestable
```

##### 4. **Percentiles de Latencia**
```
p50, p75, p90, p95, p99

Ejemplo:
- p50: 200ms  → 50% requests < 200ms
- p95: 800ms  → 95% requests < 800ms
- p99: 1.5s   → 99% requests < 1.5s

✅ p95 < 1s    = Experiencia de usuario excelente
⚠️  p95 1-2s   = Experiencia aceptable
❌ p95 > 2s    = Experiencia pobre
```

#### 🔍 Dónde Ver los Resultados:

##### **A. Jenkins Console Output**
La pipeline imprime automáticamente:
```bash
📊 ANÁLISIS DE MÉTRICAS DE PERFORMANCE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📈 Tiempo de Respuesta (Response Times):
  - GET /products: Avg=245.50ms, Min=120.00ms, Max=890.00ms
  - POST /orders: Avg=456.20ms, Min=200.00ms, Max=1200.00ms

🚦 Throughput (Requests per Second):
  - GET /products: 45.30 req/s
  - POST /orders: 12.50 req/s

❌ Tasa de Errores (Failure Rate):
  - GET /products: 0.50% (5/1000)
  - POST /orders: 2.30% (23/1000)
```

##### **B. HTML Report (Recomendado)**
Acceso: Jenkins → Build → "Locust Performance Report"

Incluye:
- 📊 Gráficos de tiempo de respuesta
- 📈 Historial de RPS (requests per second)
- 📉 Distribución de usuarios
- ❌ Detalle de fallos
- 📋 Tabla completa de estadísticas

##### **C. CSV Files**
Para análisis custom:
```bash
# Descargar artifacts de Jenkins
test-results/performance/load_test_stats.csv

# Columnas principales:
# Name, Requests, Failures, Median, Average, Min, Max, p90, p95, p99, RPS
```

---

## 🚀 Cómo Ejecutar

### Opción 1: Jenkins Pipeline (Recomendado)
```
1. Ir a Jenkins
2. Seleccionar job: "user-service-stage-pipeline"
3. Click "Build Now"
4. Esperar ~20-30 minutos total:
   - Deploy: 5 min
   - Health checks: 2 min
   - E2E Tests: 10-15 min
   - Performance Tests: 7-10 min
5. Ver resultados en tabs:
   - "Test Results" (E2E JUnit)
   - "Locust Performance Report" (HTML)
   - "Build Artifacts" (todos los archivos)
```

### Opción 2: Manual desde kubectl (Debug)
```bash
# Ver pods de tests activos
kubectl get pods -n staging | grep -E "(e2e|locust)"

# Ver logs en tiempo real
kubectl logs -f e2e-test-runner-XXX -n staging
kubectl logs -f locust-test-runner-XXX -n staging

# Copiar reportes manualmente
kubectl cp staging/locust-test-runner-XXX:/results/ ./locust-results/
```

---

## 📊 Interpretación de Resultados

### ✅ **Criterios de Éxito**

#### E2E Tests:
```
✅ Todos los tests pasan (verde en JUnit)
✅ Sin errores críticos en logs
✅ Integración entre servicios funciona
```

#### Performance Tests:
```
✅ Failure Rate < 1%
✅ Avg Response Time < 500ms
✅ p95 Latency < 1s
✅ Throughput > 50 req/s (para endpoints críticos)
✅ Sin errores 5xx en más del 0.5% de requests
```

### ⚠️ **Señales de Advertencia**

```
⚠️  Failure Rate entre 1-5%
    → Investigar logs, puede haber timeouts ocasionales

⚠️  Avg Response Time 500ms-1s
    → Revisar queries de BD, caching

⚠️  p95 > 1s
    → 5% de usuarios experimentan lentitud

⚠️  Throughput < 20 req/s
    → Posible bottleneck en recursos
```

### ❌ **Problemas Críticos**

```
❌ Failure Rate > 5%
    → Sistema inestable, rollback recomendado

❌ Avg Response Time > 1s
    → Experiencia de usuario inaceptable

❌ p95 > 2s
    → Mayoría de usuarios afectados

❌ Errores 5xx frecuentes
    → Servicios crasheando, revisar logs
```

---

## 🔧 Troubleshooting

### Problema: E2E Tests fallan
```bash
# Ver logs detallados
kubectl logs e2e-test-runner-XXX -n staging | grep -i error

# Verificar conectividad al gateway
kubectl exec -it e2e-test-runner-XXX -n staging -- curl http://10.22.10.27/app/actuator/health

# Revisar estado de microservicios
kubectl get pods -n staging
```

### Problema: Locust no genera reportes
```bash
# Verificar que el pod completó
kubectl get pod locust-test-runner-XXX -n staging

# Revisar si el volumen tiene archivos
kubectl exec locust-test-runner-XXX -n staging -- ls -la /results/

# Ver errores de Locust
kubectl logs locust-test-runner-XXX -n staging | grep -i error
```

### Problema: Performance pobre
```bash
# 1. Revisar recursos del pod
kubectl top pods -n staging

# 2. Verificar logs de aplicación
kubectl logs -l app=user-service -n staging --tail=100

# 3. Revisar métricas de BD (si aplica)
kubectl exec -it postgres-pod -n staging -- psql -c "SELECT * FROM pg_stat_activity;"

# 4. Escalar si necesario
kubectl scale deployment user-service -n staging --replicas=3
```

---

## 📁 Estructura de Archivos

```
ecommerce-microservice-backend-app/
├── tests/
│   ├── e2e/
│   │   ├── pom.xml                           # Maven config
│   │   └── src/test/java/.../e2e/
│   │       ├── UserRegistrationFlowE2ETest.java
│   │       ├── ECommerceShoppingFlowE2ETest.java
│   │       ├── MultiServiceIntegrationE2ETest.java
│   │       ├── ErrorHandlingAndResilienceE2ETest.java
│   │       └── PerformanceAndLoadE2ETest.java
│   │
│   └── performance/
│       ├── ecommerce_load_test.py            # Test principal
│       ├── user_behavior.py                  # Comportamiento de usuarios
│       ├── data_generator.py                 # Datos de prueba
│       ├── requirements.txt                  # Dependencias Python
│       ├── ecommerce_stress_test.py          # Test de estrés
│       ├── ecommerce_spike_test.py           # Test de picos
│       └── ecommerce_endurance_test.py       # Test de resistencia
│
├── jenkins-pipelines/
│   └── user-service-stage-pipeline.groovy    # Pipeline con tests
│
└── test-results/                             # Generados por pipeline
    ├── e2e/
    │   └── surefire-reports/                 # XML/TXT reports
    └── performance/
        ├── load_test_report.html             # ⭐ REPORTE PRINCIPAL
        ├── load_test_stats.csv
        ├── load_test_stats_history.csv
        └── load_test_failures.csv
```

---

## 🎓 Mejores Prácticas

### Durante Desarrollo:
1. ✅ **Ejecutar tests E2E** después de cambios importantes
2. ✅ **Ejecutar performance tests** antes de releases
3. ✅ **Comparar métricas** con builds anteriores
4. ✅ **Investigar degradaciones** de performance temprano

### En Producción:
1. ✅ **Establecer baselines** de performance aceptables
2. ✅ **Configurar alertas** si métricas degradan
3. ✅ **Archivar reportes** para análisis histórico
4. ✅ **Documentar optimizaciones** realizadas

### Métricas a Monitorear:
```
📊 KPIs Críticos:
- Response Time p95 < 1s
- Error Rate < 1%
- Throughput estable
- Resource utilization < 80%

📈 Tendencias:
- Comparar con último build exitoso
- Detectar degradaciones > 10%
- Identificar patrones de uso
```

---

## 🔗 Referencias

- [Locust Documentation](https://docs.locust.io/)
- [JUnit Reports](https://junit.org/junit5/docs/current/user-guide/)
- [Performance Testing Best Practices](https://martinfowler.com/articles/performance-testing.html)
- [SRE Book - Monitoring](https://sre.google/sre-book/monitoring-distributed-systems/)

---

## 📞 Soporte

¿Problemas con los tests? Revisa:
1. 📄 Logs de pipeline en Jenkins Console Output
2. 🐛 Logs de pods: `kubectl logs POD_NAME -n staging`
3. 📊 Estado del cluster: `kubectl get all -n staging`
4. 💾 Reportes archivados en Jenkins Artifacts

---

**Última actualización**: Noviembre 2, 2025  
**Versión**: 1.0.0
