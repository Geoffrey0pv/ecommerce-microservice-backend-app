# Microservices Architecture Documentation

## Overview
This document describes the microservices architecture implemented for the ecommerce platform.

## Architecture Components

### Service Discovery Pattern
- **Technology**: Netflix Eureka
- **Purpose**: Central service registry for microservice discovery
- **Deployment**: Kubernetes LoadBalancer service for external access
- **Port**: 8761
- **Hostname**: discovery

### Microservices

#### User Service
- **Purpose**: User management, authentication, and user data
- **Technology**: Spring Boot with H2 database
- **Port**: 8200
- **Context Path**: /user-service
- **Deployment**: Kubernetes ClusterIP service
- **Hostname**: user-service

#### Product Service  
- **Purpose**: Product catalog management and inventory
- **Technology**: Spring Boot with H2 database
- **Port**: 8500
- **Context Path**: /product-service
- **Deployment**: Kubernetes ClusterIP service
- **Hostname**: product-service

#### Order Service
- **Purpose**: Order processing and order management
- **Technology**: Spring Boot with H2 database
- **Port**: 8300
- **Context Path**: /order-service
- **Deployment**: Kubernetes ClusterIP service
- **Hostname**: order-service

### Supporting Services

#### Zipkin Tracing
- **Purpose**: Distributed request tracing and monitoring
- **Technology**: OpenZipkin
- **Port**: 9411
- **Deployment**: Kubernetes ClusterIP service
- **Hostname**: zipkin

## Communication Patterns

### Service Discovery
1. All microservices register themselves with Eureka on startup
2. Services query Eureka to discover other services
3. Load balancing is handled by Spring Cloud LoadBalancer

### Inter-Service Communication
- **Protocol**: HTTP/REST
- **Discovery**: Via Eureka service names
- **Load Balancing**: Client-side load balancing
- **Circuit Breaker**: Resilience4j for fault tolerance

### Health Monitoring
- **Health Checks**: Spring Boot Actuator endpoints
- **Readiness Probes**: Kubernetes readiness probes with 60-120s initial delay
- **Startup Probes**: Kubernetes startup probes for longer initialization times

## Deployment Architecture

### Kubernetes Resources
Each service is deployed using:
- **Deployment**: Manages pods and replica sets
- **Service**: Provides stable network endpoint
- **ConfigMap**: Environment-specific configuration (via Helm values)

### Helm Charts Structure
```
manifests/
├── discovery/          # Eureka server
├── user-service/       # User microservice
├── product-service/    # Product microservice  
├── order-service/      # Order microservice
└── zipkin/            # Tracing service
```

Each chart contains:
- `Chart.yaml`: Helm chart metadata
- `values.yaml`: Default configuration values
- `templates/deployment.yaml`: Kubernetes deployment manifest
- `templates/service.yaml`: Kubernetes service manifest
- `templates/_helpers.tpl`: Helm template helpers

## Configuration Management

### Environment Variables
All services use consistent environment variable patterns:
- `SPRING_PROFILES_ACTIVE`: Active Spring profile (dev/prod)
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`: Eureka server URL
- `EUREKA_INSTANCE_HOSTNAME`: Service hostname for registration
- `SPRING_ZIPKIN_BASE_URL`: Zipkin server URL for tracing

### Service Registration Configuration
```yaml
eureka:
  client:
    serviceUrl:
      defaultZone: http://discovery:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    hostname: <service-name>
    preferIpAddress: false
```

## Resource Requirements

### Minikube Configuration
- **Memory**: 4GB minimum
- **CPUs**: 4 cores recommended
- **Disk**: 20GB for images and logs

### Container Resources
Each microservice is configured with:
- **Memory Request**: 512Mi
- **Memory Limit**: 1Gi
- **CPU Request**: 250m
- **CPU Limit**: 500m

## Security Considerations

### Network Security
- Services communicate via ClusterIP (internal only)
- Only Discovery service exposed via LoadBalancer
- No external access to individual microservices

### Authentication
- Currently using HTTP basic authentication
- Future: Implement OAuth2/JWT token-based authentication

## Monitoring and Observability

### Logging
- Centralized logging via stdout/stderr
- Structured JSON logging format
- Log aggregation via kubectl logs

### Tracing
- Distributed tracing via Zipkin
- Request correlation IDs
- Performance monitoring

### Metrics
- Spring Boot Actuator metrics endpoints
- Health check endpoints for liveness/readiness probes

## Scalability Patterns

### Horizontal Scaling
Services can be scaled independently:
```bash
kubectl scale deployment user-service --replicas=3
```

### Load Balancing
- Client-side load balancing via Spring Cloud LoadBalancer
- Kubernetes service load balancing for pod distribution

## Data Management

### Database Strategy
- **Pattern**: Database per microservice
- **Technology**: H2 in-memory databases (development)
- **Future**: Migrate to PostgreSQL/MySQL for production

### Data Consistency
- **Pattern**: Eventual consistency
- **Implementation**: Future event-driven architecture with message queues

## Development Patterns

### Circuit Breaker
```yaml
resilience4j:
  circuitbreaker:
    instances:
      userService:
        register-health-indicator: true
        failure-rate-threshold: 50
        minimum-number-of-calls: 5
```

### Retry Mechanism
- Automatic retries for transient failures
- Exponential backoff strategy
- Maximum retry attempts configuration

## Deployment Pipeline

### Local Development
1. Build Docker images in Minikube context
2. Deploy via Helm charts
3. Verify service registration in Eureka
4. Test inter-service communication

### Production Considerations
- Blue-green deployment strategy
- Database migration scripts
- Environment-specific configuration
- SSL/TLS termination at load balancer