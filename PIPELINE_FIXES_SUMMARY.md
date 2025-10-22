# Fix Completo para Error de Pipeline Jenkins

## Problema Identificado

**Error Root Cause:**
- Las pipelines Jenkins fallaban en etapas de `Compile` y `Build Docker Image`
- Mensaje de error: `Non-resolvable parent POM` y `failed to calculate checksum`

## Problemas Específicos

### 1. Maven Parent POM Issue
- Los POMs de servicios (`user-service`, `order-service`, etc.) no tenían configurado `<relativePath>../pom.xml</relativePath>`
- Maven buscaba el parent POM en el repositorio central en lugar del proyecto local
- Jenkins ejecutaba Maven desde subdirectorios de servicios

### 2. Dockerfile Path Issues
- Los Dockerfiles tenían rutas incorrectas como `COPY user-service/ .` y `ADD user-service/target/...`
- Jenkins ejecuta `docker build` desde el directorio del servicio, no desde la raíz

## Soluciones Implementadas

### ✅ 1. Corrección de POMs de Servicios
Agregado `<relativePath>../pom.xml</relativePath>` a todos los servicios:
- `user-service/pom.xml`
- `order-service/pom.xml`
- `payment-service/pom.xml`
- `product-service/pom.xml`
- `shipping-service/pom.xml`
- `favourite-service/pom.xml`

### ✅ 2. Actualización de Pipelines Jenkins
Modificadas todas las pipelines para instalar parent POM primero:

**Antes:**
```groovy
stage('Compile') {
    steps {
        dir("${SERVICE_DIR}") {
            sh 'mvn clean compile -Dspring.profiles.active=dev'
        }
    }
}
```

**Después:**
```groovy
stage('Compile') {
    steps {
        script {
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
```

### ✅ 3. Corrección de Dockerfiles
Arregladas las rutas en todos los Dockerfiles:

**Antes:**
```dockerfile
ENV SPRING_PROFILES_ACTIVE dev
COPY user-service/ .
ADD user-service/target/user-service-v${PROJECT_VERSION}.jar user-service.jar
```

**Después:**
```dockerfile
ENV SPRING_PROFILES_ACTIVE=dev
COPY . .
ADD target/user-service-v${PROJECT_VERSION}.jar user-service.jar
```

## Pipelines Actualizadas

✅ **Pipelines Principales:**
- `user-service-pipeline.groovy`
- `product-service-pipeline.groovy`
- `order-service-pipeline.groovy`
- `payment-service-pipeline.groovy`
- `shipping-service-pipeline.groovy`
- `favourite-service-pipeline.groovy`

✅ **Dockerfiles Corregidos:**
- `user-service/Dockerfile`
- `order-service/Dockerfile`
- `payment-service/Dockerfile`
- `product-service/Dockerfile`
- `shipping-service/Dockerfile`
- `favourite-service/Dockerfile`

## Validación Exitosa

**✅ Tests Locales Pasaron:**
1. `mvn clean install -N` - Parent POM instalado correctamente
2. `mvn package -DskipTests=true -pl user-service -am` - Compilación exitosa
3. `docker build -t test-user-service:latest .` - Imagen Docker construida exitosamente

## Beneficios del Fix

1. **Robustez:** Las pipelines ahora instalan dependencias correctamente
2. **Consistencia:** Mismo patrón aplicado a todos los servicios
3. **Eficiencia:** Uso de comandos Maven optimizados con `-pl` y `-am`
4. **Mantenibilidad:** Estructura clara y reutilizable

## Comandos de Validación

```bash
# Instalar parent POM
mvn clean install -N -Dspring.profiles.active=dev

# Compilar servicio específico
mvn package -DskipTests=true -Dspring.profiles.active=dev -pl user-service -am

# Verificar build Docker
cd user-service && docker build -t test-user-service:latest .
```

¡Todas las pipelines Jenkins de los 6 microservicios ahora deberían funcionar correctamente! 🚀