pipeline {
    agent any
    
    environment {
        IMAGE_NAME = "user-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}"
        IMAGE_TAG = "latest-dev" 
        
        // Credenciales
        GCP_CREDENTIALS = credentials('gke-credentials')
        GCP_PROJECT = "ecommerce-backend-1760307199"
        
        // Clúster
        CLUSTER_NAME = "ecommerce-devops-cluster" 
        CLUSTER_LOCATION_FLAG = "--region=us-central1"
        
        // Kubernetes
        K8S_NAMESPACE = "staging"
        K8S_DEPLOYMENT_NAME = "user-service"
        K8S_CONTAINER_NAME = "user-service"
        K8S_SERVICE_NAME = "user-service"
        SERVICE_PORT = "8200"
        
        // Gateway para Pruebas
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
                        ...
                        helm upgrade --install \${K8S_DEPLOYMENT_NAME} manifests-gcp/user-service/ \
                            --namespace \${K8S_NAMESPACE} \
                            --set image.tag=\${IMAGE_TAG} \
                            --set env[4].name=EUREKA_CLIENT_REGISTER_WITH_EUREKA,env[4].value="false" \
                            --set env[5].name=EUREKA_CLIENT_FETCH_REGISTRY,env[5].value="false" \
                            --wait --timeout=5m
                    """
                }
            }
        }

        stage('Health Check & Smoke Tests') {
            steps {
                script {
                    sh """
                        echo "🏥 Ejecutando health checks internos..."
                        # La etiqueta 'app' viene de tus manifiestos de Helm
                        kubectl wait --for=condition=ready pod -l app=user-service -n \${K8S_NAMESPACE} --timeout=300s
                        
                        POD_NAME=\$(kubectl get pods -n \${K8S_NAMESPACE} -l app=user-service -o jsonpath='{.items[0].metadata.name}')
                        
                        for i in {1..10}; do
                            echo "Intento \$i/10: Verificando http://localhost:${SERVICE_PORT}/actuator/health"
                            if kubectl exec \$POD_NAME -n \${K8S_NAMESPACE} -- curl -s --fail http://localhost:${SERVICE_PORT}/actuator/health | grep '"status":"UP"'; then
                                echo "✅ Health check interno exitoso."
                                break
                            fi
                            if [ \$i -eq 10 ]; then
                                echo "❌ Health check interno falló."
                                kubectl logs \$POD_NAME -n \${K8S_NAMESPACE} --tail=50
                                exit 1
                            fi
                            sleep 10
                        done
                    """
                }
            }
        }

        stage('Get Gateway IP for Tests') {
            steps {
                script {
                    echo "🌐 Obteniendo IP externa del API Gateway (\${API_GATEWAY_SERVICE_NAME})..."
                    sh """
                        for i in {1..30}; do
                            # Asumimos que el gateway también está en el namespace 'staging'
                            STAGING_GATEWAY_IP=\$(kubectl get svc \${API_GATEWAY_SERVICE_NAME} -n \${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                            if [ -n "\$STAGING_GATEWAY_IP" ]; then
                                echo "✅ IP del Gateway de Staging: \$STAGING_GATEWAY_IP"
                                break
                            fi
                            echo "Esperando IP del Gateway (LoadBalancer)... intento \$i/30"
                            sleep 10
                        done
                        if [ -z "\$STAGING_GATEWAY_IP" ]; then
                            echo "❌ Error: No se pudo obtener la IP del Gateway. Asegúrate de que '\${API_GATEWAY_SERVICE_NAME}' esté desplegado en '\${K8S_NAMESPACE}' y sea de tipo LoadBalancer."
                            exit 1
                        fi
                    """
                    env.STAGING_GATEWAY_IP = sh(script: "echo \$STAGING_GATEWAY_IP", returnStdout: true).trim()
                }
            }
        }
        
        stage('Run E2E Tests (Maven)') {
            steps {
                script {
                    echo "🧪 Ejecutando pruebas E2E contra http://${env.STAGING_GATEWAY_IP}..."
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        sh "mvn test -f tests/e2e/pom.xml -Dapi.gateway.url=http://${env.STAGING_GATEWAY_IP}"
                    }
                }
            }
            post {
                always {
                    junit 'tests/e2e/target/surefire-reports/*.xml'
                }
            }
        }
        
        stage('Run Performance Tests (Locust)') {
            steps {
                script {
                    echo "⚡ Ejecutando pruebas de rendimiento (Locust) contra http://${env.STAGING_GATEWAY_IP}..."
                    docker.image('locustio/locust').inside {
                        sh "cp -R tests/performance /home/locust"
                        sh """
                            locust -f /home/locust/ecommerce_load_test.py \
                                --host=http://${env.STAGING_GATEWAY_IP} \
                                --headless --users 100 --spawn-rate 10 --run-time 1m \
                                --exit-code-on-fail 1 --html /home/locust/report.html
                        """
                    }
                    archiveArtifacts artifacts: 'report.html', allowEmptyArchive: true
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
                    # El rollback de Helm es más robusto
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