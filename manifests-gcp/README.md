# Manifiestos para Google Cloud Platform (GCP)

## 📋 Propósito
Este directorio contiene los manifiestos de Kubernetes configurados para usar imágenes de **Google Container Registry (GCR)** para despliegue en producción en GCP.

## 🔄 Diferencias con manifests/
- **manifests/**: Usa imágenes de Docker Hub (`geoffrey0pv/*`) para Minikube local
- **manifests-gcp/**: Usa imágenes de GCR (`gcr.io/PROJECT-ID/*`) para GCP producción

## 🚀 Servicios Configurados
### Servicios Principales (Taller)
- `user-service/` - Gestión de usuarios
- `product-service/` - Catálogo de productos  
- `order-service/` - Procesamiento de órdenes

### Servicios de Soporte
- `discovery/` - Eureka service discovery
- `payment-service/` - Procesamiento de pagos
- `shipping-service/` - Gestión de envíos
- `favourite-service/` - Lista de favoritos

## 📦 Estructura de Imágenes
```
gcr.io/YOUR-PROJECT-ID/user-service:latest-master
gcr.io/YOUR-PROJECT-ID/product-service:latest-master
gcr.io/YOUR-PROJECT-ID/order-service:latest-master
```

## 🔧 Uso
```bash
# Desplegar en GCP
kubectl apply -f manifests-gcp/discovery/
kubectl apply -f manifests-gcp/user-service/
kubectl apply -f manifests-gcp/product-service/
kubectl apply -f manifests-gcp/order-service/
```

## ⚠️ Importante
Antes del despliegue, asegurar que:
1. Las imágenes estén disponibles en GCR
2. El cluster de GCP tenga acceso al registry
3. El PROJECT_ID esté correctamente configurado
