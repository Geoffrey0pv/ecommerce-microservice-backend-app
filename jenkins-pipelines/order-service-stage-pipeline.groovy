pipeline {
    agent any
    
    parameters {
        string(name: 'IMAGE_TO_DEPLOY', defaultValue: '', description: 'Full image name with tag to deploy to staging (ej: .../order-service:a1b2c3d)')
    }
    
    environment {
        IMAGE_NAME = "order-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        SPRING_PROFILES_ACTIVE = "stage"
        KUBERNETES_NAMESPACE = "ecommerce-staging"
        GCP_CREDENTIALS = credentials('gke-credentials') 
        //  Variables de entorno base
    }

    stages {
        stage('Validate Parameters') {
            // [cite: 1055] Valida que el parámetro de entrada no esté vacío
            steps {
                script {
                    if (!params.IMAGE_TO_DEPLOY) {
                        error("IMAGE_TO_DEPLOY parameter is required")
                    }
                    echo "🚀 Desplegando imagen ya construida: ${params.IMAGE_TO_DEPLOY}"
                }
            }
        }
        
        stage('Authenticate GCP') {
            // [cite: 1056] Se autentica en GKE
            steps {
                script {
                    sh 'gcloud auth activate-service-account --key-file=$GCP_CREDENTIALS'
                    sh 'gcloud container clusters get-credentials ecommerce-gke-cluster --zone us-central1-a --project ecommerce-backend-1760307199'
                }
            }
        }
        
        stage('Deploy to Staging') {
            // [cite: 1058] Despliega la imagen específica del parámetro
            steps {
                script {
                    echo "Desplegando ${params.IMAGE_TO_DEPLOY} a ambiente staging..."
                    sh "kubectl create namespace ${KUBERNETES_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -"
                    
                    // [cite: 1059] Actualiza la imagen del deployment existente O crea uno nuevo si no existe
                    sh """
                        kubectl set image deployment/${IMAGE_NAME} ${IMAGE_NAME}=${params.IMAGE_TO_DEPLOY} -n ${KUBERNETES_NAMESPACE} || \
                        kubectl create deployment ${IMAGE_NAME} --image=${params.IMAGE_TO_DEPLOY} -n ${KUBERNETES_NAMESPACE}
                        
                        kubectl expose deployment ${IMAGE_NAME} --port=8083 --target-port=8083 -n ${KUBERNETES_NAMESPACE} --type=ClusterIP --dry-run=client -o yaml | kubectl apply -f -
                        
                        kubectl rollout status deployment/${IMAGE_NAME} -n ${KUBERNETES_NAMESPACE} --timeout=300s
                    """
                }
            }
        }
        
        stage('Health Check') {
            // [cite: 1062] Verifica que los pods estén listos
            steps {
                script {
                    echo "Verificando salud del servicio desplegado..."
                    sh """
                        kubectl wait --for=condition=ready pod -l app=${IMAGE_NAME} -n ${KUBERNETES_NAMESPACE} --timeout=300s
                        kubectl get pods -l app=${IMAGE_NAME} -n ${KUBERNETES_NAMESPACE}
                    """
                }
            }
        }

        // --- ETAPAS DE PRUEBA INTEGRADAS (NUEVO) ---

        stage('Get Gateway IP for Tests') {
            steps {
                script {
                    echo "Obteniendo IP externa del API Gateway (proxy-client) en Staging..."
                    // Asumimos que tu gateway (proxy-client) está expuesto como LoadBalancer en staging
                    // Espera a que la IP esté disponible.
                    sh """
                        for i in {1..30}; do
                            STAGING_GATEWAY_IP=\$(kubectl get svc proxy-client -n ${KUBERNETES_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                            if [ -n "\$STAGING_GATEWAY_IP" ]; then
                                echo "IP del Gateway de Staging: \$STAGING_GATEWAY_IP"
                                break
                            fi
                            echo "Esperando IP del Gateway... intento \$i/30"
                            sleep 10
                        done
                        
                        if [ -z "\$STAGING_GATEWAY_IP" ]; then
                            echo "Error: No se pudo obtener la IP del Gateway de Staging."
                            exit 1
                        fi
                        
                        env.STAGING_GATEWAY_IP = sh(script: "echo \$STAGING_GATEWAY_IP", returnStdout: true).trim()
                    """
                }
            }
        }

        stage('Run E2E Tests (Maven)') {
            steps {
                script {
                    echo "Ejecutando pruebas E2E contra http://${env.STAGING_GATEWAY_IP}..."
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        //  Ejecuta los tests de Maven del directorio tests/e2e
                        // [cite: 445] Sobrescribe la URL del gateway con la IP de staging
                        sh "mvn test -f tests/e2e/pom.xml -Dapi.gateway.url=http://${env.STAGING_GATEWAY_IP}"
                    }
                }
            }
            post {
                always {
                    // Archiva los resultados de las pruebas E2E
                    junit 'tests/e2e/target/surefire-reports/*.xml'
                }
            }
        }
        
        stage('Run Performance Tests (Locust)') {
            steps {
                script {
                    echo "Ejecutando pruebas de rendimiento (Locust) contra http://${env.STAGING_GATEWAY_IP}..."
                    //  Usa una imagen de Docker que contenga Locust
                    docker.image('locustio/locust').inside {
                        // [cite: 242] Ejecuta la prueba de carga en modo "headless" (sin UI)
                        sh """
                            locust -f tests/performance/ecommerce_load_test.py \
                                --host=http://${env.STAGING_GATEWAY_IP} \
                                --headless \
                                --users 100 \
                                --spawn-rate 10 \
                                --run-time 1m \
                                --exit-code-on-fail 1
                        """
                    }
                }
            }
        }
    }

    post {
        // [cite: 1066] Bloque post original para notificación
        success {
            echo "Despliegue a STAGING de ${params.IMAGE_TO_DEPLOY} y pruebas E2E/Rendimiento completadas exitosamente."
        }
        always {
            cleanWs()
            sh 'gcloud auth revoke --all || true'
        }
        failure {
            echo "Despliegue a STAGING o las pruebas fallaron para ${params.IMAGE_TO_DEPLOY}"
            // Opcional: Implementar un rollback automático en staging si fallan los tests
            sh "kubectl rollout undo deployment/${IMAGE_NAME} -n ${KUBERNETES_NAMESPACE} || echo 'No hay rollback disponible'"
        }
    }
}