@Library('shared-library') _

pipeline {
    agent any
    
    environment {
        // GCP & GKE
        GCP_PROJECT = 'ecommerce-backend-1760307199'
        GCR_REGISTRY = 'us-central1-docker.pkg.dev'
        GKE_CLUSTER = 'ecommerce-devops-cluster'
        GKE_REGION = 'us-central1'
        K8S_NAMESPACE = 'staging'
        
        // SERVICE_PLACEHOLDER
        SERVICE_NAME = 'order-service'
        SERVICE_PORT = '8300'
        IMAGE_NAME = "${GCR_REGISTRY}/${GCP_PROJECT}/ecommerce-microservices/${SERVICE_NAME}"
        IMAGE_TAG = 'latest-dev'
        
        // Imágenes custom de tests
        TEST_MAVEN_IMAGE = "${GCR_REGISTRY}/${GCP_PROJECT}/ecommerce-microservices/test-runner-maven:latest"
        TEST_LOCUST_IMAGE = "${GCR_REGISTRY}/${GCP_PROJECT}/ecommerce-microservices/test-runner-locust:latest"
    }
    
    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
                script {
                    echo "📦 Iniciando despliegue de ${SERVICE_NAME} a STAGING"
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
                        echo "🚀 Desplegando ${SERVICE_NAME} a staging usando Helm..."
                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        echo "📋 Aplicando/Actualizando Chart de Helm: ${SERVICE_NAME}"
                        helm upgrade --install ${SERVICE_NAME} manifests-gcp/${SERVICE_NAME}/ \
                            --namespace ${K8S_NAMESPACE} \
                            --set image.tag=${IMAGE_TAG} \
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
                        kubectl wait --for=condition=ready pod -l app=${SERVICE_NAME} -n ${K8S_NAMESPACE} --timeout=300s
                        
                        POD_NAME=$(kubectl get pods -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME} -o jsonpath="{.items[0].metadata.name}")
                        echo "🎯 Testing pod: $POD_NAME en puerto ${SERVICE_PORT}"
                        
                        kubectl exec $POD_NAME -n ${K8S_NAMESPACE} -- curl -f http://localhost:${SERVICE_PORT}/${SERVICE_NAME}/actuator/health
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
                        
                        echo "🔍 Verificando conectividad al Gateway"
                        kubectl run test-gateway-${BUILD_NUMBER} \
                            --image=curlimages/curl:latest \
                            -n ${K8S_NAMESPACE} \
                            --rm -i --restart=Never --timeout=60s \
                            -- curl -f --retry 5 --retry-delay 5 --retry-connrefused \
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
                            namespace "${K8S_NAMESPACE}"
                            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: ${TEST_MAVEN_IMAGE}
    command: ['sleep']
    args: ['infinity']
    env:
    - name: SERVICE_NAME
      value: "${SERVICE_NAME}"
    - name: SERVICE_PORT
      value: "${SERVICE_PORT}"
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
                                    set +e
                                    GATEWAY_IP=$(cat ${WORKSPACE}/gateway-ip.txt 2>/dev/null || echo "10.22.10.27")
                                    
                                    echo "🧪 =============================================="
                                    echo "🧪 E2E Tests: ${SERVICE_NAME} @ http://${GATEWAY_IP}"
                                    echo "🧪 =============================================="
                                    
                                    if [ -d "${WORKSPACE}/tests/e2e" ]; then
                                        cp -r ${WORKSPACE}/tests/e2e/* /workspace/
                                        cd /workspace
                                        
                                        mvn clean test \
                                            -Dapi.gateway.url=http://${GATEWAY_IP} \
                                            -Dservice.name=${SERVICE_NAME} \
                                            -Dservice.port=${SERVICE_PORT} \
                                            -Dmaven.test.failure.ignore=true \
                                            -Dsurefire.reports.directory=/workspace/target/surefire-reports
                                        
                                        mkdir -p ${WORKSPACE}/e2e-results
                                        cp -r /workspace/target/surefire-reports/* ${WORKSPACE}/e2e-results/ 2>/dev/null || true
                                    else
                                        echo "⚠️ No E2E tests found"
                                        mkdir -p ${WORKSPACE}/e2e-results
                                    fi
                                    exit 0
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
                            namespace "${K8S_NAMESPACE}"
                            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: locust
    image: ${TEST_LOCUST_IMAGE}
    command: ['sleep']
    args: ['infinity']
    env:
    - name: SERVICE_NAME
      value: "${SERVICE_NAME}"
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
                                    GATEWAY_IP=$(cat ${WORKSPACE}/gateway-ip.txt 2>/dev/null || echo "10.22.10.27")
                                    
                                    echo "🚀 =============================================="
                                    echo "🚀 Performance: ${SERVICE_NAME} @ http://${GATEWAY_IP}"
                                    echo "🚀 =============================================="
                                    
                                    if [ -d "${WORKSPACE}/tests/performance" ]; then
                                        cp -r ${WORKSPACE}/tests/performance/* /workspace/
                                        cd /workspace
                                        
                                        locust -f ecommerce_load_test.py \
                                            --host=http://${GATEWAY_IP} \
                                            --users 50 \
                                            --spawn-rate 5 \
                                            --run-time 3m \
                                            --headless \
                                            --csv=/workspace/load_test_${SERVICE_NAME} \
                                            --html=/workspace/load_test_${SERVICE_NAME}_report.html \
                                            --loglevel INFO \
                                            --exit-code-on-error 0 || true
                                        
                                        mkdir -p ${WORKSPACE}/performance-results
                                        cp /workspace/load_test_${SERVICE_NAME}* ${WORKSPACE}/performance-results/ 2>/dev/null || true
                                    else
                                        echo "⚠️ No performance tests found"
                                        mkdir -p ${WORKSPACE}/performance-results
                                    fi
                                    exit 0
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            publishHTML([
                                reportDir: 'performance-results',
                                reportFiles: "load_test_${SERVICE_NAME}_report.html",
                                reportName: 'Locust Performance Report',
                                keepAll: true,
                                alwaysLinkToLastBuild: true,
                                allowMissing: true
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
                        echo "❌ ${SERVICE_NAME} DEPLOY FALLÓ"
                        gcloud auth activate-service-account --key-file=${GCP_CREDENTIALS}
                        gcloud config set project ${GCP_PROJECT}
                        gcloud container clusters get-credentials ${GKE_CLUSTER} --region=${GKE_REGION} --project=${GCP_PROJECT}
                        
                        if [ "${STAGE_NAME}" = "Deploy to Staging (Helm)" ]; then
                            helm rollback ${SERVICE_NAME} -n ${K8S_NAMESPACE} || echo "⚠️ No hay versión previa"
                        fi
                        
                        kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20 || true
                        gcloud auth revoke --all || true
                    '''
                }
            }
        }
        success {
            echo "✅ 🎉 ${SERVICE_NAME} DEPLOYMENT EXITOSO!"
        }
    }
}
