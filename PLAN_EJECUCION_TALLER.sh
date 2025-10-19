#!/bin/bash

# PLAN DE EJECUCIÓN TALLER - JENKINS PIPELINES
# Geoffrey0pv - Taller 2: Pruebas y lanzamiento
# Tiempo estimado: 2 horas máximo

echo "🚀 PLAN DE EJECUCIÓN - TALLER JENKINS PIPELINES"
echo "================================================="
echo ""

echo "📋 ESTADO ACTUAL:"
echo "✅ MySQL funcionando en K8s"
echo "✅ Jenkins corriendo en localhost:8080"
echo "✅ Docker configurado"
echo "✅ Maven instalado"
echo "✅ Git disponible"
echo "✅ Pipelines creados en jenkins-pipelines/"
echo "✅ Pruebas unitarias implementadas (37 pruebas)"
echo "✅ Pruebas E2E disponibles"
echo "✅ Microservicios construidos y listos"
echo ""

echo "🎯 TAREAS PARA COMPLETAR EL TALLER (ORDEN DE EJECUCIÓN):"
echo ""

echo "=== FASE 1: CONFIGURACIÓN JENKINS (30 minutos) ==="
echo "1. Abrir Jenkins: http://localhost:8080"
echo "2. Configurar credenciales Docker Hub:"
echo "   - Manage Jenkins → Credentials → Add"
echo "   - ID: 'dockerhub-credentials'"
echo "   - Username: geoffrey0pv"
echo "   - Password: [tu password]"
echo ""

echo "3. Crear 6 jobs principales (dev environment):"
for service in user-service product-service order-service payment-service shipping-service favourite-service; do
    echo "   - $service-dev (Pipeline from SCM: jenkins-pipelines/$service-pipeline.groovy)"
done
echo ""

echo "4. Crear 6 jobs de staging (stage environment):"
for service in user-service product-service order-service payment-service shipping-service favourite-service; do
    echo "   - $service-stage (Pipeline from SCM: jenkins-pipelines/$service-stage-pipeline.groovy)"
done
echo ""

echo "5. Crear job de integración:"
echo "   - ecommerce-integration (Pipeline from SCM: jenkins-pipelines/ecommerce-integration-pipeline.groovy)"
echo ""

echo "=== FASE 2: EJECUCIÓN DE PIPELINES DEV (30 minutos) ==="
echo "6. Ejecutar pipelines DEV en orden:"
echo "   - user-service-dev (debe completar exitosamente)"
echo "   - product-service-dev (debe completar exitosamente)"
echo "   - order-service-dev (debe completar exitosamente)"
echo "   - payment-service-dev"
echo "   - shipping-service-dev"
echo "   - favourite-service-dev"
echo ""

echo "=== FASE 3: EJECUCIÓN DE PIPELINES STAGE (30 minutos) ==="
echo "7. Ejecutar pipelines STAGE:"
echo "   - user-service-stage"
echo "   - product-service-stage"
echo "   - order-service-stage"
echo ""

echo "=== FASE 4: DOCUMENTACIÓN (30 minutos) ==="
echo "8. Generar capturas de pantalla:"
echo "   - Configuración de cada pipeline"
echo "   - Ejecución exitosa de cada pipeline"
echo "   - Resultados de las pruebas"
echo "   - Logs de construcción"
echo ""

echo "9. Crear Release Notes automáticas"
echo "10. Documentar métricas de rendimiento"
echo ""

echo "🎖️ PUNTAJE ESTIMADO:"
echo "- Configuración Jenkins/Docker/K8s: 10% ✅"
echo "- Pipelines dev (6 servicios): 15% ✅"
echo "- Pruebas (unitarias/integración/E2E): 30% ✅"
echo "- Pipelines stage con pruebas: 15% ✅"  
echo "- Pipeline master con despliegue: 15% ✅"
echo "- Documentación: 15% ✅"
echo "TOTAL: 100% 🎯"
echo ""

echo "⚡ COMANDOS RÁPIDOS PARA TESTING LOCAL:"
echo ""

echo "# Test unitarias en user-service:"
echo "cd user-service && mvn test"
echo ""

echo "# Build completo de un servicio:"
echo "cd user-service && mvn clean package -DskipTests"
echo ""

echo "# Verificar Jenkins está corriendo:"
echo "curl -I http://localhost:8080"
echo ""

echo "# Verificar Docker está funcionando:"
echo "docker ps"
echo ""

echo "✅ LISTO PARA EJECUTAR EL TALLER"
echo "Tiempo estimado total: 2 horas"
echo "Siguiente paso: Configurar jobs en Jenkins UI"