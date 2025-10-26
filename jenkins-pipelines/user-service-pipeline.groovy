// jenkins-pipelines/user-service-dev-pipeline.groovy
pipeline {
    agent any
    environment {
        IMAGE_NAME = "user-service"
        GCR_REGISTRY = "us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices"
        SERVICE_DIR = "user-service"
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