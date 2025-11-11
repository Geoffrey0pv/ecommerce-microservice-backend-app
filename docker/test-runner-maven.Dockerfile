# Imagen optimizada para E2E tests con Maven
# Basada en Eclipse Temurin (reemplazo oficial de AdoptOpenJDK)
FROM maven:3.8.6-eclipse-temurin-17

LABEL maintainer="ecommerce-devops"
LABEL description="Custom Maven image for E2E tests with pre-cached dependencies"

# Variables de entorno
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
ENV MAVEN_CONFIG=/root/.m2

# Instalar utilidades adicionales
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    jq \
    git \
    && rm -rf /var/lib/apt/lists/*

# Crear directorios
RUN mkdir -p /workspace /root/.m2/repository

# Pre-descargar dependencias comunes de Maven (esto acelera mucho los builds)
# Copiamos el pom.xml del proyecto para cachear dependencias
COPY tests/e2e/pom.xml /tmp/pom.xml

# Descargar todas las dependencias del proyecto (se cachean en la capa de Docker)
RUN cd /tmp && \
    mvn dependency:go-offline -B && \
    mvn dependency:resolve-plugins -B && \
    rm -rf /tmp/target /tmp/pom.xml

# Configurar settings.xml de Maven para usar mirror más rápido (opcional)
RUN mkdir -p /root/.m2 && cat > /root/.m2/settings.xml <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <localRepository>/root/.m2/repository</localRepository>
  <interactiveMode>false</interactiveMode>
  <offline>false</offline>
  
  <!-- Mirror de Google (más rápido en GCP) -->
  <mirrors>
    <mirror>
      <id>google-maven-central</id>
      <name>Google Maven Central</name>
      <url>https://maven-central.storage-download.googleapis.com/maven2/</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF

# Directorio de trabajo por defecto
WORKDIR /workspace

# Verificar instalación
RUN mvn --version && \
    java -version

# El comando se sobreescribirá en el podTemplate
CMD ["/bin/bash"]
