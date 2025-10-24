# Configuración de Jenkins para Google Cloud Platform

## 1. Configuración de Credenciales en Jenkins

### Service Account Key
1. **Crear credencial en Jenkins:**
   - Ir a "Manage Jenkins" → "Manage Credentials"
   - Seleccionar el dominio apropiado (Global)
   - Hacer clic en "Add Credentials"
   - **Kind:** Secret file
   - **File:** Subir tu archivo JSON de service account de GCP
   - **ID:** `gcp-service-account-key`
   - **Description:** "GCP Service Account Key for Container Registry"

### Configuración del Service Account en GCP
```bash
# 1. Crear service account en GCP
gcloud iam service-accounts create jenkins-gcr-pusher \
    --description="Service account for Jenkins to push to GCR" \
    --display-name="Jenkins GCR Pusher"

# 2. Asignar roles necesarios
gcloud projects add-iam-policy-binding YOUR-PROJECT-ID \
    --member="serviceAccount:jenkins-gcr-pusher@YOUR-PROJECT-ID.iam.gserviceaccount.com" \
    --role="roles/storage.admin"

# 3. Crear y descargar key
gcloud iam service-accounts keys create jenkins-gcr-key.json \
    --iam-account=jenkins-gcr-pusher@YOUR-PROJECT-ID.iam.gserviceaccount.com
```

## 2. Instalación de Google Cloud SDK en Jenkins

### En Jenkins Master/Agents
```bash
# Descargar e instalar Google Cloud SDK
curl https://sdk.cloud.google.com | bash
exec -l $SHELL

# O usando package manager (Ubuntu/Debian)
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -
sudo apt-get update && sudo apt-get install google-cloud-sdk
```

### Configuración en Jenkins Pipeline Global Tool Configuration
1. **Ir a:** "Manage Jenkins" → "Global Tool Configuration"
2. **Agregar Google Cloud SDK:**
   - Name: `gcloud`
   - Installation directory: `/usr/lib/google-cloud-sdk` (o donde esté instalado)

## 3. Variables de Entorno en Jenkins

### Variables Globales (Manage Jenkins → Configure System)
```properties
GCP_PROJECT_ID=your-gcp-project-id
GCP_REGION=us-central1
GCR_REGISTRY=gcr.io
```

## 4. Configuración de Docker en Jenkins

### Asegurar que Docker esté configurado
```bash
# Verificar que Jenkins user puede usar Docker
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins

# Verificar instalación
docker --version
gcloud --version
```

## 5. Pipeline de Prueba

### Crear pipeline de prueba para verificar configuración:
```groovy
pipeline {
    agent any
    
    environment {
        GCP_PROJECT_ID = "your-project-id"
        GCR_REGISTRY = "gcr.io"
    }
    
    stages {
        stage('Test GCP Authentication') {
            steps {
                script {
                    withCredentials([file(credentialsId: 'gcp-service-account-key', variable: 'GCP_KEY_FILE')]) {
                        sh '''
                            gcloud auth activate-service-account --key-file=$GCP_KEY_FILE
                            gcloud config set project ${GCP_PROJECT_ID}
                            gcloud auth configure-docker ${GCR_REGISTRY} --quiet
                            
                            # Verificar autenticación
                            gcloud auth list
                            gcloud config get-value project
                        '''
                    }
                }
            }
        }
        
        stage('Test Docker Build and Push') {
            steps {
                script {
                    sh '''
                        # Crear Dockerfile de prueba
                        echo "FROM alpine:latest" > Dockerfile
                        echo "RUN echo 'Test image'" >> Dockerfile
                        
                        # Build y push de prueba
                        docker build -t ${GCR_REGISTRY}/${GCP_PROJECT_ID}/test-image:latest .
                        docker push ${GCR_REGISTRY}/${GCP_PROJECT_ID}/test-image:latest
                        
                        # Limpiar
                        docker rmi ${GCR_REGISTRY}/${GCP_PROJECT_ID}/test-image:latest
                        rm Dockerfile
                    '''
                }
            }
        }
    }
}
```

## 6. Troubleshooting

### Problemas Comunes

#### 1. Error de permisos Docker
```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

#### 2. Error de autenticación GCP
```bash
# Verificar service account key
gcloud auth activate-service-account --key-file=path/to/key.json
gcloud auth list
```

#### 3. Error de push a GCR
```bash
# Verificar configuración de Docker
gcloud auth configure-docker gcr.io --quiet
docker push gcr.io/PROJECT-ID/IMAGE:TAG
```

#### 4. Variables de entorno no disponibles
- Verificar configuración en "Manage Jenkins" → "Configure System"
- Reiniciar Jenkins después de cambios de configuración

## 7. Verificación Final

### Checklist de configuración:
- [ ] Service account creado en GCP con permisos correctos
- [ ] Credencial `gcp-service-account-key` configurada en Jenkins
- [ ] Google Cloud SDK instalado en Jenkins
- [ ] Docker configurado para Jenkins user
- [ ] Variables de entorno configuradas
- [ ] Pipeline de prueba ejecutada exitosamente

### Comandos de verificación:
```bash
# En Jenkins agent/master
gcloud --version
docker --version
gcloud auth list
gcloud config get-value project
```