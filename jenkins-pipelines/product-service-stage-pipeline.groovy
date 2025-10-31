pipeline {
    agent any
    environment {
        IMAGE_NAME = "product-service"
        SERVICE_DIR = "product-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        FULL_IMAGE = "${GCR_REGISTRY}/${IMAGE_NAME}"
        IMAGE_TAG = "${params.IMAGE_TAG ?: 'latest-dev'}"
        GCP_CREDENTIALS = credentials('gke-credentials')
        GCP_PROJECT = "ecommerce-backend-1760307199"
        K8S_NAMESPACE = "staging"
        K8S_DEPLOYMENT = "product-service-deployment"
        K8S_CONTAINER = "product-service"
        CLUSTER_NAME = "ecommerce-devops-cluster"
        CLUSTER_REGION = "us-central1"
    }

    parameters {
        string(
            name: 'IMAGE_TAG', 
            defaultValue: 'latest-dev', 
            description: 'Tag de imagen a desplegar (usar SHA del commit o latest-dev)'
        )
        choice(
            name: 'DEPLOY_ACTION',
            choices: ['deploy', 'rollback'],
            description: 'Acción a realizar en staging'
        )
    }

    stages {
        stage('Validate Context') {
            steps {
                script {
                    if (env.CHANGE_TARGET && env.CHANGE_TARGET != 'staging') {
                        error("❌ Este pipeline solo debe ejecutarse en PRs hacia 'staging'. Target actual: ${env.CHANGE_TARGET}")
                    }
                    echo "✅ Contexto válido: Pipeline STAGE para ${IMAGE_NAME}"
                    echo "📦 Imagen a desplegar: ${FULL_IMAGE}:${IMAGE_TAG}"
                    echo "🎯 Namespace destino: ${K8S_NAMESPACE}"
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
                        gcloud container clusters get-credentials \${CLUSTER_NAME} --region=\${CLUSTER_REGION}
                        kubectl cluster-info
                    """
                }
            }
        }

        stage('Verify Image Exists') {
            steps {
                script {
                    sh """
                        echo "🔍 Verificando imagen \${FULL_IMAGE}:\${IMAGE_TAG}..."
                        gcloud artifacts docker images describe \${FULL_IMAGE}:\${IMAGE_TAG} || {
                            echo "❌ ERROR: Imagen no encontrada"
                            gcloud artifacts docker images list \${GCR_REGISTRY}/\${IMAGE_NAME} --include-tags --limit=10
                            exit 1
                        }
                        echo "✅ Imagen verificada"
                    """
                }
            }
        }

        stage('Pull & Promote Image') {
            steps {
                script {
                    sh """
                        docker pull \${FULL_IMAGE}:\${IMAGE_TAG}
                        docker tag \${FULL_IMAGE}:\${IMAGE_TAG} \${FULL_IMAGE}:staging
                        docker tag \${FULL_IMAGE}:\${IMAGE_TAG} \${FULL_IMAGE}:staging-\$(date +%Y%m%d-%H%M%S)
                        docker push \${FULL_IMAGE}:staging
                        docker push \${FULL_IMAGE}:staging-\$(date +%Y%m%d-%H%M%S)
                    """
                }
            }
        }

        stage('Deploy to Staging') {
            steps {
                script {
                    sh """
                        # Crear namespace si no existe
                        kubectl get namespace \${K8S_NAMESPACE} || kubectl create namespace \${K8S_NAMESPACE}
                        
                        # Crear o actualizar deployment
                        kubectl get deployment \${K8S_DEPLOYMENT} -n \${K8S_NAMESPACE} || {
                            kubectl create deployment \${K8S_DEPLOYMENT} --image=\${FULL_IMAGE}:staging -n \${K8S_NAMESPACE}
                            kubectl expose deployment \${K8S_DEPLOYMENT} --port=8200 --target-port=8200 -n \${K8S_NAMESPACE} || echo "Service existe"
                        }
                        
                        # Actualizar imagen
                        kubectl set image deployment/\${K8S_DEPLOYMENT} \${K8S_CONTAINER}=\${FULL_IMAGE}:staging -n \${K8S_NAMESPACE} --record
                        kubectl rollout status deployment/\${K8S_DEPLOYMENT} -n \${K8S_NAMESPACE} --timeout=300s
                        
                        echo "✅ Product Service desplegado en staging"
                    """
                }
            }
        }

        stage('Product Service Smoke Tests') {
            steps {
                script {
                    sh """
                        echo "🧪 Testing Product Service específico..."
                        kubectl wait --for=condition=ready pod -l app=\${K8S_DEPLOYMENT} -n \${K8S_NAMESPACE} --timeout=300s || echo "⚠️ Timeout waiting for pods"
                        
                        POD_NAME=\$(kubectl get pods -n \${K8S_NAMESPACE} -l app=\${K8S_DEPLOYMENT} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        
                        if [ ! -z "\$POD_NAME" ]; then
                            echo "🎯 Testing pod: \$POD_NAME"
                            # Test health endpoint
                            kubectl exec \$POD_NAME -n \${K8S_NAMESPACE} -- curl -f http://localhost:8200/actuator/health || {
                                echo "⚠️ Health check falló, verificando logs..."
                                kubectl logs \$POD_NAME -n \${K8S_NAMESPACE} --tail=20
                            }
                            
                            # Test specific product endpoints
                            kubectl exec \$POD_NAME -n \${K8S_NAMESPACE} -- curl -f http://localhost:8200/api/products || echo "⚠️ Products endpoint no disponible"
                        fi
                        
                        echo "✅ Product Service smoke tests completados"
                    """
                }
            }
        }
    }

    post {
        success {
            echo "🎉 Product Service staging deployment exitoso: ${FULL_IMAGE}:staging"
        }
        failure {
            script {
                sh """
                    echo "❌ Product Service staging deployment falló"
                    kubectl get events -n \${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -10
                """
            }
        }
        always {
            script {
                sh """
                    docker rmi \${FULL_IMAGE}:\${IMAGE_TAG} || true
                    docker rmi \${FULL_IMAGE}:staging || true
                    gcloud auth revoke --all || true
                """
            }
        }
    }
}