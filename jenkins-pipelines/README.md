# Jenkins Pipelines para E-commerce Microservices

Este directorio contiene los pipelines de Jenkins para el proyecto de microservicios de e-commerce.

## Microservicios Incluidos

Los siguientes 6 microservicios han sido seleccionados para el taller de DevOps:

1. **user-service** - Gestión de usuarios y autenticación
2. **product-service** - Catálogo de productos  
3. **order-service** - Procesamiento de órdenes
4. **payment-service** - Procesamiento de pagos
5. **shipping-service** - Gestión de envíos
6. **favourite-service** - Lista de favoritos del usuario

## Pipelines Disponibles

### Pipelines Individuales de Microservicios

Cada microservicio tiene su propio pipeline que incluye:

- **Checkout**: Descarga del código fuente
- **Compile**: Compilación con Maven y Java 11
- **Unit Testing**: Ejecución de pruebas unitarias
- **Package**: Empaquetado del JAR
- **Build Docker Image**: Construcción de imagen Docker
- **Push Docker Image**: Subida a Docker Hub

### Pipeline de Integración

El pipeline `ecommerce-integration-pipeline.groovy` se ejecuta automáticamente cuando se hace push a la rama `master` e incluye:

- **Integration Tests**: Pruebas de integración entre servicios
- **E2E Tests**: Pruebas end-to-end de flujos completos
- **Performance Tests**: Pruebas de rendimiento con Locust
- **Deploy to Dev**: Despliegue al ambiente de desarrollo

## Configuración Requerida en Jenkins

### Credenciales Necesarias

1. **dockerhub-credentials**: Credenciales para Docker Hub
   - ID: `dockerhub-credentials`
   - Usuario: `geoffrey0pv`
   - Contraseña: [Token de acceso de Docker Hub]

### Plugins Requeridos

- Docker Pipeline Plugin
- Git Plugin
- Pipeline Plugin
- Parameterized Trigger Plugin

### Configuración de Jobs

1. **Jobs Individuales**: Crear un job por cada microservicio
   - Nombre: `user-service`, `product-service`, etc.
   - Tipo: Pipeline
   - Script Path: `jenkins-pipelines/[service-name]-pipeline.groovy`

2. **Job de Integración**: Crear job para integración
   - Nombre: `ecommerce-integration`
   - Tipo: Pipeline
   - Script Path: `jenkins-pipelines/ecommerce-integration-pipeline.groovy`

## Variables de Entorno

### Variables Globales

- `DOCKER_REGISTRY_USER`: `geoffrey0pv`
- `SPRING_PROFILES_ACTIVE`: `dev` (para ambiente de desarrollo)

### Variables por Pipeline

- `IMAGE_NAME`: Nombre del microservicio
- `SERVICE_DIR`: Directorio del microservicio en el repositorio
- `FINAL_IMAGE_TAG`: Tag final de la imagen Docker
- `SHORT_COMMIT`: Hash corto del commit
- `FULL_IMAGE_NAME`: Nombre completo de la imagen

## Flujo de Trabajo

### Desarrollo (Feature Branches)

1. Desarrollador hace push a una rama de feature
2. Se ejecuta el pipeline individual del microservicio
3. Se construye y publica la imagen Docker
4. **NO** se dispara el pipeline de integración

### Integración (Master Branch)

1. Se hace merge a la rama `master`
2. Se ejecuta el pipeline individual del microservicio
3. Se dispara automáticamente el pipeline de integración
4. Se ejecutan pruebas de integración, E2E y rendimiento
5. Se despliega al ambiente de desarrollo

## Pruebas Locales

Para probar los pipelines localmente antes del despliegue:

### Prerrequisitos

- Docker instalado y ejecutándose
- Maven 3.8.4+ instalado
- Java 11 instalado
- Acceso a Docker Hub (opcional para pruebas locales)

### Comandos de Prueba

```bash
# Compilar un microservicio
cd user-service
mvn clean compile -Dspring.profiles.active=dev

# Ejecutar pruebas unitarias
mvn test -Dspring.profiles.active=dev

# Empaquetar
mvn package -DskipTests=true -Dspring.profiles.active=dev

# Construir imagen Docker
docker build -t geoffrey0pv/user-service:test .

# Verificar imagen
docker images | grep user-service
```

### Probar con Docker Compose

```bash
# Desde el directorio raíz del proyecto
docker-compose -f compose.yml up -d

# Verificar que todos los servicios estén corriendo
docker-compose ps

# Ver logs de un servicio específico
docker-compose logs user-service-container

# Detener todos los servicios
docker-compose down
```

## Estructura de Directorios

```
jenkins-pipelines/
├── README.md
├── user-service-pipeline.groovy
├── product-service-pipeline.groovy
├── order-service-pipeline.groovy
├── payment-service-pipeline.groovy
├── shipping-service-pipeline.groovy
├── favourite-service-pipeline.groovy
└── ecommerce-integration-pipeline.groovy
```

## Próximos Pasos

1. **Configurar Jenkins** con las credenciales y plugins necesarios
2. **Crear los jobs** de Jenkins usando estos pipelines
3. **Probar localmente** cada microservicio
4. **Implementar pruebas unitarias** adicionales
5. **Implementar pruebas de integración** entre servicios
6. **Implementar pruebas E2E** para flujos completos
7. **Implementar pruebas de rendimiento** con Locust
8. **Crear pipelines para staging y production**

## Troubleshooting

### Problemas Comunes

1. **Error de credenciales Docker Hub**
   - Verificar que las credenciales estén configuradas correctamente
   - Usar token de acceso en lugar de contraseña

2. **Error de compilación Maven**
   - Verificar que Java 11 esté disponible
   - Verificar que las dependencias estén disponibles

3. **Error de construcción Docker**
   - Verificar que Dockerfile esté presente en cada microservicio
   - Verificar que Docker esté ejecutándose

4. **Error de push a Docker Hub**
   - Verificar conectividad a internet
   - Verificar permisos de escritura en Docker Hub

### Logs y Debugging

- Los logs de Jenkins están disponibles en la interfaz web
- Los logs de Docker se pueden ver con `docker-compose logs`
- Los logs de Maven se pueden ver en la salida del pipeline
