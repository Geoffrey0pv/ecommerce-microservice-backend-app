#!/bin/bash

# Script para agregar variables de entorno de MySQL a los deployments

SERVICES=("user-service" "product-service" "order-service")

for SERVICE in "${SERVICES[@]}"; do
  echo "Actualizando $SERVICE..."
  
  # Crear un archivo temporal con las variables de entorno de MySQL
  cat > /tmp/mysql-env.yaml << EOF
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: $SERVICE-config
              key: SPRING_DATASOURCE_URL
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            configMapKeyRef:
              name: $SERVICE-config
              key: SPRING_DATASOURCE_USERNAME
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            configMapKeyRef:
              name: $SERVICE-config
              key: SPRING_DATASOURCE_PASSWORD
EOF

  # Insertar las variables después de EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
  sed -i '/EUREKA_CLIENT_SERVICEURL_DEFAULTZONE/r /tmp/mysql-env.yaml' "k8s/staging/${SERVICE}-deployment.yaml"
  
  echo "✅ $SERVICE actualizado"
done

rm -f /tmp/mysql-env.yaml
echo "✅ Todos los manifiestos actualizados con variables de MySQL"

