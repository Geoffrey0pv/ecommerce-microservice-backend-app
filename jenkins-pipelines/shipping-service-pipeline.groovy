// jenkins-pipelines/shipping-service-dev-pipeline.groovy
pipeline {
    agent any
    environment {
        IMAGE_NAME = "shipping-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        SERVICE_DIR = "shipping-service"
        SPRING_PROFILES_ACTIVE = "dev"
        // Credencial de GKE (archivo de clave de servicio JSON)
        GCP_CREDENTIALS = credentials('gke-credentials') 
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // Guarda el Git Commit SHA para usarlo como tag inmutable
                    env.GIT_COMMIT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}"
                    env.IMAGE_TAG = "${env.GIT_COMMIT_SHA}"
                }
                echo "Procesando Build para ${IMAGE_NAME} commit ${IMAGE_TAG}"
            }
        }

        // --- PRUEBAS ESTÁTICAS (SOBRE EL CÓDIGO) ---
        stage('Compile') {
            steps {
                script {
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        sh '''
                            mvn clean install -N -Dspring.profiles.active=dev
                            cd ${SERVICE_DIR}
                            mvn clean compile -Dspring.profiles.active=dev
                        '''
                    }
                }
            }
        }

        stage('Unit & Integration Tests (Maven)') {
            steps {
                script {
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        // Ejecuta unitarias Y de integración (las que corren con Maven)
                        sh "mvn verify -Dspring.profiles.active=dev -pl ${SERVICE_DIR} -am"
                    }
                }
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
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
                script {
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        sh "mvn package -DskipTests=true -Dspring.profiles.active=dev -pl ${SERVICE_DIR} -am"
                    }
                }
            }
        }
        
        // --- CONSTRUCCIÓN Y ESCANEO (SOBRE LA IMAGEN) ---
        stage('Build Docker Image') {
            steps {
                echo "Construyendo imagen: ${FULL_IMAGE_NAME}:${IMAGE_TAG}"
                // docker.build requiere el contexto, -f especifica el Dockerfile
                def customImage = docker.build("${FULL_IMAGE_NAME}:${IMAGE_TAG}", "-f ${SERVICE_DIR}/Dockerfile .")
                customImage.tag("latest-dev")
            }
        }

        stage('Security Scan (Trivy)') {
            steps {
                script {
                    echo "Escaneando imagen ${FULL_IMAGE_NAME}:${IMAGE_TAG} para vulnerabilidades..."
                    sh """
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                            aquasec/trivy:latest image \
                            --severity HIGH,CRITICAL \
                            --exit-code 1 \
                            ${FULL_IMAGE_NAME}:${IMAGE_TAG}
                    """
                    // --exit-code 1 hace que el pipeline falle si encuentra vulnerabilidades
                }
            }
        }

        stage('Authenticate & Push Docker Image') {
            steps {
                script {
                    // Autenticarse en GCR/Artifact Registry
                    sh 'gcloud auth activate-service-account --key-file=$GCP_CREDENTIALS'
                    sh 'gcloud auth configure-docker us-central1-docker.pkg.dev --quiet'

                    // Subir la imagen
                    docker.image("${env.FULL_IMAGE_NAME}:${env.IMAGE_TAG}").push()
                    docker.image("${env.FULL_IMAGE_NAME}:latest-dev").push()
                    
                    echo "Imagen publicada: ${env.FULL_IMAGE_NAME}:${env.IMAGE_TAG}"
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline de Build [${IMAGE_NAME}] completado exitosamente."
        }
        always {
            cleanWs()
            // Limpiar credenciales de GCloud
            sh 'gcloud auth revoke --all || true'
        }
        failure {
            echo "Build falló para ${IMAGE_NAME}"
        }
    }
}

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Procesando cambios en ${IMAGE_NAME}"
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
                            cd ${SERVICE_DIR}
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
                        sh "mvn test -Dspring.profiles.active=dev -pl ${SERVICE_DIR} -am"
                    }
                }
            }
        }

        stage('Package') {
            steps {
                script {
                    docker.image('maven:3.8.4-openjdk-11').inside {
                        sh "mvn package -DskipTests=true -Dspring.profiles.active=dev -pl ${SERVICE_DIR} -am"
                        sh "ls -la ${SERVICE_DIR}/target/"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("${SERVICE_DIR}") {
                    script {
                        def shortCommit = env.GIT_COMMIT.substring(0,7)
                        def imageTag = "latest-${env.BRANCH_NAME}"
                        def fullImageName = "${GCR_REGISTRY}/${GCP_PROJECT_ID}/${IMAGE_NAME}"
        GCP_REGION = "us-central1"
                        
                        echo "Construyendo imagen: ${fullImageName}:${imageTag}"
                        
                        // Construir imagen directamente con el nombre completo
                        def customImage = docker.build("${fullImageName}:${shortCommit}", ".")
                        
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

        stage('Push Docker Image') {
            steps {
                script {
                    // GCR Authentication handled in separate stage {
                        // Push ambos tags usando las variables de entorno
                        docker.image("${env.FULL_IMAGE_NAME}:${env.SHORT_COMMIT}").push()
                        docker.image("${env.FULL_IMAGE_NAME}:${env.FINAL_IMAGE_TAG}").push()
                        
                        echo "Imagen publicada: ${env.FULL_IMAGE_NAME}:${env.FINAL_IMAGE_TAG}"
                    }
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
                                  string(name: 'TRIGGERING_SERVICE', value: "${IMAGE_NAME}"),
                                  string(name: 'IMAGE_TAG', value: "${env.FINAL_IMAGE_TAG}")
                              ],
                              wait: false
                        echo "Pipeline de integracion disparado exitosamente"
                    } catch (Exception e) {
                        echo "Error al disparar pipeline de integracion: ${e.getMessage()}"
                    }
                } else {
                    echo "Branch '${env.BRANCH_NAME}' - No se dispara integracion (solo master)"
                }
            }
        }
        always {
            cleanWs()
        }
        failure {
            echo "Build fallo para ${IMAGE_NAME}"
        }
    }
}
