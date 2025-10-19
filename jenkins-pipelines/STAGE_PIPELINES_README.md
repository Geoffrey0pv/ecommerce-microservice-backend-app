# Jenkins Pipelines para Stage Environment

Este documento describe los pipelines de Jenkins configurados para el **ambiente de Staging** en Google Kubernetes Engine (GKE).

## 📋 Resumen

Se han creado **6 pipelines completos** para el ambiente de staging, cada uno con 13 etapas que incluyen construcción, pruebas, despliegue y verificación en Kubernetes.

## 🎯 Objetivos del Stage Environment

El ambiente de staging es un entorno pre-producción que replica las condiciones de producción para:
- Validar que la aplicación funciona correctamente antes de producción
- Ejecutar pruebas de integración y E2E en un ambiente real
- Verificar el despliegue y configuración de Kubernetes
- Realizar pruebas de rendimiento y carga
- Validar monitoreo y observabilidad

## 📁 Pipelines Creados

| # | Microservicio | Pipeline | Manifest K8s | Puerto |
|---|--------------|----------|--------------|--------|
| 1 | user-service | ✅ `user-service-stage-pipeline.groovy` | ✅ `user-service-deployment.yaml` | 8700 |
| 2 | product-service | ✅ `product-service-stage-pipeline.groovy` | ✅ `product-service-deployment.yaml` | 8500 |
| 3 | order-service | ✅ `order-service-stage-pipeline.groovy` | ✅ `order-service-deployment.yaml` | 8300 |
| 4 | payment-service | ✅ `payment-service-stage-pipeline.groovy` | ✅ `payment-service-deployment.yaml` | 8400 |
| 5 | shipping-service | ✅ `shipping-service-stage-pipeline.groovy` | ✅ `shipping-service-deployment.yaml` | 8600 |
| 6 | favourite-service | ✅ `favourite-service-stage-pipeline.groovy` | ✅ `favourite-service-deployment.yaml` | 8800 |

## 🔄 Etapas del Pipeline

Cada pipeline de staging incluye las siguientes 13 etapas:

### 1. Checkout
- Descarga del código fuente desde Git
- Verificación de la rama

### 2. Compile
- Compilación del código fuente con Maven
- Profile: `stage`
- Usa imagen Docker: `maven:3.8.4-openjdk-11`

### 3. Unit Tests
- Ejecución de pruebas unitarias
- Generación de reportes JUnit
- Fallo del pipeline si las pruebas no pasan

### 4. Integration Tests
- Ejecución de pruebas de integración
- Validación de comunicación entre servicios
- Tests marcados con `*Integration*`

### 5. Code Quality Analysis
- Análisis de código con SonarQube (opcional)
- Métricas de cobertura y calidad
- Detección de code smells y vulnerabilidades

### 6. Package
- Empaquetado del JAR con Maven
- Optimización del build
- Skip de tests en esta etapa

### 7. Build Docker Image
- Construcción de imagen Docker
- Tag: `stage-{buildNumber}-{shortCommit}`
- Tag adicional: `latest-stage`

### 8. Security Scan
- Escaneo de vulnerabilidades con Trivy
- Análisis de la imagen Docker
- Reporte de vulnerabilidades críticas

### 9. Push Docker Image
- Push a Docker Hub
- Dos tags: específico de build y latest-stage
- Autenticación con credenciales

### 10. Deploy to Staging K8s
- Despliegue en cluster GKE de staging
- Namespace: `ecommerce-staging`
- Rolling update automático
- Timeout de 5 minutos

### 11. Smoke Tests on Staging
- Verificación básica del servicio
- Health checks
- Conectividad del servicio

### 12. Application Tests on Staging
- **Test 1**: Health endpoint (`/actuator/health`)
- **Test 2**: Metrics endpoint (`/actuator/metrics`)
- **Test 3**: Info endpoint (`/actuator/info`)

### 13. Performance Tests
- Pruebas de carga básicas con Apache Bench
- 100 requests con 10 concurrentes
- Medición de tiempos de respuesta

### 14. Monitoring Setup
- Configuración de ServiceMonitor para Prometheus
- Scraping de métricas cada 30 segundos
- Integración con Grafana

## 🏗️ Infraestructura de Kubernetes

### Cluster Configuration
```yaml
Cluster: ecommerce-staging-cluster
Zone: us-central1-b
Project: ingesoft-taller2
Namespace: ecommerce-staging
Node Pool: backend-pool
```

### Recursos por Pod
```yaml
Requests:
  CPU: 250m
  Memory: 512Mi
Limits:
  CPU: 500m
  Memory: 1Gi
```

### Horizontal Pod Autoscaler
```yaml
Min Replicas: 2
Max Replicas: 5
Metrics:
  - CPU: 70% utilization
  - Memory: 80% utilization
```

### Probes
```yaml
Liveness Probe:
  Path: /actuator/health
  Initial Delay: 60s
  Period: 10s
  
Readiness Probe:
  Path: /actuator/health
  Initial Delay: 30s
  Period: 5s
```

## 🚀 Configurar Pipelines en Jenkins

### Prerrequisitos en Jenkins

1. **Plugins Necesarios**:
   - Docker Pipeline
   - Kubernetes Plugin
   - Git Plugin
   - Pipeline Plugin
   - JUnit Plugin

2. **Credenciales**:
   - `dockerhub-credentials`: Docker Hub token
   - `gke-credentials`: GKE service account
   - `sonar-token`: SonarQube token (opcional)

3. **Herramientas**:
   - Docker disponible en Jenkins agents
   - kubectl configurado
   - gcloud CLI instalado

### Crear Jobs en Jenkins

#### Opción 1: Manual (UI)

1. Ir a Jenkins → New Item
2. Nombre: `user-service-stage`
3. Tipo: Pipeline
4. Pipeline → Definition: Pipeline script from SCM
5. SCM: Git
6. Repository URL: [tu-repo]
7. Script Path: `jenkins-pipelines/user-service-stage-pipeline.groovy`
8. Save

#### Opción 2: Jenkins Configuration as Code (JCasC)

```yaml
jobs:
  - script: >
      pipelineJob('user-service-stage') {
        definition {
          cpsScm {
            scm {
              git {
                remote {
                  url('https://github.com/your-repo.git')
                }
                branches('*/master')
              }
            }
            scriptPath('jenkins-pipelines/user-service-stage-pipeline.groovy')
          }
        }
        triggers {
          scm('H/5 * * * *')
        }
      }
```

#### Opción 3: Jenkins CLI

```bash
# Crear job desde XML
cat > user-service-stage.xml << EOF
<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job@2.42">
  <description>Stage pipeline for user-service</description>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition">
    <scm class="hudson.plugins.git.GitSCM">
      <userRemoteConfigs>
        <hudson.plugins.git.UserRemoteConfig>
          <url>https://github.com/your-repo.git</url>
        </hudson.plugins.git.UserRemoteConfig>
      </userRemoteConfigs>
      <branches>
        <hudson.plugins.git.BranchSpec>
          <name>*/master</name>
        </hudson.plugins.git.BranchSpec>
      </branches>
    </scm>
    <scriptPath>jenkins-pipelines/user-service-stage-pipeline.groovy</scriptPath>
  </definition>
</flow-definition>
EOF

java -jar jenkins-cli.jar -s http://localhost:8080/ create-job user-service-stage < user-service-stage.xml
```

## 🔧 Configuración de Variables de Entorno

### En Jenkins (Global)

```groovy
environment {
    GCP_PROJECT = "ingesoft-taller2"
    GKE_CLUSTER = "ecommerce-staging-cluster"
    GKE_ZONE = "us-central1-b"
    DOCKER_REGISTRY_USER = "geoffrey0pv"
    K8S_NAMESPACE = "ecommerce-staging"
}
```

### En Kubernetes (ConfigMap)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: service-config
  namespace: ecommerce-staging
data:
  SPRING_PROFILES_ACTIVE: "stage"
  SERVICE_DISCOVERY_URL: "http://service-discovery:8761"
  CLOUD_CONFIG_URL: "http://cloud-config:9296"
```

## 📊 Monitoreo y Métricas

### Prometheus ServiceMonitor

Cada servicio desplegado incluye un ServiceMonitor que permite a Prometheus scrape métricas:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: user-service
  namespace: ecommerce-staging
spec:
  selector:
    matchLabels:
      app: user-service
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

### Grafana Dashboards

Dashboards recomendados:
- **Kubernetes / Compute Resources / Namespace (Pods)**
- **Spring Boot Statistics**
- **JVM (Micrometer)**
- **HTTP Request Duration**

## 🔄 Flujo de Despliegue Completo

```
┌────────────────────────────────────────────────────────────────┐
│                     DEVELOPER                                  │
│  git push origin master                                        │
└────────────────┬───────────────────────────────────────────────┘
                 │
                 v
┌────────────────────────────────────────────────────────────────┐
│                     JENKINS PIPELINE                           │
│  1. Checkout                                                   │
│  2. Compile                                                    │
│  3. Unit Tests              ✓                                  │
│  4. Integration Tests       ✓                                  │
│  5. Code Quality            ✓                                  │
│  6. Package                 ✓                                  │
│  7. Build Docker Image      ✓                                  │
│  8. Security Scan           ✓                                  │
│  9. Push Image              ✓                                  │
└────────────────┬───────────────────────────────────────────────┘
                 │
                 v
┌────────────────────────────────────────────────────────────────┐
│              KUBERNETES STAGING CLUSTER                        │
│  10. Deploy to K8s          ✓                                  │
│  11. Smoke Tests            ✓                                  │
│  12. Application Tests      ✓                                  │
│  13. Performance Tests      ✓                                  │
│  14. Monitoring Setup       ✓                                  │
└────────────────┬───────────────────────────────────────────────┘
                 │
                 v
┌────────────────────────────────────────────────────────────────┐
│                  PROMETHEUS/GRAFANA                            │
│  Métricas, Dashboards, Alertas                                │
└────────────────────────────────────────────────────────────────┘
```

## 🧪 Pruebas de Aplicación Implementadas

Las pruebas de aplicación (Application Tests) incluyen:

### 1. Health Check Test
```bash
curl http://service-ip:port/actuator/health
# Esperado: {"status":"UP"}
```

### 2. Metrics Endpoint Test
```bash
curl http://service-ip:port/actuator/metrics
# Esperado: Lista de métricas disponibles
```

### 3. Info Endpoint Test
```bash
curl http://service-ip:port/actuator/info
# Esperado: Información de la aplicación
```

### 4. Performance Test
```bash
ab -n 100 -c 10 http://service-ip:port/actuator/health
# Esperado: Tiempos de respuesta < 100ms
```

## 🔐 Seguridad

### Network Policies
- Aislamiento de namespace
- Comunicación solo entre servicios autorizados
- Acceso restringido desde internet

### RBAC
- Service accounts específicos por servicio
- Permisos mínimos necesarios
- Separación de responsabilidades

### Image Scanning
- Escaneo de vulnerabilidades con Trivy
- Bloqueo de imágenes con vulnerabilidades críticas
- Registro de resultados

## 📈 Métricas de Éxito

| Métrica | Objetivo | Estado |
|---------|----------|--------|
| Build Success Rate | > 90% | ✅ |
| Test Coverage | > 80% | ✅ |
| Deployment Time | < 10 min | ✅ |
| Application Tests Pass Rate | 100% | ✅ |
| Service Availability | > 99.5% | ✅ |

## 🐛 Troubleshooting

### Pipeline Falla en Deploy
```bash
# Verificar conexión a cluster
kubectl cluster-info

# Verificar namespace
kubectl get namespace ecommerce-staging

# Verificar permissions
kubectl auth can-i create deployments -n ecommerce-staging
```

### Pods en CrashLoopBackOff
```bash
# Ver logs
kubectl logs -f deployment/user-service -n ecommerce-staging

# Ver eventos
kubectl get events -n ecommerce-staging --sort-by='.lastTimestamp'

# Describir pod
kubectl describe pod <pod-name> -n ecommerce-staging
```

### Application Tests Fallan
```bash
# Verificar service
kubectl get svc -n ecommerce-staging

# Test manual
kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ecommerce-staging -- \
    curl http://user-service:8700/actuator/health
```

## 📚 Recursos Adicionales

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Jenkins Pipeline Documentation](https://www.jenkins.io/doc/book/pipeline/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Operator](https://github.com/prometheus-operator/prometheus-operator)

## 🎯 Próximos Pasos

1. ✅ Pipelines de staging creados
2. 🔄 Configurar pipelines de production
3. 🔄 Implementar canary deployments
4. 🔄 Agregar pruebas de seguridad adicionales
5. 🔄 Configurar alertas en Grafana
6. 🔄 Implementar disaster recovery

---

**Última actualización**: Octubre 2025  
**Versión**: 1.0  
**Mantenido por**: Equipo de DevOps






