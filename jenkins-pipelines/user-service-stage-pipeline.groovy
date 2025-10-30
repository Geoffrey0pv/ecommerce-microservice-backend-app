pipeline {
    agent any
    
    environment {
        IMAGE_NAME = "user-service"
        SERVICE_DIR = "user-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}"
        GCP_CREDENTIALS = credentials('gke-credentials')
        GCP_PROJECT = "ecommerce-backend-1760307199"
        K8S_NAMESPACE = "staging"
        K8S_DEPLOYMENT_NAME = "user-service" 
        K8S_SERVICE_NAME = "user-service"  
        CLUSTER_NAME = "ecommerce-devops-cluster"
        CLUSTER_REGION = "us-central1"
        SERVICE_PORT = "8200" 
        API_GATEWAY_SERVICE_NAME = "proxy-client"
    }

    // 2. Parámetros (para ejecución manual)
    parameters {
        string(
            name: 'IMAGE_TAG_MANUAL', 
            defaultValue: '', 
            description: 'Dejar vacío para PRs. Llenar para corridas manuales (ej: latest-dev o un SHA)'
        )
    }

    stages {
        
        // 3. Etapa Modificada: Obtiene el tag de la imagen automáticamente en un PR
        stage('Validate Context & Get Image Tag') {
            steps {
                script {
                    checkout scm
                    
                    if (params.IMAGE_TAG_MANUAL) {
                        echo "🏃 Ejecución Manual detectada."
                        env.IMAGE_TAG = params.IMAGE_TAG_MANUAL
                    } else if (env.CHANGE_TARGET == 'staging' && env.CHANGE_BRANCH == 'develop') {
                        // Flujo de PR (develop -> staging)
                        echo "🔄 PR de develop -> staging detectado."
                        echo "Obteniendo SHA de la rama 'develop' (env.CHANGE_BRANCH)..."
                        
                        // Obtenemos el SHA de la rama fuente (develop)
                        // El pipeline de DEV ya debió haber pusheado esta imagen
                        env.IMAGE_TAG = sh(script: "git rev-parse --short origin/${env.CHANGE_BRANCH}", returnStdout: true).trim()
                        
                    } else {
                        // Flujo no soportado (ej: PR de feature -> staging)
                        error("❌ Este pipeline solo acepta PRs de 'develop' a 'staging' o ejecuciones manuales con IMAGE_TAG_MANUAL.")
                    }
                    
                    echo "✅ Contexto válido: Pipeline STAGE para ${IMAGE_NAME}"
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
                        gcloud container clusters get-credentials \${CLUSTER_NAME} --region=\${CLUSTER_REGION}
                    """
                }
            }
        }

        stage('Verify Image Exists in GCR') {
            steps {
                script {
                    sh """
                        echo "🔍 Verificando que la imagen \${FULL_IMAGE_NAME}:\${env.IMAGE_TAG} existe..."
                        gcloud artifacts docker images describe \${FULL_IMAGE_NAME}:\${env.IMAGE_TAG} || {
                            echo "❌ ERROR: Imagen no encontrada"
                            echo "📋 Imágenes disponibles recientemente:"
                            gcloud artifacts docker images list \${GCR_REGISTRY}/\${IMAGE_NAME} --include-tags --limit=10 --sort-by=~UPDATE_TIME
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
                    echo "🚀 Desplegando ${FULL_IMAGE_NAME}:${env.IMAGE_TAG} a ${K8S_NAMESPACE}..."
                    sh """
                        # Asegurar que el namespace exista
                        kubectl create namespace \${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Aplicar los manifiestos declarativos (¡Mucho más seguro!)
                        # Esto crea o actualiza el Servicio y el Deployment
                        echo "Aplicando manifiestos de manifests-gcp/user-service/..."
                        kubectl apply -f manifests-gcp/user-service/ -n \${K8S_NAMESPACE}
                        
                        # Actualizar la imagen del deployment
                        echo "Actualizando la imagen del deployment \${K8S_DEPLOYMENT_NAME}..."
                        kubectl set image deployment/\${K8S_DEPLOYMENT_NAME} \
                            ${K8S_DEPLOYMENT_NAME}=\${FULL_IMAGE_NAME}:\${env.IMAGE_TAG} \
                            -n \${K8S_NAMESPACE} --record
                        
                        # Esperar que el despliegue termine
                        echo "⏳ Esperando rollout..."
                        kubectl rollout status deployment/\${K8S_DEPLOYMENT_NAME} -n \${K8S_NAMESPACE} --timeout=300s
                        
                        echo "✅ Despliegue completado."
                        kubectl get pods -n \${K8S_NAMESPACE} -l app=\${K8S_DEPLOYMENT_NAME}
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
                        echo "🎯 Probando pod: \$POD_NAME"
                        
                        # Bucle de reintento para el health check interno
                        for i in {1..10}; do
                            echo "Intento \$i/10: Verificando http://localhost:${SERVICE_PORT}/actuator/health"
                            if kubectl exec \$POD_NAME -n \${K8S_NAMESPACE} -- curl -s --fail http://localhost:${SERVICE_PORT}/actuator/health | grep '"status":"UP"'; then
                                echo "✅ Health check interno exitoso."
                                break
                            fi
                            if [ \$i -eq 10 ]; then
                                echo "❌ Health check interno falló después de 10 intentos."
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
                    echo "🌐 Obteniendo IP externa del API Gateway (\${API_GATEWAY_SERVICE_NAME}) en Staging..."
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
                            echo "❌ Error: No se pudo obtener la IP del Gateway de Staging."
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
                        // Ejecuta los tests de Maven del directorio tests/e2e
                        // Sobrescribe la URL del gateway con la IP de staging
                        // Usamos -f para apuntar al pom.xml específico
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
                    echo "⚡ Ejecutando pruebas de rendimiento (Locust) contra http://${env.STAGING_GATEWAY_IP}..."
                    // Usamos una imagen de Docker que ya tiene locust y python
                    docker.image('locustio/locust').inside {
                        sh "cp -R tests/performance /home/locust"
                        sh """
                            locust -f /home/locust/ecommerce_load_test.py \
                                --host=http://${env.STAGING_GATEWAY_IP} \
                                --headless \
                                --users 100 \
                                --spawn-rate 10 \
                                --run-time 1m \
                                --exit-code-on-fail 1 \
                                --html /home/locust/report.html
                        """
                    }
                    // Archivar el reporte HTML de Locust
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
                """
            }
        }
        failure {
            script {
                sh """
                    echo "❌ 💥 STAGING DEPLOY FALLÓ"
                    echo "🔍 Realizando rollback al deployment anterior..."
                    kubectl rollout undo deployment/\${K8S_DEPLOYMENT_NAME} -n \${K8S_NAMESPACE} || echo "No hay rollback disponible."
                    echo "📋 Información de debug:"
                    kubectl get events -n \${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -10
                """
            }
        }
        always {
            script {
                sh """
                    docker rmi \${FULL_IMAGE_NAME}:\${env.IMAGE_TAG} || true
                    docker rmi \${FULL_IMAGE_NAME}:staging || true
                    gcloud auth revoke --all || true
                """
                cleanWs() 
            }
        }
    }
}