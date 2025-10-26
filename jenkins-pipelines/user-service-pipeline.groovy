pipeline {
    agent any

    environment {
        IMAGE_NAME = "user-service"
        GCP_PROJECT_ID = "your-gcp-project-id"
        SERVICE_DIR = "user-service"
        SPRING_PROFILES_ACTIVE = "dev"
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
                script {
                    env.FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}"
                    env.IMAGE_TAG = "${env.GIT_COMMIT_SHA}" // Tag inmutable
                    
                    echo "Construyendo imagen: ${FULL_IMAGE_NAME}:${IMAGE_TAG}"
                    
                    // Asegúrate que el contexto ('.') es el directorio raíz del monorepo
                    def customImage = docker.build("${FULL_IMAGE_NAME}:${IMAGE_TAG}", "-f ${SERVICE_DIR}/Dockerfile .")
                    
                    // También etiqueta como 'latest-dev' para saber cuál es la última de develop
                    customImage.tag("latest-dev")
                }
            }
        }
        
        stage('Authenticate & Push Docker Image') {
            steps {
                script {
                    // Usa las credenciales de GKE/GCP que configuraste en Jenkins
                    withCredentials([file(credentialsId: 'gke-credentials', variable: 'GCP_KEY_FILE')]) {
                        sh 'gcloud auth activate-service-account --key-file=$GCP_KEY_FILE'
                        sh 'gcloud auth configure-docker us-central1-docker.pkg.dev --quiet'

                        // Pushear ambos tags
                        docker.image("${env.FULL_IMAGE_NAME}:${env.IMAGE_TAG}").push()
                        docker.image("${env.FULL_IMAGE_NAME}:latest-dev").push()
                        
                        echo "Imagen publicada: ${env.FULL_IMAGE_NAME}:${env.IMAGE_TAG}"
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline de Build [${IMAGE_NAME}] completado exitosamente."
            // Aquí podrías disparar automáticamente el pipeline de Staging
        }
        always {
            cleanWs()
        }
        failure {
            echo "Build falló para ${IMAGE_NAME}"
        }
    }
}
