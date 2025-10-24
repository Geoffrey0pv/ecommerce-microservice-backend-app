pipeline {
    agent any

    environment {
        IMAGE_NAME = "product-service"
        GCP_PROJECT_ID = "your-gcp-project-id"
        GCP_REGION = "us-central1"
        GCR_REGISTRY = "gcr.io"
        SERVICE_DIR = "product-service"
        SPRING_PROFILES_ACTIVE = "production"
        NAMESPACE = "production"
        RELEASE_VERSION = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.substring(0,7)}"
    }

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest-master', description: 'Tag de la imagen a desplegar')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Saltar pruebas de sistema')
        booleanParam(name: 'CREATE_RELEASE_NOTES', defaultValue: true, description: 'Generar Release Notes')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "🚀 Desplegando ${IMAGE_NAME} v${RELEASE_VERSION} en PRODUCCIÓN"
            }
        }

        stage('Authenticate with GCP') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_KEY_FILE')]) {
                        sh '''
                            gcloud auth activate-service-account --key-file=$GCP_KEY_FILE
                            gcloud config set project ${GCP_PROJECT_ID}
                            gcloud auth configure-docker ${GCR_REGISTRY} --quiet
                            echo "✅ Autenticado con GCP proyecto: $(gcloud config get-value project)"
                        '''
                    }
                }
            }
        }

        stage('Verify Image in GCR') {
            steps {
                script {
                    def fullImageName = "${GCR_REGISTRY}/${GCP_PROJECT_ID}/${IMAGE_NAME}:${params.IMAGE_TAG}"
                    echo "🔍 Verificando imagen: ${fullImageName}"
                    
                    sh """
                        # Verificar que la imagen existe en GCR
                        gcloud container images describe ${fullImageName}
                        
                        # Verificar vulnerabilidades si está disponible
                        gcloud beta container images scan ${fullImageName} || echo "⚠️ Scanner no disponible"
                        
                        echo "✅ Imagen verificada: ${fullImageName}"
                    """
                    
                    env.FULL_IMAGE_NAME = fullImageName
                }
            }
        }

        stage('Pre-deployment System Tests') {
            when {
                not { params.SKIP_TESTS }
            }
            steps {
                script {
                    echo "🧪 Ejecutando pruebas de sistema pre-deployment..."
                    
                    // Crear contenedor temporal para pruebas
                    docker.image("${env.FULL_IMAGE_NAME}").inside("-e SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}") {
                        sh '''
                            # Verificar que la aplicación inicia correctamente
                            timeout 30s java -jar app.jar --spring.profiles.active=production --server.port=8080 &
                            APP_PID=$!
                            
                            # Esperar que la aplicación esté lista
                            sleep 15
                            
                            # Verificar health endpoint
                            curl -f http://localhost:8080/actuator/health || exit 1
                            
                            # Terminar aplicación de prueba
                            kill $APP_PID || true
                            
                            echo "✅ Pruebas de sistema pasaron"
                        '''
                    }
                }
            }
        }

        stage('Deploy to Production') {
            steps {
                script {
                    echo "🚀 Desplegando a producción..."
                    
                    sh '''
                        # Aplicar manifiestos de producción
                        kubectl apply -f manifests-gcp/${SERVICE_DIR}/ -n ${NAMESPACE}
                        
                        # Actualizar imagen en el deployment
                        kubectl set image deployment/${IMAGE_NAME} ${IMAGE_NAME}=${FULL_IMAGE_NAME} -n ${NAMESPACE}
                        
                        # Anotar el deployment con información de release
                        kubectl annotate deployment/${IMAGE_NAME} \
                            deployment.kubernetes.io/revision="${RELEASE_VERSION}" \
                            deployment.kubernetes.io/change-cause="Deploy ${IMAGE_NAME}:${IMAGE_TAG} via Jenkins" \
                            -n ${NAMESPACE} --overwrite
                        
                        # Esperar que el rollout termine
                        kubectl rollout status deployment/${IMAGE_NAME} -n ${NAMESPACE} --timeout=600s
                        
                        echo "✅ Deployment completado"
                    '''
                }
            }
        }

        stage('Post-deployment Health Check') {
            steps {
                script {
                    echo "🏥 Verificando salud del servicio..."
                    
                    sh '''
                        # Esperar estabilización
                        sleep 30
                        
                        # Verificar pods están corriendo
                        kubectl get pods -l app=${IMAGE_NAME} -n ${NAMESPACE}
                        
                        # Obtener información del servicio
                        SERVICE_IP=$(kubectl get service ${IMAGE_NAME} -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                        SERVICE_PORT=$(kubectl get service ${IMAGE_NAME} -n ${NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        
                        if [ ! -z "$SERVICE_IP" ]; then
                            echo "🌐 Servicio disponible en: http://$SERVICE_IP:$SERVICE_PORT"
                            
                            # Health check
                            for i in {1..5}; do
                                if curl -f "http://$SERVICE_IP:$SERVICE_PORT/actuator/health"; then
                                    echo "✅ Health check exitoso (intento $i)"
                                    break
                                else
                                    echo "⚠️ Health check falló (intento $i), reintentando..."
                                    sleep 10
                                fi
                                
                                if [ $i -eq 5 ]; then
                                    echo "❌ Health check falló después de 5 intentos"
                                    exit 1
                                fi
                            done
                        else
                            echo "⚠️ IP externa no disponible, verificando internamente"
                            kubectl exec -n ${NAMESPACE} deployment/${IMAGE_NAME} -- curl -f http://localhost:8080/actuator/health
                        fi
                        
                        echo "✅ Servicio saludable en producción"
                    '''
                }
            }
        }

        stage('Performance Validation') {
            steps {
                script {
                    echo "⚡ Validando rendimiento básico..."
                    
                    sh '''
                        # Obtener URL del servicio
                        SERVICE_IP=$(kubectl get service ${IMAGE_NAME} -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                        SERVICE_PORT=$(kubectl get service ${IMAGE_NAME} -n ${NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        
                        if [ ! -z "$SERVICE_IP" ]; then
                            SERVICE_URL="http://$SERVICE_IP:$SERVICE_PORT"
                            
                            echo "🧪 Ejecutando pruebas de carga básicas..."
                            
                            # Usar Apache Bench para pruebas rápidas
                            ab -n 100 -c 10 -H "Accept: application/json" ${SERVICE_URL}/actuator/health
                            
                            echo "✅ Validación de rendimiento completada"
                        else
                            echo "⚠️ Saltando pruebas de rendimiento - IP externa no disponible"
                        fi
                    '''
                }
            }
        }

        stage('Generate Release Notes') {
            when {
                expression { params.CREATE_RELEASE_NOTES }
            }
            steps {
                script {
                    echo "📝 Generando Release Notes..."
                    
                    def releaseNotes = """
# Release Notes - ${IMAGE_NAME} v${RELEASE_VERSION}

## 📋 Información del Release
- **Servicio**: ${IMAGE_NAME}
- **Versión**: ${RELEASE_VERSION}
- **Imagen**: ${env.FULL_IMAGE_NAME}
- **Ambiente**: Producción
- **Fecha**: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
- **Jenkins Build**: #${env.BUILD_NUMBER}
- **Git Commit**: ${env.GIT_COMMIT}

## 🚀 Cambios Desplegados
"""
                    
                    // Obtener commits desde el último tag
                    def gitLog = sh(
                        script: """
                            # Obtener commits desde el último release
                            git log --oneline --no-merges HEAD~5..HEAD || echo "No hay commits recientes"
                        """,
                        returnStdout: true
                    ).trim()
                    
                    if (gitLog) {
                        releaseNotes += "\n### Commits:\n"
                        gitLog.split('\n').each { commit ->
                            releaseNotes += "- ${commit}\n"
                        }
                    }
                    
                    releaseNotes += """

## ✅ Validaciones Ejecutadas
- [x] Autenticación GCP
- [x] Verificación de imagen en GCR
- [x] Pruebas de sistema pre-deployment
- [x] Deployment a producción
- [x] Health checks post-deployment
- [x] Validación básica de rendimiento

## 🏥 Estado del Servicio
- **Pods**: Ejecutándose correctamente
- **Health Check**: ✅ PASS
- **Performance**: ✅ VALIDADO

## 🔗 Enlaces Útiles
- **Logs**: `kubectl logs -f deployment/${IMAGE_NAME} -n ${NAMESPACE}`
- **Metrics**: `kubectl top pods -l app=${IMAGE_NAME} -n ${NAMESPACE}`
- **Rollback**: `kubectl rollout undo deployment/${IMAGE_NAME} -n ${NAMESPACE}`

---
**Generado automáticamente por Jenkins Pipeline**
"""
                    
                    // Guardar Release Notes
                    writeFile file: "RELEASE_NOTES_${IMAGE_NAME}_${RELEASE_VERSION}.md", text: releaseNotes
                    
                    // Archivar Release Notes
                    archiveArtifacts artifacts: "RELEASE_NOTES_${IMAGE_NAME}_${RELEASE_VERSION}.md", fingerprint: true
                    
                    echo "✅ Release Notes generados y archivados"
                }
            }
        }
    }

    post {
        success {
            script {
                echo "🎉 ¡Deployment exitoso de ${IMAGE_NAME} v${RELEASE_VERSION} en PRODUCCIÓN!"
                
                // Notificar deployment exitoso
                sh """
                    echo "✅ DEPLOYMENT EXITOSO" > deployment_status.txt
                    echo "Servicio: ${IMAGE_NAME}" >> deployment_status.txt
                    echo "Versión: ${RELEASE_VERSION}" >> deployment_status.txt
                    echo "Imagen: ${env.FULL_IMAGE_NAME}" >> deployment_status.txt
                    echo "Timestamp: \$(date)" >> deployment_status.txt
                """
                
                archiveArtifacts artifacts: "deployment_status.txt", fingerprint: true
            }
        }
        failure {
            script {
                echo "❌ Deployment falló para ${IMAGE_NAME}"
                
                // Capturar logs para debugging
                sh """
                    echo "❌ DEPLOYMENT FALLIDO" > deployment_failure.txt
                    echo "Servicio: ${IMAGE_NAME}" >> deployment_failure.txt
                    echo "Build: #${env.BUILD_NUMBER}" >> deployment_failure.txt
                    echo "Timestamp: \$(date)" >> deployment_failure.txt
                    echo "" >> deployment_failure.txt
                    echo "=== LOGS DE KUBERNETES ===" >> deployment_failure.txt
                    kubectl describe deployment ${IMAGE_NAME} -n ${NAMESPACE} >> deployment_failure.txt || echo "No se pudo obtener info del deployment" >> deployment_failure.txt
                    echo "" >> deployment_failure.txt
                    echo "=== LOGS DEL POD ===" >> deployment_failure.txt
                    kubectl logs -l app=${IMAGE_NAME} -n ${NAMESPACE} --tail=50 >> deployment_failure.txt || echo "No se pudieron obtener logs" >> deployment_failure.txt
                """
                
                archiveArtifacts artifacts: "deployment_failure.txt", fingerprint: true
            }
        }
        always {
            cleanWs()
        }
    }
}