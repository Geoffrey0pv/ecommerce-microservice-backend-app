#!/bin/bash

# Script para crear pipelines para los 3 microservicios adicionales
echo "🚀 Creando pipelines para 3 microservicios adicionales..."

# Servicios adicionales seleccionados
ADDITIONAL_SERVICES=("payment-service" "shipping-service" "favourite-service")

echo ""
echo "📋 Servicios adicionales seleccionados:"
echo "   💳 payment-service - Procesamiento de pagos"
echo "   📦 shipping-service - Gestión de envíos"
echo "   ❤️ favourite-service - Lista de favoritos"
echo ""

cd jenkins-pipelines

# Función para crear pipeline DEV
create_dev_pipeline() {
    local service=$1
    local pipeline_file="${service}-pipeline.groovy"
    
    echo "🔵 Creando pipeline DEV para: $service"
    
    # Usar user-service como template y adaptar
    sed "s/user-service/$service/g" user-service-pipeline.groovy > "$pipeline_file"
    
    echo "   ✅ Creado: $pipeline_file"
}

# Función para crear pipeline STAGE  
create_stage_pipeline() {
    local service=$1
    local pipeline_file="${service}-stage-pipeline.groovy"
    
    echo "🟡 Creando pipeline STAGE para: $service"
    
    # Usar user-service como template y adaptar
    sed "s/user-service/$service/g" user-service-stage-pipeline.groovy > "$pipeline_file"
    
    echo "   ✅ Creado: $pipeline_file"
}

# Función para crear pipeline MASTER
create_master_pipeline() {
    local service=$1
    local pipeline_file="${service}-master-pipeline.groovy"
    
    echo "🟢 Creando pipeline MASTER para: $service"
    
    # Usar user-service como template y adaptar
    sed "s/user-service/$service/g" user-service-master-pipeline.groovy > "$pipeline_file"
    
    echo "   ✅ Creado: $pipeline_file"
}

echo "🔵 CREANDO PIPELINES DEV..."
for service in "${ADDITIONAL_SERVICES[@]}"; do
    create_dev_pipeline "$service"
done

echo ""
echo "🟡 CREANDO PIPELINES STAGE..."
for service in "${ADDITIONAL_SERVICES[@]}"; do
    create_stage_pipeline "$service"
done

echo ""
echo "🟢 CREANDO PIPELINES MASTER..."
for service in "${ADDITIONAL_SERVICES[@]}"; do
    create_master_pipeline "$service"
done

echo ""
echo "📊 VERIFICANDO PIPELINES CREADOS..."
echo ""

ALL_SERVICES=("user-service" "product-service" "order-service" "payment-service" "shipping-service" "favourite-service")

for service in "${ALL_SERVICES[@]}"; do
    echo "🔍 $service:"
    echo "   🔵 DEV: ${service}-pipeline.groovy $([ -f "${service}-pipeline.groovy" ] && echo "✅" || echo "❌")"
    echo "   🟡 STAGE: ${service}-stage-pipeline.groovy $([ -f "${service}-stage-pipeline.groovy" ] && echo "✅" || echo "❌")"
    echo "   🟢 MASTER: ${service}-master-pipeline.groovy $([ -f "${service}-master-pipeline.groovy" ] && echo "✅" || echo "❌")"
    echo ""
done

echo "📈 RESUMEN FINAL:"
echo "   🔵 DEV Pipelines: $(find . -name "*-pipeline.groovy" -not -name "*-stage*" -not -name "*-master*" -not -name "*integration*" | wc -l)/6 ✅"
echo "   🟡 STAGE Pipelines: $(find . -name "*-stage-pipeline.groovy" | wc -l)/6 ✅"
echo "   🟢 MASTER Pipelines: $(find . -name "*-master-pipeline.groovy" | wc -l)/6 ✅"
echo ""
echo "🎉 ¡PIPELINES PARA 6 MICROSERVICIOS COMPLETADOS!"