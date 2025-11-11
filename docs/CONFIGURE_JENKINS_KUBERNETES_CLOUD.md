# Configuración de Kubernetes Cloud en Jenkins

## 🎯 Problema

El error `No Kubernetes cloud was found` indica que Jenkins no tiene configurado el plugin de Kubernetes para crear pods dinámicamente.

## ✅ Solución: Configurar Kubernetes Cloud

### Paso 1: Verificar que el plugin esté instalado

1. Ve a **Manage Jenkins** → **Manage Plugins**
2. En la pestaña **Installed**, busca: `Kubernetes`
3. Si no está instalado:
   - Ve a **Available plugins**
   - Busca `Kubernetes`
   - Marca la casilla e instala

### Paso 2: Configurar Kubernetes Cloud

#### 2.1 Acceder a la configuración

1. **Manage Jenkins** → **Nodes and Clouds** → **Clouds**
2. Click en **New cloud**
3. Nombre: `kubernetes` (debe ser exactamente este nombre)
4. Tipo: **Kubernetes**

#### 2.2 Configuración básica

```yaml
Kubernetes Cloud Details:
├── Name: kubernetes
├── Kubernetes URL: https://kubernetes.default
├── Kubernetes server certificate key: (dejar vacío si Jenkins corre en el cluster)
├── Disable https certificate check: ☑️ (solo para desarrollo)
├── Kubernetes Namespace: staging
├── Credentials: (crear ServiceAccount credentials)
├── Jenkins URL: http://jenkins:8080  (o la URL de tu Jenkins)
└── Jenkins tunnel: (dejar vacío)
```

### Paso 3: Crear ServiceAccount para Jenkins (si no existe)

Ya tienes los archivos en `manifests-gcp/jenkins-rbac.yaml`. Aplícalos si no lo has hecho:

```bash
# Aplicar RBAC para Jenkins
kubectl apply -f manifests-gcp/jenkins-rbac.yaml

# Verificar que se creó
kubectl get serviceaccount jenkins-agent -n staging
kubectl get role jenkins-agent-role -n staging
kubectl get rolebinding jenkins-agent-rolebinding -n staging
```

### Paso 4: Obtener el token del ServiceAccount

```bash
# Obtener el token del ServiceAccount
kubectl get secret -n staging | grep jenkins-agent

# Descripción del secret (buscar el token)
SECRET_NAME=$(kubectl get sa jenkins-agent -n staging -o jsonpath='{.secrets[0].name}')
kubectl get secret $SECRET_NAME -n staging -o jsonpath='{.data.token}' | base64 -d

# O crear un nuevo token (Kubernetes 1.24+)
kubectl create token jenkins-agent -n staging --duration=999999h
```

### Paso 5: Agregar credenciales en Jenkins

1. **Manage Jenkins** → **Manage Credentials**
2. Click en **(global)** domain
3. **Add Credentials**:
   - Kind: **Secret text**
   - Scope: **Global**
   - Secret: `<token del paso anterior>`
   - ID: `kubernetes-jenkins-agent-token`
   - Description: `Kubernetes ServiceAccount token for jenkins-agent`

### Paso 6: Completar configuración de Kubernetes Cloud

Vuelve a **Manage Jenkins** → **Nodes and Clouds** → **Clouds** → **kubernetes** y configura:

```yaml
Connection test:
├── Test Connection → debería decir "Connected to Kubernetes v1.xx.x"

Pod Templates (opcional - los pipelines ya definen los pods con YAML):
└── (Dejar vacío, usamos podTemplate en los Jenkinsfiles)
```

### Paso 7: Guardar y probar

1. Click en **Save**
2. Vuelve a ejecutar el pipeline `user-service-stage-pipeline-k8s-plugin`
3. Debería crear los pods dinámicamente en el namespace `staging`

---

## 🔍 Verificación

### Verificar que funciona:

```bash
# Ver logs de Jenkins (si corre en K8s)
kubectl logs -f deployment/jenkins -n jenkins

# Ver pods creados durante el pipeline
kubectl get pods -n staging -w
# Deberías ver pods temporales con nombres como:
# - maven-xxxxx (para E2E tests)
# - locust-xxxxx (para performance tests)
```

### Troubleshooting

#### Error: "Could not create pod"
```bash
# Verificar permisos del ServiceAccount
kubectl auth can-i create pods --as=system:serviceaccount:staging:jenkins-agent -n staging
kubectl auth can-i get pods --as=system:serviceaccount:staging:jenkins-agent -n staging
kubectl auth can-i delete pods --as=system:serviceaccount:staging:jenkins-agent -n staging
```

#### Error: "Unauthorized"
```bash
# Verificar que el token es válido
kubectl get secret -n staging | grep jenkins-agent

# Recrear el token
kubectl delete secret <jenkins-agent-token-xxx> -n staging
kubectl create token jenkins-agent -n staging --duration=999999h
```

#### Error: PVCs no se montan
```bash
# Verificar que existen
kubectl get pvc -n staging

# Ver eventos
kubectl describe pvc maven-cache-pvc -n staging
kubectl describe pvc npm-cache-pvc -n staging
```

---

## 📋 Checklist de Configuración

- [ ] Plugin Kubernetes instalado en Jenkins
- [ ] Kubernetes Cloud configurado con nombre `kubernetes`
- [ ] ServiceAccount `jenkins-agent` creado en namespace `staging`
- [ ] Role y RoleBinding aplicados
- [ ] Token del ServiceAccount obtenido
- [ ] Credenciales agregadas a Jenkins con ID `kubernetes-jenkins-agent-token`
- [ ] Kubernetes URL configurada (https://kubernetes.default)
- [ ] Namespace configurado como `staging`
- [ ] Test connection exitoso
- [ ] PVCs creados (`maven-cache-pvc`, `npm-cache-pvc`)

---

## 🚀 Configuración Rápida (Script)

Si Jenkins corre **dentro del cluster GKE**, puedes usar este script:

```bash
#!/bin/bash

# 1. Aplicar RBAC
kubectl apply -f manifests-gcp/jenkins-rbac.yaml

# 2. Aplicar PVCs
kubectl apply -f manifests-gcp/jenkins-pvc.yaml

# 3. Obtener token
TOKEN=$(kubectl create token jenkins-agent -n staging --duration=999999h)
echo "Token del ServiceAccount jenkins-agent:"
echo "$TOKEN"

# 4. Instrucciones
echo ""
echo "✅ Ahora en Jenkins:"
echo "1. Manage Jenkins → Manage Credentials → Add Credentials"
echo "2. Kind: Secret text"
echo "3. Secret: <pega el token de arriba>"
echo "4. ID: kubernetes-jenkins-agent-token"
echo ""
echo "5. Manage Jenkins → Nodes and Clouds → Clouds → New cloud"
echo "6. Name: kubernetes"
echo "7. Kubernetes URL: https://kubernetes.default"
echo "8. Credentials: kubernetes-jenkins-agent-token"
echo "9. Namespace: staging"
echo "10. Jenkins URL: http://jenkins:8080 (o tu URL)"
echo "11. Test Connection → Save"
```

---

## ⚡ Resultado Esperado

Cuando esté configurado correctamente:

1. El pipeline iniciará normalmente
2. Al llegar a la etapa `Run Tests in Parallel`:
   - Jenkins creará un pod con nombre `maven-xxxxx` en namespace `staging`
   - Jenkins creará un pod con nombre `locust-xxxxx` en namespace `staging`
3. Los pods ejecutarán los tests
4. Los resultados se copiarán de vuelta a Jenkins
5. Los pods se eliminarán automáticamente
6. El pipeline continuará

**Tiempo de primera ejecución**: ~8 minutos (pulling images + running tests)  
**Ejecuciones subsecuentes**: ~3 minutos (images cached + PVC cached dependencies)
