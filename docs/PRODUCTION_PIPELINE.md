# 🚀 Pipeline de Despliegue a Producción - Documentación

## 📋 Descripción General

Pipeline completo de CI/CD para despliegue del microservicio **user-service** en ambiente de **PRODUCCIÓN** en Kubernetes (GKE). Implementa todas las mejores prácticas de DevOps, Change Management y Release Engineering.

**Archivo**: `jenkins-pipelines/user-service-production-pipeline.groovy`

---

## 🎯 Requisitos Cumplidos (15% del Proyecto)

### ✅ 1. Construcción Incluyendo Pruebas Unitarias
- **Fase**: "Build & Unit Tests"
- **Herramienta**: Maven
- **Comando**: `mvn clean package`
- **Validación**: Compilación del código fuente y ejecución de JUnit tests
- **Artefactos**: JAR file archivado en Jenkins

### ✅ 2. Validación de Pruebas de Sistema (E2E)
- **Fase**: "E2E / System Tests"
- **Tipo**: End-to-End tests contra ambiente staging
- **Framework**: JUnit + Spring Boot Test + RestTemplate
- **Validación**: Pruebas de integración completas entre microservicios
- **Autenticación**: Tests con JWT tokens generados dinámicamente

### ✅ 3. Despliegue en Kubernetes
- **Fase**: "Deploy to Production"
- **Orquestador**: Kubernetes (Google Kubernetes Engine - GKE)
- **Herramienta**: Helm Charts
- **Namespace**: `production`
- **Estrategia**: RollingUpdate (zero-downtime deployment)
- **Health Checks**: Liveness y Readiness probes configurados

### ✅ 4. Release Notes Automáticas (Change Management)
- **Fase**: "Generate Release Notes"
- **Formato**: Markdown profesional
- **Contenido Incluido**:
  - Información de versión y build
  - Lista de cambios (commits)
  - Quality gates ejecutados
  - Comandos útiles de operación
  - Plan de rollback
  - Información de seguridad y compliance
  - Contactos de soporte

### ✅ 5. Fases Adicionales de Calidad
- **Code Coverage**: Análisis con JaCoCo
- **Dependency Check**: Verificación de dependencias vulnerables
- **Security Scan**: Revisión de seguridad en dependencies
- **Post-Deployment Validation**: Health checks automáticos
- **Approval Gate**: Aprobación manual antes de producción

---

## 📊 Arquitectura del Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRODUCTION DEPLOYMENT PIPELINE                │
└─────────────────────────────────────────────────────────────────┘

1. 📦 Checkout & Preparation
   ├─ Git checkout (master branch)
   ├─ Obtener commit SHA, author, message
   ├─ Generar versión de release (v<build>.<sha>)
   └─ Definir imagen Docker target
   
2. 🔨 Build & Unit Tests
   ├─ Compilación Maven (mvn clean package)
   ├─ Ejecución de pruebas unitarias (JUnit)
   ├─ Generación de JAR artifact
   └─ Archivar artefactos en Jenkins
   
3. 🔍 Code Quality & Security (Parallel)
   ├─ Code Coverage Analysis (JaCoCo)
   └─ Dependency Vulnerability Scan
   
4. 🐳 Docker Build & Push
   ├─ Build imagen Docker desde Dockerfile
   ├─ Tag: prod-<build>-<sha>
   ├─ Push a Google Container Registry (GCR)
   └─ Tag adicional: latest-prod
   
5. 🧪 E2E / System Tests
   ├─ Deploy temporal a staging
   ├─ Port-forward al API Gateway
   ├─ Ejecutar test suite completo
   │  ├─ UserRegistrationFlowE2ETest
   │  ├─ MultiServiceIntegrationE2ETest
   │  ├─ ECommerceShoppingFlowE2ETest
   │  ├─ ErrorHandlingAndResilienceE2ETest
   │  └─ PerformanceAndLoadE2ETest
   └─ Validar con JWT authentication
   
6. ✋ Production Approval Gate
   ├─ Mostrar resumen de deployment
   ├─ Esperar aprobación manual (30 min timeout)
   └─ Requiere rol: admin o devops-team
   
7. 🚀 Deploy to Production
   ├─ Crear namespace 'production' si no existe
   ├─ Guardar versión anterior para rollback
   ├─ Helm upgrade/install con chart
   ├─ Wait for rollout completion (10 min timeout)
   └─ Anotar deployment con metadata
   
8. ✅ Post-Deployment Validation
   ├─ Verificar todos los pods en Running state
   ├─ Health check en /actuator/health
   ├─ Smoke tests básicos
   └─ Validar métricas de Prometheus
   
9. 📝 Generate Release Notes
   ├─ Recopilar información de Git commits
   ├─ Obtener detalles del deployment K8s
   ├─ Generar documento Markdown completo
   ├─ Archivar en Jenkins artifacts
   └─ Crear Git tag (v<release>)
```

---

## 🔧 Configuración y Uso

### Prerrequisitos

1. **Jenkins con plugins**:
   - Pipeline
   - Git
   - Docker Pipeline
   - Kubernetes CLI
   - Google Cloud SDK

2. **Credenciales configuradas en Jenkins**:
   - `gke-credentials`: Service Account JSON de GCP

3. **Cluster Kubernetes**:
   - GKE cluster: `ecommerce-devops-cluster`
   - Namespace: `production` (se crea automáticamente)

4. **Google Container Registry**:
   - Proyecto: `ecommerce-backend-1760307199`
   - Registry: `us-central1-docker.pkg.dev`

### Crear el Job en Jenkins

```groovy
// 1. Nuevo Item → Pipeline
// 2. Nombre: user-service-production-pipeline
// 3. Pipeline → Definition: Pipeline script from SCM
// 4. SCM: Git
// 5. Repository URL: https://github.com/Geoffrey0pv/ecommerce-microservice-backend-app.git
// 6. Branch: */master
// 7. Script Path: jenkins-pipelines/user-service-production-pipeline.groovy
```

### Parámetros del Pipeline

| Parámetro | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `SKIP_TESTS` | Boolean | `false` | ⚠️ Saltar unit tests (NO recomendado) |
| `SKIP_E2E` | Boolean | `false` | ⚠️ Saltar E2E tests (NO recomendado) |
| `FORCE_DEPLOY` | Boolean | `false` | 🚨 Forzar deploy aunque fallen tests |
| `DEPLOYMENT_STRATEGY` | Choice | `RollingUpdate` | Estrategia de deployment K8s |

### Ejecutar el Pipeline

```bash
# Opción 1: Desde Jenkins UI
# 1. Ir a job "user-service-production-pipeline"
# 2. Click "Build with Parameters"
# 3. Seleccionar opciones deseadas
# 4. Click "Build"

# Opción 2: Trigger automático por Git
# El pipeline se ejecuta automáticamente al hacer merge a master:
git checkout master
git merge develop
git push origin master
```

---

## 📝 Release Notes Generadas Automáticamente

Cada despliegue genera un documento completo de Release Notes que incluye:

### Estructura del Documento

```markdown
# Release Notes - user-service v<version>

## 🚀 Deployment Information
- Tabla con detalles completos del deployment

## 📦 Artifact Information
- Imagen Docker, tags, registry

## 📝 Changes in This Release
- Lista de commits desde último release

## ✅ Quality Gates Passed
- Checklist de todas las validaciones ejecutadas

## 🔧 Deployment Strategy
- Estrategia usada, replicas, tiempo

## 🔗 Useful Commands
- Comandos kubectl para operaciones comunes
- View logs, check status, rollback

## 📊 Monitoring & Observability
- Endpoints de métricas y health

## 🔒 Security & Compliance
- Medidas de seguridad aplicadas

## 👥 Change Management
- Información de aprobación y riesgo

## 📞 Support & Escalation
- Contactos y canales de soporte
```

### Ubicación

- **Jenkins**: Artifacts archivados en cada build
- **Git**: Tag creado con versión de release
- **Archivo**: `RELEASE_NOTES_user-service_v<version>.md`

---

## 🎯 Quality Gates y Validaciones

### Pre-Deployment (Antes de llegar a producción)

1. ✅ **Unit Tests**: Mínimo 80% de código cubierto
2. ✅ **Code Coverage**: Análisis con JaCoCo
3. ✅ **Dependency Check**: Sin vulnerabilidades críticas
4. ✅ **Docker Build**: Imagen construida exitosamente
5. ✅ **E2E Tests**: Todos los tests de integración pasan
6. ✅ **Manual Approval**: Aprobación explícita del equipo

### Post-Deployment (Después de desplegar)

1. ✅ **Pod Health**: Todos los pods en estado Running/Ready
2. ✅ **Health Endpoint**: `/actuator/health` responde 200
3. ✅ **Smoke Tests**: Validaciones básicas de funcionalidad
4. ✅ **Metrics Available**: Prometheus puede scrapear métricas

---

## 🔄 Rollback Plan

### Rollback Automático

En caso de fallo durante deployment, el pipeline ejecuta rollback automático:

```bash
kubectl rollout undo deployment/user-service -n production
```

### Rollback Manual

```bash
# Opción 1: Rollback a versión inmediatamente anterior
kubectl rollout undo deployment/user-service -n production

# Opción 2: Rollback a revisión específica
kubectl rollout history deployment/user-service -n production
kubectl rollout undo deployment/user-service -n production --to-revision=5

# Opción 3: Re-deploy versión anterior con Helm
helm rollback user-service 0 -n production
```

---

## 📊 Monitoreo Post-Deployment

### Logs en Tiempo Real

```bash
# Logs del deployment
kubectl logs -f deployment/user-service -n production

# Logs de pod específico
kubectl logs -f <pod-name> -n production

# Logs anteriores (si crasheó)
kubectl logs <pod-name> -n production --previous
```

### Estado del Deployment

```bash
# Ver estado de pods
kubectl get pods -n production -l app=user-service

# Ver detalles del deployment
kubectl describe deployment user-service -n production

# Ver historial de rollouts
kubectl rollout history deployment/user-service -n production

# Ver eventos recientes
kubectl get events -n production --sort-by='.lastTimestamp' | grep user-service
```

### Métricas y Health

```bash
# Health check
kubectl exec -n production deployment/user-service -- curl http://localhost:8700/actuator/health

# Métricas
kubectl exec -n production deployment/user-service -- curl http://localhost:8700/actuator/metrics

# Info de la aplicación
kubectl exec -n production deployment/user-service -- curl http://localhost:8700/actuator/info
```

---

## 🔐 Seguridad y Compliance

### Medidas Implementadas

1. **Autenticación**: JWT tokens obligatorios para endpoints protegidos
2. **Secrets Management**: Credenciales almacenadas en Jenkins Credentials
3. **Image Scanning**: Análisis de vulnerabilidades en dependencies
4. **RBAC**: Control de acceso basado en roles en Kubernetes
5. **Network Policies**: Segmentación de red en cluster
6. **Resource Limits**: CPU y memoria limitados por pod
7. **TLS/HTTPS**: Comunicación encriptada en endpoints públicos

### Compliance

- ✅ **Change Management**: Aprobación requerida para producción
- ✅ **Audit Trail**: Todos los cambios registrados en Git y Jenkins
- ✅ **Rollback Plan**: Procedimiento documentado y automatizado
- ✅ **Monitoring**: Métricas y logs centralizados
- ✅ **Documentation**: Release notes generadas automáticamente

---

## 🏆 Mejores Prácticas Implementadas

### DevOps

- ✅ **Infrastructure as Code**: Helm charts y manifests en Git
- ✅ **GitOps**: Deployments basados en commits a master
- ✅ **Continuous Integration**: Build y tests automáticos
- ✅ **Continuous Deployment**: Deploy automático con approval gate
- ✅ **Blue-Green Deployments**: RollingUpdate con zero downtime

### Release Management

- ✅ **Semantic Versioning**: v<build>.<commit-sha>
- ✅ **Change Logs**: Commits incluidos en release notes
- ✅ **Tagging**: Git tags automáticos por release
- ✅ **Artifact Archiving**: JARs y release notes guardados
- ✅ **Rollback Strategy**: Plan claro y automatizado

### Testing

- ✅ **Unit Tests**: JUnit con cobertura de código
- ✅ **Integration Tests**: Tests E2E entre microservicios
- ✅ **Smoke Tests**: Validación post-deployment
- ✅ **Health Checks**: Probes de Kubernetes configurados
- ✅ **Performance Tests**: Opcional en suite E2E

---

## 📞 Soporte y Contacto

### Equipo DevOps
- **Email**: devops@ecommerce.com
- **Slack**: #devops-team
- **On-Call**: PagerDuty rotation

### Escalation
1. **Nivel 1**: DevOps Engineer (respuesta en 15 min)
2. **Nivel 2**: Senior DevOps / SRE (respuesta en 30 min)
3. **Nivel 3**: Engineering Manager (respuesta en 1 hora)

### Incidentes de Producción
- **Canal**: #production-incidents (Slack)
- **Procedimiento**: [Runbook de Incidentes](wiki/runbooks/incidents)
- **Post-Mortem**: Obligatorio para incidentes P0/P1

---

## 📚 Referencias

- [Documentación Kubernetes](https://kubernetes.io/docs/)
- [Helm Charts Best Practices](https://helm.sh/docs/chart_best_practices/)
- [Jenkins Pipeline Syntax](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [Google Kubernetes Engine (GKE)](https://cloud.google.com/kubernetes-engine/docs)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)

---

## 🎓 Para la Entrega Académica

### Evidencias a Incluir

1. **Screenshot del Pipeline en Jenkins**
   - Vista general del job
   - Ejecución exitosa con todas las fases
   - Console output de build exitoso

2. **Release Notes Generadas**
   - Documento Markdown completo
   - Muestra de información incluida
   - Artifacts archivados en Jenkins

3. **Deployment en Kubernetes**
   - `kubectl get pods -n production`
   - `kubectl describe deployment user-service -n production`
   - `kubectl rollout history deployment/user-service -n production`

4. **Tests Ejecutados**
   - Unit tests output
   - E2E tests results
   - Coverage report

5. **Código del Pipeline**
   - Archivo completo del Groovy script
   - Comentarios explicando cada fase

### Rubrica Cumplida (15%)

| Criterio | Implementado | Evidencia |
|----------|--------------|-----------|
| Build con Unit Tests | ✅ | Fase "Build & Unit Tests" + Maven output |
| Pruebas de Sistema | ✅ | Fase "E2E / System Tests" + Test results |
| Deploy a Kubernetes | ✅ | Fase "Deploy to Production" + kubectl output |
| Release Notes | ✅ | Documento Markdown generado automáticamente |
| Fases adicionales | ✅ | Quality gates, security, approval, validation |

---

**Generado**: 2025-11-03  
**Autor**: DevOps Team  
**Versión**: 1.0
