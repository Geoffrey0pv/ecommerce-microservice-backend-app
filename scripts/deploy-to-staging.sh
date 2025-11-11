# scripts/deploy-all-services-staging.sh
#!/bin/bash

set -e

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  DESPLIEGUE DE TODOS LOS MICROSERVICIOS A GCP STAGING     ${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}\n"

# Configuración
NAMESPACE="staging"
GCP_PROJECT="ecommerce-backend-1760307199"
CLUSTER_NAME="ecommerce-devops-cluster"
CLUSTER_REGION="us-central1"

# Servicios a desplegar (en orden de dependencias)
declare -A SERVICES=(
    ["discovery"]="8761"
    ["zipkin"]="9411"
    ["user-service"]="8700"
    ["product-service"]="8500"
    ["order-service"]="8300"
    ["payment-service"]="8084"
    ["shipping-service"]="8085"
    ["favourite-service"]="8086"
)

# Función para mostrar progreso
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

# Función para verificar si un pod está listo
wait_for_pod() {
    local service_name=$1
    local max_attempts=60
    local attempt=1
    
    print_step "Esperando a que ${service_name} esté listo..."
    
    while [ $attempt -le $max_attempts ]; do
        READY=$(kubectl get pods -n ${NAMESPACE} -l app=${service_name} \
            -o jsonpath='{.items[0].status.containerStatuses[0].ready}' 2>/dev/null || echo "false")
        
        if [ "$READY" == "true" ]; then
            print_success "${service_name} está listo (intento ${attempt}/${max_attempts})"
            return 0
        fi
        
        echo -n "."
        sleep 5
        attempt=$((attempt + 1))
    done
    
    print_error "${service_name} no está listo después de ${max_attempts} intentos"
    kubectl get pods -n ${NAMESPACE} -l app=${service_name}
    kubectl logs -n ${NAMESPACE} -l app=${service_name} --tail=50
    return 1
}

# Función para verificar health endpoint
check_health() {
    local service_name=$1
    local port=$2
    local context_path=$3
    
    print_step "Verificando health de ${service_name}..."
    
    # Obtener IP externa del LoadBalancer
    local external_ip=""
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        external_ip=$(kubectl get svc ${service_name} -n ${NAMESPACE} \
            -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
        
        if [ -n "$external_ip" ] && [ "$external_ip" != "<pending>" ]; then
            print_success "IP externa obtenida: ${external_ip}"
            break
        fi
        
        echo -n "."
        sleep 5
        attempt=$((attempt + 1))
    done
    
    if [ -z "$external_ip" ]; then
        print_warning "No se pudo obtener IP externa, probando internamente..."
        
        # Test interno desde un pod
        POD_NAME=$(kubectl get pods -n ${NAMESPACE} -l app=${service_name} \
            -o jsonpath='{.items[0].metadata.name}')
        
        kubectl exec ${POD_NAME} -n ${NAMESPACE} -- \
            curl -f http://localhost:${port}${context_path}/actuator/health || {
                print_error "Health check interno falló"
                return 1
            }
        
        print_success "Health check interno OK"
        return 0
    fi
    
    # Test externo
    curl -f --retry 5 --retry-delay 5 \
        http://${external_ip}:${port}${context_path}/actuator/health || {
            print_error "Health check externo falló"
            return 1
        }
    
    print_success "Health check externo OK: http://${external_ip}:${port}${context_path}/actuator/health"
}

# Autenticación GCP
print_step "Autenticando con GCP..."
gcloud auth activate-service-account --key-file=${GOOGLE_APPLICATION_CREDENTIALS}
gcloud config set project ${GCP_PROJECT}
gcloud container clusters get-credentials ${CLUSTER_NAME} --region=${CLUSTER_REGION} --project=${GCP_PROJECT}
print_success "Autenticación completada"

# Crear namespace
print_step "Creando namespace ${NAMESPACE}..."
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
print_success "Namespace listo"

# Desplegar cada servicio
for service in discovery zipkin user-service product-service order-service payment-service shipping-service favourite-service; do
    port=${SERVICES[$service]}
    
    echo -e "\n${YELLOW}═══════════════════════════════════════════════════════${NC}"
    print_step "Desplegando ${service}..."
    echo -e "${YELLOW}═══════════════════════════════════════════════════════${NC}"
    
    # Verificar que existe el chart
    if [ ! -d "manifests-gcp/${service}" ]; then
        print_error "Chart no encontrado: manifests-gcp/${service}"
        continue
    fi
    
    # Desplegar con Helm
    helm upgrade --install ${service} manifests-gcp/${service}/ \
        --namespace ${NAMESPACE} \
        --set image.tag=latest-dev \
        --wait \
        --timeout=5m || {
            print_error "Falló el despliegue de ${service}"
            kubectl get events -n ${NAMESPACE} --sort-by='.lastTimestamp' | tail -20
            exit 1
        }
    
    print_success "${service} desplegado"
    
    # Esperar a que el pod esté listo
    wait_for_pod ${service} || {
        print_error "Pod de ${service} no está listo"
        exit 1
    }
    
    # Verificar health (solo para microservicios, no para discovery/zipkin)
    case $service in
        user-service|product-service|order-service|payment-service|shipping-service|favourite-service)
            check_health ${service} ${port} "/${service}" || {
                print_warning "Health check falló para ${service}, pero continuando..."
            }
            ;;
        discovery)
            check_health ${service} ${port} "" || {
                print_warning "Health check falló para discovery, pero continuando..."
            }
            ;;
    esac
    
    # Pausa entre despliegues
    if [ "$service" == "discovery" ] || [ "$service" == "zipkin" ]; then
        print_step "Esperando 30 segundos para que ${service} se estabilice..."
        sleep 30
    else
        sleep 10
    fi
done

# Resumen final
echo -e "\n${GREEN}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ DESPLIEGUE COMPLETADO EXITOSAMENTE                      ${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}\n"

print_step "Resumen de Servicios Desplegados:"
kubectl get pods -n ${NAMESPACE}

echo ""
print_step "Servicios y sus IPs externas:"
kubectl get svc -n ${NAMESPACE}

echo -e "\n${BLUE}Comandos útiles:${NC}"
echo -e "  • Ver pods:     ${YELLOW}kubectl get pods -n ${NAMESPACE}${NC}"
echo -e "  • Ver services: ${YELLOW}kubectl get svc -n ${NAMESPACE}${NC}"
echo -e "  • Logs:         ${YELLOW}kubectl logs -f <pod-name> -n ${NAMESPACE}${NC}"
echo ""