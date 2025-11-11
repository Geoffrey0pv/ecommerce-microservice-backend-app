# scripts/check-services-health.sh
#!/bin/bash

NAMESPACE="staging"

echo "🔍 Verificando estado de servicios en ${NAMESPACE}..."
echo ""

SERVICES=(discovery zipkin user-service product-service order-service payment-service shipping-service favourite-service)

for service in "${SERVICES[@]}"; do
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "📦 ${service}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Estado del pod
    kubectl get pods -n ${NAMESPACE} -l app=${service}
    
    # IP externa
    EXTERNAL_IP=$(kubectl get svc ${service} -n ${NAMESPACE} \
        -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null)
    
    if [ -n "$EXTERNAL_IP" ]; then
        echo "🌐 External IP: ${EXTERNAL_IP}"
    else
        echo "⏳ External IP: Pending..."
    fi
    
    echo ""
done

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Resumen General"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
kubectl get all -n ${NAMESPACE}