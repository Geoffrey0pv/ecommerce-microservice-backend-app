#!/bin/bash

# Script de despliegue para microservicios usando templates funcionales

BASE_DIR="./manifests"
NAMESPACE="default"

echo "🚀 Iniciando despliegue de microservicios en Minikube"
echo "📁 Directorio base: $BASE_DIR"
echo "🎯 Namespace: $NAMESPACE"
echo ""

# Orden de despliegue (respetando dependencias)
CHARTS=(
  "discovery"      # Primero Eureka (otros dependen de él)
  "zipkin"         # Segundo el tracing (opcional pero útil)
  "user-service"   # Luego los microservicios
  "product-service"
  "order-service"
)

# Función para verificar estado del pod
check_pod_status() {
  local service_name=$1
  echo "🔍 Verificando estado de $service_name..."
  kubectl get pods -l app=$service_name --no-headers | head -1
}

# Función para esperar que el pod esté listo
wait_for_pod() {
  local service_name=$1
  local max_attempts=30
  local attempt=1
  
  echo "⏳ Esperando que $service_name esté listo..."
  
  while [ $attempt -le $max_attempts ]; do
    local pod_status=$(kubectl get pods -l app=$service_name --no-headers 2>/dev/null | head -1 | awk '{print $3}')
    
    if [ "$pod_status" = "Running" ]; then
      echo "✅ $service_name está corriendo"
      return 0
    elif [ "$pod_status" = "CrashLoopBackOff" ] || [ "$pod_status" = "Error" ]; then
      echo "❌ $service_name falló con estado: $pod_status"
      return 1
    fi
    
    echo "🔄 Intento $attempt/$max_attempts - Estado actual: $pod_status"
    sleep 10
    attempt=$((attempt + 1))
  done
  
  echo "⏰ Timeout esperando $service_name"
  return 1
}

# Despliegue secuencial
for CHART in "${CHARTS[@]}"; do
  echo "🚀 Desplegando $CHART..."
  
  if [ ! -d "$BASE_DIR/$CHART" ]; then
    echo "⚠️  Directorio $BASE_DIR/$CHART no encontrado, saltando..."
    continue
  fi
  
  # Instalar o actualizar el chart
  helm upgrade --install "$CHART" "$BASE_DIR/$CHART" --namespace "$NAMESPACE"
  
  if [ $? -eq 0 ]; then
    echo "📦 Chart $CHART instalado/actualizado correctamente"
    
    # Para discovery, esperamos que esté listo antes de continuar
    if [ "$CHART" = "discovery" ]; then
      wait_for_pod "discovery"
      if [ $? -ne 0 ]; then
        echo "❌ Discovery falló. Deteniendo despliegue."
        exit 1
      fi
      echo "🎯 Discovery listo, continuando con el resto..."
      echo ""
    fi
  else
    echo "❌ Error instalando $CHART"
    exit 1
  fi
  
  echo ""
done

echo "🎉 Despliegue completado!"
echo ""
echo "📊 Estado final de los pods:"
kubectl get pods -l 'app in (discovery,zipkin,user-service,product-service,order-service)'
echo ""
echo "🌐 Servicios desplegados:"
kubectl get services -l 'app in (discovery,zipkin,user-service,product-service,order-service)'
echo ""
echo "🔍 Para ver logs de un servicio específico:"
echo "kubectl logs -l app=<service-name> --tail=50"