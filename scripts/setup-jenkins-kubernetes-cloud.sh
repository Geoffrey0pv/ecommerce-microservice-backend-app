#!/bin/bash

###############################################################################
# Script de Configuración de Kubernetes Cloud en Jenkins
# 
# Este script prepara todo lo necesario para que Jenkins pueda crear
# pods dinámicos en el namespace 'staging' usando el Kubernetes Plugin
###############################################################################

set -e

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Configuración de Kubernetes Cloud para Jenkins         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Namespace
NAMESPACE="staging"

echo -e "${YELLOW}📋 Paso 1: Aplicando RBAC para Jenkins...${NC}"
kubectl apply -f manifests-gcp/jenkins-rbac.yaml
echo -e "${GREEN}✅ RBAC aplicado${NC}"
echo ""

echo -e "${YELLOW}📋 Paso 2: Aplicando PVCs para cache de dependencias...${NC}"
kubectl apply -f manifests-gcp/jenkins-pvc.yaml
echo -e "${GREEN}✅ PVCs aplicados${NC}"
echo ""

echo -e "${YELLOW}📋 Paso 3: Verificando recursos creados...${NC}"
echo "ServiceAccount:"
kubectl get sa jenkins-agent -n ${NAMESPACE}
echo ""
echo "Role:"
kubectl get role jenkins-agent-role -n ${NAMESPACE}
echo ""
echo "RoleBinding:"
kubectl get rolebinding jenkins-agent-rolebinding -n ${NAMESPACE}
echo ""
echo "PVCs:"
kubectl get pvc -n ${NAMESPACE} | grep -E "(maven-cache|npm-cache)" || echo "⚠️ PVCs no encontrados"
echo ""

echo -e "${YELLOW}📋 Paso 4: Generando token del ServiceAccount...${NC}"
TOKEN=$(kubectl create token jenkins-agent -n ${NAMESPACE} --duration=999999h 2>/dev/null || \
        kubectl get secret -n ${NAMESPACE} -o jsonpath='{.items[?(@.metadata.annotations.kubernetes\.io/service-account\.name=="jenkins-agent")].data.token}' | base64 -d)

if [ -z "$TOKEN" ]; then
    echo -e "${RED}❌ No se pudo obtener el token del ServiceAccount${NC}"
    echo "Intenta manualmente:"
    echo "  kubectl create token jenkins-agent -n ${NAMESPACE} --duration=999999h"
    exit 1
fi

echo -e "${GREEN}✅ Token generado correctamente${NC}"
echo ""

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Token del ServiceAccount (CÓPIALO)                      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}${TOKEN}${NC}"
echo ""

echo -e "${YELLOW}📋 Paso 5: Verificando permisos del ServiceAccount...${NC}"
echo -n "  - Crear pods: "
kubectl auth can-i create pods --as=system:serviceaccount:${NAMESPACE}:jenkins-agent -n ${NAMESPACE} && echo -e "${GREEN}✅${NC}" || echo -e "${RED}❌${NC}"

echo -n "  - Ver pods: "
kubectl auth can-i get pods --as=system:serviceaccount:${NAMESPACE}:jenkins-agent -n ${NAMESPACE} && echo -e "${GREEN}✅${NC}" || echo -e "${RED}❌${NC}"

echo -n "  - Eliminar pods: "
kubectl auth can-i delete pods --as=system:serviceaccount:${NAMESPACE}:jenkins-agent -n ${NAMESPACE} && echo -e "${GREEN}✅${NC}" || echo -e "${RED}❌${NC}"

echo -n "  - Ver logs de pods: "
kubectl auth can-i get pods/log --as=system:serviceaccount:${NAMESPACE}:jenkins-agent -n ${NAMESPACE} && echo -e "${GREEN}✅${NC}" || echo -e "${RED}❌${NC}"

echo -n "  - Ejecutar en pods: "
kubectl auth can-i create pods/exec --as=system:serviceaccount:${NAMESPACE}:jenkins-agent -n ${NAMESPACE} && echo -e "${GREEN}✅${NC}" || echo -e "${RED}❌${NC}"

echo ""

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Siguiente: Configurar en Jenkins                        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}1. Agregar Credenciales:${NC}"
echo "   - Ve a: Manage Jenkins → Manage Credentials → (global)"
echo "   - Click: Add Credentials"
echo "   - Kind: ${GREEN}Secret text${NC}"
echo "   - Scope: ${GREEN}Global${NC}"
echo "   - Secret: ${GREEN}<pega el token de arriba>${NC}"
echo "   - ID: ${GREEN}kubernetes-jenkins-agent-token${NC}"
echo "   - Description: ${GREEN}ServiceAccount token for jenkins-agent in staging${NC}"
echo "   - Click: ${GREEN}Create${NC}"
echo ""

echo -e "${YELLOW}2. Configurar Kubernetes Cloud:${NC}"
echo "   - Ve a: Manage Jenkins → Nodes and Clouds → Clouds"
echo "   - Click: ${GREEN}New cloud${NC}"
echo "   - Name: ${GREEN}kubernetes${NC}"
echo "   - Type: ${GREEN}Kubernetes${NC}"
echo "   - Click: ${GREEN}Create${NC}"
echo ""

echo -e "${YELLOW}3. Configuración del Cloud:${NC}"
echo "   - Kubernetes URL: ${GREEN}https://kubernetes.default${NC}"
echo "   - Kubernetes server certificate key: ${GREEN}(dejar vacío)${NC}"
echo "   - ☑️ Disable https certificate check: ${GREEN}MARCADO${NC} (solo para dev/staging)"
echo "   - Kubernetes Namespace: ${GREEN}staging${NC}"
echo "   - Credentials: ${GREEN}kubernetes-jenkins-agent-token${NC}"
echo "   - Jenkins URL: ${GREEN}http://jenkins.jenkins.svc.cluster.local:8080${NC} (ajusta según tu instalación)"
echo "   - Jenkins tunnel: ${GREEN}(dejar vacío)${NC}"
echo ""

echo -e "${YELLOW}4. Test Connection:${NC}"
echo "   - Click en ${GREEN}Test Connection${NC}"
echo "   - Deberías ver: ${GREEN}Connected to Kubernetes vX.XX.X${NC}"
echo "   - Click: ${GREEN}Save${NC}"
echo ""

echo -e "${YELLOW}5. Probar Pipeline:${NC}"
echo "   - Ejecuta: ${GREEN}user-service-stage-pipeline-k8s-plugin${NC}"
echo "   - Observa los pods creados: ${GREEN}kubectl get pods -n staging -w${NC}"
echo ""

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Recursos Útiles                                         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "📄 Documentación completa: docs/CONFIGURE_JENKINS_KUBERNETES_CLOUD.md"
echo "🔍 Ver pods en tiempo real: kubectl get pods -n staging -w"
echo "📋 Ver eventos del cluster: kubectl get events -n staging --sort-by=.lastTimestamp"
echo "🗂️ Ver PVCs: kubectl get pvc -n staging"
echo ""

echo -e "${GREEN}✅ Preparación completada!${NC}"
echo ""
