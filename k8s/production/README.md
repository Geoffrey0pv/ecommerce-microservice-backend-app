# 🏭 Kubernetes Production Manifests

Este directorio contiene los manifiestos de Kubernetes para el ambiente de **PRODUCCIÓN**.

## 🎯 Ambiente de Producción

- **Cluster:** ecommerce-production-cluster
- **Namespace:** ecommerce-production
- **Proyecto GCP:** ingesoft-taller2-prod
- **Zona:** us-central1-a

## 📦 Servicios Desplegados

| Servicio | Puerto | Replicas | Recursos |
|----------|--------|----------|----------|
| user-service | 8700 | 3 | 2Gi RAM, 1 CPU |
| product-service | 8500 | 3 | 2Gi RAM, 1 CPU |
| order-service | 8300 | 3 | 2Gi RAM, 1 CPU |
| payment-service | 8400 | 3 | 2Gi RAM, 1 CPU |
| shipping-service | 8600 | 3 | 2Gi RAM, 1 CPU |
| favourite-service | 8800 | 3 | 2Gi RAM, 1 CPU |

## 🔧 Configuración de Producción

- **Alta Disponibilidad:** 3 réplicas por servicio
- **Resource Limits:** Configurados para estabilidad
- **Health Checks:** Readiness y Liveness probes
- **Rolling Updates:** Despliegue sin downtime
- **HPA:** Auto-escalado horizontal configurado

## 🚀 Comandos de Despliegue

```bash
# Aplicar todos los manifiestos
kubectl apply -f k8s/production/

# Verificar estado
kubectl get pods -n ecommerce-production
kubectl get services -n ecommerce-production

# Verificar logs
kubectl logs -f deployment/user-service -n ecommerce-production
```