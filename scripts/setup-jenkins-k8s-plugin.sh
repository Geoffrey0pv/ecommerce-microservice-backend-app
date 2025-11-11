#!/bin/bash

###############################################################################
# Script de setup para Kubernetes Plugin en Jenkins
# Ejecutar DESPUÉS de instalar el plugin desde la UI
###############################################################################

set -e

echo "🔧 SETUP: Jenkins Kubernetes Plugin"
echo "===================================="
echo ""

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Variables de configuración
JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
K8S_NAMESPACE="staging"
GKE_CLUSTER="ecommerce-devops-cluster"
GKE_REGION="us-central1"
GCP_PROJECT="ecommerce-backend-1760307199"

echo "${YELLOW}📋 Configuración:${NC}"
echo "  Jenkins URL: $JENKINS_URL"
echo "  K8s Namespace: $K8S_NAMESPACE"
echo "  GKE Cluster: $GKE_CLUSTER"
echo "  GCP Project: $GCP_PROJECT"
echo ""

# Paso 1: Verificar conexión a GKE
echo "${YELLOW}1️⃣  Verificando conexión a GKE...${NC}"
if ! kubectl cluster-info &>/dev/null; then
    echo "${RED}❌ No hay conexión a Kubernetes. Autenticando...${NC}"
    gcloud container clusters get-credentials $GKE_CLUSTER \
        --region=$GKE_REGION \
        --project=$GCP_PROJECT
else
    echo "${GREEN}✅ Conectado a Kubernetes${NC}"
fi

# Paso 2: Obtener URL del API server de Kubernetes
echo ""
echo "${YELLOW}2️⃣  Obteniendo configuración de Kubernetes...${NC}"
K8S_API_URL=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
K8S_CA_CERT=$(kubectl config view --raw --minify --flatten -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')

echo "${GREEN}✅ Kubernetes API URL:${NC} $K8S_API_URL"
echo ""

# Paso 3: Crear ServiceAccount para Jenkins
echo "${YELLOW}3️⃣  Creando ServiceAccount para Jenkins en namespace '$K8S_NAMESPACE'...${NC}"

kubectl apply -f - <<EOF
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: jenkins-agent
  namespace: $K8S_NAMESPACE
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: jenkins-agent-role
  namespace: $K8S_NAMESPACE
rules:
- apiGroups: [""]
  resources: ["pods", "pods/exec", "pods/log", "persistentvolumeclaims"]
  verbs: ["get", "list", "watch", "create", "delete", "patch", "update"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list", "create", "update", "patch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jenkins-agent-rolebinding
  namespace: $K8S_NAMESPACE
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: jenkins-agent-role
subjects:
- kind: ServiceAccount
  name: jenkins-agent
  namespace: $K8S_NAMESPACE
EOF

echo "${GREEN}✅ ServiceAccount 'jenkins-agent' creado${NC}"
echo ""

# Paso 4: Obtener token del ServiceAccount
echo "${YELLOW}4️⃣  Obteniendo token de autenticación...${NC}"

# Crear un secret para el ServiceAccount (K8s 1.24+)
kubectl apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: jenkins-agent-token
  namespace: $K8S_NAMESPACE
  annotations:
    kubernetes.io/service-account.name: jenkins-agent
type: kubernetes.io/service-account-token
EOF

# Esperar a que el token se genere
sleep 2

# Obtener el token
SERVICE_ACCOUNT_TOKEN=$(kubectl get secret jenkins-agent-token -n $K8S_NAMESPACE -o jsonpath='{.data.token}' | base64 -d)

if [ -z "$SERVICE_ACCOUNT_TOKEN" ]; then
    echo "${RED}❌ No se pudo obtener el token${NC}"
    exit 1
fi

echo "${GREEN}✅ Token obtenido (primeros 20 caracteres):${NC} ${SERVICE_ACCOUNT_TOKEN:0:20}..."
echo ""

# Paso 5: Guardar configuración en archivo
CONFIG_FILE="jenkins-k8s-config.txt"
cat > $CONFIG_FILE <<EOF
================================================================================
CONFIGURACIÓN DE KUBERNETES PLUGIN PARA JENKINS
================================================================================

📋 COPIA ESTOS VALORES EN JENKINS:

1. Ve a: Jenkins → Manage Jenkins → Manage Nodes and Clouds → Configure Clouds
2. Click "Add a new cloud" → Selecciona "Kubernetes"
3. Usa estos valores:

┌─────────────────────────────────────────────────────────────────────────────┐
│ CONFIGURACIÓN BÁSICA                                                        │
└─────────────────────────────────────────────────────────────────────────────┘

Name: kubernetes-staging
Kubernetes URL: $K8S_API_URL
Kubernetes Namespace: $K8S_NAMESPACE
Credentials: [Crear nuevo "Secret text" con el token de abajo]

┌─────────────────────────────────────────────────────────────────────────────┐
│ CREDENTIALS (Secret Text)                                                   │
└─────────────────────────────────────────────────────────────────────────────┘

Kind: Secret text
Secret: $SERVICE_ACCOUNT_TOKEN
ID: k8s-staging-token
Description: Kubernetes staging namespace token

┌─────────────────────────────────────────────────────────────────────────────┐
│ CONFIGURACIÓN AVANZADA                                                      │
└─────────────────────────────────────────────────────────────────────────────┘

☑ Disable https certificate check (solo si usas certificados auto-firmados)
Jenkins URL: http://jenkins.default.svc.cluster.local:8080 
  (o la URL donde Jenkins es accesible desde los pods)

Jenkins tunnel: jenkins-agent.default.svc.cluster.local:50000
  (si Jenkins está dentro del cluster)

┌─────────────────────────────────────────────────────────────────────────────┐
│ POD TEMPLATES (configurar después en el pipeline)                           │
└─────────────────────────────────────────────────────────────────────────────┘

Se configurarán directamente en el Jenkinsfile usando podTemplate {}

================================================================================
SIGUIENTE PASO: Configurar Jenkins UI con estos valores
================================================================================

Después de configurar, ejecuta el test de conexión en Jenkins UI.

================================================================================
EOF

echo "${GREEN}✅ Configuración guardada en: $CONFIG_FILE${NC}"
echo ""
echo "${YELLOW}📄 Contenido del archivo de configuración:${NC}"
cat $CONFIG_FILE
echo ""

# Paso 6: Crear namespace y recursos necesarios
echo "${YELLOW}5️⃣  Verificando recursos en namespace...${NC}"
kubectl get serviceaccount jenkins-agent -n $K8S_NAMESPACE
kubectl get role jenkins-agent-role -n $K8S_NAMESPACE
kubectl get rolebinding jenkins-agent-rolebinding -n $K8S_NAMESPACE
echo ""

echo "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo "${GREEN}✅ SETUP COMPLETADO${NC}"
echo "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo ""
echo "${YELLOW}📌 PRÓXIMOS PASOS:${NC}"
echo ""
echo "1. Instala el plugin 'Kubernetes' en Jenkins (si no lo has hecho)"
echo "   → Jenkins → Manage Plugins → Available → Buscar 'Kubernetes'"
echo ""
echo "2. Configura el cloud en Jenkins usando el archivo: $CONFIG_FILE"
echo "   → Jenkins → Manage Jenkins → Manage Nodes and Clouds → Configure Clouds"
echo ""
echo "3. Prueba la conexión usando 'Test Connection' en Jenkins UI"
echo ""
echo "4. Continúa con el siguiente paso del setup (crear imagen custom)"
echo ""
