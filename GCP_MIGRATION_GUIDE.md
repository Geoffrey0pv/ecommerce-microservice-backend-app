# Guía de Migración a Google Cloud Platform

## 📋 Resumen
Esta guía documenta la migración de la infraestructura de microservicios desde Docker Hub hacia Google Cloud Platform (GCP) usando Google Container Registry (GCR).

## 🎯 Objetivos de la Migración
1. **Jenkins Pipelines**: Convertir de Docker Hub a Google Container Registry
2. **Kubernetes Manifests**: Actualizar para usar imágenes de GCR
3. **Infraestructura**: Preparar para despliegue en GCP

## 📁 Archivos Generados

### Pipelines Jenkins - GCR
```
jenkins-pipelines/
├── *-pipeline-gcr-new.groovy         # Pipelines principales para GCR
├── *-stage-pipeline-gcr-new.groovy   # Pipelines de staging para GCR
├── *-pipeline-dockerhub-backup.groovy # Backups de pipelines originales
└── JENKINS_GCP_CONFIG.md             # Configuración de Jenkins para GCP
```

### Scripts de Migración
```
├── convert-pipelines-to-gcr.sh       # Conversión automática (usado)
├── generate-gcr-pipelines.sh         # Generación de pipelines limpias
└── GCP_MIGRATION_GUIDE.md           # Esta guía
```

## 🔧 Configuración Requerida

### 1. Variables de GCP (IMPORTANTES - CAMBIAR)
```bash
GCP_PROJECT_ID="your-gcp-project-id"  # ⚠️ CAMBIAR por tu project ID real
GCP_REGION="us-central1"              # ⚠️ CAMBIAR por tu región
GCR_REGISTRY="gcr.io"                 # o us.gcr.io, eu.gcr.io, asia.gcr.io
```

### 2. Credenciales Jenkins Requeridas
- **gcp-service-account-key**: Archivo JSON del service account de GCP
- **Permisos necesarios**: Storage Admin, Container Registry roles

### 3. Software Requerido en Jenkins
- Docker
- Google Cloud SDK (`gcloud`)
- kubectl (para deployments)

## 🚀 Pasos de Migración

### Fase 1: Preparación de Jenkins
```bash
# 1. Configurar credenciales GCP en Jenkins
# 2. Instalar Google Cloud SDK
# 3. Configurar service account con permisos
# Ver: JENKINS_GCP_CONFIG.md para detalles completos
```

### Fase 2: Reemplazar Pipelines
```bash
# Opción A: Usar pipelines generadas limpias (RECOMENDADO)
cp jenkins-pipelines/user-service-pipeline-gcr-new.groovy jenkins-pipelines/user-service-pipeline.groovy

# Opción B: Usar pipelines convertidas automáticamente
# Ya aplicado con convert-pipelines-to-gcr.sh
```

### Fase 3: Actualizar PROJECT_ID
```bash
# Buscar y reemplazar en todas las pipelines nuevas
find jenkins-pipelines -name "*-gcr-new.groovy" -exec sed -i 's/your-gcp-project-id/TU-PROJECT-ID-REAL/g' {} +
```

### Fase 4: Actualizar Manifiestos Kubernetes
```bash
# Actualizar manifests para usar imágenes de GCR
./update-manifests-for-gcr.sh  # (Script a crear)
```

## 📊 Comparación: Docker Hub vs GCR

| Aspecto | Docker Hub | Google Container Registry |
|---------|------------|---------------------------|
| **Registry URL** | `docker.io/geoffrey0pv/` | `gcr.io/PROJECT-ID/` |
| **Autenticación** | dockerhub-credentials | gcp-service-account-key |
| **Push Command** | `docker push` | `docker push` (con gcloud auth) |
| **Pricing** | Limitado gratis | Integrado con GCP billing |
| **Security** | Público/Privado | IAM integrado |

## 🔄 Cambios en Pipelines

### Antes (Docker Hub)
```groovy
environment {
    DOCKER_REGISTRY_USER = "geoffrey0pv"
}

docker.withRegistry('', 'dockerhub-credentials') {
    docker.image("${DOCKER_REGISTRY_USER}/${IMAGE_NAME}:${tag}").push()
}
```

### Después (GCR)
```groovy
environment {
    GCP_PROJECT_ID = "your-project-id"
    GCR_REGISTRY = "gcr.io"
}

stage('Authenticate with GCP') {
    withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_KEY_FILE')]) {
        sh 'gcloud auth activate-service-account --key-file=$GCP_KEY_FILE'
        sh 'gcloud auth configure-docker ${GCR_REGISTRY} --quiet'
    }
}

sh 'docker push ${GCR_REGISTRY}/${GCP_PROJECT_ID}/${IMAGE_NAME}:${tag}'
```

## 🏗️ Estructura de Imágenes

### Naming Convention
```
# Docker Hub (anterior)
docker.io/geoffrey0pv/user-service:latest-master
docker.io/geoffrey0pv/product-service:abcd123

# GCR (nuevo)
gcr.io/YOUR-PROJECT-ID/user-service:latest-master
gcr.io/YOUR-PROJECT-ID/product-service:abcd123
```

## ✅ Verificación Post-Migración

### 1. Verificar Pipelines
```bash
# Revisar que todas las pipelines tengan:
grep -r "GCP_PROJECT_ID" jenkins-pipelines/*-gcr-new.groovy
grep -r "gcp-service-account-key" jenkins-pipelines/*-gcr-new.groovy
```

### 2. Test Pipeline
```bash
# Ejecutar pipeline de prueba en Jenkins
# Verificar que las imágenes aparezcan en GCR:
gcloud container images list --repository=gcr.io/YOUR-PROJECT-ID
```

### 3. Verificar Manifiestos
```bash
# Revisar que los manifiestos usen imágenes de GCR
grep -r "image:" manifests/*/
```

## 🔍 Troubleshooting

### Errores Comunes

#### 1. "Permission denied" en GCR
```bash
# Verificar permisos del service account
gcloud projects get-iam-policy YOUR-PROJECT-ID
# Debe tener roles/storage.admin
```

#### 2. "Docker push failed"
```bash
# Re-configurar Docker auth
gcloud auth configure-docker gcr.io --quiet
```

#### 3. "Project not found"
```bash
# Verificar project ID
gcloud config get-value project
gcloud projects list
```

## 📈 Próximos Pasos

### 1. Implementación Inmediata
- [ ] Actualizar PROJECT_ID en pipelines nuevas
- [ ] Configurar credenciales Jenkins
- [ ] Probar pipeline de un servicio

### 2. Optimización
- [ ] Configurar Container Analysis para seguridad
- [ ] Implementar cleanup de imágenes antiguas
- [ ] Configurar monitoring de pipelines

### 3. Migración Completa
- [ ] Actualizar manifiestos Kubernetes
- [ ] Migrar todas las pipelines
- [ ] Eliminar dependencias de Docker Hub

## 🆘 Soporte

### Documentación Adicional
- `JENKINS_GCP_CONFIG.md` - Configuración detallada de Jenkins
- `LOCAL_SETUP_README.md` - Setup local con Minikube
- `ARCHITECTURE.md` - Arquitectura del sistema

### Rollback Plan
Si hay problemas, los backups están disponibles:
```bash
# Restaurar pipeline original
cp jenkins-pipelines/user-service-pipeline-dockerhub-backup.groovy jenkins-pipelines/user-service-pipeline.groovy
```

---

**Autor**: GitHub Copilot  
**Fecha**: $(date +%Y-%m-%d)  
**Estado**: ✅ Pipelines generadas, pendiente configuración PROJECT_ID