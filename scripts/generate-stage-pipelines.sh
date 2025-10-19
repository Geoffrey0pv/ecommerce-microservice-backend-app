#!/bin/bash

# Script para generar pipelines de staging y manifiestos K8s para todos los microservicios
# Basado en el template de user-service

set -e

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

# Microservicios a generar
SERVICES=(
    "product-service:8500"
    "order-service:8300"
    "payment-service:8400"
    "shipping-service:8600"
    "favourite-service:8800"
)

echo -e "${BLUE}Generando pipelines de staging y manifiestos K8s...${NC}"

# Función para generar deployment K8s
generate_k8s_deployment() {
    local service_name=$1
    local port=$2
    local file_path="k8s/staging/${service_name}-deployment.yaml"
    
    cat > "$file_path" << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: ${service_name}-config
  namespace: ecommerce-staging
data:
  SPRING_PROFILES_ACTIVE: "stage"
  SERVICE_DISCOVERY_URL: "http://service-discovery.ecommerce-staging.svc.cluster.local:8761"
  CLOUD_CONFIG_URL: "http://cloud-config.ecommerce-staging.svc.cluster.local:9296"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${service_name}
  namespace: ecommerce-staging
  labels:
    app: ${service_name}
    environment: staging
    version: v1
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ${service_name}
  template:
    metadata:
      labels:
        app: ${service_name}
        environment: staging
        version: v1
    spec:
      nodeSelector:
        pool: backend-pool
      containers:
      - name: ${service_name}
        image: geoffrey0pv/${service_name}:latest-master
        imagePullPolicy: Always
        ports:
        - containerPort: ${port}
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          valueFrom:
            configMapKeyRef:
              name: ${service_name}-config
              key: SPRING_PROFILES_ACTIVE
        - name: SERVICE_DISCOVERY_URL
          valueFrom:
            configMapKeyRef:
              name: ${service_name}-config
              key: SERVICE_DISCOVERY_URL
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: ${port}
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: ${port}
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: ${service_name}
  namespace: ecommerce-staging
  labels:
    app: ${service_name}
spec:
  type: ClusterIP
  ports:
  - port: ${port}
    targetPort: ${port}
    protocol: TCP
    name: http
  selector:
    app: ${service_name}
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ${service_name}-hpa
  namespace: ecommerce-staging
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ${service_name}
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
EOF

    echo -e "${GREEN}✓${NC} Generado: $file_path"
}

# Función para generar pipeline de Jenkins
generate_jenkins_pipeline() {
    local service_name=$1
    local port=$2
    local file_path="jenkins-pipelines/${service_name}-stage-pipeline.groovy"
    
    # Copiar el template y reemplazar valores
    cp jenkins-pipelines/user-service-stage-pipeline.groovy "$file_path"
    
    # Reemplazar nombre del servicio y puerto
    sed -i "s/user-service/${service_name}/g" "$file_path"
    sed -i "s/8700/${port}/g" "$file_path"
    
    echo -e "${GREEN}✓${NC} Generado: $file_path"
}

# Crear directorios si no existen
mkdir -p k8s/staging
mkdir -p jenkins-pipelines

# Generar para cada servicio
for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service_name port <<< "$service_config"
    
    echo -e "\n${BLUE}Procesando: ${service_name}${NC}"
    
    # Generar K8s deployment
    generate_k8s_deployment "$service_name" "$port"
    
    # Generar Jenkins pipeline
    generate_jenkins_pipeline "$service_name" "$port"
done

echo -e "\n${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ Generación completada exitosamente${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "\nArchivos generados:"
echo -e "  - 5 manifiestos K8s en: k8s/staging/"
echo -e "  - 5 pipelines Jenkins en: jenkins-pipelines/"
echo -e "\nPróximos pasos:"
echo -e "  1. Revisar los archivos generados"
echo -e "  2. Desplegar manifiestos: kubectl apply -f k8s/staging/"
echo -e "  3. Configurar pipelines en Jenkins"
echo ""







