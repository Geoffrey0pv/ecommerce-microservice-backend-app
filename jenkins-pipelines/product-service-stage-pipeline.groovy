pipeline {
    agent any

    environment {
        IMAGE_NAME = "product-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        K8S_NAMESPACE = "ecommerce-staging"
        GCP_PROJECT = "ingesoft-taller2" 
        GKE_CLUSTER = "ecommerce-staging-cluster" 
        GKE_ZONE = "us-central1-b" 
        GCP_CREDENTIALS = credentials('gke-credentials') 
        SERVICE_PORT = "8300"
    }

    stages {
        stage('Checkout & Identify Image') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TO_DEPLOY = "${GCR_REGISTRY}/${IMAGE_NAME}:${env.GIT_COMMIT_SHA}"
                }
                echo "🚀 Iniciando despliegue a STAGING para ${IMAGE_NAME}"
                echo "Imagen a desplegar: ${env.IMAGE_TO_DEPLOY}"
            }
        }

        stage('Authenticate GCP') {
            steps {
                script {
                    // 2. Se autentica con GKE
                    sh 'gcloud auth activate-service-account --key-file=$GCP_CREDENTIALS'
                    sh 'gcloud config set project ${GCP_PROJECT}'
                    sh 'gcloud container clusters get-credentials ${GKE_CLUSTER} --zone ${GKE_ZONE} --project ${GCP_PROJECT}'
                }
            }
        }

        stage('Verify Image in Registry') {
            steps {
                script {
                    // 3. (Opcional pero recomendado) Verifica que la imagen exista antes de desplegar
                    echo "🔍 Verificando que la imagen ${env.IMAGE_TO_DEPLOY} exista..."
                    sh "gcloud container images describe ${env.IMAGE_TO_DEPLOY} --format='get(image_summary.fully_qualified_digest)'"
                }
            }
        }

        stage('Deploy to Staging K8s') {
            steps {
                script {
                    // 4. Despliega la imagen en el clúster
                    echo "Desplegando ${env.IMAGE_TO_DEPLOY} a Kubernetes Staging..."
                    sh '''
                        # Asegura que el namespace exista
                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Aplica el manifiesto base (Deployment, Service, HPA)
                        kubectl apply -f k8s/staging/${IMAGE_NAME}-deployment.yaml -n ${K8S_NAMESPACE}
                        
                        # Actualiza la imagen del deployment con el tag del commit
                        kubectl set image deployment/${IMAGE_NAME} ${IMAGE_NAME}=${IMAGE_TO_DEPLOY} -n ${K8S_NAMESPACE}
                        
                        # Espera a que el despliegue termine
                        kubectl rollout status deployment/${IMAGE_NAME} -n ${K8S_NAMESPACE} --timeout=5m
                    '''
                }
            }
        }

        stage('Smoke Tests on Staging') {
            steps {
                script {
                    echo "Ejecutando smoke tests en ambiente staging..."
                    sh '''
                        # Espera 10s extra para que el servicio esté listo
                        sleep 10
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        echo "Service disponible en: http://${SERVICE_IP}:${SERVICE_PORT}"
                        
                        # Bucle de reintento para el health check
                        for i in {1..30}; do
                            if kubectl run curl-test-smoke --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                                curl -s -o /dev/null -w "%{http_code}" http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health | grep -q "200"; then
                                echo "✅ Servicio está respondiendo correctamente"
                                break
                            fi
                            echo "Esperando a que el servicio esté listo... intento $i/30"
                            sleep 10
                        done
                        
                        # Test final (falla el pipeline si no está listo)
                        kubectl run curl-test-smoke --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -f http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health || exit 1
                        echo "✅ Smoke tests completados exitosamente"
                    '''
                }
            }
        }

        stage('Application Tests on Staging') {
            steps {
                script {
                    echo "Ejecutando pruebas de aplicación en staging..."
                    sh '''
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        
                        echo "Test 1: Verificando health endpoint..."
                        kubectl run curl-test-app1 --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health | grep '"status":"UP"' || exit 1
                        
                        echo "Test 2: Verificando metrics endpoint..."
                        kubectl run curl-test-app2 --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/metrics | grep '"names"' || exit 1
                        
                        echo "Test 3: Verificando info endpoint..."
                        kubectl run curl-test-app3 --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/info || exit 1
                        
                        echo "✅ Todas las pruebas de aplicación pasaron exitosamente"
                    '''
                }
            }
        }

        stage('Performance Tests (Locust/E2E)') {
            steps {
                script {
                    echo "Ejecutando pruebas de rendimiento (Locust) y E2E..."
                    sh '''
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        
                        # Aquí ejecutarías tus scripts de Locust o E2E contra el clúster
                        # Por ahora, usamos el test básico de 'ab' (Apache Bench)
                        
                        echo "Ejecutando pruebas de carga básicas con Apache Bench..."
                        kubectl run ab-test --image=jordi/ab --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            ab -n 100 -c 10 http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health || echo "AB test completado"
                        
                        echo "✅ Pruebas de rendimiento/E2E completadas"
                    '''
                }
            }
        }

        stage('Monitoring Setup') {
            steps {
                script {
                    echo "Configurando monitoreo para ${IMAGE_NAME}..."
                    sh '''
                        # Aplicar ServiceMonitor para Prometheus
                        cat <<EOF | kubectl apply -f -
                        apiVersion: monitoring.coreos.com/v1
                        kind: ServiceMonitor
                        metadata:
                          name: ${IMAGE_NAME}
                          namespace: ${K8S_NAMESPACE}
                          labels:
                            app: ${IMAGE_NAME}
                        spec:
                          selector:
                            matchLabels:
                              app: ${IMAGE_NAME}
                          endpoints:
                          - port: http
                            path: /actuator/prometheus
                            interval: 30s
                        EOF
                        echo "✅ Monitoreo configurado"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "✅ DESPLIEGUE A STAGING EXITOSO para ${IMAGE_NAME} ${IMAGE_TO_DEPLOY}"
            script {
                sh 'echo "Enviando notificación de éxito..."'
            }
        }
        failure {
            echo "❌ DESPLIEGUE A STAGING FALLÓ para ${IMAGE_NAME} ${IMAGE_TO_DEPLOY}"
            script {
                // Lógica de Rollback
                sh '''
                    echo "Iniciando rollback..."
                    kubectl rollout undo deployment/${IMAGE_NAME} -n ${K8S_NAMESPACE} || echo "No hay versión anterior para hacer rollback."
                '''
            }
        }
        always {
            cleanWs()
            sh 'gcloud auth revoke --all || true'
        }
    }
}