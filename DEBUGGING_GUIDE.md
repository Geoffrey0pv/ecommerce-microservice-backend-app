# Diagnóstico del Problema: CrashLoopBackOff en el Microservicio 'service-discovery'

## 1. Resumen del Problema

El microservicio `service-discovery` falla al desplegarse en un clúster local de Kubernetes (Minikube), entrando en un estado de `CrashLoopBackOff`.

### Síntomas Principales:
- **Estado del Pod:** `CrashLoopBackOff`. El pod se reinicia continuamente.
- **Logs Vacíos:** Al ejecutar `kubectl logs <pod-name>`, no se obtiene ninguna salida. Esto indica que el contenedor falla antes de que el proceso principal (la aplicación Spring Boot) pueda inicializar su sistema de logs.
- **Código de Salida 143:** El último estado del contenedor muestra un código de salida 143. Este código corresponde a una terminación por la señal `SIGTERM`, que generalmente indica que el proceso fue terminado externamente o se apagó de forma anómala.
- **Funciona con Docker:** El contenedor se ejecuta correctamente fuera de Kubernetes usando el comando `docker run service-discovery:dev`. Esto confirma que la imagen del contenedor no está fundamentalmente rota.

## 2. Análisis de la Causa Raíz

La diferencia clave entre un `docker run` exitoso y un despliegue fallido en Kubernetes radica en el **entorno de ejecución**, específicamente la configuración de red y las variables de entorno.

La investigación determinó que la causa raíz es la **falta de una configuración explícita del nombre de host para el servidor Eureka** (`eureka.instance.hostname`).

En un entorno de Kubernetes, la red es compleja y la autodetección del nombre de host por parte de una aplicación puede fallar. El servidor Eureka, al no poder determinar su propio nombre de host durante el arranque, sufre un error fatal tan temprano que no puede registrar el problema.

## 3. Guía de Soluciones

A continuación se presentan varias causas posibles para un `CrashLoopBackOff` y sus soluciones correspondientes, comenzando por la que se aplicó a este caso.

### Solución 1: Establecer el Hostname de la Instancia de Eureka (Causa Más Probable)

- **Problema:** El servidor Eureka no puede autodetectar su hostname en la red de Kubernetes y falla al arrancar.
- **Solución:** Añadir una configuración explícita del hostname en el archivo `application-dev.yml` (o el perfil que corresponda).
  ```yaml
  eureka:
    instance:
      hostname: localhost
  ```
- **Pasos para Aplicar:**
  1. Modificar el archivo `application-{profile}.yml`.
  2. Reconstruir la imagen de Docker: `docker build -t service-discovery:dev .`
  3. Cargar la imagen actualizada a Minikube: `minikube image load service-discovery:dev`
  4. Kubernetes debería desplegar automáticamente un nuevo pod. Verificar con `kubectl get pods -l app=discovery`.

### Solución 2: Imagen de Docker Desactualizada en el Caché de Minikube

- **Problema:** Has hecho cambios en el código, pero Minikube sigue usando una versión antigua de la imagen que tiene en su caché local.
- **Solución:** Forzar la carga de la versión más reciente de la imagen desde tu máquina host al clúster de Minikube.
- **Comando:**
  ```bash
  minikube image load <nombre-de-tu-imagen>:<tag>
  ```

### Solución 3: Configuración Incorrecta en el Manifiesto de Kubernetes

- **Problema:** Un error en el archivo `deployment.yaml` (o similar) puede pasar variables de entorno incorrectas, configurar mal los puertos o montar volúmenes de forma errónea.
- **Solución:**
  1. Revisar cuidadosamente los manifiestos de despliegue en busca de errores tipográficos o lógicos.
  2. Usar `kubectl describe pod <pod-name>` para inspeccionar el entorno final que Kubernetes ha creado para el pod, incluyendo las variables de entorno, los montajes y los eventos.

### Solución 4: Recursos Insuficientes (Memoria/CPU)

- **Problema:** El pod no tiene suficiente memoria o CPU asignada, y el sistema operativo del nodo de Kubernetes lo termina abruptamente.
- **Síntoma Clave:** El pod muestra el estado `OOMKilled` (Out of Memory) o un código de salida 137.
- **Solución:** Especificar `requests` (recursos garantizados) y `limits` (recursos máximos) en la sección de `resources` del manifiesto del contenedor.
  ```yaml
  resources:
    requests:
      memory: "256Mi"
      cpu: "250m"
    limits:
      memory: "512Mi"
      cpu: "500m"
  ```
