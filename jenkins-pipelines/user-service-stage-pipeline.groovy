pipeline {
    agent any
    
    environment {
        IMAGE_NAME = "user-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}"
        
        IMAGE_TAG = "latest-dev" 
        
        GCP_CREDENTIALS = credentials('gke-credentials')
        GCP_PROJECT = "ecommerce-backend-1760307199"
        
        CLUSTER_NAME = "ecommerce-devops-cluster" 
        CLUSTER_LOCATION_FLAG = "--region=us-central1"
        
        K8S_NAMESPACE = "staging"
        K8S_DEPLOYMENT_NAME = "user-service"
        K8S_CONTAINER_NAME = "user-service"
        K8S_SERVICE_NAME = "user-service"
        SERVICE_PORT = "8700" 
        
        API_GATEWAY_SERVICE_NAME = "proxy-client" 
    }

    stages {
        
        stage('Checkout SCM') {
            steps {
                checkout scm
                echo "📦 Iniciando despliegue a STAGING"
                echo "📦 Imagen a desplegar: ${FULL_IMAGE_NAME}:${IMAGE_TAG}"
            }
        }

        stage('Authenticate GCP & Kubernetes') {
            steps {
                script {
                    sh """
                        echo "🔐 Autenticando con GCP..."
                        gcloud auth activate-service-account --key-file=\${GCP_CREDENTIALS}
                        gcloud config set project \${GCP_PROJECT}
                        gcloud auth configure-docker us-central1-docker.pkg.dev --quiet
                        echo "☸️ Obteniendo credenciales de GKE..."
                        gcloud container clusters get-credentials \${CLUSTER_NAME} \${CLUSTER_LOCATION_FLAG} --project \${GCP_PROJECT}
                    """
                }
            }
        }

        stage('Verify Image Exists in GCR') {
            steps {
                script {
                    sh """
                        echo "🔍 Verificando \${FULL_IMAGE_NAME}:\${IMAGE_TAG}..."
                        gcloud artifacts docker images describe \${FULL_IMAGE_NAME}:\${IMAGE_TAG} || {
                            echo "❌ ERROR: Imagen no encontrada"
                            echo "Asegúrate de que el pipeline de DEV ('user-service-pipeline.groovy') haya corrido exitosamente."
                            exit 1
                        }
                        echo "✅ Imagen verificada."
                    """
                }
            }
        }
        
        stage('Deploy to Staging (Helm)') {
            steps {
                script {
                    sh """
                        echo "🚀 Desplegando a \${K8S_NAMESPACE} usando Helm..."
                        kubectl create namespace \${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        echo "📋 Aplicando/Actualizando Chart de Helm: \${K8S_DEPLOYMENT_NAME}"
                        
                        # Deshabilitamos Eureka para que el pod arranque solo
                        helm upgrade --install \${K8S_DEPLOYMENT_NAME} manifests-gcp/user-service/ \
                            --namespace \${K8S_NAMESPACE} \
                            --set image.tag=\${IMAGE_TAG} \
                            --set env[4].value="false" \
                            --set env[5].value="false" \
                            --wait --timeout=5m
                        
                        echo "✅ Despliegue completado."
                    """
                }
            }
        }

        stage('Health Check & Smoke Tests') {
            steps {
                script {
                    sh """
                        echo "🏥 Ejecutando health checks..."
                        
                        kubectl wait --for=condition=ready pod \
                            -l app=\${K8S_DEPLOYMENT_NAME} \
                            -n \${K8S_NAMESPACE} \
                            --timeout=300s
                        
                        POD_NAME=\$(kubectl get pods -n \${K8S_NAMESPACE} \
                            -l app=\${K8S_DEPLOYMENT_NAME} \
                            -o jsonpath='{.items[0].metadata.name}')
                        
                        # 1. ¡CORRECCIÓN IMPORTANTE! Usando el puerto 8200
                        echo "🎯 Testing pod: \$POD_NAME en puerto \${SERVICE_PORT}"
                        
                        kubectl exec \$POD_NAME -n \${K8S_NAMESPACE} -- \
                            curl -f http://localhost:\${SERVICE_PORT}/user-service/actuator/health || {
                                echo "⚠️ Health check falló"
                                kubectl logs \$POD_NAME -n \${K8S_NAMESPACE} --tail=50
                                exit 1
                            }
                        
                        echo "✅ Health check passed!"
                    """
                }
            }
        }

        stage('Verify Gateway Availability') {
            steps {
                script {
                    sh """
                        echo "🌐 Verificando disponibilidad del API Gateway (\${API_GATEWAY_SERVICE_NAME})..."
                        
                        kubectl wait --for=condition=ready pod \
                            -l app=\${API_GATEWAY_SERVICE_NAME} \
                            -n \${K8S_NAMESPACE} \
                            --timeout=300s
                        
                        GATEWAY_IP=\$(kubectl get svc \${API_GATEWAY_SERVICE_NAME} -n \${K8S_NAMESPACE} \
                            -o jsonpath='{.spec.clusterIP}')
                        
                        if [ -z "\$GATEWAY_IP" ]; then
                            echo "❌ No se pudo obtener la IP del servicio \${API_GATEWAY_SERVICE_NAME}"
                            exit 1
                        fi
                        
                        echo "✅ Gateway ClusterIP: \$GATEWAY_IP"
                        echo "\$GATEWAY_IP" > gateway-ip.txt
                        
                        echo "🔍 Verificando conectividad al Gateway en http://\$GATEWAY_IP:80/app/actuator/health"
                        kubectl run test-gateway-\${BUILD_NUMBER} --image=curlimages/curl:latest \
                            -n \${K8S_NAMESPACE} --rm -i --restart=Never --timeout=60s -- \
                            curl -f --retry 5 --retry-delay 5 --retry-connrefused \
                            http://\$GATEWAY_IP:80/app/actuator/health || {
                                echo "⚠️ No se pudo conectar al Gateway internamente"
                                exit 1
                            }
                        
                        echo "✅ Gateway respondiendo correctamente"
                    """
                }
            }
        }

        stage('Run E2E Tests (Maven)') {
            when {
                expression { fileExists('tests/e2e/pom.xml') }
            }
            steps {
                script {
                    sh """
                        set +e  # No fallar si el port-forward ya existe
                        
                        echo "🌐 =============================================="
                        echo "🌐 Configurando Port-Forward al Gateway"
                        echo "🌐 =============================================="
                        
                        # Matar cualquier port-forward existente en el puerto 8100
                        pkill -f "kubectl port-forward.*proxy-client.*8100" || true
                        
                        # Iniciar port-forward en segundo plano
                        kubectl port-forward svc/\${API_GATEWAY_SERVICE_NAME} 8100:80 -n \${K8S_NAMESPACE} > /dev/null 2>&1 &
                        PORT_FORWARD_PID=\$!
                        echo "Port-forward PID: \$PORT_FORWARD_PID"
                        
                        # Esperar a que el port-forward esté listo
                        echo "Esperando a que el port-forward esté activo..."
                        for i in {1..30}; do
                            if curl -s http://localhost:8100/app/actuator/health > /dev/null 2>&1; then
                                echo "✅ Port-forward activo!"
                                break
                            fi
                            if [ \$i -eq 30 ]; then
                                echo "❌ Port-forward no se pudo establecer"
                                kill \$PORT_FORWARD_PID 2>/dev/null || true
                                exit 1
                            fi
                            sleep 1
                        done
                        
                        set -e  # Volver a modo estricto
                        
                        BASE_URL="http://localhost:8100"
                        
                        echo "🧪 =============================================="
                        echo "🧪 Ejecutando E2E Tests contra: \$BASE_URL"
                        echo "🧪 =============================================="
                        
                        # Ejecuta maven dentro de un contenedor docker
                        # --network host: Permite al contenedor acceder a localhost del host
                        # -v \${WORKSPACE}:/app: Monta tu código en /app
                        echo "🧪 Compilando tests E2E con dependencias JWT..."
                        docker run --rm --network host -v "\${WORKSPACE}":/app -w /app maven:3.9.9-eclipse-temurin-17 \
                            mvn clean test -f tests/e2e/pom.xml \
                            -Dapi.gateway.url=\$BASE_URL \
                            -Dorg.slf4j.simpleLogger.log.org.springframework.web.client=DEBUG
                        
                        echo "📋 Verificando que JwtTestHelper fue compilado..."
                        ls -la tests/e2e/target/test-classes/com/selimhorri/app/e2e/util/JwtTestHelper.class || \
                            echo "⚠️ WARNING: JwtTestHelper.class no encontrado"
                        
                        echo "✅ E2E Tests completados."
                        
                        # Limpiar port-forward
                        echo "🧹 Limpiando port-forward..."
                        kill \$PORT_FORWARD_PID 2>/dev/null || true
                    """
                }
            }
            post {
                always {
                    script {
                        sh "pkill -f 'kubectl port-forward.*proxy-client.*8100' || true"
                    }
                    junit allowEmptyResults: true, testResults: 'tests/e2e/target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'tests/e2e/target/surefire-reports/**/*', allowEmptyArchive: true
                }
            }
        }

        stage('Run Performance Tests (Locust)') {
            when {
                expression { fileExists('tests/performance/ecommerce_load_test.py') }
            }
            steps {
                script {
                    sh """
                        set +e  # No fallar si el port-forward ya existe
                        
                        echo "🌐 =============================================="
                        echo "🌐 Configurando Port-Forward al Gateway para Locust"
                        echo "🌐 =============================================="
                        
                        # Matar cualquier port-forward existente en el puerto 8100
                        pkill -f "kubectl port-forward.*proxy-client.*8100" || true
                        
                        # Iniciar port-forward en segundo plano
                        kubectl port-forward svc/\${API_GATEWAY_SERVICE_NAME} 8100:80 -n \${K8S_NAMESPACE} > /dev/null 2>&1 &
                        PORT_FORWARD_PID=\$!
                        echo "Port-forward PID: \$PORT_FORWARD_PID"
                        
                        # Esperar a que el port-forward esté listo
                        echo "Esperando a que el port-forward esté activo..."
                        for i in {1..30}; do
                            if curl -s http://localhost:8100/app/actuator/health > /dev/null 2>&1; then
                                echo "✅ Port-forward activo!"
                                break
                            fi
                            if [ \$i -eq 30 ]; then
                                echo "❌ Port-forward no se pudo establecer"
                                kill \$PORT_FORWARD_PID 2>/dev/null || true
                                exit 1
                            fi
                            sleep 1
                        done
                        
                        set -e  # Volver a modo estricto
                        
                        BASE_URL="http://localhost:8100"
                        
                        echo "🚀 =============================================="
                        echo "🚀 Ejecutando Performance Tests con Locust"
                        echo "🚀 Target: \$BASE_URL"
                        echo "🚀 =============================================="
                        
                        # Crear directorio para reportes
                        mkdir -p reports
                        
                        # Ejecuta locust dentro de un contenedor docker
                        # --network host: Permite al contenedor acceder a localhost del host
                        # -v \${WORKSPACE}:/mnt/locust: Monta tu código
                        docker run --rm --network host -v "\${WORKSPACE}":/mnt/locust -w /mnt/locust \
                            locustio/locust \
                            -f tests/performance/ecommerce_load_test.py \
                            --host \$BASE_URL \
                            --users 50 --spawn-rate 5 --run-time 1m \
                            --headless \
                            --csv=reports/locust --exit-code-on-fail 0
                        
                        echo "✅ Performance tests completados"
                        
                        # Limpiar port-forward
                        echo "🧹 Limpiando port-forward..."
                        kill \$PORT_FORWARD_PID 2>/dev/null || true
                        
                        # Mostrar estadísticas si existen
                        if [ -f "reports/locust_stats.csv" ]; then
                            echo ""
                            echo "📊 =============================================="
                            echo "📊 RESUMEN DE PERFORMANCE TESTS"
                            echo "📊 =============================================="
                            cat reports/locust_stats.csv
                        fi
                    """
                }
            }
            post {
                always {
                    script {
                        sh "pkill -f 'kubectl port-forward.*proxy-client.*8100' || true"
                    }
                    archiveArtifacts artifacts: 'reports/**/*', allowEmptyArchive: true
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'reports',
                        reportFiles: 'load_test_report.html',
                        reportName: 'Locust Performance Report',
                        reportTitles: 'Performance Test Results'
                    ])
                }
            }
        }
    }

    post {
        success {
            script {
                sh """
                    echo "🎉 ✅ STAGING DEPLOY EXITOSO"
                    echo "📦 Imagen desplegada: \${FULL_IMAGE_NAME}:\${IMAGE_TAG}"
                    gcloud auth activate-service-account --key-file=\${GCP_CREDENTIALS}
                    gcloud auth revoke --all || true
                """
            }
        }
        failure {
            script {
                sh """
                    echo "🔐 Re-autenticando para operaciones de rollback..."
                    gcloud auth activate-service-account --key-file=\${GCP_CREDENTIALS}
                    gcloud config set project \${GCP_PROJECT}
                    gcloud container clusters get-credentials \${CLUSTER_NAME} \${CLUSTER_LOCATION_FLAG} --project \${GCP_PROJECT}
                """
                
                def failedStage = env.STAGE_NAME ?: 'Unknown'
                
                sh """
                    echo "❌ 💥 STAGING DEPLOY FALLÓ"
                    echo "🔍 Fallo detectado en stage: ${failedStage}"
                    
                    if [ "${failedStage}" = "Deploy to Staging (Helm)" ]; then
                        echo "🔄 Realizando rollback del despliegue fallido..."
                        helm rollback \${K8S_DEPLOYMENT_NAME} 0 -n \${K8S_NAMESPACE} || echo "⚠️ No hay revisión anterior para rollback."
                    else
                        echo "⚠️ Fallo en stage '${failedStage}'. El despliegue NO será revertido."
                    fi
                    
                    echo "📋 Información de debug:"
                    kubectl get events -n \${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20
                    gcloud auth revoke --all || true
                """
            }
        }
        always {
            cleanWs()
        }
    }
}