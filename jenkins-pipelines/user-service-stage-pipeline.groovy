// jenkins-pipelines/user-service-stage-pipeline.groovy
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
                        
                        # Corrección: Se usan flags '--set' SEPARADOS para cada variable.
                        helm upgrade --install ${K8S_DEPLOYMENT_NAME} manifests-gcp/user-service/ \
                            --namespace ${K8S_NAMESPACE} \
                            --set image.tag=${IMAGE_TAG} \
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
                            -l app=${K8S_DEPLOYMENT_NAME} \
                            -n ${K8S_NAMESPACE} \
                            --timeout=300s
                        
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} \
                            -l app=${K8S_DEPLOYMENT_NAME} \
                            -o jsonpath='{.items[0].metadata.name}')
                        
                        echo "🎯 Testing pod: \$POD_NAME"
                        
                        kubectl exec \$POD_NAME -n ${K8S_NAMESPACE} -- \
                            curl -f http://localhost:${SERVICE_PORT}/user-service/actuator/health || {
                                echo "⚠️ Health check falló"
                                kubectl logs \$POD_NAME -n ${K8S_NAMESPACE} --tail=50
                                exit 1
                            }
                        
                        echo "✅ Health check passed!"
                    """
                }
            }
        }

        stage('Get Gateway IP for Tests') {
            steps {
                script {
                    sh """
                        echo "🌐 Obteniendo IP externa del LoadBalancer..."
                        
                        for i in {1..30}; do
                            EXTERNAL_IP=\$(kubectl get svc ${K8S_DEPLOYMENT_NAME} -n ${K8S_NAMESPACE} \
                                -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
                            
                            if [ ! -z "\$EXTERNAL_IP" ] && [ "\$EXTERNAL_IP" != "<pending>" ]; then
                                echo "✅ IP Externa: \$EXTERNAL_IP"
                                echo "\$EXTERNAL_IP" > gateway-ip.txt
                                break
                            fi
                            
                            echo "⏳ Esperando IP... intento \$i/30"
                            sleep 10
                        done
                        
                        if [ -z "\$EXTERNAL_IP" ] || [ "\$EXTERNAL_IP" == "<pending>" ]; then
                            echo "❌ Timeout esperando IP"
                            exit 1
                        fi
                        
                        curl -f --retry 5 --retry-delay 5 --retry-connrefused \
                            http://\$EXTERNAL_IP:${SERVICE_PORT}/user-service/actuator/health
                        
                        echo "✅ Servicio accesible externamente en \$EXTERNAL_IP:${SERVICE_PORT}"
                    """
                }
            }
        }

        stage('Run E2E Tests (Maven)') {
            steps {
                script {
                    sh """
                        GATEWAY_IP=\$(cat gateway-ip.txt)
                        BASE_URL="http://\${GATEWAY_IP}:${SERVICE_PORT}"
                        
                        echo "🧪 E2E Tests contra: \$BASE_URL"
                        
                        if [ -f "e2e-tests/pom.xml" ]; then
                            cd e2e-tests
                            mvn clean test -Dtest.base.url=\$BASE_URL
                            cd ..
                        else
                            echo "⚠️ No hay pruebas Maven, ejecutando smoke test..."
                            curl -f \$BASE_URL/user-service/actuator/health
                        fi
                    """
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'e2e-tests/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Run Performance Tests (Locust)') {
            steps {
                script {
                    sh """
                        GATEWAY_IP=\$(cat gateway-ip.txt)
                        BASE_URL="http://\${GATEWAY_IP}:${SERVICE_PORT}"
                        
                        echo "🚀 Performance Tests contra: \$BASE_URL"
                        
                        if [ -f "performance-tests/locustfile.py" ]; then
                            cd performance-tests
                            locust -f locustfile.py --host \$BASE_URL \
                                --users 10 --spawn-rate 2 --run-time 1m \
                                --headless --csv=reports/locust-report
                            cd ..
                        else
                            echo "⚠️ No hay scripts Locust, ejecutando test básico..."
                            for i in {1..50}; do
                                curl -s -o /dev/null \$BASE_URL/user-service/actuator/health
                            done
                        fi
                    """
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
                    echo "❌ 💥 STAGING DEPLOY FALLÓ"
                    echo "🔍 Realizando rollback..."
                    helm rollback \${K8S_DEPLOYMENT_NAME} 0 -n \${K8S_NAMESPACE} || echo "No hay revisión anterior para hacer rollback."
                    echo "📋 Información de debug:"
                    kubectl get events -n \${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -10
                    gcloud auth revoke --all || true
                """
            }
        }
        always {
            cleanWs()
        }
    }
}


