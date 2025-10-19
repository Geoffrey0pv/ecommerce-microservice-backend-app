#!/bin/bash

# Script de Despliegue Automatizado a Staging
# Este script construye, sube y despliega todos los microservicios

set -e

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuración
DOCKER_REGISTRY_USER="geoffrey0pv"
VERSION="v1.0"
NAMESPACE="ecommerce-staging"
PROJECT_ROOT="/home/geoffrey0pv/Projects/2025-2/Ingeosft5/ecommerce-microservice-backend-app"

# Microservicios
SERVICES=(
    "user-service:8700"
    "product-service:8500"
    "order-service:8300"
    "payment-service:8400"
    "shipping-service:8600"
    "favourite-service:8800"
)

echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}     DESPLIEGUE AUTOMATIZADO A STAGING ENVIRONMENT           ${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}\n"

# Función para imprimir mensajes
print_step() {
    echo -e "\n${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Verificar prerequisitos
print_step "PASO 1: Verificando prerequisitos..."

if ! command -v kubectl &> /dev/null; then
    print_error "kubectl no está instalado"
    exit 1
fi

if ! command -v docker &> /dev/null; then
    print_error "Docker no está instalado"
    exit 1
fi

if ! kubectl cluster-info &> /dev/null; then
    print_error "No hay conexión a cluster Kubernetes"
    exit 1
fi

print_success "Todos los prerequisitos están disponibles"

# Verificar conexión a Docker Hub
print_step "PASO 2: Verificando acceso a Docker Hub..."

if docker info | grep -q "Username: ${DOCKER_REGISTRY_USER}"; then
    print_success "Conectado a Docker Hub como ${DOCKER_REGISTRY_USER}"
else
    print_warning "No estás logueado en Docker Hub"
    read -p "¿Deseas hacer login ahora? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker login -u ${DOCKER_REGISTRY_USER}
    else
        print_error "Debes estar logueado en Docker Hub para continuar"
        exit 1
    fi
fi

# Crear namespace si no existe
print_step "PASO 3: Preparando namespace en Kubernetes..."

if kubectl get namespace ${NAMESPACE} &> /dev/null; then
    print_success "Namespace ${NAMESPACE} ya existe"
else
    kubectl create namespace ${NAMESPACE}
    print_success "Namespace ${NAMESPACE} creado"
fi

# Función para construir imagen Docker
build_docker_image() {
    local service_name=$1
    local port=$2
    
    print_step "Construyendo ${service_name}..."
    
    cd "${PROJECT_ROOT}/${service_name}"
    
    # Compilar con Maven
    echo "  → Compilando con Maven..."
    ../mvnw clean package -DskipTests -q || {
        print_error "Error compilando ${service_name}"
        return 1
    }
    
    # Construir imagen Docker
    echo "  → Construyendo imagen Docker..."
    cd "${PROJECT_ROOT}"
    docker build \
        -t "${DOCKER_REGISTRY_USER}/${service_name}:${VERSION}" \
        -t "${DOCKER_REGISTRY_USER}/${service_name}:latest" \
        -f "${service_name}/Dockerfile" \
        . -q || {
        print_error "Error construyendo imagen Docker para ${service_name}"
        return 1
    }
    
    print_success "${service_name} construido exitosamente"
    return 0
}

# Función para subir imagen a Docker Hub
push_docker_image() {
    local service_name=$1
    
    echo "  → Subiendo ${service_name} a Docker Hub..."
    docker push "${DOCKER_REGISTRY_USER}/${service_name}:${VERSION}" -q
    docker push "${DOCKER_REGISTRY_USER}/${service_name}:latest" -q
    
    print_success "${service_name} subido a Docker Hub"
}

# Construir y subir todas las imágenes
print_step "PASO 4: Construyendo imágenes Docker..."

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service_name port <<< "$service_config"
    build_docker_image "$service_name" "$port" || exit 1
done

print_step "PASO 5: Subiendo imágenes a Docker Hub..."

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service_name port <<< "$service_config"
    push_docker_image "$service_name"
done

# Desplegar en Kubernetes
print_step "PASO 6: Desplegando en Kubernetes Staging..."

cd "${PROJECT_ROOT}"

if [ -d "k8s/staging" ]; then
    kubectl apply -f k8s/staging/ --namespace=${NAMESPACE}
    print_success "Manifiestos aplicados exitosamente"
else
    print_error "Directorio k8s/staging no encontrado"
    exit 1
fi

# Esperar a que los pods estén listos
print_step "PASO 7: Esperando a que los pods estén listos..."

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service_name port <<< "$service_config"
    
    echo "  → Esperando ${service_name}..."
    kubectl wait --for=condition=ready pod \
        -l app=${service_name} \
        -n ${NAMESPACE} \
        --timeout=300s || {
        print_warning "${service_name} no está listo aún (puede necesitar más tiempo)"
    }
done

print_success "Despliegue completado"

# Verificar estado
print_step "PASO 8: Verificando estado del despliegue..."

echo -e "\n${CYAN}Pods:${NC}"
kubectl get pods -n ${NAMESPACE}

echo -e "\n${CYAN}Services:${NC}"
kubectl get svc -n ${NAMESPACE}

echo -e "\n${CYAN}HPA:${NC}"
kubectl get hpa -n ${NAMESPACE}

# Probar health endpoints
print_step "PASO 9: Probando health endpoints..."

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service_name port <<< "$service_config"
    
    echo "  → Probando ${service_name}..."
    
    # Obtener IP del servicio
    SERVICE_IP=$(kubectl get svc ${service_name} -n ${NAMESPACE} -o jsonpath='{.spec.clusterIP}' 2>/dev/null)
    
    if [ -n "$SERVICE_IP" ]; then
        # Probar con curl desde un pod temporal
        if kubectl run curl-test-${service_name} \
            --image=curlimages/curl:latest \
            --rm -i --restart=Never \
            -n ${NAMESPACE} \
            -- curl -s -f http://${SERVICE_IP}:${port}/actuator/health &> /dev/null; then
            print_success "${service_name} respondiendo correctamente"
        else
            print_warning "${service_name} no responde aún (puede necesitar inicialización)"
        fi
    else
        print_warning "No se pudo obtener IP del servicio ${service_name}"
    fi
done

# Resumen final
echo -e "\n${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}          ✅ DESPLIEGUE COMPLETADO EXITOSAMENTE              ${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}\n"

echo -e "${CYAN}Resumen del Despliegue:${NC}"
echo -e "  • Namespace: ${NAMESPACE}"
echo -e "  • Servicios desplegados: ${#SERVICES[@]}"
echo -e "  • Versión de imágenes: ${VERSION}"
echo -e "  • Registry: ${DOCKER_REGISTRY_USER}"

echo -e "\n${CYAN}Comandos útiles:${NC}"
echo -e "  • Ver pods:    ${YELLOW}kubectl get pods -n ${NAMESPACE}${NC}"
echo -e "  • Ver logs:    ${YELLOW}kubectl logs -f deployment/user-service -n ${NAMESPACE}${NC}"
echo -e "  • Port-forward: ${YELLOW}kubectl port-forward -n ${NAMESPACE} svc/user-service 8700:8700${NC}"
echo -e "  • Ver eventos: ${YELLOW}kubectl get events -n ${NAMESPACE} --sort-by='.lastTimestamp'${NC}"

echo -e "\n${CYAN}Acceso a Monitoreo:${NC}"
echo -e "  • Grafana: ${YELLOW}http://grafana.35.226.128.62.nip.io${NC}"
echo -e "  • Jenkins: ${YELLOW}http://jenkins.35.226.128.62.nip.io${NC}"

echo -e "\n${CYAN}Próximos pasos:${NC}"
echo -e "  1. Verificar que todos los pods estén en estado Running"
echo -e "  2. Configurar Ingress para acceso externo (opcional)"
echo -e "  3. Configurar pipelines de Jenkins"
echo -e "  4. Ejecutar pruebas E2E"

echo -e "\n${GREEN}¡Despliegue completado! 🚀${NC}\n"

