#!/bin/bash

# Script para crear manifiestos Kubernetes para los 3 servicios adicionales
echo "☸️ Creando manifiestos Kubernetes para servicios adicionales..."

ADDITIONAL_SERVICES=("payment-service" "shipping-service" "favourite-service")

echo ""
echo "📋 Servicios a crear:"
for service in "${ADDITIONAL_SERVICES[@]}"; do
    echo "   📦 $service"
done
echo ""

# Función para crear manifiestos basados en user-service template
create_manifests_for_service() {
    local service=$1
    local port=$2
    local description=$3
    
    echo "📝 Creando manifiestos para: $service"
    
    # Crear directorio para manifests locales
    mkdir -p "manifests/$service/templates"
    
    # Crear directorio para manifests GCP
    mkdir -p "manifests-gcp/$service/templates"
    
    # Crear Chart.yaml para manifests locales
    cat > "manifests/$service/Chart.yaml" << EOF
apiVersion: v2
name: $service
description: $description
type: application
version: 0.1.0
appVersion: "1.0"
EOF

    # Crear Chart.yaml para manifests GCP
    cat > "manifests-gcp/$service/Chart.yaml" << EOF
apiVersion: v2
name: $service
description: $description
type: application
version: 0.1.0
appVersion: "1.0"
EOF

    # Crear values.yaml para manifests locales (Docker Hub)
    cat > "manifests/$service/values.yaml" << EOF
# Default values for $service
replicaCount: 1

image:
  repository: geoffrey0pv/$service
  tag: latest-master
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: $port
  targetPort: $port

ingress:
  enabled: false

resources:
  limits:
    cpu: 500m
    memory: 512Mi
  requests:
    cpu: 250m
    memory: 256Mi

nodeSelector: {}
tolerations: []
affinity: {}

# Configuración específica del servicio
config:
  springProfiles: dev
  serverPort: $port
  
# Health checks
healthCheck:
  enabled: true
  path: /actuator/health
  initialDelaySeconds: 60
  periodSeconds: 30
EOF

    # Crear values.yaml para manifests GCP (Container Registry)
    cat > "manifests-gcp/$service/values.yaml" << EOF
# Default values for $service
replicaCount: 2

image:
  repository: gcr.io/your-gcp-project-id/$service
  tag: latest-master
  pullPolicy: IfNotPresent

service:
  type: LoadBalancer
  port: $port
  targetPort: $port

ingress:
  enabled: true
  className: "gce"
  hosts:
    - host: $service.your-domain.com
      paths:
        - path: /
          pathType: Prefix

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi

nodeSelector: {}
tolerations: []
affinity: {}

# Configuración específica del servicio
config:
  springProfiles: production
  serverPort: $port
  
# Health checks
healthCheck:
  enabled: true
  path: /actuator/health
  initialDelaySeconds: 90
  periodSeconds: 30
EOF

    # Crear deployment.yaml para manifests locales
    cat > "manifests/$service/templates/deployment.yaml" << 'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "{{ .Chart.Name }}.fullname" . }}
  labels:
    app: {{ .Chart.Name }}
    version: {{ .Chart.AppVersion }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: {{ .Chart.Name }}
  template:
    metadata:
      labels:
        app: {{ .Chart.Name }}
        version: {{ .Chart.AppVersion }}
    spec:
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.service.targetPort }}
              protocol: TCP
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: {{ .Values.config.springProfiles }}
            - name: SERVER_PORT
              value: "{{ .Values.config.serverPort }}"
            - name: EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE
              value: "http://discovery:8761/eureka"
          {{- if .Values.healthCheck.enabled }}
          livenessProbe:
            httpGet:
              path: {{ .Values.healthCheck.path }}
              port: http
            initialDelaySeconds: {{ .Values.healthCheck.initialDelaySeconds }}
            periodSeconds: {{ .Values.healthCheck.periodSeconds }}
          readinessProbe:
            httpGet:
              path: {{ .Values.healthCheck.path }}
              port: http
            initialDelaySeconds: {{ .Values.healthCheck.initialDelaySeconds }}
            periodSeconds: {{ .Values.healthCheck.periodSeconds }}
          {{- end }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
EOF

    # Copiar deployment.yaml a manifests-gcp
    cp "manifests/$service/templates/deployment.yaml" "manifests-gcp/$service/templates/deployment.yaml"

    # Crear service.yaml para manifests locales
    cat > "manifests/$service/templates/service.yaml" << 'EOF'
apiVersion: v1
kind: Service
metadata:
  name: {{ include "{{ .Chart.Name }}.fullname" . }}
  labels:
    app: {{ .Chart.Name }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: {{ .Values.service.targetPort }}
      protocol: TCP
      name: http
  selector:
    app: {{ .Chart.Name }}
EOF

    # Copiar service.yaml a manifests-gcp
    cp "manifests/$service/templates/service.yaml" "manifests-gcp/$service/templates/service.yaml"

    # Crear ingress.yaml solo para manifests-gcp
    cat > "manifests-gcp/$service/templates/ingress.yaml" << 'EOF'
{{- if .Values.ingress.enabled -}}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "{{ .Chart.Name }}.fullname" . }}
  labels:
    app: {{ .Chart.Name }}
  {{- with .Values.ingress.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  {{- if .Values.ingress.className }}
  ingressClassName: {{ .Values.ingress.className }}
  {{- end }}
  rules:
    {{- range .Values.ingress.hosts }}
    - host: {{ .host | quote }}
      http:
        paths:
          {{- range .paths }}
          - path: {{ .path }}
            pathType: {{ .pathType }}
            backend:
              service:
                name: {{ include "{{ $.Chart.Name }}.fullname" $ }}
                port:
                  number: {{ $.Values.service.port }}
          {{- end }}
    {{- end }}
{{- end }}
EOF

    echo "   ✅ Manifiestos creados para $service"
}

# Crear manifiestos para cada servicio con puertos específicos
echo "📦 Creando manifiestos..."

create_manifests_for_service "payment-service" "8083" "Payment processing microservice"
create_manifests_for_service "shipping-service" "8084" "Shipping management microservice"  
create_manifests_for_service "favourite-service" "8085" "User favorites microservice"

echo ""
echo "📊 Verificando estructura creada..."
echo ""

ALL_SERVICES=("user-service" "product-service" "order-service" "payment-service" "shipping-service" "favourite-service")

for service in "${ALL_SERVICES[@]}"; do
    echo "🔍 $service:"
    echo "   📂 manifests/$service $([ -d "manifests/$service" ] && echo "✅" || echo "❌")"
    echo "   📂 manifests-gcp/$service $([ -d "manifests-gcp/$service" ] && echo "✅" || echo "❌")"
    
    if [ -d "manifests/$service" ]; then
        echo "      📄 Chart.yaml $([ -f "manifests/$service/Chart.yaml" ] && echo "✅" || echo "❌")"
        echo "      📄 values.yaml $([ -f "manifests/$service/values.yaml" ] && echo "✅" || echo "❌")"
        echo "      📄 deployment.yaml $([ -f "manifests/$service/templates/deployment.yaml" ] && echo "✅" || echo "❌")"
        echo "      📄 service.yaml $([ -f "manifests/$service/templates/service.yaml" ] && echo "✅" || echo "❌")"
    fi
    echo ""
done

echo "🌍 ESTRUCTURA FINAL:"
echo ""
echo "📂 manifests/ (Local - Docker Hub):"
tree manifests/ | head -20

echo ""
echo "📂 manifests-gcp/ (GCP - Container Registry):"
tree manifests-gcp/ | head -20

echo ""
echo "🎯 PUERTOS ASIGNADOS:"
echo "   🟦 discovery: 8761"
echo "   👥 user-service: 8080"
echo "   📦 product-service: 8081"
echo "   📋 order-service: 8082"
echo "   💳 payment-service: 8083"
echo "   🚚 shipping-service: 8084"
echo "   ❤️ favourite-service: 8085"
echo ""
echo "✅ ¡MANIFIESTOS KUBERNETES PARA 6 MICROSERVICIOS COMPLETADOS!"