# Despliegue en Staging Environment - GKE

Este directorio contiene los manifiestos de Kubernetes para desplegar los microservicios en el ambiente de **Staging** en Google Kubernetes Engine (GKE).

## Arquitectura de Staging

### Cluster GKE
- **Nombre**: `ecommerce-staging-cluster`
- **Región**: `us-central1-b`
- **Namespace**: `ecommerce-staging`
- **Node Pool**: `backend-pool`

### Configuración de Red
- **Subnet CIDR**: 10.10.0.0/20
- **Pods CIDR**: 10.11.0.0/16
- **Services CIDR**: 10.12.0.0/20

## Servicios Desplegados

| Servicio | Puerto | Réplicas | Recursos (CPU/Mem) | HPA |
|----------|--------|----------|-------------------|-----|
| user-service | 8700 | 2-5 | 250m-500m / 512Mi-1Gi | ✅ |
| product-service | 8500 | 2-5 | 250m-500m / 512Mi-1Gi | ✅ |
| order-service | 8300 | 2-5 | 250m-500m / 512Mi-1Gi | ✅ |
| payment-service | 8400 | 2-5 | 250m-500m / 512Mi-1Gi | ✅ |
| shipping-service | 8600 | 2-5 | 250m-500m / 512Mi-1Gi | ✅ |
| favourite-service | 8800 | 2-5 | 250m-500m / 512Mi-1Gi | ✅ |

## Componentes de Cada Deployment

Cada microservicio incluye:

1. **Namespace**: Aislamiento lógico de recursos
2. **ConfigMap**: Configuración de variables de entorno
3. **Deployment**: Definición del pod y contenedores
4. **Service**: Expone el deployment internamente
5. **HorizontalPodAutoscaler**: Escalado automático basado en CPU/memoria

## Desplegar Manualmente

### Prerrequisitos

```bash
# Conectar a GKE
gcloud container clusters get-credentials ecommerce-staging-cluster \
    --zone us-central1-b \
    --project ingesoft-taller2

# Verificar conexión
kubectl cluster-info
kubectl get nodes
```

### Desplegar un Servicio

```bash
# Desplegar user-service
kubectl apply -f user-service-deployment.yaml

# Verificar deployment
kubectl get deployments -n ecommerce-staging
kubectl get pods -n ecommerce-staging
kubectl get svc -n ecommerce-staging

# Ver logs
kubectl logs -f deployment/user-service -n ecommerce-staging

# Ver estado del rollout
kubectl rollout status deployment/user-service -n ecommerce-staging
```

### Desplegar Todos los Servicios

```bash
# Desplegar todos los manifiestos
kubectl apply -f .

# Verificar que todos estén corriendo
kubectl get all -n ecommerce-staging
```

## Verificar Salud de los Servicios

### Health Checks

```bash
# Obtener IP del servicio
SERVICE_IP=$(kubectl get svc user-service -n ecommerce-staging -o jsonpath='{.spec.clusterIP}')

# Verificar health endpoint
kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ecommerce-staging -- \
    curl http://${SERVICE_IP}:8700/actuator/health

# Ver métricas
kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ecommerce-staging -- \
    curl http://${SERVICE_IP}:8700/actuator/metrics
```

### Monitoring con Prometheus

```bash
# Verificar ServiceMonitor
kubectl get servicemonitor -n ecommerce-staging

# Ver métricas en Prometheus
# Acceder a Grafana: http://localhost:3000
# Dashboard: Kubernetes / Compute Resources / Namespace (Pods)
```

## Actualizar un Deployment

### Rolling Update

```bash
# Actualizar imagen
kubectl set image deployment/user-service \
    user-service=geoffrey0pv/user-service:stage-123-abc1234 \
    -n ecommerce-staging

# Ver progreso
kubectl rollout status deployment/user-service -n ecommerce-staging

# Ver historial
kubectl rollout history deployment/user-service -n ecommerce-staging
```

### Rollback

```bash
# Rollback a versión anterior
kubectl rollout undo deployment/user-service -n ecommerce-staging

# Rollback a revisión específica
kubectl rollout undo deployment/user-service --to-revision=2 -n ecommerce-staging
```

## Scaling

### Manual Scaling

```bash
# Escalar a 3 réplicas
kubectl scale deployment user-service --replicas=3 -n ecommerce-staging

# Verificar
kubectl get pods -n ecommerce-staging -l app=user-service
```

### Auto Scaling (HPA)

```bash
# Ver estado del HPA
kubectl get hpa -n ecommerce-staging

# Describir HPA
kubectl describe hpa user-service-hpa -n ecommerce-staging

# Ver métricas en tiempo real
kubectl top pods -n ecommerce-staging
```

## Troubleshooting

### Ver Logs

```bash
# Logs del deployment
kubectl logs -f deployment/user-service -n ecommerce-staging

# Logs de un pod específico
kubectl logs <pod-name> -n ecommerce-staging

# Logs de todos los pods del servicio
kubectl logs -l app=user-service -n ecommerce-staging --tail=100

# Logs de contenedor específico (si hay múltiples)
kubectl logs <pod-name> -c user-service -n ecommerce-staging
```

### Debugging

```bash
# Describir pod
kubectl describe pod <pod-name> -n ecommerce-staging

# Ejecutar comando en pod
kubectl exec -it <pod-name> -n ecommerce-staging -- /bin/sh

# Port forward para debugging local
kubectl port-forward svc/user-service 8700:8700 -n ecommerce-staging
```

### Problemas Comunes

#### Pod en CrashLoopBackOff
```bash
# Ver razón del crash
kubectl describe pod <pod-name> -n ecommerce-staging

# Ver logs anteriores
kubectl logs <pod-name> -n ecommerce-staging --previous
```

#### ImagePullBackOff
```bash
# Verificar nombre de imagen
kubectl describe pod <pod-name> -n ecommerce-staging

# Verificar credenciales de Docker
kubectl get secret -n ecommerce-staging
```

#### Service no responde
```bash
# Verificar endpoints
kubectl get endpoints user-service -n ecommerce-staging

# Verificar etiquetas del selector
kubectl get pods -n ecommerce-staging --show-labels
```

## Configuración de Recursos

### Requests vs Limits

```yaml
resources:
  requests:    # Mínimo garantizado
    memory: "512Mi"
    cpu: "250m"
  limits:      # Máximo permitido
    memory: "1Gi"
    cpu: "500m"
```

### Best Practices

- **Requests**: Lo que la aplicación necesita para funcionar
- **Limits**: Previene que un pod consuma todos los recursos
- **CPU**: 1 CPU = 1000m (milicores)
- **Memory**: Usar Gi (Gibibytes) para memoria

## Seguridad

### Network Policies

```bash
# Ver network policies
kubectl get networkpolicies -n ecommerce-staging

# Aplicar política de red
kubectl apply -f network-policy.yaml
```

### RBAC

```bash
# Ver roles y bindings
kubectl get roles,rolebindings -n ecommerce-staging

# Ver service accounts
kubectl get serviceaccounts -n ecommerce-staging
```

## Backup y Disaster Recovery

### Backup de ConfigMaps y Secrets

```bash
# Exportar todos los recursos
kubectl get all -n ecommerce-staging -o yaml > staging-backup.yaml

# Backup de configmaps
kubectl get configmap -n ecommerce-staging -o yaml > configmaps-backup.yaml
```

### Restaurar desde Backup

```bash
# Restaurar recursos
kubectl apply -f staging-backup.yaml
```

## Integración con Jenkins

Los pipelines de Jenkins automatizan todo este proceso:

1. **Build**: Compilar y empaquetar la aplicación
2. **Test**: Ejecutar pruebas unitarias e integración
3. **Image**: Construir y publicar imagen Docker
4. **Deploy**: Desplegar en Kubernetes
5. **Verify**: Ejecutar smoke tests y application tests
6. **Monitor**: Configurar monitoreo con Prometheus

Ver: `jenkins-pipelines/user-service-stage-pipeline.groovy`

## Monitoreo y Observabilidad

### Prometheus Metrics

```bash
# Acceder a métricas de Prometheus
kubectl port-forward -n monitoring svc/prometheus-server 9090:80

# Queries útiles:
# - container_memory_usage_bytes
# - container_cpu_usage_seconds_total
# - http_server_requests_seconds_count
```

### Grafana Dashboards

```bash
# Acceder a Grafana
kubectl port-forward -n monitoring svc/grafana 3000:80

# Dashboards recomendados:
# - Kubernetes / Compute Resources / Namespace (Pods)
# - Spring Boot Statistics
# - JVM (Micrometer)
```

## Limpieza

### Eliminar un Servicio

```bash
kubectl delete -f user-service-deployment.yaml
```

### Eliminar Todo el Namespace

```bash
# ⚠️ CUIDADO: Esto elimina TODOS los recursos
kubectl delete namespace ecommerce-staging
```

## Próximos Pasos

1. ✅ Desplegar servicios en staging
2. 🔄 Configurar Ingress para acceso externo
3. 🔄 Implementar pruebas de carga con Locust
4. 🔄 Configurar alertas en Grafana
5. 🔄 Implementar canary deployments
6. 🔄 Configurar service mesh (Istio)

## Referencias

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [GKE Documentation](https://cloud.google.com/kubernetes-engine/docs)
- [Spring Boot on Kubernetes](https://spring.io/guides/gs/spring-boot-kubernetes/)
- [Prometheus Operator](https://github.com/prometheus-operator/prometheus-operator)

---

**Última actualización**: Octubre 2025  
**Versión**: 1.0  
**Mantenido por**: Equipo de DevOps
