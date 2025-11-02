# scripts/teardown-all-services-staging.sh
#!/bin/bash

set -e

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}════════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}  ELIMINANDO TODOS LOS SERVICIOS DE GCP STAGING            ${NC}"
echo -e "${YELLOW}════════════════════════════════════════════════════════════${NC}\n"

# Configuración
NAMESPACE="staging"
GCP_PROJECT="ecommerce-backend-1760307199"
CLUSTER_NAME="ecommerce-devops-cluster"
CLUSTER_REGION="us-central1"

# Servicios a eliminar (en orden inverso)
SERVICES=(
    "favourite-service"
    "shipping-service"
    "payment-service"
    "order-service"
    "product-service"
    "user-service"
    "zipkin"
    "discovery"
)

print_step() {
    echo -e "\n${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Autenticación
print_step "Autenticando con GCP..."
gcloud auth activate-service-account --key-file=${GOOGLE_APPLICATION_CREDENTIALS}
gcloud config set project ${GCP_PROJECT}
gcloud container clusters get-credentials ${CLUSTER_NAME} --region=${CLUSTER_REGION} --project=${GCP_PROJECT}
print_success "Autenticación completada"

# Confirmar eliminación
echo -e "\n${RED}⚠️  ADVERTENCIA: Esto eliminará TODOS los servicios del namespace ${NAMESPACE}${NC}"
read -p "¿Estás seguro? (escriba 'yes' para confirmar): " confirmation

if [ "$confirmation" != "yes" ]; then
    echo "Operación cancelada"
    exit 0
fi

# Eliminar cada servicio
for service in "${SERVICES[@]}"; do
    print_step "Eliminando ${service}..."
    
    helm uninstall ${service} -n ${NAMESPACE} 2>/dev/null || {
        print_warning "${service} no encontrado o ya eliminado"
    }
    
    print_success "${service} eliminado"
    sleep 2
done

# Opcional: Eliminar namespace completo
echo ""
read -p "¿Deseas eliminar el namespace ${NAMESPACE} completo? (y/n): " delete_ns

if [ "$delete_ns" == "y" ] || [ "$delete_ns" == "Y" ]; then
    print_step "Eliminando namespace ${NAMESPACE}..."
    kubectl delete namespace ${NAMESPACE} --wait=true
    print_success "Namespace eliminado"
else
    print_warning "Namespace ${NAMESPACE} conservado"
fi

echo -e "\n${GREEN}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ LIMPIEZA COMPLETADA                                     ${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}\n"

# Verificar estado final
kubectl get all -n ${NAMESPACE} 2>/dev/null || echo "Namespace eliminado o vacío"