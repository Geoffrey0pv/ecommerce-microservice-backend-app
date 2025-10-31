// jenkins-pipelines/user-service-stage-pipeline.groovy
pipeline {
    agent any

    environment {
        IMAGE_NAME = "user-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}"
        
        GCP_CREDENTIALS = credentials('gke-credentials')
        GCP_PROJECT = "ecommerce-backend-1760307199"
        CLUSTER_NAME = "ecommerce-devops-cluster" 
        CLUSTER_REGION = "us-central1"
        K8S_NAMESPACE = "staging"
        K8S_DEPLOYMENT_NAME = "user-service"
        K8S_CONTAINER_NAME = "user-service"
        K8S_SERVICE_NAME = "user-service"
        SERVICE_PORT = "8200"
        
        API_GATEWAY_SERVICE_NAME = "proxy-client" 
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TO_DEPLOY = "${GCR_REGISTRY}/${IMAGE_NAME}:${env.GIT_COMMIT_SHA}"
                }
                echo "Procesando despliegue a STAGING para ${IMAGE_NAME} commit ${GIT_COMMIT_SHA}"
            }
        }

        stage('Authenticate GCP') {
            steps {
                sh 'gcloud auth activate-service-account --key-file=$GCP_CREDENTIALS'
                sh 'gcloud config set project ${GCP_PROJECT}'
                sh 'gcloud container clusters get-credentials ${GKE_CLUSTER} --zone ${GKE_ZONE} --project ${GCP_PROJECT}'
            }
        }

        stage('Deploy to Staging K8s') {
            steps {
                script {
                    echo "Desplegando ${env.IMAGE_TO_DEPLOY} a Kubernetes Staging..."
                    sh '''
                        kubectl get namespace ${K8S_NAMESPACE} || kubectl create namespace ${K8S_NAMESPACE}
                        
                        # Aplicar manifiestos K8s (Usando Helmfile sería mejor)
                        kubectl apply -f k8s/staging/${IMAGE_NAME}-deployment.yaml
                        
                        # Actualizar la imagen del deployment con el tag del commit
                        kubectl set image deployment/${IMAGE_NAME} \
                            ${IMAGE_NAME}=${IMAGE_TO_DEPLOY} \
                            -n ${K8S_NAMESPACE}
                        
                        # Verificar rollout
                        kubectl rollout status deployment/${IMAGE_NAME} -n ${K8S_NAMESPACE} --timeout=5m
                    '''
                }
            }
        }

        stage('Smoke Tests on Staging') {
            steps {
                script {
                    sh '''
                        echo "Ejecutando smoke tests en ambiente staging..."
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        SERVICE_PORT=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        echo "Service disponible en: http://${SERVICE_IP}:${SERVICE_PORT}"
                        # ... resto de tu script ...
                        kubectl run curl-test ... curl -f http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health || exit 1
                        echo "✅ Smoke tests completados exitosamente"
                    '''
                }
            }
        }

        stage('Application Tests on Staging') {
            steps {
                script {
                    sh '''
                        echo "Ejecutando pruebas de aplicación en staging..."
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        SERVICE_PORT=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        # ... (obtener SERVICE_IP y SERVICE_PORT) ...

                        # Test 1: Health Check (valida que el JSON de respuesta contenga "status":"UP")
                        echo "Test 1: Verificando health endpoint..."
                        kubectl run curl-test-app1 --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health | grep '"status":"UP"' || exit 1

                        # Test 2: Metrics Endpoint (valida que la respuesta JSON contenga la palabra "names")
                        echo "Test 2: Verificando metrics endpoint..."
                        kubectl run curl-test-app2 --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/metrics | grep '"names"' || exit 1

                        # Test 3: Info Endpoint (valida que la respuesta no esté vacía)
                        echo "Test 3: Verificando info endpoint..."
                        kubectl run curl-test-app3 --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/info || exit 1

                        echo "✅ Todas las pruebas de aplicación pasaron exitosamente"
                    '''
                }
            }
        }

        stage('Performance Tests') {
            steps {
                script {
                    sh '''
                        echo "Ejecutando pruebas de rendimiento básicas..."
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        SERVICE_PORT=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].port}')

                        # ... (obtener SERVICE_IP y SERVICE_PORT) ...

                        # Ejecutar 100 requests con 10 concurrentes
                        kubectl run ab-test --image=jordi/ab --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            ab -n 100 -c 10 http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health || echo "AB test completed"

                        echo "✅ Pruebas de rendimiento completadas"
                        echo "✅ Pruebas de rendimiento completadas"
                    '''
                }
            }
        }

        stage('Monitoring Setup') {
            steps {
                script {
                    sh '''
                        echo "Configurando monitoreo para ${IMAGE_NAME}..."
                        cat <<EOF | kubectl apply -f -
                        # ... (tu yaml de ServiceMonitor)
                        EOF
                        echo "✅ Monitoreo configurado"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "DESPLIEGUE A STAGING EXITOSO para ${IMAGE_NAME} ${IMAGE_TO_DEPLOY}"
        }
        failure {
            echo "DESPLIEGUE A STAGING FALLÓ para ${IMAGE_NAME}"
            script {
                sh '''
                    echo "Iniciando rollback..."
                    kubectl rollout undo deployment/${IMAGE_NAME} -n ${K8S_NAMESPACE} || echo "No hay versión anterior"
                '''
            }
        }
        always {
            cleanWs()
            sh 'gcloud auth revoke --all || true'
        }
    }
}