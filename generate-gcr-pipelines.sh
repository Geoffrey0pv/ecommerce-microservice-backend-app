#!/bin/bash

# Script para generar pipelines GCR limpias basadas en el template
# Uso: ./generate-gcr-pipelines.sh

echo "Generando pipelines GCR limpias basadas en template..."

# Variables de configuración - CAMBIAR ESTOS VALORES
GCP_PROJECT_ID="your-gcp-project-id"  # CAMBIAR por tu project ID
GCP_REGION="us-central1"              # CAMBIAR por tu región  
GCR_REGISTRY="gcr.io"                 # o us.gcr.io, eu.gcr.io, asia.gcr.io

# Lista de servicios
SERVICES=(
    "user-service"
    "product-service" 
    "order-service"
    "payment-service"
    "shipping-service"
    "favourite-service"
)

# Función para generar pipeline para un servicio
generate_pipeline() {
    local service=$1
    local pipeline_file="jenkins-pipelines/${service}-pipeline-gcr-new.groovy"
    
    echo "🔄 Generando pipeline para: $service"
    
    cat > "$pipeline_file" << EOF
pipeline {
    agent any

    environment {
        IMAGE_NAME = "${service}"
        GCP_PROJECT_ID = "${GCP_PROJECT_ID}"
        GCP_REGION = "${GCP_REGION}"
        GCR_REGISTRY = "${GCR_REGISTRY}"
        SERVICE_DIR = "${service}"
        SPRING_PROFILES_ACTIVE = "dev"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Procesando cambios en \${IMAGE_NAME}"
            }
        }

        stage('Compile') {
            steps {
                script {
                    // Usar contenedor Maven para Java 11 con Spring Boot
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        sh '''
                            # Primero instalar el parent POM en el repositorio local Maven
                            mvn clean install -N -Dspring.profiles.active=dev
                            
                            # Ahora compilar el servicio específico
                            cd \${SERVICE_DIR}
                            mvn clean compile -Dspring.profiles.active=dev
                            ls -la target/
                        '''
                    }
                }
            }
        }

        stage('Unit Testing') {
            steps {
                script {
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        sh "mvn test -Dspring.profiles.active=dev -pl \${SERVICE_DIR} -am"
                    }
                }
            }
        }

        stage('Package') {
            steps {
                script {
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        sh "mvn package -DskipTests=true -Dspring.profiles.active=dev -pl \${SERVICE_DIR} -am"
                        sh "ls -la \${SERVICE_DIR}/target/"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("\${SERVICE_DIR}") {
                    script {
                        def shortCommit = env.GIT_COMMIT.substring(0,7)
                        def imageTag = "latest-\${env.BRANCH_NAME}"
                        def fullImageName = "\${GCR_REGISTRY}/\${GCP_PROJECT_ID}/\${IMAGE_NAME}"
                        
                        echo "Construyendo imagen: \${fullImageName}:\${imageTag}"
                        
                        // Construir imagen directamente con el nombre completo
                        def customImage = docker.build("\${fullImageName}:\${shortCommit}", ".")
                        
                        // Tagear con la versión de la rama
                        customImage.tag(imageTag)
                        
                        // Guardar variables para el siguiente stage
                        env.FINAL_IMAGE_TAG = imageTag
                        env.SHORT_COMMIT = shortCommit
                        env.FULL_IMAGE_NAME = fullImageName
                    }
                }
            }
        }

        stage('Authenticate with GCP') {
            steps {
                script {
                    // Autenticación con Google Cloud usando service account
                    withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_KEY_FILE')]) {
                        sh '''
                            # Activar service account
                            gcloud auth activate-service-account --key-file=\$GCP_KEY_FILE
                            
                            # Configurar proyecto
                            gcloud config set project \${GCP_PROJECT_ID}
                            
                            # Configurar Docker para usar gcloud como helper de credenciales
                            gcloud auth configure-docker \${GCR_REGISTRY} --quiet
                            
                            # Verificar autenticación
                            gcloud auth list
                            echo "Proyecto configurado: \$(gcloud config get-value project)"
                        '''
                    }
                }
            }
        }

        stage('Push Docker Image to GCR') {
            steps {
                script {
                    // Push a Google Container Registry
                    sh '''
                        # Push imagen con commit hash
                        docker push \${FULL_IMAGE_NAME}:\${SHORT_COMMIT}
                        
                        # Push imagen con tag de rama
                        docker push \${FULL_IMAGE_NAME}:\${FINAL_IMAGE_TAG}
                        
                        echo "✅ Imagen publicada: \${FULL_IMAGE_NAME}:\${FINAL_IMAGE_TAG}"
                        echo "✅ Imagen publicada: \${FULL_IMAGE_NAME}:\${SHORT_COMMIT}"
                        
                        # Verificar que la imagen esté en GCR
                        gcloud container images list --repository=\${GCR_REGISTRY}/\${GCP_PROJECT_ID}
                    '''
                }
            }
        }
    }

    post {
        success {
            script {
                if (env.BRANCH_NAME == 'master') {
                    echo "Disparando el pipeline de integracion..."
                    
                    try {
                        build job: 'ecommerce-integration/master',
                              parameters: [
                                  string(name: 'TRIGGERING_SERVICE', value: "\${IMAGE_NAME}"),
                                  string(name: 'IMAGE_TAG', value: "\${env.FINAL_IMAGE_TAG}")
                              ],
                              wait: false
                        echo "Pipeline de integracion disparado exitosamente"
                    } catch (Exception e) {
                        echo "Error al disparar pipeline de integracion: \${e.getMessage()}"
                    }
                } else {
                    echo "Branch '\${env.BRANCH_NAME}' - No se dispara integracion (solo master)"
                }
            }
        }
        always {
            // Limpiar imágenes locales para ahorrar espacio
            sh '''
                docker rmi \${FULL_IMAGE_NAME}:\${SHORT_COMMIT} || true
                docker rmi \${FULL_IMAGE_NAME}:\${FINAL_IMAGE_TAG} || true
                echo "Imágenes locales limpiadas"
            '''
            cleanWs()
        }
        failure {
            echo "❌ Build falló para \${IMAGE_NAME}"
            // Notificar fallo si es necesario
        }
    }
}
EOF

    echo "   ✅ Pipeline generada: $pipeline_file"
}

# Función para generar stage pipeline
generate_stage_pipeline() {
    local service=$1
    local pipeline_file="jenkins-pipelines/${service}-stage-pipeline-gcr-new.groovy"
    
    echo "🔄 Generando stage pipeline para: $service"
    
    cat > "$pipeline_file" << EOF
pipeline {
    agent any

    environment {
        IMAGE_NAME = "${service}"
        GCP_PROJECT_ID = "${GCP_PROJECT_ID}"
        GCP_REGION = "${GCP_REGION}"
        GCR_REGISTRY = "${GCR_REGISTRY}"
        SERVICE_DIR = "${service}"
        SPRING_PROFILES_ACTIVE = "staging"
        NAMESPACE = "staging"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Desplegando \${IMAGE_NAME} en ambiente staging"
            }
        }

        stage('Authenticate with GCP') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_KEY_FILE')]) {
                        sh '''
                            gcloud auth activate-service-account --key-file=\$GCP_KEY_FILE
                            gcloud config set project \${GCP_PROJECT_ID}
                            gcloud auth configure-docker \${GCR_REGISTRY} --quiet
                        '''
                    }
                }
            }
        }

        stage('Deploy to Staging') {
            steps {
                script {
                    def imageTag = params.IMAGE_TAG ?: "latest-master"
                    def fullImageName = "\${GCR_REGISTRY}/\${GCP_PROJECT_ID}/\${IMAGE_NAME}:\${imageTag}"
                    
                    echo "Desplegando imagen: \${fullImageName}"
                    
                    sh '''
                        # Verificar que la imagen existe en GCR
                        gcloud container images describe \${GCR_REGISTRY}/\${GCP_PROJECT_ID}/\${IMAGE_NAME}:\${imageTag}
                        
                        # Aplicar manifiestos de Kubernetes para staging
                        kubectl apply -f manifests/\${SERVICE_DIR}/ -n \${NAMESPACE}
                        
                        # Actualizar la imagen en el deployment
                        kubectl set image deployment/\${IMAGE_NAME} \${IMAGE_NAME}=\${GCR_REGISTRY}/\${GCP_PROJECT_ID}/\${IMAGE_NAME}:\${imageTag} -n \${NAMESPACE}
                        
                        # Esperar que el rollout termine
                        kubectl rollout status deployment/\${IMAGE_NAME} -n \${NAMESPACE} --timeout=600s
                        
                        # Verificar que el pod esté corriendo
                        kubectl get pods -l app=\${IMAGE_NAME} -n \${NAMESPACE}
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    sh '''
                        # Esperar un poco para que el servicio esté listo
                        sleep 30
                        
                        # Obtener la URL del servicio
                        SERVICE_URL=\$(kubectl get service \${IMAGE_NAME} -n \${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
                        PORT=\$(kubectl get service \${IMAGE_NAME} -n \${NAMESPACE} -o jsonpath='{.spec.ports[0].port}')
                        
                        if [ ! -z "\$SERVICE_URL" ]; then
                            echo "Verificando health check en: http://\$SERVICE_URL:\$PORT/actuator/health"
                            curl -f "http://\$SERVICE_URL:\$PORT/actuator/health" || exit 1
                        else
                            echo "⚠️ No se pudo obtener IP externa, verificando pods internamente"
                            kubectl get pods -l app=\${IMAGE_NAME} -n \${NAMESPACE}
                        fi
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "✅ Despliegue exitoso de \${IMAGE_NAME} en staging"
        }
        failure {
            echo "❌ Fallo en despliegue de \${IMAGE_NAME} en staging"
            sh '''
                echo "Logs del deployment:"
                kubectl describe deployment \${IMAGE_NAME} -n \${NAMESPACE}
                echo "Logs de los pods:"
                kubectl logs -l app=\${IMAGE_NAME} -n \${NAMESPACE} --tail=50
            '''
        }
        always {
            cleanWs()
        }
    }
}
EOF

    echo "   ✅ Stage pipeline generada: $pipeline_file"
}

# Verificar que estamos en el directorio correcto
if [ ! -d "jenkins-pipelines" ]; then
    echo "❌ Error: Directorio jenkins-pipelines no encontrado"
    echo "   Ejecuta este script desde el directorio raíz del proyecto"
    exit 1
fi

echo ""
echo "🚀 Generando pipelines GCR limpias..."
echo "📋 Configuración:"
echo "   - GCP Project ID: $GCP_PROJECT_ID"
echo "   - GCP Region: $GCP_REGION" 
echo "   - GCR Registry: $GCR_REGISTRY"
echo ""

# Generar pipelines principales
for service in "${SERVICES[@]}"; do
    generate_pipeline "$service"
done

echo ""
echo "🔄 Generando stage pipelines..."

# Generar stage pipelines
for service in "${SERVICES[@]}"; do
    generate_stage_pipeline "$service"
done

echo ""
echo "🎉 Generación completada!"
echo ""
echo "📝 Archivos generados:"
find jenkins-pipelines -name "*-gcr-new.groovy" -exec echo "   {}" \;
echo ""
echo "📋 Para usar las nuevas pipelines:"
echo "1. Revisar y ajustar GCP_PROJECT_ID en cada archivo"
echo "2. Configurar credenciales 'gcp-service-account-key' en Jenkins"
echo "3. Reemplazar las pipelines originales con las nuevas versiones"
echo "4. Actualizar manifiestos de Kubernetes para usar imágenes de GCR"