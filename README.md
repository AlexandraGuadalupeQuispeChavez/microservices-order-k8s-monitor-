# Microservicios Java con Monitoreo en Kubernetes

Sistema completo de microservicios Java con Spring Boot, desplegado en Kubernetes (Minikube) y monitoreado con Prometheus y Grafana.

**Curso:** ORQUESTADORES Y AUTOMATIZACIÓN DE SOFTWARE
**Modalidad:** Individual  
**Dominio de negocio:** Sistema de Gestión de Pedidos para Tienda Online

---

## 📋 Tabla de Contenidos

- [Dominio de Negocio](#dominio-de-negocio)
- [Arquitectura del Sistema](#arquitectura-del-sistema)
- [Estructura del Repositorio](#estructura-del-repositorio)
- [Prerequisitos](#prerequisitos)
- [Instalación Rápida](#instalación-rápida)
- [Instalación Manual](#instalación-manual)
- [Acceso a los Servicios](#acceso-a-los-servicios)
- [Verificación del Sistema](#verificación-del-sistema)
- [Casuísticas de Monitoreo](#casuísticas-de-monitoreo)
- [Limpieza](#limpieza)

---

## 🏢 Dominio de Negocio

**Contexto:** Sistema de pedidos de una tienda online

### Servicio A: Product Catalog (Catálogo de Productos)
- Gestiona el inventario de productos
- Expone endpoints para consultar productos y verificar disponibilidad
- Almacenamiento en memoria (ConcurrentHashMap)
- Puerto: 8080

### Servicio B: Order Management (Gestión de Pedidos)
- Procesa pedidos de clientes
- **Consulta al Product Catalog** para validar disponibilidad antes de crear órdenes
- Implementa timeout de 2 segundos para comunicación con Product Catalog
- Almacenamiento en memoria (ConcurrentHashMap)
- Puerto: 8081

**Comunicación:** Order Management → Product Catalog (HTTP via WebClient)

---

## 🏗️ Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                         Minikube                            │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Namespace: ordersystem                     │  │
│  │                                                      │  │
│  │  ┌─────────────────┐      ┌────────────────────┐   │  │
│  │  │ Product Catalog │◄─────│ Order Management   │   │  │
│  │  │   (Port 8080)   │      │   (Port 8081)      │   │  │
│  │  │                 │      │                    │   │  │
│  │  │ /metrics        │      │ /metrics           │   │  │
│  │  │ /health         │      │ /health            │   │  │
│  │  └────────┬────────┘      └─────────┬──────────┘   │  │
│  │           │                          │              │  │
│  └───────────┼──────────────────────────┼──────────────┘  │
│              │                          │                 │
│  ┌───────────┼──────────────────────────┼──────────────┐  │
│  │           │  Namespace: monitoring   │              │  │
│  │           │                          │              │  │
│  │           ▼                          ▼              │  │
│  │  ┌─────────────────────────────────────────────┐   │  │
│  │  │         Prometheus                          │   │  │
│  │  │  - Scrapes metrics every 30s                │   │  │
│  │  │  - Evaluates alerts                         │   │  │
│  │  │  - Stores time series data                  │   │  │
│  │  └──────────────────┬──────────────────────────┘   │  │
│  │                     │                              │  │
│  │                     ▼                              │  │
│  │  ┌─────────────────────────────────────────────┐   │  │
│  │  │         Grafana                             │   │  │
│  │  │  - Visualizes metrics                       │   │  │
│  │  │  - 6 dashboard panels                       │   │  │
│  │  │  - Alert visualization                      │   │  │
│  │  └─────────────────────────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 Estructura del Repositorio

```
microservices-order-k8s-monitor/
├── microservices/
│   ├── product-catalog/          # Servicio de catálogo de productos
│   │   ├── src/main/java/.../   # Controllers, Services, Models
│   │   ├── src/main/resources/  # application.yml
│   │   ├── Dockerfile            # Multi-stage build
│   │   └── pom.xml               # Maven dependencies
│   │
│   └── order-management/         # Servicio de gestión de pedidos
│       ├── src/main/java/.../   # Controllers, Services (WebClient)
│       ├── src/main/resources/  # application.yml
│       ├── Dockerfile            # Multi-stage build
│       └── pom.xml               # Maven dependencies
│
├── k8s/
│   ├── namespace.yaml
│   ├── product-catalog-deployment.yaml
│   ├── product-catalog-service.yaml
│   ├── order-management-deployment.yaml
│   ├── order-management-service.yaml
│   └── monitoring/
│       ├── values.yaml                   # Helm chart config
│       ├── product-catalog-servicemonitor.yaml
│       ├── order-management-servicemonitor.yaml
│       ├── alerts.yaml                   # 8 alertas
│       └── dashboards/
│           └── dashboard-microservicios.json
│
├── scripts/
│   ├── setup.sh                  # Instalación automática
│   └── teardown.sh               # Limpieza (con --delete-cluster)
│
├── load-testing/
│   └── stress.sh                 # Generador de carga
│
├── docs/
│   └── informe.md                # Informe técnico completo
│
└── README.md                     # Este archivo
```

---

## 🔧 Prerequisitos

### Software Requerido

#### Windows

```bash
# Docker Desktop
winget install Docker.DockerDesktop

# Minikube
winget install Kubernetes.minikube

# kubectl
winget install Kubernetes.kubectl

# Helm
winget install Helm.Helm

# Verificar instalaciones
docker --version      # v24.x+
minikube version      # v1.32.x+
kubectl version       # v1.28.x+
helm version          # v3.13.x+
```

#### Linux

```bash
# Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install kubectl /usr/local/bin/kubectl

# Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

### Recursos Recomendados
- **CPU:** 4 cores+
- **RAM:** 8 GB+ (16 GB recomendado)
- **Disco:** 20 GB libres

---

## 🚀 Instalación Rápida

```bash
# 1. Clonar el repositorio
git clone https://github.com/tu-usuario/microservices-order-k8s-monitor.git
cd microservices-order-k8s-monitor

# 2. Ejecutar instalación automática
bash scripts/setup.sh

# Tiempo estimado: 10-15 minutos
```

El script realiza:
- ✅ Inicia Minikube con recursos adecuados
- ✅ Habilita metrics-server
- ✅ Construye imágenes Docker
- ✅ Instala kube-prometheus-stack con Helm
- ✅ Despliega los microservicios
- ✅ Aplica ServiceMonitors y alertas
- ✅ Muestra URLs de acceso

---

## 📝 Instalación Manual

### 1. Iniciar Minikube

```bash
minikube start --driver=docker --cpus=4 --memory=8192
minikube addons enable metrics-server
kubectl cluster-info
```

### 2. Construir Imágenes

```bash
# Apuntar al daemon de Docker de Minikube
eval $(minikube docker-env)

# Construir imágenes
docker build -t product-catalog:latest ./microservices/product-catalog
docker build -t order-management:latest ./microservices/order-management

# Volver al daemon local
eval $(minikube docker-env -u)
```

### 3. Instalar Stack de Monitoreo

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  -f k8s/monitoring/values.yaml

kubectl wait --for=condition=ready pod -l "release=monitoring" \
  -n monitoring --timeout=300s
```

### 4. Desplegar Microservicios

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/product-catalog-deployment.yaml
kubectl apply -f k8s/product-catalog-service.yaml
kubectl apply -f k8s/order-management-deployment.yaml
kubectl apply -f k8s/order-management-service.yaml

kubectl wait --for=condition=ready pod -l app=product-catalog \
  -n ordersystem --timeout=120s
kubectl wait --for=condition=ready pod -l app=order-management \
  -n ordersystem --timeout=120s
```

### 5. Configurar Monitoreo

```bash
kubectl apply -f k8s/monitoring/product-catalog-servicemonitor.yaml
kubectl apply -f k8s/monitoring/order-management-servicemonitor.yaml
kubectl apply -f k8s/monitoring/alerts.yaml
```

---

## 🌐 Acceso a los Servicios

### URLs Rápidas

```bash
# Product Catalog
echo "Product Catalog: http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')"

# Order Management
echo "Order Management: http://$(minikube ip):$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')"

# Prometheus
echo "Prometheus: http://$(minikube ip):30090"

# Grafana
echo "Grafana: http://$(minikube ip):30030"
echo "  Username: admin"
echo "  Password: devpassword"
```

### Endpoints Disponibles

#### Product Catalog

```bash
PRODUCT_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')

# Health check
curl $PRODUCT_URL/health

# Métricas
curl $PRODUCT_URL/metrics

# Listar productos
curl $PRODUCT_URL/api/products

# Obtener producto
curl $PRODUCT_URL/api/products/p1

# Verificar disponibilidad
curl $PRODUCT_URL/api/products/p1/availability

# Agregar producto
curl -X POST $PRODUCT_URL/api/products \
  -H "Content-Type: application/json" \
  -d '{"id":"p4","name":"Mouse","description":"Gaming Mouse","quantity":50}'

# Configurar delay artificial (para testing)
curl -X POST "$PRODUCT_URL/api/products/admin/delay?ms=2000"

# Ver delay actual
curl $PRODUCT_URL/api/products/admin/delay
```

#### Order Management

```bash
ORDER_URL=http://$(minikube ip):$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')

# Health check
curl $ORDER_URL/health

# Métricas
curl $ORDER_URL/metrics

# Listar órdenes
curl $ORDER_URL/api/orders

# Crear orden
curl -X POST $ORDER_URL/api/orders \
  -H "Content-Type: application/json" \
  -d '{"id":"order-1","productIds":["p1","p2"]}'

# Obtener orden
curl $ORDER_URL/api/orders/order-1

# Estadísticas
curl $ORDER_URL/api/orders/stats
```

---

## ✅ Verificación del Sistema

### 1. Pods Running

```bash
kubectl get pods -n ordersystem
kubectl get pods -n monitoring
```

**Esperado:** Todos en estado `Running`, READY `1/1` o `2/2`

### 2. Prometheus Targets

```bash
# Abrir Prometheus
open http://$(minikube ip):30090

# Ir a: Status → Targets
# Buscar: serviceMonitor/ordersystem
# Verificar: Ambos targets en estado UP
```

### 3. Queries de Prueba

En Prometheus UI, ejecutar:

```promql
# Verificar servicios UP
up{namespace="ordersystem"}

# Total de productos
productcatalog_products_total

# Órdenes creadas
ordermanagement_orders_created_total
```

### 4. Grafana Dashboard

```bash
# Abrir Grafana
open http://$(minikube ip):30030

# Login: admin / devpassword

# Importar dashboard:
# + → Import → Upload JSON
# Archivo: k8s/monitoring/dashboards/dashboard-microservicios.json
```

### 5. Test de Integración

```bash
PRODUCT_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')
ORDER_URL=http://$(minikube ip):$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')

# Verificar productos
curl $PRODUCT_URL/api/products

# Crear orden
curl -X POST $ORDER_URL/api/orders \
  -H "Content-Type: application/json" \
  -d '{"id":"test-1","productIds":["p1"]}'

# Debe devolver: {"id":"test-1","status":"CREATED",...}
```

---

## 🔥 Casuísticas de Monitoreo

Tres escenarios documentados que demuestran el valor del monitoreo. **Ver detalles completos en `docs/informe.md`**.

### Casuística 1: El Servicio Caído

**Simula:** CrashLoopBackOff por liveness probe incorrecto

```bash
# Activar
kubectl patch deployment product-catalog -n ordersystem --type='json' \
  -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/livenessProbe/httpGet/path", "value": "/healthz"}]'

# Observar: Prometheus Targets DOWN, alerta "ProductCatalogDown"

# Resolver
kubectl apply -f k8s/product-catalog-deployment.yaml
```

### Casuística 2: El Servicio Lento

**Simula:** Latencia alta que causa timeouts

```bash
# Activar delay de 3s (supera timeout de 2s)
PRODUCT_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')
curl -X POST "$PRODUCT_URL/api/products/admin/delay?ms=3000"

# Generar tráfico
bash load-testing/stress.sh 120 5 normal

# Observar: Grafana muestra latencia alta, 100% órdenes rechazadas

# Resolver
curl -X POST "$PRODUCT_URL/api/products/admin/delay?ms=0"
```

### Casuística 3: Inventario Agotado

**Simula:** Catálogo vacío (storage in-memory)

```bash
# Activar: reiniciar pod pierde datos
kubectl delete pod -n ordersystem -l app=product-catalog
kubectl wait --for=condition=ready pod -l app=product-catalog -n ordersystem

# Observar: productcatalog_products_total = 0, 100% órdenes rechazadas

# Resolver: repoblar catálogo
curl -X POST $PRODUCT_URL/api/products -H "Content-Type: application/json" \
  -d '{"id":"p1","name":"Laptop","description":"Gaming Laptop","quantity":10}'
# Repetir para p2, p3...
```

---

## 📊 Métricas Disponibles

### Product Catalog
- `productcatalog_products_total` (Gauge): Total productos
- `productcatalog_products_added_total` (Counter): Productos agregados
- `productcatalog_products_requested_total` (Counter): Consultas
- `productcatalog_requests_get_all_seconds` (Timer): Latencia GET all
- `productcatalog_requests_get_by_id_seconds` (Timer): Latencia GET by ID
- `productcatalog_artificial_delay_ms` (Gauge): Delay artificial

### Order Management
- `ordermanagement_orders_pending` (Gauge): Órdenes pendientes
- `ordermanagement_orders_created_total` (Counter): Órdenes creadas
- `ordermanagement_orders_rejected_total` (Counter): Órdenes rechazadas
- `ordermanagement_orders_processing_time_seconds` (Timer): Tiempo procesamiento
- `ordermanagement_requests_create_seconds` (Timer): Latencia POST

---

## 🧹 Limpieza

### Mantener cluster

```bash
bash scripts/teardown.sh
```

### Eliminar todo

```bash
bash scripts/teardown.sh --delete-cluster
```

---

## 🐛 Troubleshooting

### ImagePullBackOff

```bash
# Verificar imágenes en Minikube
minikube ssh docker images

# Reconstruir si faltan
eval $(minikube docker-env)
docker build -t product-catalog:latest ./microservices/product-catalog
docker build -t order-management:latest ./microservices/order-management
eval $(minikube docker-env -u)
```

### Targets no aparecen en Prometheus

```bash
# Verificar ServiceMonitor existe
kubectl get servicemonitor -n ordersystem

# Verificar labels coinciden
kubectl get servicemonitor product-catalog-servicemonitor -n ordersystem -o yaml
kubectl get svc product-catalog -n ordersystem -o yaml | grep labels

# Reconfigurar Prometheus
helm upgrade monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring -f k8s/monitoring/values.yaml
```

### Grafana sin datos

```bash
# 1. Verificar Prometheus Targets están UP
open http://$(minikube ip):30090/targets

# 2. Probar query en Prometheus
# Query: up{namespace="ordersystem"}

# 3. Generar datos
bash load-testing/stress.sh 60 5 normal

# 4. Esperar 1-2 minutos
```

---

## 📚 Documentación

- **Informe Técnico:** `docs/informe.md` - Conceptos, queries PromQL, evidencias
- **Spring Boot Actuator:** https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- **Micrometer:** https://micrometer.io/docs
- **Prometheus Operator:** https://prometheus-operator.dev/
- **kube-prometheus-stack:** https://github.com/prometheus-community/helm-charts

---
