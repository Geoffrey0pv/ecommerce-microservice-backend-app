pipeline {
    agent any

    environment {
        IMAGE_NAME = "shipping-service"
        DOCKER_REGISTRY_USER = "geoffrey0pv"
        SERVICE_DIR = "shipping-service"
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
                dir("${SERVICE_DIR}") {
                    script {
                        def shortCommit = env.GIT_COMMIT.substring(0,7)
                        def imageTag = "latest-${env.BRANCH_NAME}"
                        def fullImageName = "${DOCKER_REGISTRY_USER}/${IMAGE_NAME}"
                        
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
                    docker.withRegistry('', 'dockerhub-credentials') {
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
