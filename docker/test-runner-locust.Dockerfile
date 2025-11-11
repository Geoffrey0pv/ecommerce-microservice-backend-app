# Imagen optimizada para Performance tests con Locust
FROM locustio/locust:2.17.0

LABEL maintainer="ecommerce-devops"
LABEL description="Custom Locust image with pre-installed dependencies"

USER root

# Instalar dependencias del sistema
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    jq \
    git \
    && rm -rf /var/lib/apt/lists/*

# Pre-instalar todas las dependencias Python del proyecto
# Esto se cachea en la imagen y acelera muchísimo los tests
COPY tests/performance/requirements.txt /tmp/requirements.txt

RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r /tmp/requirements.txt && \
    rm /tmp/requirements.txt

# Crear directorio de trabajo
RUN mkdir -p /workspace /results && \
    chown -R locust:locust /workspace /results

# Volver a usuario locust (no root por seguridad)
USER locust

# Directorio de trabajo
WORKDIR /workspace

# Verificar instalación
RUN locust --version && \
    python --version && \
    pip list | grep -E "(locust|faker|numpy|pandas)"

# El comando se sobreescribirá en el podTemplate
CMD ["/bin/bash"]
