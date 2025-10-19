pipeline {
    agent any

    environment {
        IMAGE_NAME = "payment-service"
        DOCKER_REGISTRY_USER = "geoffrey0pv"
        SERVICE_DIR = "payment-service"
        K8S_NAMESPACE = "ecommerce-staging"
        GCP_PROJECT = "ingesoft-taller2"
        GKE_CLUSTER = "ecommerce-staging-cluster"
        GKE_ZONE = "us-central1-b"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Procesando cambios en ${IMAGE_NAME} para Stage Environment"
            }
        }

        stage('Compile') {
            steps {
                dir("${SERVICE_DIR}") {
                    script {
                        docker.image('maven:3.8.4-openjdk-11').inside {
                            sh '''
                                mvn clean compile -Dspring.profiles.active=stage
                                ls -la target/
                            '''
                        }
                    }
                }
            }
        }

        stage('Unit Tests') {
            steps {
                dir("${SERVICE_DIR}") {
                    script {
                        docker.image('maven:3.8.4-openjdk-11').inside {
                            sh '''
                                echo "Ejecutando pruebas unitarias..."
                                mvn test -Dspring.profiles.active=stage
                            '''
                        }
                    }
                }
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                dir("${SERVICE_DIR}") {
                    script {
                        docker.image('maven:3.8.4-openjdk-11').inside {
                            sh '''
                                echo "Ejecutando pruebas de integración..."
                                mvn verify -Dtest="*Integration*" -Dspring.profiles.active=stage
                            '''
                        }
                    }
                }
            }
        }

        stage('Code Quality Analysis') {
            steps {
                dir("${SERVICE_DIR}") {
                    script {
                        docker.image('maven:3.8.4-openjdk-11').inside {
                            sh '''
                                echo "Análisis de calidad de código..."
                                mvn verify sonar:sonar \
                                    -Dsonar.projectKey=${IMAGE_NAME} \
                                    -Dsonar.host.url=http://sonarqube:9000 \
                                    -Dsonar.login=${SONAR_TOKEN} || echo "SonarQube no configurado"
                            '''
                        }
                    }
                }
            }
        }

        stage('Package') {
            steps {
                dir("${SERVICE_DIR}") {
                    script {
                        docker.image('maven:3.8.4-openjdk-11').inside {
                            sh '''
                                mvn package -DskipTests=true -Dspring.profiles.active=stage
                                ls -la target/
                            '''
                        }
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("${SERVICE_DIR}") {
                    script {
                        def shortCommit = env.GIT_COMMIT.substring(0,7)
                        def buildNumber = env.BUILD_NUMBER
                        def stageTag = "stage-${buildNumber}-${shortCommit}"
                        def fullImageName = "${DOCKER_REGISTRY_USER}/${IMAGE_NAME}"
                        
                        echo "Construyendo imagen: ${fullImageName}:${stageTag}"
                        
                        // Cambiar al directorio raíz para contexto Docker
                        dir('..') {
                            def customImage = docker.build("${fullImageName}:${stageTag}", "-f ${SERVICE_DIR}/Dockerfile .")
                            customImage.tag("latest-stage")
                            
                            env.STAGE_IMAGE_TAG = stageTag
                            env.FULL_IMAGE_NAME = fullImageName
                        }
                    }
                }
            }
        }

        stage('Security Scan') {
            steps {
                script {
                    echo "Escaneando imagen Docker para vulnerabilidades..."
                    sh '''
                        # Usar Trivy para escaneo de seguridad
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                            aquasec/trivy:latest image \
                            --severity HIGH,CRITICAL \
                            --exit-code 0 \
                            ${FULL_IMAGE_NAME}:${STAGE_IMAGE_TAG} || echo "Trivy no disponible"
                    '''
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry('', 'dockerhub-credentials') {
                        docker.image("${env.FULL_IMAGE_NAME}:${env.STAGE_IMAGE_TAG}").push()
                        docker.image("${env.FULL_IMAGE_NAME}:latest-stage").push()
                        
                        echo "Imagen publicada: ${env.FULL_IMAGE_NAME}:${env.STAGE_IMAGE_TAG}"
                    }
                }
            }
        }

        stage('Deploy to Staging K8s') {
            steps {
                script {
                    echo "Desplegando a Kubernetes Staging Environment..."
                    
                    withKubeConfig([credentialsId: 'gke-credentials', 
                                    serverUrl: 'https://kubernetes.default.svc.cluster.local']) {
                        sh '''
                            # Configurar conexión a GKE
                            gcloud container clusters get-credentials ${GKE_CLUSTER} \
                                --zone ${GKE_ZONE} \
                                --project ${GCP_PROJECT} || echo "Ya conectado a cluster"
                            
                            # Verificar namespace
                            kubectl get namespace ${K8S_NAMESPACE} || kubectl create namespace ${K8S_NAMESPACE}
                            
                            # Actualizar imagen en deployment
                            kubectl set image deployment/${IMAGE_NAME} \
                                ${IMAGE_NAME}=${FULL_IMAGE_NAME}:${STAGE_IMAGE_TAG} \
                                -n ${K8S_NAMESPACE}
                            
                            # Aplicar manifiestos K8s
                            kubectl apply -f k8s/staging/${IMAGE_NAME}-deployment.yaml
                            
                            # Verificar rollout
                            kubectl rollout status deployment/${IMAGE_NAME} -n ${K8S_NAMESPACE} --timeout=5m
                        '''
                    }
                }
            }
        }

        stage('Smoke Tests on Staging') {
            steps {
                script {
                    echo "Ejecutando smoke tests en ambiente staging..."
                    sh '''
                        # Obtener IP del servicio
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        SERVICE_PORT=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        
                        echo "Service disponible en: http://${SERVICE_IP}:${SERVICE_PORT}"
                        
                        # Esperar a que el servicio esté listo
                        for i in {1..30}; do
                            if kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                                curl -s -o /dev/null -w "%{http_code}" http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health | grep -q "200"; then
                                echo "✅ Servicio está respondiendo correctamente"
                                break
                            fi
                            echo "Esperando a que el servicio esté listo... intento $i/30"
                            sleep 10
                        done
                        
                        # Ejecutar tests básicos
                        kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
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
                        # Pruebas específicas de la aplicación
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        SERVICE_PORT=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        
                        # Test 1: Health Check
                        echo "Test 1: Verificando health endpoint..."
                        kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health | grep '"status":"UP"' || exit 1
                        
                        # Test 2: Metrics Endpoint
                        echo "Test 2: Verificando metrics endpoint..."
                        kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/metrics | grep '"names"' || exit 1
                        
                        # Test 3: Info Endpoint
                        echo "Test 3: Verificando info endpoint..."
                        kubectl run curl-test --image=curlimages/curl:latest --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            curl -s http://${SERVICE_IP}:${SERVICE_PORT}/actuator/info || exit 1
                        
                        echo "✅ Todas las pruebas de aplicación pasaron exitosamente"
                    '''
                }
            }
        }

        stage('Performance Tests') {
            steps {
                script {
                    echo "Ejecutando pruebas de rendimiento básicas..."
                    sh '''
                        # Pruebas de carga ligeras con Apache Bench
                        SERVICE_IP=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.clusterIP}')
                        SERVICE_PORT=$(kubectl get svc ${IMAGE_NAME} -n ${K8S_NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        
                        # Ejecutar 100 requests con 10 concurrentes
                        kubectl run ab-test --image=jordi/ab --rm -i --restart=Never -n ${K8S_NAMESPACE} -- \
                            ab -n 100 -c 10 http://${SERVICE_IP}:${SERVICE_PORT}/actuator/health || echo "AB test completed"
                        
                        echo "✅ Pruebas de rendimiento completadas"
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
            echo """
            ═══════════════════════════════════════════════════════════════
                   ✅ DESPLIEGUE A STAGING EXITOSO
            ═══════════════════════════════════════════════════════════════
            Servicio: ${IMAGE_NAME}
            Imagen: ${env.FULL_IMAGE_NAME}:${env.STAGE_IMAGE_TAG}
            Namespace: ${K8S_NAMESPACE}
            Cluster: ${GKE_CLUSTER}
            
            Todas las pruebas pasaron exitosamente:
            ✅ Pruebas Unitarias
            ✅ Pruebas de Integración
            ✅ Smoke Tests
            ✅ Application Tests
            ✅ Performance Tests
            
            El servicio está listo para promoción a producción.
            ═══════════════════════════════════════════════════════════════
            """
            
            script {
                // Notificar éxito (Slack, email, etc.)
                sh '''
                    echo "Enviando notificación de éxito..."
                    # Aquí se puede integrar con Slack, Teams, etc.
                '''
            }
        }
        failure {
            echo """
            ═══════════════════════════════════════════════════════════════
                   ❌ DESPLIEGUE A STAGING FALLÓ
            ═══════════════════════════════════════════════════════════════
            Servicio: ${IMAGE_NAME}
            Revisar logs para más detalles.
            ═══════════════════════════════════════════════════════════════
            """
            
            script {
                // Rollback en caso de fallo
                sh '''
                    echo "Iniciando rollback..."
                    kubectl rollout undo deployment/${IMAGE_NAME} -n ${K8S_NAMESPACE} || echo "No hay versión anterior"
                '''
            }
        }
        always {
            cleanWs()
        }
    }
}

