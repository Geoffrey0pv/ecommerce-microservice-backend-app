# Corrected CI/CD Pipeline Architecture: "Build Once, Deploy Many"

## 🚀 Overview

This document describes the **corrected** CI/CD pipeline architecture that implements the industry-standard **"Build Once, Deploy Many"** pattern with proper GitFlow branching strategy. This architecture ensures immutable artifacts are built once and promoted through environments, eliminating security vulnerabilities and deployment inconsistencies.

## 🔧 What Was Fixed

### ❌ Previous Problem (Anti-Pattern)
- **Rebuilding images for each environment** 
- Environment-specific tags (dev-v1, stage-v2, master-v3)
- Different artifacts in different environments
- **Security risk**: untested code could reach production
- **Consistency risk**: "works in staging but fails in production"

### ✅ Corrected Implementation 
- **Single build per Git commit** using immutable `GIT_COMMIT_SHA` tags
- **Same tested artifact** promoted through all environments
- **Proper separation of concerns**: DEV builds, STAGE tests, MASTER promotes
- **Security-first**: Trivy vulnerability scanning at build and pre-production
- **Traceability**: Full audit trail from commit to production

## 🏗️ Architecture Components

### 🔄 Pipeline Types
1. **DEV Pipelines** (`*-service-pipeline.groovy`)
   - **Purpose**: Build immutable artifacts
   - **Triggers**: Git push to feature/develop branches
   - **Output**: Docker images tagged with `GIT_COMMIT_SHA`

2. **STAGE Pipelines** (`*-service-stage-pipeline.groovy`) 
   - **Purpose**: Deploy and test pre-built artifacts
   - **Triggers**: Manual with `IMAGE_TO_DEPLOY` parameter
   - **Input**: Image from DEV pipeline (`IMAGE_TO_DEPLOY`)

3. **MASTER Pipelines** (`*-service-master-pipeline.groovy`)
   - **Purpose**: Promote tested artifacts to production
   - **Triggers**: Manual with `IMAGE_TO_DEPLOY` parameter  
   - **Input**: Same tested image from STAGE environment

### 🎯 GitFlow Integration
```
Feature Branch → Develop → Staging → Main/Master
     ↓             ↓         ↓         ↓
   (Dev)        BUILD     DEPLOY   PROMOTE
             (once only)  (test)  (production)
```

## 📋 Service Portfolio

### Microservices (6 total)
- **user-service** (Port: 8081) - User management and authentication
- **product-service** (Port: 8082) - Product catalog and inventory  
- **order-service** (Port: 8083) - Order processing and management
- **payment-service** (Port: 8084) - Payment processing and validation
- **shipping-service** (Port: 8085) - Shipping and logistics
- **favourite-service** (Port: 8086) - User favorites and wishlists

### Infrastructure Components
- **Google Container Registry**: `us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices`
- **Kubernetes Namespaces**: 
  - `ecommerce-staging` (testing environment)
  - `ecommerce-production` (live environment)
- **Security Scanning**: Trivy for vulnerability detection
- **Quality Assurance**: SonarQube integration for code quality

## 🔧 Technical Implementation

### 🏷️ Immutable Tagging Strategy
```groovy
// Generate immutable tag from Git commit
env.GIT_COMMIT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
env.IMAGE_TAG = "${env.GIT_COMMIT_SHA}"
env.FULL_IMAGE_NAME = "${GCR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
```

### 🛡️ Security Scanning Pipeline
```groovy
// Vulnerability scanning with Trivy
sh """
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
        aquasec/trivy:latest image \
        --severity HIGH,CRITICAL \
        --exit-code 1 \
        ${FULL_IMAGE_NAME}:${IMAGE_TAG}
"""
```

### 🚀 Promotion Strategy
```groovy
// Production promotion with same tested artifact
sh """
    docker pull ${params.IMAGE_TO_DEPLOY}
    docker tag ${params.IMAGE_TO_DEPLOY} ${GCR_REGISTRY}/${IMAGE_NAME}:latest
    docker tag ${params.IMAGE_TO_DEPLOY} ${GCR_REGISTRY}/${IMAGE_NAME}:stable
    docker push ${GCR_REGISTRY}/${IMAGE_NAME}:latest
    docker push ${GCR_REGISTRY}/${IMAGE_NAME}:stable
"""
```

## 🔄 Pipeline Flow

### 1️⃣ DEV Pipeline (Build Phase)
**Trigger**: Code push to feature/develop branch
**Key Stages**:
- 📝 Checkout & Generate immutable `GIT_COMMIT_SHA` tag
- 🔨 Compile & Package (Maven build)  
- ✅ Unit & Integration Tests
- 📊 Code Quality Analysis (SonarQube)
- 🐳 Docker Image Build with immutable tag
- 🛡️ Security Scan (Trivy vulnerability detection)
- 📤 Push to Google Container Registry

**Output**: `us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices/[service]:${GIT_COMMIT_SHA}`

### 2️⃣ STAGE Pipeline (Test Phase)
**Trigger**: Manual execution with `IMAGE_TO_DEPLOY` parameter
**Key Stages**:
- ✨ Validate Parameters (require `IMAGE_TO_DEPLOY`)
- 🔐 Authenticate with GCP/GKE
- 🚀 Deploy to Staging Namespace (`ecommerce-staging`)
- 💊 Health Check (verify pod readiness)
- 🧪 Smoke Tests (basic functionality validation)

**Input**: Pre-built image from DEV pipeline
**Environment**: `ecommerce-staging` namespace

### 3️⃣ MASTER Pipeline (Production Phase)  
**Trigger**: Manual execution with `IMAGE_TO_DEPLOY` parameter
**Key Stages**:
- ✨ Validate Parameters (require `IMAGE_TO_DEPLOY`)
- 🛡️ Final Security Scan (production readiness)
- 🏷️ Tag for Production (`latest`, `stable`)
- 🚀 Deploy to Production Namespace (`ecommerce-production`)
- 💊 Production Health Check (extended timeout)

**Input**: Same tested image from STAGE pipeline
**Environment**: `ecommerce-production` namespace

## 🛠️ Jenkins Configuration

### Required Credentials
- **`gke-credentials`**: GCP service account key for Kubernetes access
- **`gcp-service-account`**: Container registry authentication

### Pipeline Parameters
```groovy
// STAGE and MASTER pipelines require this parameter
parameters {
    string(name: 'IMAGE_TO_DEPLOY', defaultValue: '', 
           description: 'Full image name with tag to deploy')
}
```

### Example Invocation
```bash
# 1. DEV Pipeline builds automatically on git push
# Output: us-central1-docker.pkg.dev/.../user-service:a1b2c3d

# 2. STAGE Pipeline (manual)
IMAGE_TO_DEPLOY="us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices/user-service:a1b2c3d"

# 3. MASTER Pipeline (manual - same image)
IMAGE_TO_DEPLOY="us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices/user-service:a1b2c3d"
```

## 🎯 Benefits Achieved

### 🔐 Security Improvements
- **Immutable artifacts**: Prevents code tampering between environments
- **Vulnerability scanning**: Trivy scans at build and pre-production
- **Audit trail**: Full traceability from Git commit to production deployment

### 🚀 Operational Benefits  
- **Consistency**: Same tested artifact in all environments
- **Rollback capability**: Easy revert to previous `GIT_COMMIT_SHA` 
- **Parallel deployments**: Multiple services can deploy independently
- **Environment isolation**: Clear separation between staging and production

### 📊 Development Workflow
- **GitFlow compatibility**: Supports feature → develop → staging → main flow
- **CI/CD best practices**: Industry-standard "Build Once, Deploy Many" pattern
- **Quality gates**: Automated testing and quality checks before deployment

## 📁 File Structure

```
jenkins-pipelines/
├── user-service-pipeline.groovy           # DEV pipeline  
├── user-service-stage-pipeline.groovy     # STAGE pipeline
├── user-service-master-pipeline.groovy    # MASTER pipeline
├── product-service-pipeline.groovy        # DEV pipeline
├── product-service-stage-pipeline.groovy  # STAGE pipeline  
├── product-service-master-pipeline.groovy # MASTER pipeline
├── order-service-pipeline.groovy          # DEV pipeline
├── order-service-stage-pipeline.groovy    # STAGE pipeline
├── order-service-master-pipeline.groovy   # MASTER pipeline
├── payment-service-pipeline.groovy        # DEV pipeline
├── payment-service-stage-pipeline.groovy  # STAGE pipeline
├── payment-service-master-pipeline.groovy # MASTER pipeline
├── shipping-service-pipeline.groovy       # DEV pipeline
├── shipping-service-stage-pipeline.groovy # STAGE pipeline
├── shipping-service-master-pipeline.groovy# MASTER pipeline
├── favourite-service-pipeline.groovy      # DEV pipeline
├── favourite-service-stage-pipeline.groovy# STAGE pipeline
└── favourite-service-master-pipeline.groovy# MASTER pipeline
```

## ⚡ Quick Reference

### Environment URLs
- **Container Registry**: `us-central1-docker.pkg.dev/ecommerce-backend-1760307199/ecommerce-microservices`
- **GKE Cluster**: `ecommerce-gke-cluster` (zone: `us-central1-a`)
- **Staging Namespace**: `ecommerce-staging`
- **Production Namespace**: `ecommerce-production`

### Service Ports
| Service | Port | Description |
|---------|------|-------------|
| user-service | 8081 | User authentication & management |
| product-service | 8082 | Product catalog & inventory |
| order-service | 8083 | Order processing & management |
| payment-service | 8084 | Payment processing & validation |
| shipping-service | 8085 | Shipping & logistics |
| favourite-service | 8086 | User favorites & wishlists |

## 🏆 Success Metrics

✅ **18 Pipelines Updated**: All 6 microservices × 3 environments corrected
✅ **Security Compliance**: Vulnerability scanning at multiple stages  
✅ **Deployment Consistency**: Same artifacts across all environments
✅ **GitFlow Integration**: Proper branching strategy implementation
✅ **Operational Excellence**: Industry-standard CI/CD practices

---

**Date**: October 2024  
**Status**: ✅ **COMPLETED** - All pipelines successfully updated to "Build Once, Deploy Many" architecture  
**Impact**: Critical security and operational improvement for ecommerce microservices platform