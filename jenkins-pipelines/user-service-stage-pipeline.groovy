pipeline {
    agent any
    
    environment {
        IMAGE_NAME = "user-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}"
        
        // Credenciales
        GCP_CREDENTIALS = credentials('gke-credentials')
        GCP_PROJECT = "ecommerce-backend-1760307199"
        
        // Clúster (Nombres de variables corregidos)
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

    // Parámetros para ejecución manual o PR
    parameters {
        string(
            name: 'IMAGE_TAG_MANUAL', 
            defaultValue: '', 
            description: 'Dejar vacío para PRs. Llenar para corridas manuales (ej: latest-dev o un SHA)'
        )
    }

    stages {
        
        stage('Validate Context & Get Image Tag') {
            steps {
                script {
                    checkout scm
                    
                    if (params.IMAGE_TAG_MANUAL) {
                        echo "🏃 Ejecución Manual detectada."
                        env.IMAGE_TAG = params.IMAGE_TAG_MANUAL
                    } else if (env.CHANGE_TARGET == 'staging' && env.CHANGE_BRANCH == 'develop') {
                        echo "🔄 PR de develop -> staging detectado."
                        env.IMAGE_TAG = sh(script: "git rev-parse --short origin/${env.CHANGE_BRANCH}", returnStdout: true).trim()
                    } else {
                        echo "🏃 Ejecución de Rama detectada (no PR)."
                        env.IMAGE_TAG = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    }
                    
                    echo "📦 Imagen a desplegar: ${FULL_IMAGE_NAME}:${env.IMAGE_TAG}"
                }
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
                        echo "🔍 Verificando ${FULL_IMAGE_NAME}:${IMAGE_TAG}..."
                        gcloud artifacts docker images describe ${FULL_IMAGE_NAME}:${IMAGE_TAG} || {
                            echo "❌ ERROR: Imagen no encontrada"
                            gcloud artifacts docker images list ${GCR_REGISTRY}/${IMAGE_NAME} --include-tags --limit=10 --sort-by=~UPDATE_TIME
                            exit 1
                        }
                        echo "✅ Imagen verificada."
                    """
                }
            }
        }
        
        stage('Deploy to Staging') {
            steps {
                script {
                    sh """
                        echo "🚀 Desplegando a ${K8S_NAMESPACE}..."
                        kubectl create namespace \${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        echo "📋 Aplicando manifiestos desde manifests-gcp/user-service/..."
                        kubectl apply -f manifests-gcp/user-service/ -n \${K8S_NAMESPACE}
                        
                        echo "🔄 Actualizando la imagen del deployment ${K8S_DEPLOYMENT_NAME}..."
                        kubectl set image deployment/\${K8S_DEPLOYMENT_NAME} \
                            \${K8S_CONTAINER_NAME}=\${FULL_IMAGE_NAME}:\${IMAGE_TAG} \
                            -n \${K8S_NAMESPACE} --record
                        
                        echo "⏳ Esperando rollout..."
                        kubectl rollout status deployment/\${K8S_DEPLOYMENT_NAME} -n \${K8S_NAMESPACE} --timeout=300s
                        echo "✅ Despliegue completado."
                    """
                }
            }
        }

        stage('Health Check & Smoke Tests') {
            steps {
                script {
                    sh """
                        echo "🏥 Ejecutando health checks internos..."
                        kubectl wait --for=condition=ready pod -l app=\${K8S_DEPLOYMENT_NAME} -n \${K8S_NAMESPACE} --timeout=300s
                        
                        POD_NAME=\$(kubectl get pods -n \${K8S_NAMESPACE} -l app=\${K8S_DEPLOYMENT_NAME} -o jsonpath='{.items[0].metadata.name}')
                        
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
                            STAGING_GATEWAY_IP=\$(kubectl get svc \${API_GATEWAY_SERVICE_NAME} -n \${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                            if [ -n "\$STAGING_GATEWAY_IP" ]; then
                                echo "✅ IP del Gateway de Staging: \$STAGING_GATEWAY_IP"
                                break
                            fi
                            echo "Esperando IP del Gateway (LoadBalancer)... intento \$i/30"
                            sleep 10
                        done
                        if [ -z "\$STAGING_GATEWAY_IP" ]; then
                            echo "❌ Error: No se pudo obtener la IP del Gateway."
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
                    echo "📦 Imagen desplegada: \${FULL_IMAGE_NAME}:\${env.IMAGE_TAG}"
                    gcloud auth revoke --all || true
                """
            }
        }
        failure {
            script {
                sh """
                    echo "❌ 💥 STAGING DEPLOY FALLÓ"
                    echo "🔍 Realizando rollback..."
                    kubectl rollout undo deployment/\${K8S_DEPLOYMENT_NAME} -n \${K8S_NAMESPACE} || echo "No hay rollback disponible."
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