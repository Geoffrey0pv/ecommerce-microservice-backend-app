#!/bin/bash

# Script para agregar configuración de Eureka a todos los microservicios

services=("user-service" "order-service" "payment-service" "shipping-service" "favourite-service" "proxy-client")

for service in "${services[@]}"; do
    echo "Actualizando $service..."
    
    config_file="$service/src/main/resources/application.yml"
    
    if [ -f "$config_file" ]; then
        # Crear un backup
        cp "$config_file" "$config_file.backup"
        
        # Buscar si ya existe configuración de Eureka
        if ! grep -q "eureka:" "$config_file"; then
            echo "Agregando configuración de Eureka a $service"
            
            # Crear archivo temporal con la nueva configuración
            {
                # Copiar hasta la línea de spring: profiles:
                sed '/^spring:/,/^$/p' "$config_file" | head -n -1
                
                # Agregar configuración de Eureka
                cat << 'EOF'

eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
    register-with-eureka: true
    fetch-registry: true
  instance:
    preferIpAddress: true

EOF
                
                # Copiar el resto del archivo
                sed -n '/^resilience4j:/,$p' "$config_file"
            } > "$config_file.tmp"
            
            mv "$config_file.tmp" "$config_file"
            echo "✅ $service actualizado"
        else
            echo "⚠️  $service ya tiene configuración de Eureka"
        fi
    else
        echo "❌ No se encontró $config_file"
    fi
done

echo "✅ Actualización completada"
