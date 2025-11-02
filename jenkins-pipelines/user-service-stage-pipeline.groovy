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
                        
                        # Esperar a que el pod del gateway esté ready
                        kubectl wait --for=condition=ready pod \
                            -l app=\${API_GATEWAY_SERVICE_NAME} \
                            -n \${K8S_NAMESPACE} \
                            --timeout=300s
                        
                        # Obtener el ClusterIP del servicio
                        GATEWAY_IP=\$(kubectl get svc \${API_GATEWAY_SERVICE_NAME} -n \${K8S_NAMESPACE} \
                            -o jsonpath='{.spec.clusterIP}')
                        
                        if [ -z "\$GATEWAY_IP" ]; then
                            echo "❌ No se pudo obtener la IP del servicio \${API_GATEWAY_SERVICE_NAME}"
                            exit 1
                        fi
                        
                        echo "✅ Gateway ClusterIP: \$GATEWAY_IP"
                        echo "\$GATEWAY_IP" > gateway-ip.txt
                        
                        # Verificar conectividad usando un pod temporal
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
                        GATEWAY_IP=\$(cat gateway-ip.txt)
                        BASE_URL="http://\${GATEWAY_IP}"
                        
                        echo "🧪 =============================================="
                        echo "🧪 Ejecutando E2E Tests contra: \$BASE_URL"
                        echo "🧪 =============================================="
                        
                        # Crear ConfigMap con el código de tests
                        kubectl create configmap e2e-tests-code --from-file=tests/e2e/ -n \${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Ejecutar tests en un pod con Maven
                        cat <<'E2E_POD_EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: e2e-test-runner-\${BUILD_NUMBER}
  namespace: \${K8S_NAMESPACE}
spec:
  restartPolicy: Never
  containers:
  - name: maven-test
    image: maven:3.8.6-openjdk-17
    command: ["/bin/bash"]
    args:
    - -c
    - |
      set -e
      echo "📦 Copiando código de tests..."
      cp -r /tests-code/* /workspace/
      cd /workspace
      
      echo "🔨 Compilando y ejecutando tests E2E..."
      mvn clean test \
        -Dapi.gateway.url=\${GATEWAY_IP} \
        -Dmaven.test.failure.ignore=true \
        -Dsurefire.reports.directory=/workspace/target/surefire-reports
      
      echo "📊 Tests E2E completados. Generando reportes..."
      ls -la /workspace/target/surefire-reports/ || true
    env:
    - name: GATEWAY_IP
      value: "\${BASE_URL}"
    volumeMounts:
    - name: tests-code
      mountPath: /tests-code
    - name: test-results
      mountPath: /workspace/target
  volumes:
  - name: tests-code
    configMap:
      name: e2e-tests-code
  - name: test-results
    emptyDir: {}
E2E_POD_EOF
                        
                        echo "⏳ Esperando a que los tests E2E completen (timeout: 15 minutos)..."
                        kubectl wait --for=condition=Ready pod/e2e-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} --timeout=60s || true
                        
                        # Esperar a que termine (máximo 15 minutos)
                        for i in \$(seq 1 90); do
                            POD_STATUS=\$(kubectl get pod e2e-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
                            
                            if [ "\$POD_STATUS" = "Succeeded" ] || [ "\$POD_STATUS" = "Failed" ]; then
                                echo "✅ Tests E2E completados con estado: \$POD_STATUS"
                                break
                            fi
                            
                            echo "⏳ Tests E2E en ejecución... (\$i/90) - Estado: \$POD_STATUS"
                            sleep 10
                        done
                        
                        # Obtener logs de los tests
                        echo "📄 =============================================="
                        echo "📄 LOGS DE E2E TESTS:"
                        echo "📄 =============================================="
                        kubectl logs e2e-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} || true
                        
                        # Copiar reportes de tests desde el pod
                        mkdir -p \${WORKSPACE}/test-results/e2e
                        kubectl cp \${K8S_NAMESPACE}/e2e-test-runner-\${BUILD_NUMBER}:/workspace/target/surefire-reports/ \${WORKSPACE}/test-results/e2e/ || {
                            echo "⚠️ No se pudieron copiar los reportes de E2E"
                        }
                        
                        # Verificar si los tests pasaron
                        POD_STATUS=\$(kubectl get pod e2e-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} -o jsonpath='{.status.phase}')
                        
                        if [ "\$POD_STATUS" != "Succeeded" ]; then
                            echo "❌ E2E Tests fallaron"
                            # No fallar el pipeline, solo advertir
                            echo "⚠️ Continuando pipeline a pesar del fallo en E2E tests"
                        else
                            echo "✅ E2E Tests pasaron exitosamente"
                        fi
                        
                        # Limpiar pod de tests
                        kubectl delete pod e2e-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} --ignore-not-found=true
                    """
                }
            }
            post {
                always {
                    // Publicar reportes JUnit
                    junit allowEmptyResults: true, testResults: 'test-results/e2e/**/*.xml'
                    
                    // Archivar reportes
                    archiveArtifacts artifacts: 'test-results/e2e/**/*', allowEmptyArchive: true
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
                        GATEWAY_IP=\$(cat gateway-ip.txt)
                        BASE_URL="http://\${GATEWAY_IP}"
                        
                        echo "🚀 =============================================="
                        echo "🚀 Ejecutando Performance Tests con Locust"
                        echo "🚀 Target: \$BASE_URL"
                        echo "🚀 =============================================="
                        
                        # Crear ConfigMap con scripts de Locust
                        kubectl create configmap locust-tests-code --from-file=tests/performance/ -n \${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Ejecutar Locust en modo headless
                        cat <<'LOCUST_POD_EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: locust-test-runner-\${BUILD_NUMBER}
  namespace: \${K8S_NAMESPACE}
spec:
  restartPolicy: Never
  containers:
  - name: locust
    image: locustio/locust:2.17.0
    command: ["/bin/bash"]
    args:
    - -c
    - |
      set -e
      echo "📦 Preparando entorno de Locust..."
      cp -r /locust-code/* /workspace/
      cd /workspace
      
      # Instalar dependencias adicionales
      pip install --no-cache-dir faker numpy pandas matplotlib seaborn 2>&1 | tail -20
      
      echo "🚀 Ejecutando Load Test (100 usuarios, 5 minutos)..."
      locust -f ecommerce_load_test.py \
        --host=\${TARGET_HOST} \
        --users 100 \
        --spawn-rate 10 \
        --run-time 5m \
        --headless \
        --csv=/results/load_test \
        --html=/results/load_test_report.html \
        --loglevel INFO \
        --exit-code-on-error 0 || echo "Load test completado con warnings"
      
      echo ""
      echo "📊 =============================================="
      echo "📊 RESUMEN DE PERFORMANCE TESTS"
      echo "📊 =============================================="
      
      # Mostrar estadísticas si existen
      if [ -f /results/load_test_stats.csv ]; then
        echo "📈 Estadísticas generales:"
        cat /results/load_test_stats.csv
        echo ""
      fi
      
      if [ -f /results/load_test_stats_history.csv ]; then
        echo "📉 Historial de estadísticas:"
        tail -20 /results/load_test_stats_history.csv
        echo ""
      fi
      
      if [ -f /results/load_test_failures.csv ]; then
        echo "❌ Fallos detectados:"
        cat /results/load_test_failures.csv
        echo ""
      fi
      
      echo "✅ Performance tests completados"
      ls -lah /results/
    env:
    - name: TARGET_HOST
      value: "\${BASE_URL}"
    volumeMounts:
    - name: locust-code
      mountPath: /locust-code
    - name: test-results
      mountPath: /results
  volumes:
  - name: locust-code
    configMap:
      name: locust-tests-code
  - name: test-results
    emptyDir: {}
LOCUST_POD_EOF
                        
                        echo "⏳ Esperando a que Locust inicie (timeout: 60s)..."
                        kubectl wait --for=condition=Ready pod/locust-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} --timeout=60s || true
                        
                        # Esperar a que termine (máximo 10 minutos para el test de 5 min + overhead)
                        for i in \$(seq 1 60); do
                            POD_STATUS=\$(kubectl get pod locust-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
                            
                            if [ "\$POD_STATUS" = "Succeeded" ] || [ "\$POD_STATUS" = "Failed" ]; then
                                echo "✅ Performance tests completados con estado: \$POD_STATUS"
                                break
                            fi
                            
                            echo "⏳ Locust ejecutando... (\$i/60) - Estado: \$POD_STATUS"
                            sleep 10
                        done
                        
                        # Obtener logs de Locust
                        echo "📄 =============================================="
                        echo "📄 LOGS DE LOCUST:"
                        echo "📄 =============================================="
                        kubectl logs locust-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} --tail=100 || true
                        
                        # Copiar reportes desde el pod
                        mkdir -p \${WORKSPACE}/test-results/performance
                        kubectl cp \${K8S_NAMESPACE}/locust-test-runner-\${BUILD_NUMBER}:/results/ \${WORKSPACE}/test-results/performance/ || {
                            echo "⚠️ No se pudieron copiar los reportes de Locust"
                        }
                        
                        # Analizar resultados de performance
                        if [ -f "\${WORKSPACE}/test-results/performance/load_test_stats.csv" ]; then
                            echo ""
                            echo "📊 =============================================="
                            echo "📊 ANÁLISIS DE MÉTRICAS DE PERFORMANCE"
                            echo "📊 =============================================="
                            
                            # Extraer métricas clave del CSV
                            echo "📈 Tiempo de Respuesta (Response Times):"
                            awk -F',' 'NR>1 {printf "  - %s: Avg=%.2fms, Min=%.2fms, Max=%.2fms\\n", \$1, \$4, \$5, \$6}' \${WORKSPACE}/test-results/performance/load_test_stats.csv
                            
                            echo ""
                            echo "🚦 Throughput (Requests per Second):"
                            awk -F',' 'NR>1 {printf "  - %s: %.2f req/s\\n", \$1, \$9}' \${WORKSPACE}/test-results/performance/load_test_stats.csv
                            
                            echo ""
                            echo "❌ Tasa de Errores (Failure Rate):"
                            awk -F',' 'NR>1 {total=\$2+\$3; if(total>0) printf "  - %s: %.2f%% (%d/%d)\\n", \$1, (\$3/total)*100, \$3, total; else printf "  - %s: 0.00%% (0/0)\\n", \$1}' \${WORKSPACE}/test-results/performance/load_test_stats.csv
                            
                            echo ""
                            echo "📊 Reporte HTML disponible en: test-results/performance/load_test_report.html"
                        else
                            echo "⚠️ No se encontraron estadísticas de Locust"
                        fi
                        
                        # Verificar si los tests pasaron
                        POD_STATUS=\$(kubectl get pod locust-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} -o jsonpath='{.status.phase}')
                        
                        if [ "\$POD_STATUS" != "Succeeded" ]; then
                            echo "⚠️ Performance Tests completaron con warnings"
                            # No fallar el pipeline
                        else
                            echo "✅ Performance Tests exitosos"
                        fi
                        
                        # Limpiar pod de tests
                        kubectl delete pod locust-test-runner-\${BUILD_NUMBER} -n \${K8S_NAMESPACE} --ignore-not-found=true
                    """
                }
            }
            post {
                always {
                    // Archivar todos los reportes de performance
                    archiveArtifacts artifacts: 'test-results/performance/**/*', allowEmptyArchive: true
                    
                    // Publicar reporte HTML de Locust
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'test-results/performance',
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
            script {
                sh "gcloud auth revoke --all || true"
            }
            cleanWs()
        }
    }
}
