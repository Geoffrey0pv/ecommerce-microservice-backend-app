#!/bin/bash

# Script para configurar Jobs de Jenkins para el taller
# Geoffrey0pv - Taller 2: Pruebas y lanzamiento

echo "🚀 Configurando Jobs de Jenkins para el taller..."

# Definir servicios principales para el taller
SERVICES=(
    "user-service"
    "product-service" 
    "order-service"
    "payment-service"
    "shipping-service"
    "favourite-service"
)

# Información del proyecto
PROJECT_DIR="/home/geoffrey0pv/Projects/2025-2/Ingeosft5/ecommerce-microservice-backend-app"
JENKINS_URL="http://localhost:8080"

echo "📁 Directorio del proyecto: $PROJECT_DIR"
echo "🔧 Jenkins URL: $JENKINS_URL"

echo ""
echo "📋 SERVICIOS A CONFIGURAR:"
for service in "${SERVICES[@]}"; do
    echo "   ✓ $service"
done

echo ""
echo "📝 PASOS PARA CONFIGURAR MANUALMENTE EN JENKINS:"
echo ""

counter=1
for service in "${SERVICES[@]}"; do
    echo "=== JOB $counter: $service-dev ==="
    echo "1. Ir a Jenkins: $JENKINS_URL"
    echo "2. Crear New Item → Pipeline → Nombre: '$service-dev'"
    echo "3. En Pipeline Definition: 'Pipeline script from SCM'"
    echo "4. SCM: Git"
    echo "5. Repository URL: $PROJECT_DIR/.git"
    echo "6. Branch: */master"
    echo "7. Script Path: jenkins-pipelines/$service-pipeline.groovy"
    echo "8. Guardar"
    echo ""
    
    echo "=== JOB $((counter+6)): $service-stage ==="
    echo "1. Crear New Item → Pipeline → Nombre: '$service-stage'"
    echo "2. En Pipeline Definition: 'Pipeline script from SCM'"
    echo "3. SCM: Git" 
    echo "4. Repository URL: $PROJECT_DIR/.git"
    echo "5. Branch: */master"
    echo "6. Script Path: jenkins-pipelines/$service-stage-pipeline.groovy"
    echo "7. Guardar"
    echo ""
    
    ((counter++))
done

echo "=== JOB ESPECIAL: ecommerce-integration ==="
echo "1. Crear New Item → Pipeline → Nombre: 'ecommerce-integration'"
echo "2. En Pipeline Definition: 'Pipeline script from SCM'"
echo "3. SCM: Git"
echo "4. Repository URL: $PROJECT_DIR/.git" 
echo "5. Branch: */master"
echo "6. Script Path: jenkins-pipelines/ecommerce-integration-pipeline.groovy"
echo "7. Guardar"
echo ""

echo "🔧 CONFIGURACIONES ADICIONALES NECESARIAS:"
echo ""
echo "1. DOCKER HUB CREDENTIALS:"
echo "   - Ir a Manage Jenkins → Credentials"
echo "   - Add Credentials → Username/Password"
echo "   - ID: 'dockerhub-credentials'"
echo "   - Username: geoffrey0pv"
echo "   - Password: [tu password de Docker Hub]"
echo ""

echo "2. VERIFICAR PLUGINS INSTALADOS:"
echo "   - Docker Pipeline Plugin"
echo "   - Git Plugin"
echo "   - Pipeline Plugin"
echo "   - Build Trigger Plugin"
echo ""

echo "3. HERRAMIENTAS NECESARIAS EN EL SISTEMA:"
echo "   - Docker (✓ disponible)"
echo "   - Maven (para builds locales)"
echo "   - Git (✓ disponible)"
echo ""

echo "🎯 EJECUCIÓN DE PRUEBAS:"
echo "Una vez configurados los jobs, ejecutar en este orden:"
echo "1. user-service-dev"
echo "2. product-service-dev" 
echo "3. order-service-dev"
echo "4. ecommerce-integration (se dispara automáticamente)"
echo ""

echo "✅ Script completado. Configurar manualmente en Jenkins UI."