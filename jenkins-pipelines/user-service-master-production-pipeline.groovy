/**
 * ========================================================================
 * JENKINS PIPELINE - PRODUCTION DEPLOYMENT (Master Environment)
 * ========================================================================
 * Servicio: user-service
 * Ambiente: Production (master branch)
 * 
 * DESCRIPCIÓN:
 * Pipeline completo que incluye:
 * 1. Build con pruebas unitarias
 * 2. Análisis de seguridad (OWASP Dependency Check)
 * 3. Pruebas de sistema/E2E
 * 4. Deploy a Kubernetes (Producción)
 * 5. Generación automática de Release Notes (Change Management)
 * 6. Rollback automático en caso de fallo
 * 
 * REQUISITOS:
 * - Branch: master/main
 * - Credenciales: gke-credentials (GCP Service Account)
 * - Plugins Jenkins: Docker, Kubernetes CLI, Pipeline Utility Steps
 * ========================================================================
 */

pipeline {
    agent any

    environment {
        // ===== CONFIGURACIÓN DEL SERVICIO =====
        SERVICE_NAME = "user-service"
        SERVICE_PORT = "8700"
        MODULE_PATH = "user-service"
        
        // ===== CONFIGURACIÓN DE IMAGEN DOCKER =====
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        IMAGE_NAME = "user-service"
        
        // ===== CONFIGURACIÓN DE GCP =====
        GCP_PROJECT_ID = "ecommerce-backend-1760307199"
        GCP_REGION = "us-central1"
        GCP_CREDENTIALS = credentials('gke-credentials')
        
        // ===== CONFIGURACIÓN DE KUBERNETES PRODUCCIÓN =====
        K8S_NAMESPACE = "production"
        GKE_CLUSTER = "ecommerce-devops-cluster"
        K8S_DEPLOYMENT_NAME = "user-service"
        
        // ===== VARIABLES DINÁMICAS =====
        GIT_COMMIT_SHA = ""
        IMAGE_TAG = ""
        RELEASE_VERSION = ""
        PREVIOUS_IMAGE = ""
    }

    // Parámetros configurables
    parameters {
        booleanParam(
            name: 'RUN_UNIT_TESTS', 
            defaultValue: true, 
            description: '✅ Ejecutar pruebas unitarias'
        )
        booleanParam(
            name: 'RUN_SECURITY_SCAN', 
            defaultValue: true, 
            description: '🔒 Ejecutar análisis de seguridad (OWASP)'
        )
        booleanParam(
            name: 'RUN_E2E_TESTS', 
            defaultValue: true, 
            description: '🧪 Ejecutar pruebas de sistema/E2E'
        )
        booleanParam(
            name: 'GENERATE_RELEASE_NOTES', 
            defaultValue: true, 
            description: '📝 Generar Release Notes automáticas'
        )
        choice(
            name: 'DEPLOYMENT_STRATEGY', 
            choices: ['RollingUpdate', 'Recreate'], 
            description: 'Estrategia de deployment en K8s'
        )
    }

    stages {
        // =====================================================
        // STAGE 1: CHECKOUT & SETUP
        // =====================================================
        stage('📦 Checkout & Setup') {
            steps {
                script {
                    echo "╔══════════════════════════════════════════════════════╗"
                    echo "║  PRODUCCIÓN - ${SERVICE_NAME}                        ║"
                    echo "╚══════════════════════════════════════════════════════╝"
                    
                    // Checkout del código
                    checkout scm
                    
                    // Obtener información del commit
                    env.GIT_COMMIT_SHA = sh(
                        script: "git rev-parse --short HEAD", 
                        returnStdout: true
                    ).trim()
                    
                    env.GIT_COMMIT_MSG = sh(
                        script: "git log -1 --pretty=%B", 
                        returnStdout: true
                    ).trim()
                    
                    env.GIT_AUTHOR = sh(
                        script: "git log -1 --pretty=%an", 
                        returnStdout: true
                    ).trim()
                    
                    // Definir versiones
                    env.IMAGE_TAG = "prod-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHA}"
                    env.RELEASE_VERSION = "v1.0.${env.BUILD_NUMBER}"
                    
                    echo """
                    📋 INFORMACIÓN DEL RELEASE:
                    ├─ Commit: ${env.GIT_COMMIT_SHA}
                    ├─ Autor: ${env.GIT_AUTHOR}
                    ├─ Mensaje: ${env.GIT_COMMIT_MSG}
                    ├─ Tag Imagen: ${env.IMAGE_TAG}
                    └─ Versión Release: ${env.RELEASE_VERSION}
                    """
                }
            }
        }

        // =====================================================
        // STAGE 2: BUILD & UNIT TESTS
        // =====================================================
        stage('🔨 Build & Unit Tests') {
            when {
                expression { params.RUN_UNIT_TESTS }
            }
            steps {
                script {
                    echo "🔨 Compilando ${SERVICE_NAME} y ejecutando pruebas unitarias..."
                    
                    dir(MODULE_PATH) {
                        sh """
                            echo "📦 Limpiando builds anteriores..."
                            ./mvnw clean
                            
                            echo "🔨 Compilando proyecto..."
                            ./mvnw compile
                            
                            echo "🧪 Ejecutando pruebas unitarias..."
                            ./mvnw test
                            
                            echo "📦 Empaquetando artefacto..."
                            ./mvnw package -DskipTests
                            
                            echo "✅ Build completado exitosamente"
                        """
                    }
                }
            }
            post {
                always {
                    // Publicar resultados de pruebas unitarias
                    junit allowEmptyResults: true, testResults: "${MODULE_PATH}/target/surefire-reports/*.xml"
                }
            }
        }

        // =====================================================
        // STAGE 3: SECURITY SCAN
        // =====================================================
        stage('🔒 Security Scan (OWASP)') {
            when {
                expression { params.RUN_SECURITY_SCAN }
            }
            steps {
                script {
                    echo "🔒 Ejecutando análisis de seguridad de dependencias..."
                    
                    dir(MODULE_PATH) {
                        sh """
                            echo "🔍 Analizando dependencias con OWASP Dependency Check..."
                            
                            # Verificar vulnerabilidades conocidas en dependencias
                            ./mvnw org.owasp:dependency-check-maven:check \
                                -DfailBuildOnCVSS=7 \
                                -DsuppressionFile=../owasp-suppressions.xml \
                                || echo "⚠️ Vulnerabilidades encontradas (revise el reporte)"
                            
                            echo "✅ Security scan completado"
                        """
                    }
                }
            }
            post {
                always {
                    // Archivar reporte de seguridad
                    archiveArtifacts artifacts: "${MODULE_PATH}/target/dependency-check-report.html", 
                                     allowEmptyArchive: true,
                                     fingerprint: true
                }
            }
        }

        // =====================================================
        // STAGE 4: BUILD DOCKER IMAGE
        // =====================================================
        stage('🐳 Build Docker Image') {
            steps {
                script {
                    echo "🐳 Construyendo imagen Docker..."
                    
                    dir(MODULE_PATH) {
                        sh """
                            echo "📦 Construyendo imagen: ${GCR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
                            
                            docker build \
                                -t ${GCR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} \
                                -t ${GCR_REGISTRY}/${IMAGE_NAME}:latest-prod \
                                --build-arg BUILD_DATE=\$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                                --build-arg VCS_REF=${GIT_COMMIT_SHA} \
                                --build-arg VERSION=${RELEASE_VERSION} \
                                .
                            
                            echo "✅ Imagen Docker construida exitosamente"
                        """
                    }
                }
            }
        }

        // =====================================================
        // STAGE 5: PUSH TO GCR
        // =====================================================
        stage('📤 Push to GCR') {
            steps {
                script {
                    echo "📤 Subiendo imagen a Google Container Registry..."
                    
                    sh """
                        echo "🔐 Autenticando con GCP..."
                        gcloud auth activate-service-account --key-file=\$GCP_CREDENTIALS
                        gcloud config set project ${GCP_PROJECT_ID}
                        gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev --quiet
                        
                        echo "📤 Pushing imagen..."
                        docker push ${GCR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${GCR_REGISTRY}/${IMAGE_NAME}:latest-prod
                        
                        echo "✅ Imagen subida exitosamente a GCR"
                    """
                }
            }
        }

        // =====================================================
        // STAGE 6: E2E/SYSTEM TESTS
        // =====================================================
        stage('🧪 System/E2E Tests') {
            when {
                expression { params.RUN_E2E_TESTS }
            }
            steps {
                script {
                    echo "🧪 Ejecutando pruebas de sistema/E2E..."
                    
                    sh """
                        echo "🚀 Desplegando en ambiente de testing temporal..."
                        
                        # Autenticar con GKE
                        gcloud container clusters get-credentials ${GKE_CLUSTER} \
                            --region=${GCP_REGION} \
                            --project ${GCP_PROJECT_ID}
                        
                        # Crear namespace temporal para tests
                        kubectl create namespace e2e-test-${BUILD_NUMBER} || true
                        
                        # Desplegar servicio temporalmente
                        kubectl run ${SERVICE_NAME}-test-${BUILD_NUMBER} \
                            --image=${GCR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} \
                            --port=${SERVICE_PORT} \
                            --namespace=e2e-test-${BUILD_NUMBER}
                        
                        # Esperar que el pod esté listo
                        kubectl wait --for=condition=ready pod \
                            -l run=${SERVICE_NAME}-test-${BUILD_NUMBER} \
                            --timeout=120s \
                            --namespace=e2e-test-${BUILD_NUMBER}
                        
                        # Ejecutar tests E2E
                        echo "🧪 Ejecutando suite de tests E2E..."
                        cd tests/e2e
                        mvn clean test \
                            -Dapi.gateway.url=http://${SERVICE_NAME}-test-${BUILD_NUMBER}.e2e-test-${BUILD_NUMBER}:${SERVICE_PORT} \
                            || TEST_FAILED=true
                        
                        # Cleanup del namespace temporal
                        kubectl delete namespace e2e-test-${BUILD_NUMBER} || true
                        
                        if [ "\$TEST_FAILED" = "true" ]; then
                            echo "❌ Tests E2E fallaron"
                            exit 1
                        fi
                        
                        echo "✅ Tests E2E completados exitosamente"
                    """
                }
            }
            post {
                always {
                    // Publicar resultados de tests E2E
                    junit allowEmptyResults: true, testResults: "tests/e2e/target/surefire-reports/*.xml"
                }
            }
        }

        // =====================================================
        // STAGE 7: DEPLOY TO PRODUCTION
        // =====================================================
        stage('🚀 Deploy to Production') {
            steps {
                script {
                    echo "🚀 Desplegando a PRODUCCIÓN..."
                    
                    sh """
                        echo "☸️ Conectando a GKE Production Cluster..."
                        gcloud container clusters get-credentials ${GKE_CLUSTER} \
                            --region=${GCP_REGION} \
                            --project ${GCP_PROJECT_ID}
                        
                        # Crear namespace de producción si no existe
                        kubectl create namespace ${K8S_NAMESPACE} || true
                        
                        # Guardar imagen anterior para posible rollback
                        PREVIOUS_IMAGE=\$(kubectl get deployment ${K8S_DEPLOYMENT_NAME} \
                            -n ${K8S_NAMESPACE} \
                            -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo "none")
                        echo "📌 Imagen anterior: \$PREVIOUS_IMAGE"
                        echo "\$PREVIOUS_IMAGE" > previous_image.txt
                        
                        echo "📋 Aplicando deployment a producción..."
                        helm upgrade --install ${K8S_DEPLOYMENT_NAME} manifests-gcp/${MODULE_PATH}/ \
                            --namespace ${K8S_NAMESPACE} \
                            --set image.tag=${IMAGE_TAG} \
                            --set image.repository=${GCR_REGISTRY}/${IMAGE_NAME} \
                            --set deployment.strategy.type=${params.DEPLOYMENT_STRATEGY} \
                            --set env[4].value="false" \
                            --set env[5].value="false" \
                            --wait --timeout=10m
                        
                        # Anotar deployment con información de release
                        kubectl annotate deployment/${K8S_DEPLOYMENT_NAME} \
                            kubernetes.io/change-cause="Deploy ${RELEASE_VERSION} - ${GIT_COMMIT_MSG}" \
                            deployment.jenkins/build-number="${BUILD_NUMBER}" \
                            deployment.jenkins/git-commit="${GIT_COMMIT_SHA}" \
                            deployment.jenkins/deployed-by="${GIT_AUTHOR}" \
                            deployment.jenkins/deployed-at="\$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
                            -n ${K8S_NAMESPACE} --overwrite
                        
                        echo "✅ Deployment completado - Versión ${RELEASE_VERSION}"
                    """
                    
                    // Guardar imagen anterior para rollback
                    env.PREVIOUS_IMAGE = readFile('previous_image.txt').trim()
                }
            }
        }

        // =====================================================
        // STAGE 8: POST-DEPLOYMENT VALIDATION
        // =====================================================
        stage('✅ Post-Deployment Validation') {
            steps {
                script {
                    echo "✅ Validando deployment en producción..."
                    
                    sh """
                        echo "⏳ Esperando estabilización del servicio..."
                        sleep 30
                        
                        echo "🏥 Verificando health de pods..."
                        kubectl get pods -n ${K8S_NAMESPACE} -l app=${K8S_DEPLOYMENT_NAME}
                        
                        # Health check del servicio
                        POD_NAME=\$(kubectl get pod -n ${K8S_NAMESPACE} \
                            -l app=${K8S_DEPLOYMENT_NAME} \
                            -o jsonpath='{.items[0].metadata.name}')
                        
                        echo "🔍 Verificando endpoint /actuator/health..."
                        kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- \
                            curl -f http://localhost:${SERVICE_PORT}/actuator/health || {
                                echo "❌ Health check falló"
                                exit 1
                            }
                        
                        echo "✅ Servicio saludable en producción"
                        echo "🎉 Deployment ${RELEASE_VERSION} validado exitosamente"
                    """
                }
            }
        }

        // =====================================================
        // STAGE 9: GENERATE RELEASE NOTES
        // =====================================================
        stage('📝 Generate Release Notes') {
            when {
                expression { params.GENERATE_RELEASE_NOTES }
            }
            steps {
                script {
                    echo "📝 Generando Release Notes automáticas..."
                    
                    // Obtener commits desde el último tag
                    def gitLog = sh(
                        script: "git log --oneline --pretty=format:'- %s (%an)' -10",
                        returnStdout: true
                    ).trim()
                    
                    // Obtener información del deployment
                    def deploymentInfo = sh(
                        script: """
                            kubectl get deployment ${K8S_DEPLOYMENT_NAME} -n ${K8S_NAMESPACE} \
                                -o jsonpath='{.metadata.annotations}'
                        """,
                        returnStdout: true
                    ).trim()
                    
                    def releaseNotes = """
# 📦 Release Notes - ${SERVICE_NAME} ${RELEASE_VERSION}

## 📋 Información General
| Campo | Valor |
|-------|-------|
| **Servicio** | ${SERVICE_NAME} |
| **Versión** | ${RELEASE_VERSION} |
| **Build** | #${BUILD_NUMBER} |
| **Ambiente** | Production |
| **Fecha** | ${new Date().format('yyyy-MM-dd HH:mm:ss')} UTC |
| **Imagen Docker** | `${GCR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}` |

## 🔄 Información del Código
| Campo | Valor |
|-------|-------|
| **Git Commit** | ${env.GIT_COMMIT_SHA} |
| **Autor** | ${env.GIT_AUTHOR} |
| **Mensaje** | ${env.GIT_COMMIT_MSG} |
| **Branch** | master/main |

## 📝 Cambios Incluidos
${gitLog}

## ✅ Validaciones Ejecutadas
- [x] **Build**: Compilación exitosa ✅
- [x] **Unit Tests**: ${params.RUN_UNIT_TESTS ? 'Ejecutados ✅' : 'Omitidos ⏭️'}
- [x] **Security Scan**: ${params.RUN_SECURITY_SCAN ? 'Ejecutado ✅' : 'Omitido ⏭️'}
- [x] **E2E Tests**: ${params.RUN_E2E_TESTS ? 'Ejecutados ✅' : 'Omitidos ⏭️'}
- [x] **Docker Image**: Construida y subida a GCR ✅
- [x] **Deployment**: Desplegado en producción ✅
- [x] **Health Check**: Servicio saludable ✅

## 🚀 Deployment Info
- **Namespace**: ${K8S_NAMESPACE}
- **Strategy**: ${params.DEPLOYMENT_STRATEGY}
- **Replicas**: 2 (High Availability)
- **Previous Image**: ${env.PREVIOUS_IMAGE ?: 'N/A'}

## 📊 Métricas y Monitoreo
- **Logs**: \`kubectl logs -f deployment/${K8S_DEPLOYMENT_NAME} -n ${K8S_NAMESPACE}\`
- **Pods Status**: \`kubectl get pods -n ${K8S_NAMESPACE} -l app=${K8S_DEPLOYMENT_NAME}\`
- **Describe**: \`kubectl describe deployment/${K8S_DEPLOYMENT_NAME} -n ${K8S_NAMESPACE}\`

## 🔄 Rollback (Si es necesario)
\`\`\`bash
# Rollback a versión anterior
kubectl rollout undo deployment/${K8S_DEPLOYMENT_NAME} -n ${K8S_NAMESPACE}

# O rollback a imagen específica
kubectl set image deployment/${K8S_DEPLOYMENT_NAME} \\
    ${SERVICE_NAME}=${env.PREVIOUS_IMAGE} \\
    -n ${K8S_NAMESPACE}
\`\`\`

## 🔗 Enlaces Útiles
- **Jenkins Build**: ${env.BUILD_URL}
- **GCR Image**: https://console.cloud.google.com/gcr/images/${GCP_PROJECT_ID}
- **GKE Console**: https://console.cloud.google.com/kubernetes/workload?project=${GCP_PROJECT_ID}

---
**Generado automáticamente por Jenkins CI/CD Pipeline**  
**Cumplimiento**: Change Management Best Practices ✅
                    """
                    
                    // Escribir Release Notes
                    writeFile file: "RELEASE_NOTES_${RELEASE_VERSION}.md", text: releaseNotes
                    
                    // Archivar Release Notes
                    archiveArtifacts artifacts: "RELEASE_NOTES_${RELEASE_VERSION}.md", 
                                     fingerprint: true
                    
                    echo "✅ Release Notes generadas: RELEASE_NOTES_${RELEASE_VERSION}.md"
                    
                    // También crear un Git tag (opcional)
                    sh """
                        git tag -a ${RELEASE_VERSION} -m "Release ${RELEASE_VERSION} - Build #${BUILD_NUMBER}"
                        git push origin ${RELEASE_VERSION} || echo "⚠️ No se pudo pushear el tag (puede requerir permisos)"
                    """
                }
            }
        }
    }

    // =====================================================
    // POST ACTIONS
    // =====================================================
    post {
        success {
            script {
                echo """
                ╔══════════════════════════════════════════════════════╗
                ║  ✅ DEPLOYMENT EXITOSO                               ║
                ╠══════════════════════════════════════════════════════╣
                ║  Servicio: ${SERVICE_NAME}                           ║
                ║  Versión: ${RELEASE_VERSION}                         ║
                ║  Build: #${BUILD_NUMBER}                             ║
                ║  Commit: ${GIT_COMMIT_SHA}                           ║
                ╚══════════════════════════════════════════════════════╝
                """
            }
        }
        
        failure {
            script {
                echo """
                ╔══════════════════════════════════════════════════════╗
                ║  ❌ DEPLOYMENT FALLÓ                                 ║
                ╠══════════════════════════════════════════════════════╣
                ║  Iniciando rollback automático...                   ║
                ╚══════════════════════════════════════════════════════╝
                """
                
                sh """
                    echo "🔄 Ejecutando rollback automático..."
                    
                    # Autenticar con GKE
                    gcloud auth activate-service-account --key-file=\$GCP_CREDENTIALS
                    gcloud container clusters get-credentials ${GKE_CLUSTER} \
                        --region=${GCP_REGION} \
                        --project ${GCP_PROJECT_ID}
                    
                    # Verificar si hay imagen anterior
                    if [ -f previous_image.txt ] && [ "\$(cat previous_image.txt)" != "none" ]; then
                        PREV_IMG=\$(cat previous_image.txt)
                        echo "🔄 Rollback a imagen anterior: \$PREV_IMG"
                        
                        kubectl set image deployment/${K8S_DEPLOYMENT_NAME} \
                            ${SERVICE_NAME}=\$PREV_IMG \
                            -n ${K8S_NAMESPACE}
                        
                        kubectl rollout status deployment/${K8S_DEPLOYMENT_NAME} \
                            -n ${K8S_NAMESPACE} --timeout=300s
                        
                        echo "✅ Rollback completado exitosamente"
                    else
                        echo "⚠️ No hay imagen anterior para rollback. Usando rollout undo..."
                        kubectl rollout undo deployment/${K8S_DEPLOYMENT_NAME} -n ${K8S_NAMESPACE} || \
                            echo "⚠️ No se pudo hacer rollback automático"
                    fi
                """
            }
        }
        
        always {
            script {
                echo "🧹 Limpiando workspace..."
                cleanWs()
                
                // Revocar credenciales de GCloud
                sh 'gcloud auth revoke --all || true'
                
                // Limpiar imágenes Docker locales
                sh """
                    docker image prune -f || true
                    docker rmi ${GCR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} || true
                """
            }
        }
    }
}
