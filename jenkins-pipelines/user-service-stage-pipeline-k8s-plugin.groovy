pipeline {
    agent any
    
    environment {
        GCP_PROJECT = 'ecommerce-backend-1760307199'
        GCR_REGISTRY = 'us-central1-docker.pkg.dev'
        GKE_CLUSTER = 'ecommerce-devops-cluster'
        GKE_REGION = 'us-central1'
        K8S_NAMESPACE = 'staging'
        
        IMAGE_NAME = "${GCR_REGISTRY}/${GCP_PROJECT}/ecommerce-microservices/user-service"
        IMAGE_TAG = 'latest-dev'
        
        TEST_MAVEN_IMAGE = "${GCR_REGISTRY}/${GCP_PROJECT}/ecommerce-microservices/test-runner-maven:latest"
        TEST_LOCUST_IMAGE = "${GCR_REGISTRY}/${GCP_PROJECT}/ecommerce-microservices/test-runner-locust:latest"
    }
    
    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
                script {
                    echo "📦 Iniciando despliegue a STAGING"
                    echo "📦 Imagen a desplegar: ${IMAGE_NAME}:${IMAGE_TAG}"
                }
            }
        }
        
        stage('Authenticate GCP & Kubernetes') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_CREDENTIALS')]) {
                        sh '''
                            echo "🔐 Autenticando con GCP..."
                            gcloud auth activate-service-account --key-file=${GCP_CREDENTIALS}
                            gcloud config set project ${GCP_PROJECT}
                            gcloud auth configure-docker ${GCR_REGISTRY} --quiet
                            
                            echo "☸️ Obteniendo credenciales de GKE..."
                            gcloud container clusters get-credentials ${GKE_CLUSTER} \
                                --region=${GKE_REGION} \
                                --project=${GCP_PROJECT}
                        '''
                    }
                }
            }
        }
        
        stage('Verify Image Exists in GCR') {
            steps {
                script {
                    sh """
                        echo "🔍 Verificando ${IMAGE_NAME}:${IMAGE_TAG}..."
                        gcloud artifacts docker images describe ${IMAGE_NAME}:${IMAGE_TAG}
                        echo "✅ Imagen verificada."
                    """
                }
            }
        }
        
        stage('Deploy to Staging (Helm)') {
            steps {
                script {
                    sh '''
                        echo "🚀 Desplegando a staging usando Helm..."
                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        echo "📋 Aplicando/Actualizando Chart de Helm: user-service"
                        helm upgrade --install user-service manifests-gcp/user-service/ \\
                            --namespace ${K8S_NAMESPACE} \\
                            --set image.tag=${IMAGE_TAG} \\
                            --set env[4].value=false \\
                            --set env[5].value=false \\
                            --wait --timeout=5m
                        
                        echo "✅ Despliegue completado."
                    '''
                }
            }
        }
        
        stage('Health Check & Smoke Tests') {
            steps {
                script {
                    sh '''
                        echo "🏥 Ejecutando health checks..."
                        kubectl wait --for=condition=ready pod -l app=user-service -n ${K8S_NAMESPACE} --timeout=300s
                        
                        POD_NAME=$(kubectl get pods -n ${K8S_NAMESPACE} -l app=user-service -o jsonpath="{.items[0].metadata.name}")
                        echo "🎯 Testing pod: $POD_NAME en puerto 8700"
                        
                        kubectl exec $POD_NAME -n ${K8S_NAMESPACE} -- curl -f http://localhost:8700/user-service/actuator/health
                        echo "✅ Health check passed!"
                    '''
                }
            }
        }
        
        stage('Verify Gateway Availability') {
            steps {
                script {
                    sh '''
                        echo "🌐 Verificando disponibilidad del API Gateway (proxy-client)..."
                        kubectl wait --for=condition=ready pod -l app=proxy-client -n ${K8S_NAMESPACE} --timeout=300s
                        
                        GATEWAY_IP=$(kubectl get svc proxy-client -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        
                        if [ -z "$GATEWAY_IP" ]; then
                            echo "❌ No se pudo obtener la IP del Gateway"
                            exit 1
                        fi
                        
                        echo "✅ Gateway ClusterIP: $GATEWAY_IP"
                        echo "$GATEWAY_IP" > gateway-ip.txt
                        
                        echo "🔍 Verificando conectividad al Gateway en http://${GATEWAY_IP}:80/app/actuator/health"
                        kubectl run test-gateway-${BUILD_NUMBER} \\
                            --image=curlimages/curl:latest \\
                            -n ${K8S_NAMESPACE} \\
                            --rm -i --restart=Never --timeout=60s \\
                            -- curl -f --retry 5 --retry-delay 5 --retry-connrefused \\
                            http://${GATEWAY_IP}:80/app/actuator/health
                        
                        echo "✅ Gateway respondiendo correctamente"
                    '''
                }
            }
        }
        stage('Run Tests in Parallel') {
            parallel {
                stage('E2E Tests (Maven)') {
                    agent {
                        kubernetes {
                            yaml """
apiVersion: v1
kind: Pod
metadata:
  namespace: ${K8S_NAMESPACE}
spec:
  containers:
  - name: maven
    image: ${TEST_MAVEN_IMAGE}
    command: ['sleep']
    args: ['infinity']
    env:
    - name: GATEWAY_IP
      value: "http://GATEWAY_IP_PLACEHOLDER"
    volumeMounts:
    - name: maven-cache
      mountPath: /root/.m2/repository
    - name: workspace
      mountPath: /workspace
  volumes:
  - name: maven-cache
    persistentVolumeClaim:
      claimName: maven-cache-pvc
  - name: workspace
    emptyDir: {}
"""
                        }
                    }
                    steps {
                        container('maven') {
                            script {
                                sh '''
                                    set +e  # No fallar el pipeline si tests fallan
                                    
                                    # Leer Gateway IP
                                    GATEWAY_IP=$(cat gateway-ip.txt || echo "10.22.10.27")
                                    
                                    echo "🧪 =============================================="
                                    echo "🧪 Ejecutando E2E Tests contra: http://${GATEWAY_IP}"
                                    echo "🧪 =============================================="
                                    
                                    # Copiar código de tests
                                    cp -r ${WORKSPACE}/tests/e2e/* /workspace/
                                    cd /workspace
                                    
                                    # Ejecutar tests
                                    mvn clean test \\
                                        -Dapi.gateway.url=http://${GATEWAY_IP} \\
                                        -Dmaven.test.failure.ignore=true \\
                                        -Dsurefire.reports.directory=/workspace/target/surefire-reports
                                    
                                    TEST_EXIT_CODE=$?
                                    
                                    echo "📊 Tests E2E completados con código: $TEST_EXIT_CODE"
                                    ls -la /workspace/target/surefire-reports/ || true
                                    
                                    # Copiar reportes de vuelta al workspace de Jenkins
                                    mkdir -p ${WORKSPACE}/e2e-results
                                    cp -r /workspace/target/surefire-reports/* ${WORKSPACE}/e2e-results/ || true
                                    
                                    exit 0  # No fallar el stage
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            junit testResults: 'e2e-results/*.xml', allowEmptyResults: true
                            archiveArtifacts artifacts: 'e2e-results/**/*', allowEmptyArchive: true
                        }
                    }
                }
                
                stage('Performance Tests (Locust)') {
                    agent {
                        kubernetes {
                            yaml """
apiVersion: v1
kind: Pod
metadata:
  namespace: ${K8S_NAMESPACE}
spec:
  containers:
  - name: locust
    image: ${TEST_LOCUST_IMAGE}
    command: ['sleep']
    args: ['infinity']
    env:
    - name: TARGET_HOST
      value: "http://GATEWAY_IP_PLACEHOLDER"
    volumeMounts:
    - name: workspace
      mountPath: /workspace
  volumes:
  - name: workspace
    emptyDir: {}
"""
                        }
                    }
                    steps {
                        container('locust') {
                            script {
                                sh '''
                                    set +e
                                    
                                    # Leer Gateway IP
                                    GATEWAY_IP=$(cat gateway-ip.txt || echo "10.22.10.27")
                                    
                                    echo "🚀 =============================================="
                                    echo "🚀 Ejecutando Performance Tests contra: http://${GATEWAY_IP}"
                                    echo "🚀 =============================================="
                                    
                                    # Copiar scripts de Locust
                                    cp -r ${WORKSPACE}/tests/performance/* /workspace/
                                    cd /workspace
                                    
                                    # Ejecutar Locust
                                    locust -f ecommerce_load_test.py \\
                                        --host=http://${GATEWAY_IP} \\
                                        --users 100 \\
                                        --spawn-rate 10 \\
                                        --run-time 5m \\
                                        --headless \\
                                        --csv=/workspace/load_test \\
                                        --html=/workspace/load_test_report.html \\
                                        --loglevel INFO \\
                                        --exit-code-on-error 0 || echo "Load test completado"
                                    
                                    echo "📊 Performance tests completados"
                                    ls -lah /workspace/ || true
                                    
                                    # Copiar reportes
                                    mkdir -p ${WORKSPACE}/performance-results
                                    cp /workspace/load_test* ${WORKSPACE}/performance-results/ || true
                                    
                                    exit 0
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            publishHTML([
                                reportDir: 'performance-results',
                                reportFiles: 'load_test_report.html',
                                reportName: 'Locust Performance Report',
                                keepAll: true,
                                alwaysLinkToLastBuild: true
                            ])
                            archiveArtifacts artifacts: 'performance-results/**/*', allowEmptyArchive: true
                        }
                    }
                }
            }
        } 
    }
    
    post {
        always {
            script {
                withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_CREDENTIALS')]) {
                    sh 'gcloud auth revoke --all || true'
                }
            }
            cleanWs()
        }
        failure {
            script {
                withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_CREDENTIALS')]) {
                    sh '''
                        echo "❌ 💥 STAGING DEPLOY FALLÓ"
                        echo "🔍 Fallo detectado en stage: ${STAGE_NAME}"
                        
                        # Re-autenticar para operaciones de rollback
                        echo "🔐 Re-autenticando para operaciones de rollback..."
                        gcloud auth activate-service-account --key-file=${GCP_CREDENTIALS}
                        gcloud config set project ${GCP_PROJECT}
                        gcloud container clusters get-credentials ${GKE_CLUSTER} \\
                            --region=${GKE_REGION} \\
                            --project=${GCP_PROJECT}
                        
                        if [ "${STAGE_NAME}" = "Deploy to Staging (Helm)" ]; then
                            echo "🔄 Haciendo rollback de Helm..."
                            helm rollback user-service -n ${K8S_NAMESPACE} || echo "⚠️ No hay versión previa"
                        else
                            echo "⚠️ Fallo en stage '${STAGE_NAME}'. El despliegue NO será revertido."
                        fi
                        
                        echo "📋 Información de debug:"
                        kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20 || true
                        
                        gcloud auth revoke --all || true
                    '''
                }
            }
        }
        success {
            echo "✅ 🎉 DEPLOYMENT A STAGING EXITOSO!"
        }
    }
}
