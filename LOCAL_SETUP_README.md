# Ecommerce Microservices Local Setup

## Architecture Overview

This project implements a microservices architecture for an ecommerce platform running on Kubernetes (Minikube) locally. The architecture consists of:

### Core Services
- **Service Discovery (Eureka)**: Central service registry for microservice discovery
- **User Service**: Manages user authentication and user data
- **Product Service**: Handles product catalog and inventory
- **Order Service**: Processes orders and order management
- **Zipkin**: Distributed tracing for monitoring and debugging

### Service Communication
- All microservices register with Eureka for service discovery
- Inter-service communication happens via service names resolved through Eureka
- Distributed tracing is handled by Zipkin for request flow monitoring

## Prerequisites

Before setting up the local cluster, ensure you have the following installed:

### Required Software
- **Docker**: Container runtime
- **Minikube**: Local Kubernetes cluster
- **kubectl**: Kubernetes command-line tool
- **Helm**: Kubernetes package manager
- **Java 11/17**: For building microservices (if rebuilding images)
- **Maven**: For building Java projects (if rebuilding images)

### Installation Commands

```bash
# Install Docker (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install docker.io
sudo usermod -aG docker $USER

# Install Minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Install Helm
curl https://baltocdn.com/helm/signing.asc | gpg --dearmor | sudo tee /usr/share/keyrings/helm.gpg > /dev/null
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/helm.gpg] https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
sudo apt-get update
sudo apt-get install helm
```

## Setup Instructions

### Step 1: Start Minikube Cluster

```bash
# Delete existing cluster if any
minikube delete

# Start Minikube with adequate resources
minikube start --memory=4096 --cpus=4

# Verify cluster is running
minikube status
kubectl cluster-info
```

### Step 2: Configure Docker Environment

```bash
# Configure Docker to use Minikube's Docker daemon
eval $(minikube docker-env)

# Verify Docker context
docker ps
```

### Step 3: Build Docker Images

Build all microservice images in Minikube's Docker environment:

```bash
# Build Service Discovery
cd service-discovery
docker build -t service-discovery:dev .

# Build User Service
cd ../user-service
docker build -t user-service:dev .

# Build Product Service
cd ../product-service
docker build -t product-service:dev .

# Build Order Service
cd ../order-service
docker build -t order-service:dev .

# Pull Zipkin image
cd ..
docker pull openzipkin/zipkin
docker tag openzipkin/zipkin zipkin:dev
```

### Step 4: Verify Images

```bash
# Verify all images are available
docker images | grep dev
```

Expected output:
```
user-service          dev
product-service       dev
order-service         dev
service-discovery     dev
zipkin                dev
```

### Step 5: Deploy Services

Deploy services in the correct order (respecting dependencies):

```bash
# Deploy Service Discovery (Eureka) first
helm install discovery ./manifests/discovery

# Wait for Discovery to be ready
kubectl wait --for=condition=ready pod -l app=discovery --timeout=120s

# Deploy Zipkin
helm install zipkin ./manifests/zipkin

# Deploy microservices
helm install user-service ./manifests/user-service
helm install product-service ./manifests/product-service
helm install order-service ./manifests/order-service
```

### Step 6: Verify Deployment

```bash
# Check all pods are running
kubectl get pods

# Check all services are exposed
kubectl get services

# Check service registration in Eureka
minikube service discovery --url
# Open the URL in browser or curl to see registered services
```

Expected pod status:
```
NAME                               READY   STATUS    RESTARTS   AGE
discovery-xxx                      1/1     Running   0          xxm
zipkin-xxx                         1/1     Running   0          xxm
user-service-xxx                   1/1     Running   0          xxm
product-service-xxx                1/1     Running   0          xxm
order-service-xxx                  1/1     Running   0          xxm
```

## Service Details

### Port Configuration
- **Discovery Service**: 8761 (LoadBalancer for external access)
- **User Service**: 8200 (ClusterIP)
- **Product Service**: 8500 (ClusterIP)
- **Order Service**: 8300 (ClusterIP)
- **Zipkin**: 9411 (ClusterIP)

### Health Check Endpoints
All services expose health check endpoints:
- Service Discovery: `http://discovery:8761/actuator/health`
- User Service: `http://user-service:8200/user-service/actuator/health`
- Product Service: `http://product-service:8500/product-service/actuator/health`
- Order Service: `http://order-service:8300/order-service/actuator/health`

## Accessing Services

### Eureka Dashboard
```bash
# Get external URL for Eureka dashboard
minikube service discovery --url

# Example: http://192.168.49.2:32118
```

### Service Logs
```bash
# View logs for any service
kubectl logs -l app=discovery --tail=50
kubectl logs -l app=user-service --tail=50
kubectl logs -l app=product-service --tail=50
kubectl logs -l app=order-service --tail=50
kubectl logs -l app=zipkin --tail=50
```

## Troubleshooting

### Common Issues

#### 1. CrashLoopBackOff
If services enter CrashLoopBackOff state:
```bash
# Check pod events
kubectl describe pod <pod-name>

# Check application logs
kubectl logs <pod-name> --previous
```

**Solution**: Verify hostname configuration in application.yml files

#### 2. Images Not Found
If deployment fails with image pull errors:
```bash
# Ensure Docker environment is configured for Minikube
eval $(minikube docker-env)

# Rebuild images
docker build -t <service-name>:dev .
```

#### 3. Service Not Registering with Eureka
Check that:
- Discovery service is running and accessible
- Service configuration has correct Eureka URL: `http://discovery:8761/eureka`
- Service hostname is configured correctly

### Reset Environment

To completely reset the local environment:
```bash
# Delete all deployments
helm uninstall discovery zipkin user-service product-service order-service

# Or reset entire Minikube cluster
minikube delete
minikube start --memory=4096 --cpus=4
```

## Development Workflow

### Making Code Changes

1. **Modify Service Code**
2. **Rebuild Docker Image**:
   ```bash
   cd <service-directory>
   docker build -t <service-name>:dev .
   ```
3. **Update Deployment**:
   ```bash
   helm upgrade <service-name> ./manifests/<service-name>
   ```

### Scaling Services
```bash
# Scale a service
kubectl scale deployment <service-name> --replicas=3

# Verify scaling
kubectl get pods -l app=<service-name>
```

## Project Structure

```
ecommerce-microservice-backend-app/
├── manifests/                      # Helm charts for each service
│   ├── discovery/                  # Eureka service discovery
│   ├── user-service/              # User service
│   ├── product-service/           # Product service
│   ├── order-service/             # Order service
│   └── zipkin/                    # Zipkin tracing
├── service-discovery/             # Discovery service source code
├── user-service/                  # User service source code
├── product-service/               # Product service source code
├── order-service/                 # Order service source code
├── deploy-microservices.sh        # Automated deployment script
└── README.md                      # This file
```

## Automated Deployment

For automated deployment, use the provided script:

```bash
# Make script executable
chmod +x deploy-microservices.sh

# Run automated deployment
./deploy-microservices.sh
```

This script will:
1. Deploy services in correct order
2. Wait for dependencies to be ready
3. Verify deployment status
4. Provide deployment summary

## Configuration Management

### Environment Variables
Each service can be configured via environment variables in their respective `values.yaml` files:

- `SPRING_PROFILES_ACTIVE`: Active Spring profile (default: dev)
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`: Eureka server URL
- `EUREKA_INSTANCE_HOSTNAME`: Service hostname for registration
- `SPRING_ZIPKIN_BASE_URL`: Zipkin server URL for tracing

### Database Configuration
Currently, services use H2 in-memory databases for development. For production, update the database configuration in `application.yml` files to use persistent databases.

## Next Steps

1. **Add API Gateway**: Implement gateway for external API access
2. **Add Config Server**: Centralized configuration management
3. **Implement Security**: Add authentication and authorization
4. **Add Monitoring**: Implement metrics collection with Prometheus/Grafana
5. **Persistent Storage**: Configure persistent databases for production use