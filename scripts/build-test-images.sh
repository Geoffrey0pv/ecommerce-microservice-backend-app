#!/bin/bash

###############################################################################
# Script para construir y publicar imágenes custom de tests a GCR
###############################################################################

set -e

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Configuración
GCP_PROJECT="ecommerce-backend-1760307199"
GCR_REGISTRY="us-central1-docker.pkg.dev"
REPO_NAME="ecommerce-microservices"
TAG="${TAG:-latest}"

# URLs de las imágenes
MAVEN_IMAGE="$GCR_REGISTRY/$GCP_PROJECT/$REPO_NAME/test-runner-maven:$TAG"
LOCUST_IMAGE="$GCR_REGISTRY/$GCP_PROJECT/$REPO_NAME/test-runner-locust:$TAG"

echo "🐳 Construyendo imágenes custom para tests"
echo "=========================================="
echo "  GCP Project: $GCP_PROJECT"
echo "  Registry: $GCR_REGISTRY"
echo "  Tag: $TAG"
echo ""

# Autenticar con GCP
echo "${YELLOW}🔐 Autenticando con GCP...${NC}"
gcloud auth configure-docker $GCR_REGISTRY --quiet
echo "${GREEN}✅ Autenticado${NC}"
echo ""

# Construir imagen Maven
echo "${YELLOW}📦 Construyendo test-runner-maven...${NC}"
docker build \
    -f docker/test-runner-maven.Dockerfile \
    -t $MAVEN_IMAGE \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    .

echo "${GREEN}✅ Imagen Maven construida: $MAVEN_IMAGE${NC}"
echo ""

# Construir imagen Locust
echo "${YELLOW}📦 Construyendo test-runner-locust...${NC}"
docker build \
    -f docker/test-runner-locust.Dockerfile \
    -t $LOCUST_IMAGE \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    .

echo "${GREEN}✅ Imagen Locust construida: $LOCUST_IMAGE${NC}"
echo ""

# Publicar imágenes
echo "${YELLOW}☁️  Publicando imágenes a GCR...${NC}"
docker push $MAVEN_IMAGE
docker push $LOCUST_IMAGE
echo "${GREEN}✅ Imágenes publicadas${NC}"
echo ""

# Mostrar información
echo "${GREEN}════════════════════════════════════════${NC}"
echo "${GREEN}✅ IMÁGENES LISTAS${NC}"
echo "${GREEN}════════════════════════════════════════${NC}"
echo ""
echo "📦 Imagen Maven:"
echo "   $MAVEN_IMAGE"
echo ""
echo "📦 Imagen Locust:"
echo "   $LOCUST_IMAGE"
echo ""
echo "💡 Uso en Jenkinsfile:"
echo ""
echo "   podTemplate(containers: ["
echo "     containerTemplate("
echo "       name: 'maven',"
echo "       image: '$MAVEN_IMAGE',"
echo "       ttyEnabled: true,"
echo "       command: 'sleep',"
echo "       args: 'infinity'"
echo "     )"
echo "   ]) {"
echo "     node(POD_LABEL) {"
echo "       container('maven') {"
echo "         sh 'mvn clean test'"
echo "       }"
echo "     }"
echo "   }"
echo ""
