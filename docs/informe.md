# Informe Técnico - Microservicios Java con Monitoreo en Kubernetes

**Proyecto:** Sistema de Gestión de Pedidos  
**Estudiante:** Alexandra Guadalupe Quispe Chavez  
**Curso:** ORQUESTADORES Y AUTOMATIZACIÓN DE SOFTWARE
**Fecha:** Octubre 2026

---

## Parte A — Conceptos Fundamentales

### 1. ¿Qué es Micrometer y por qué se usa en lugar de la librería directa de Prometheus?

**Respuesta:**

Micrometer es una fachada (facade) de métricas para aplicaciones Java que proporciona una API unificada y vendor-neutral para instrumentar código. Es el equivalente a SLF4J pero para métricas en lugar de logs.

**¿Por qué usar Micrometer en lugar de la librería directa de Prometheus?**

1. **Abstracción de vendor:** Micrometer permite cambiar el sistema de métricas (Prometheus, Datadog, New Relic, etc.) sin modificar el código de instrumentación. Si en el futuro se decide migrar de Prometheus a otro sistema, solo se cambia la dependencia del registry.

2. **API más limpia y expresiva:** Micrometer ofrece builders fluidos que hacen el código más legible:
   ```java
   Counter.builder("ordermanagement.orders.created")
       .description("Total de órdenes creadas exitosamente")
       .register(meterRegistry);
   ```

3. **Integración con Spring Boot:** Spring Boot Actuator tiene integración nativa con Micrometer, exponiendo automáticamente métricas de JVM, HTTP, base de datos, etc.

4. **Convenciones de nombres:** Micrometer maneja automáticamente las convenciones de nombres de cada sistema (Prometheus usa `_`, otros usan `.`).

**Ejemplo de mi código:**

En `OrderService.java`, defino métricas usando Micrometer:
```java
this.ordersCreated = meterRegistry.counter("ordermanagement.orders.created");
this.ordersRejected = meterRegistry.counter("ordermanagement.orders.rejected");
```

Estas métricas se exponen automáticamente en el formato de Prometheus en `/metrics` sin escribir código específico de Prometheus.

---

### 2. ¿Cuál es la diferencia entre Counter, Gauge y Timer? Da un ejemplo de cada uno tomado de tu código.

**Respuesta:**

| Tipo | Descripción | Uso típico | Comportamiento |
|------|-------------|------------|----------------|
| **Counter** | Métrica que solo incrementa | Contar eventos (requests, errores, órdenes) | Monotónica creciente, nunca decrece |
| **Gauge** | Métrica que puede subir y bajar | Valores actuales (memoria, conexiones activas, temperatura) | Puede aumentar o disminuir |
| **Timer** | Mide duración de eventos | Latencia de requests, tiempo de procesamiento | Registra count + sum, genera histogramas |

**Ejemplos de mi código:**

**Counter - `ordermanagement.orders.created`:**
```java
// En OrderService.java
this.ordersCreated = meterRegistry.counter("ordermanagement.orders.created");

// Se incrementa cada vez que se crea una orden exitosamente
order.setStatus("CREATED");
ordersCreated.increment();
```
Este counter cuenta el total acumulado de órdenes creadas. Solo puede subir.

**Gauge - `ordermanagement.orders.pending`:**
```java
// En OrderService.java
this.pendingOrdersGauge = Gauge.builder("ordermanagement.orders.pending", 
    orderStore, Map::size)
    .register(meterRegistry);
```
Este gauge refleja la cantidad actual de órdenes en memoria. Puede subir (cuando se crea una orden) o bajar (si se implementara eliminación).

**Timer - `ordermanagement.orders.processing.time`:**
```java
// En OrderService.java
this.orderProcessingTimer = meterRegistry.timer("ordermanagement.orders.processing.time");

// Se usa para medir cuánto tarda en procesarse una orden
public Order createOrder(Order order) {
    return orderProcessingTimer.record(() -> {
        // Lógica de negocio...
    });
}
```
Este timer mide la duración del procesamiento de cada orden, generando métricas de latencia (p50, p95, p99).

---

### 3. ¿Qué es un ServiceMonitor y cómo sabe el Prometheus Operator qué monitorear?

**Respuesta:**

**ServiceMonitor** es un Custom Resource Definition (CRD) del Prometheus Operator que define cómo Prometheus debe descubrir y scrapear métricas de un servicio de Kubernetes.

**Funcionamiento:**

1. El ServiceMonitor tiene un **selector** que busca Services de Kubernetes que coincidan con ciertos labels
2. El Prometheus Operator observa continuamente los ServiceMonitors
3. Cuando encuentra un ServiceMonitor, Prometheus Operator configura automáticamente Prometheus para scrapear el endpoint del Service que coincide

**Matching entre ServiceMonitor y Service:**

El ServiceMonitor usa `matchLabels` para buscar Services:

```yaml
# En product-catalog-servicemonitor.yaml
spec:
  selector:
    matchLabels:
      app: product-catalog
```

Este ServiceMonitor busca cualquier Service con el label `app: product-catalog`.

El Service debe tener ese label:
```yaml
# En product-catalog-service.yaml
metadata:
  labels:
    app: product-catalog
```

**Configuración del scraping:**

El ServiceMonitor también especifica:
- El puerto a scrapear (por nombre o número)
- La ruta del endpoint de métricas
- El intervalo de scraping

```yaml
endpoints:
  - port: http        # Nombre del puerto en el Service
    path: /metrics    # Ruta del endpoint
    interval: 30s     # Frecuencia de scraping
```

**¿Cómo sabe Prometheus Operator qué ServiceMonitors procesar?**

En el `values.yaml` del chart kube-prometheus-stack, configuramos el `serviceMonitorSelector`:

```yaml
prometheus:
  prometheusSpec:
    serviceMonitorSelector: {}  # {} = seleccionar todos los ServiceMonitors
```

Si no configuramos esto correctamente, Prometheus ignorará nuestros ServiceMonitors.

---

### 4. ¿Cuál es la diferencia entre liveness probe y readiness probe?

**Respuesta:**

Ambos probes son health checks que Kubernetes ejecuta periódicamente, pero tienen propósitos diferentes:

| Aspecto | Liveness Probe | Readiness Probe |
|---------|----------------|-----------------|
| **Pregunta** | ¿Está vivo el contenedor? | ¿Está listo para recibir tráfico? |
| **Acción si falla** | Kubernetes **mata y reinicia** el contenedor | Kubernetes **remueve el pod del Service**, pero no lo reinicia |
| **Uso típico** | Detectar deadlocks, procesos colgados | Detectar si el servicio está inicializando, conectándose a BD, etc. |
| **Momento** | Durante toda la vida del pod | Solo afecta el enrutamiento de tráfico |

**Liveness Probe:**
- Detecta si la aplicación está en un estado irrecuperable (deadlock, loop infinito)
- Si falla repetidamente, Kubernetes reinicia el contenedor (CrashLoopBackOff)
- **Ejemplo:** Verificar que el proceso Java responde y no está bloqueado

**Readiness Probe:**
- Detecta si la aplicación está lista para procesar requests
- Si falla, el pod se marca como "NotReady" y se saca del load balancing
- El pod sigue corriendo, solo deja de recibir tráfico
- **Ejemplo:** Verificar que la aplicación ha cargado configuración, conectado a dependencias

**Ejemplo de mi código:**

En `product-catalog-deployment.yaml`:

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3
```
Si `/health` falla 3 veces seguidas, Kubernetes reinicia el pod.

```yaml
readinessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 3
```
Si `/health` falla, el pod se marca como NotReady y deja de recibir tráfico del Service.

**Flujo de inicio de un pod:**
1. Pod inicia → Readiness falla → Pod en estado `0/1 Running` (no recibe tráfico)
2. Aplicación termina de inicializar → Readiness pasa → Pod en estado `1/1 Running` (recibe tráfico)
3. Si en algún momento liveness falla → Kubernetes reinicia el contenedor

---

### 5. ¿Por qué es necesario apuntar Docker al daemon de Minikube antes de construir las imágenes?

**Respuesta:**

Minikube corre un **cluster de Kubernetes completo en una máquina virtual o contenedor**, con su propio daemon de Docker interno. Cuando se construyen imágenes con Docker en tu máquina local, esas imágenes quedan en el **Docker daemon local**, no en el Docker daemon de Minikube.

**El problema:**

Cuando Kubernetes (dentro de Minikube) intenta crear un pod con `image: product-catalog:latest`, busca esa imagen en su propio daemon de Docker. Si la imagen no existe ahí, Kubernetes intenta descargarla de un registry remoto (Docker Hub). Como `product-catalog:latest` no existe en Docker Hub, el pod falla con error `ImagePullBackOff`.

**La solución:**

Apuntar Docker al daemon interno de Minikube antes de construir las imágenes. Esto se hace con:

```bash
eval $(minikube docker-env)
```

Este comando configura variables de entorno que hacen que el CLI de Docker apunte al daemon de Docker que corre dentro de Minikube en lugar del daemon local.

Después de esto, cuando ejecutas:
```bash
docker build -t product-catalog:latest .
```

La imagen se construye directamente en el daemon de Docker de Minikube, y Kubernetes puede encontrarla sin necesidad de un registry externo.

**Configuración adicional necesaria:**

En el Deployment, debes usar `imagePullPolicy: Never` o `imagePullPolicy: IfNotPresent`:

```yaml
spec:
  containers:
  - name: product-catalog
    image: product-catalog:latest
    imagePullPolicy: Never  # No intentar pull de registry remoto
```

**Alternativa (usada en mi setup.sh):**

En lugar de usar `eval $(minikube docker-env)`, mi script usa otro enfoque:
1. Construir imágenes localmente
2. Exportarlas a archivos tar
3. Copiarlas a Minikube con `minikube cp`
4. Cargarlas en el daemon de Minikube con `minikube ssh "docker load"`

Ambos enfoques logran el mismo objetivo: que las imágenes estén disponibles en el daemon de Docker de Minikube.

---

### 6. ¿Qué ocurre si no configuras el selector de ServiceMonitors en el values.yaml del chart?

**Respuesta:**

Si no se configura correctamente el `serviceMonitorSelector` en el `values.yaml` del chart `kube-prometheus-stack`, **Prometheus ignorará todos los ServiceMonitors que crees** y no scrapeará métricas de tus servicios.

**¿Por qué sucede esto?**

Por defecto, el Prometheus Operator usa un selector restrictivo que solo busca ServiceMonitors con labels específicos. Esto es una medida de seguridad para evitar que Prometheus intente scrapear cualquier servicio del cluster.

**Configuración por defecto (restrictiva):**
```yaml
prometheus:
  prometheusSpec:
    serviceMonitorSelector:
      matchLabels:
        prometheus: kube-prometheus-stack
```

Con esta configuración, Prometheus **solo** detectará ServiceMonitors que tengan el label `prometheus: kube-prometheus-stack`.

**Síntomas del problema:**

1. Los ServiceMonitors existen: `kubectl get servicemonitor -n ordersystem` los muestra
2. Pero en Prometheus UI (`/targets`), los endpoints no aparecen
3. Las queries PromQL no encuentran métricas de tus servicios

**Solución:**

Configurar el selector para aceptar todos los ServiceMonitors:

```yaml
# En k8s/monitoring/values.yaml
prometheus:
  prometheusSpec:
    serviceMonitorSelector: {}  # {} = selector vacío = acepta todos
    serviceMonitorNamespaceSelector: {}  # Buscar en todos los namespaces
```

Con `serviceMonitorSelector: {}`, Prometheus detectará y procesará **todos** los ServiceMonitors del cluster, independientemente de sus labels.

**Mi configuración:**

En mi `values.yaml`, configuré:

```yaml
prometheus:
  prometheusSpec:
    serviceMonitorSelector: {}
    serviceMonitorNamespaceSelector: {}
```

Esto permite que Prometheus detecte mis ServiceMonitors en el namespace `ordersystem` aunque estén en un namespace diferente al de Prometheus (`monitoring`).

**Alternativa:**

Si prefieres ser más específico, puedes agregar labels a tus ServiceMonitors:

```yaml
# ServiceMonitor
metadata:
  labels:
    prometheus: kube-prometheus-stack
```

Y mantener el selector por defecto. Pero para proyectos pequeños, `{}` es más simple.

---

## Parte B — Queries PromQL

### 1. Tasa de requests por minuto de Order Management en los últimos 5 minutos

```promql
rate(ordermanagement_orders_create_seconds_count[5m]) * 60
```

**Explicación:**
- `ordermanagement_orders_create_seconds_count`: Counter del Timer que mide las llamadas al endpoint de crear órdenes
- `rate()[5m]`: Calcula la tasa por segundo en una ventana de 5 minutos
- `* 60`: Convierte de por-segundo a por-minuto

**Uso:** Visualizar el throughput del servicio de órdenes en tiempo real.

---

### 2. Latencia p95 del endpoint más crítico del sistema

```promql
histogram_quantile(0.95,
  sum(rate(ordermanagement_orders_processing_time_seconds_bucket[5m])) by (le)
)
```

**Explicación:**
- `ordermanagement_orders_processing_time_seconds_bucket`: Histograma del Timer que mide el tiempo de procesamiento de órdenes
- `rate()[5m]`: Tasa de observaciones por bucket en 5 minutos
- `sum() by (le)`: Agrupa por bucket (le = less or equal)
- `histogram_quantile(0.95, ...)`: Calcula el percentil 95

**Interpretación:** Si el resultado es `0.350`, significa que el 95% de las órdenes se procesan en 350ms o menos.

**¿Por qué es el endpoint más crítico?**
Porque `createOrder` es la operación principal del negocio y requiere comunicación con el Product Catalog, siendo susceptible a latencia de red y timeouts.

---

### 3. Estado UP/DOWN de ambos servicios al mismo tiempo

```promql
up{namespace="ordersystem"}
```

**Explicación:**
- `up`: Métrica automática de Prometheus que indica si un target está accesible
- `1` = servicio UP (responde correctamente)
- `0` = servicio DOWN (no responde o error)
- `{namespace="ordersystem"}`: Filtra solo los servicios de nuestro namespace

**Resultado esperado:**
```
up{job="product-catalog", namespace="ordersystem"} = 1
up{job="order-management", namespace="ordersystem"} = 1
```

**Alternativa para alertas:**
```promql
count(up{namespace="ordersystem"} == 1)
```
Cuenta cuántos servicios están UP. Si el resultado es 2, ambos están funcionando.

---

### 4. Query de negocio: Porcentaje de órdenes rechazadas vs creadas

```promql
(
  sum(rate(ordermanagement_orders_rejected_total[5m]))
  /
  (sum(rate(ordermanagement_orders_created_total[5m])) + sum(rate(ordermanagement_orders_rejected_total[5m])))
) * 100
```

**Explicación:**
- Numerador: Tasa de órdenes rechazadas
- Denominador: Tasa total de órdenes (creadas + rechazadas)
- `* 100`: Convierte a porcentaje

**Interpretación del negocio:**

- **< 5%**: Operación normal. Algunos productos ocasionalmente no disponibles.
- **5-20%**: Advertencia. Posible problema con inventario o comunicación entre servicios.
- **> 20%**: Crítico. Product Catalog caído o todos los productos sin stock.

**Uso en dashboard:**
Este valor es clave para el área de negocio. Si el porcentaje sube repentinamente, indica:
1. Problema técnico (Product Catalog lento/caído)
2. Problema de negocio (inventario agotado)
3. Problema de validación (IDs de productos inválidos)

**Query adicional - Total de productos disponibles en catálogo:**
```promql
productcatalog_products_total
```

Si este número baja a 0, explicaría por qué todas las órdenes son rechazadas.

---

## Parte C — Evidencias de Casuísticas

### 🔴 Casuística 1 — El Servicio Caído

#### Contexto narrativo
Durante un deployment de mantenimiento, un desarrollador modifica accidentalmente el Deployment de Product Catalog, configurando un `livenessProbe` con un path incorrecto (`/healthz` en lugar de `/health`). Al aplicar el cambio, el pod entra en un ciclo infinito de reinicios (CrashLoopBackOff) porque Kubernetes mata el contenedor cada vez que el probe falla.

#### Síntoma
Los usuarios no pueden ver productos ni crear órdenes. La API de Order Management devuelve errores 500 con mensaje "Connection refused" o "Timeout" al intentar validar productos contra el catálogo.

#### Activación
```bash
# 1. Modificar el deployment con un liveness probe incorrecto
kubectl patch deployment product-catalog -n ordersystem --type='json' \
  -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/livenessProbe/httpGet/path", "value": "/healthz"}]'

# 2. Observar que el pod entra en CrashLoopBackOff
kubectl get pods -n ordersystem -w
```

#### Diagnóstico

**En kubectl:**
```bash
$ kubectl get pods -n ordersystem
NAME                                READY   STATUS             RESTARTS   AGE
product-catalog-7d8f9c5b6d-x7k2l   0/1     CrashLoopBackOff   5          3m
order-management-6b8d4c7f9a-m4n8p  1/1     Running            0          10m

$ kubectl describe pod product-catalog-7d8f9c5b6d-x7k2l -n ordersystem
# Muestra:
# Warning  Unhealthy  Liveness probe failed: HTTP probe failed with statuscode: 404
# Warning  BackOff    Back-off restarting failed container
```

**En Prometheus UI (`http://<minikube-ip>:30090/targets`):**
- Target `product-catalog` aparece en estado **DOWN** (rojo)
- Last Scrape: "Get request failed"

**En Grafana:**
- Panel "Product Catalog Requests Total": línea cae a 0
- Panel "Service Status": Product Catalog muestra valor 0 (DOWN)
- Alerta **ProductCatalogDown** en estado **FIRING**

**En Prometheus Alerts (`/alerts`):**
```
ProductCatalogDown - FIRING
- El servicio Product Catalog no responde. Todos los endpoints del catálogo están inaccesibles.
- Labels: severity=critical, service=product-catalog
```

#### Resolución
```bash
# Opción 1: Revertir el patch
kubectl patch deployment product-catalog -n ordersystem --type='json' \
  -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/livenessProbe/httpGet/path", "value": "/health"}]'

# Opción 2: Aplicar el deployment original
kubectl apply -f k8s/product-catalog-deployment.yaml

# Verificar que el pod vuelve a Running
kubectl wait --for=condition=ready pod -l app=product-catalog -n ordersystem --timeout=60s

# Confirmar que el servicio responde
PRODUCT_CATALOG_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')
curl $PRODUCT_CATALOG_URL/health
# Debe devolver: {"status":"UP"}
```

#### Lección técnica

**Diferencia entre Liveness y Readiness Probe:**

- **Liveness Probe**: Responde "¿está vivo el proceso?" Si falla, Kubernetes asume que el contenedor está en un estado irrecuperable y lo **reinicia**.
  
- **Readiness Probe**: Responde "¿está listo para recibir tráfico?" Si falla, Kubernetes **remueve el pod del Service** pero no lo reinicia.

**En este caso:**
- El liveness probe apuntaba a `/healthz` (ruta inexistente)
- El endpoint devolvía 404
- Kubernetes interpretó esto como "el contenedor está muerto"
- Mató el contenedor y lo reinició
- El nuevo contenedor también fallaba el probe → ciclo infinito

**Buena práctica:**
- El liveness probe debe verificar solo que el proceso está vivo (no bloqueado)
- No debe depender de servicios externos
- Debe ser un endpoint simple y rápido
- Si falla, significa que reiniciar es la única solución

**Readiness probe:**
- Puede verificar dependencias (BD, otros servicios)
- Si falla, solo detiene el tráfico temporalmente
- Útil durante el inicio de la aplicación o problemas transitorios

---

### 🟡 Casuística 2 — El Servicio Lento

#### Contexto narrativo
Durante una campaña de Black Friday, el equipo de infraestructura detecta que el servidor de base de datos del Product Catalog está experimentando alta carga. Como medida temporal mientras escalan recursos, deciden activar un cache que introduce un delay de 3 segundos en las respuestas. Este delay supera el timeout configurado en Order Management (2 segundos), provocando que todas las órdenes fallen.

#### Síntoma
Los clientes pueden ver el catálogo de productos (lentamente), pero todas las órdenes fallan con mensaje "Service temporarily unavailable". Desde el punto de vista del usuario, parece que el proceso de checkout está roto, aunque el catálogo funciona.

#### Activación
```bash
# 1. Obtener la URL del Product Catalog
PRODUCT_CATALOG_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')

# 2. Activar delay artificial de 3000ms (supera el timeout de 2s de Order Management)
curl -X POST "$PRODUCT_CATALOG_URL/api/products/admin/delay?ms=3000"
# Respuesta: "Artificial delay set to 3000ms"

# 3. Generar tráfico para observar el problema
bash load-testing/stress.sh 120 5 normal

# 4. Intentar crear una orden (fallará por timeout)
ORDER_URL=http://$(minikube ip):$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')
curl -X POST "$ORDER_URL/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"id":"test-order","productIds":["p1"]}'
```

#### Diagnóstico

**En logs de Order Management:**
```bash
$ kubectl logs -n ordersystem -l app=order-management --tail=50

java.net.http.HttpTimeoutException: request timed out
  at java.net.http/jdk.internal.net.http.HttpClientImpl.lambda$send$0(HttpClientImpl.java:528)
  at com.example.ordermanagement.service.OrderService.createOrder(OrderService.java:45)
  
[WARN] Order rejected due to timeout communicating with Product Catalog
```

**En Prometheus:**

Query para verificar el delay:
```promql
productcatalog_artificial_delay_ms
```
Resultado: `3000`

Query para ver la latencia real:
```promql
histogram_quantile(0.95,
  sum(rate(productcatalog_requests_get_by_id_seconds_bucket[5m])) by (le)
)
```
Resultado: `3.05` (más de 3 segundos)

Query para ver órdenes rechazadas:
```promql
rate(ordermanagement_orders_rejected_total[5m])
```
Muestra un pico pronunciado.

**En Grafana:**

- **Panel "Product Catalog Request Latency"**: Línea sube de ~50ms a 3000ms
- **Panel "Artificial Delay"**: Muestra 3000ms (si se agregó este panel)
- **Panel "Order Rejection Rate"**: Sube de ~2% a 100%
- **Panel "Pending Orders"**: Se mantiene en 0 (todas rechazadas, no se crean)

**Alertas disparadas:**
1. **ArtificialDelayEnabled** (FIRING)
   - "Se ha detectado un delay artificial de 3000ms en el Product Catalog"
2. **HighOrderRejectionRate** (FIRING después de 3 minutos)
   - "Más del 30% de las órdenes están siendo rechazadas"

#### Resolución
```bash
# Opción 1: Desactivar el delay artificial
curl -X POST "$PRODUCT_CATALOG_URL/api/products/admin/delay?ms=0"

# Verificar que el delay se eliminó
curl "$PRODUCT_CATALOG_URL/api/products/admin/delay"
# Debe devolver: 0

# Opción 2: Si el problema es real (no simulado), escalar Product Catalog
kubectl scale deployment product-catalog -n ordersystem --replicas=3

# Esperar a que los nuevos pods estén listos
kubectl rollout status deployment product-catalog -n ordersystem

# Verificar que las órdenes vuelven a funcionar
curl -X POST "$ORDER_URL/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"id":"test-order-2","productIds":["p1"]}'
# Debe devolver status: "CREATED"
```

**Verificación en métricas:**
```bash
# La latencia debe volver a niveles normales
# Query en Prometheus:
histogram_quantile(0.95,
  sum(rate(productcatalog_requests_get_by_id_seconds_bucket[5m])) by (le)
)
# Debe mostrar < 0.1 segundos
```

#### Lección técnica

**La latencia se propaga entre microservicios**

En una arquitectura de microservicios, la latencia no es un problema aislado:

1. **Efecto cascada**: Si el Servicio A se vuelve lento, todos los servicios que dependen de él también se vuelven lentos o fallan.

2. **Timeouts son esenciales**: Sin timeouts configurados:
   - Order Management esperaría indefinidamente
   - Los threads se agotarían
   - Todo el servicio colapsaría

3. **Sin observabilidad es imposible diagnosticar**: 
   - Sin métricas: ¿El problema está en Order Management o Product Catalog?
   - Sin trazas distribuidas: ¿Dónde se pierde el tiempo?
   - Los usuarios solo ven "error 500"

**En mi implementación:**

```java
// OrderService.java - configuración de timeout
this.productCatalogClient = WebClient.builder()
    .baseUrl(catalogUrl)
    .responseTimeout(Duration.ofSeconds(2))  // Timeout de 2 segundos
    .build();
```

Si Product Catalog tarda más de 2 segundos, Order Management:
1. Cancela la petición
2. Registra el error
3. Rechaza la orden
4. Incrementa la métrica `ordermanagement_orders_rejected_total`

**Estrategias de mitigación:**
1. **Timeouts agresivos**: Fallar rápido es mejor que esperar indefinidamente
2. **Circuit breakers**: Dejar de intentar si el servicio está claramente caído
3. **Retry con backoff**: Reintentar con esperas exponenciales
4. **Fallbacks**: Respuestas degradadas (ej: asumir que producto está disponible)
5. **Rate limiting**: Evitar sobrecargar servicios lentos

**Métricas clave para detectar este problema:**
- Latencia p95 y p99 de cada endpoint
- Timeout errors (contador específico)
- Tasa de órdenes rechazadas
- Delay artificial (en este caso, para testing)

---

### 🟠 Casuística 3 — Inventario Agotado

#### Contexto narrativo
Durante un evento de liquidación, todos los productos del catálogo se agotan rápidamente. El sistema permite eliminar productos sin stock del catálogo. Un administrador elimina manualmente los últimos productos disponibles para actualizar la oferta. Esto deja el catálogo completamente vacío, causando que todas las órdenes sean rechazadas porque no hay productos para validar.

#### Síntoma
Los clientes acceden al sitio pero ven "No hay productos disponibles". Al intentar crear órdenes con IDs de productos guardados previamente (wishlist, carrito persistente), reciben errores "Product not found" o "Product unavailable". Las métricas de negocio muestran 0% de conversión de órdenes.

#### Activación
```bash
# 1. Obtener URL del Product Catalog
PRODUCT_CATALOG_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')

# 2. Ver productos actuales
curl "$PRODUCT_CATALOG_URL/api/products"

# 3. Simular eliminación de inventario: reiniciar el pod (in-memory storage, datos se pierden)
kubectl delete pod -n ordersystem -l app=product-catalog

# Esperar a que el pod se recree (sin datos seed esta vez)
kubectl wait --for=condition=ready pod -l app=product-catalog -n ordersystem --timeout=60s

# 4. Verificar que el catálogo está vacío
curl "$PRODUCT_CATALOG_URL/api/products"
# Debe devolver: []

# 5. Intentar crear una orden (fallará porque no existen productos)
ORDER_URL=http://$(minikube ip):$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')
curl -X POST "$ORDER_URL/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"id":"order-empty-catalog","productIds":["p1"]}'
# Respuesta: {"id":"order-empty-catalog", "status":"REJECTED", ...}
```

#### Diagnóstico

**En Prometheus:**

Query para ver total de productos:
```promql
productcatalog_products_total
```
Resultado: `0` (catálogo vacío)

Query para tasa de productos consultados:
```promql
rate(productcatalog_products_requested_total[5m])
```
Sigue habiendo requests, pero no encuentran productos.

Query para porcentaje de órdenes rechazadas:
```promql
(
  sum(rate(ordermanagement_orders_rejected_total[5m]))
  /
  (sum(rate(ordermanagement_orders_created_total[5m])) + sum(rate(ordermanagement_orders_rejected_total[5m])))
) * 100
```
Resultado: `100` (todas las órdenes rechazadas)

**En Grafana:**

- **Panel "Total Products in Catalog"**: Cae de 3 a 0
- **Panel "Order Success Rate"**: Cae de ~95% a 0%
- **Panel "Orders Rejected"**: Línea vertical hacia arriba
- **Panel "Pending Orders"**: Permanece en 0 (nada se crea)

**Alertas disparadas:**

1. **LowProductInventory** (FIRING)
   - "El catálogo tiene menos de 5 productos disponibles"
   - severity: info

2. **HighOrderRejectionRate** (FIRING después de 3 min)
   - "Más del 30% de las órdenes están siendo rechazadas"
   - severity: warning

**En logs de Order Management:**
```bash
$ kubectl logs -n ordersystem -l app=order-management --tail=20

[WARN] Product p1 not found in catalog, rejecting order order-empty-catalog
[INFO] Order order-empty-catalog rejected: product unavailable
```

#### Resolución
```bash
# Opción 1: Repoblar el catálogo manualmente
PRODUCT_CATALOG_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')

curl -X POST "$PRODUCT_CATALOG_URL/api/products" \
  -H "Content-Type: application/json" \
  -d '{"id":"p1","name":"Laptop","description":"Gaming Laptop","quantity":10}'

curl -X POST "$PRODUCT_CATALOG_URL/api/products" \
  -H "Content-Type: application/json" \
  -d '{"id":"p2","name":"Smartphone","description":"Flagship phone","quantity":25}'

curl -X POST "$PRODUCT_CATALOG_URL/api/products" \
  -H "Content-Type: application/json" \
  -d '{"id":"p3","name":"Headphones","description":"Noise-cancelling","quantity":15}'

# Verificar que los productos fueron agregados
curl "$PRODUCT_CATALOG_URL/api/products"

# Opción 2: Reiniciar el servicio con datos seed (si ProductService inicializa datos)
kubectl rollout restart deployment product-catalog -n ordersystem
kubectl rollout status deployment product-catalog -n ordersystem

# Verificar que las órdenes vuelven a funcionar
ORDER_URL=http://$(minikube ip):$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')
curl -X POST "$ORDER_URL/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"id":"order-restored","productIds":["p1"]}'
# Debe devolver status: "CREATED"
```

**Verificación en métricas:**
```bash
# Prometheus query: Total de productos debe ser > 0
productcatalog_products_total
# Resultado esperado: 3

# Prometheus query: Tasa de órdenes creadas debe ser > 0
rate(ordermanagement_orders_created_total[5m])
# Debe mostrar valores > 0
```

#### Lección técnica

**La pérdida de datos de negocio es indistinguible de un fallo técnico sin métricas adecuadas**

Este escenario demuestra que:

1. **Los síntomas son idénticos**: 
   - 100% de órdenes rechazadas
   - Usuarios no pueden comprar
   - Desde el punto de vista técnico, ambos servicios están "UP"

2. **Sin métricas de negocio, el diagnóstico es imposible**:
   - ¿Es un problema de red? No, los servicios se comunican.
   - ¿Es un problema de latencia? No, las respuestas son rápidas.
   - ¿Es un problema de código? No, no hay excepciones.
   - **El problema real**: No hay datos de negocio (productos).

3. **La métrica clave que expone el problema**:
   ```promql
   productcatalog_products_total
   ```
   Esta métrica de negocio (Gauge) revela instantáneamente la causa raíz.

**Diseño de métricas de negocio:**

En mi código, expuse métricas que van más allá de latencia/throughput:

```java
// ProductService.java
this.productCountGauge = Gauge.builder("productcatalog.products.total", 
    productStore, store -> store.size())
    .register(meterRegistry);
```

Esta métrica representa el **valor de negocio**: ¿cuántos productos hay disponibles para vender?

**Otras métricas de negocio útiles:**
- `revenue_per_minute`: Ingresos en tiempo real
- `cart_abandonment_rate`: Tasa de carritos abandonados
- `average_order_value`: Valor promedio de órdenes
- `inventory_turnover`: Rotación de inventario

**Diferencia con métricas técnicas:**
- **Técnicas**: CPU, memoria, latencia → miden la salud del sistema
- **Negocio**: Productos, órdenes, ingresos → miden la salud del negocio

**En producción:**
En un sistema real, `productStore` sería una base de datos persistente, no in-memory. Pero la lección permanece: **las métricas de negocio son tan importantes como las técnicas** para diagnosticar problemas en microservicios.

---

## Parte D — Evidencias del Sistema

### 1. Captura de /targets en Prometheus con ambos servicios en estado UP

**Acceso a Prometheus:**
```bash
# URL de Prometheus
minikube service monitoring-kube-prometheus-prometheus -n monitoring --url
# O si está configurado como NodePort:
http://$(minikube ip):30090
```

**Navegación:**
1. Abrir Prometheus UI
2. Click en menú "Status" → "Targets"
3. Buscar la sección "serviceMonitor/ordersystem/..."

**Evidencia esperada:**

```
Endpoint: http://10.244.0.15:8080/metrics
State: UP
Labels: 
  job="product-catalog"
  namespace="ordersystem"
  pod="product-catalog-7d8f9c5b6d-x7k2l"
Last Scrape: 15s ago
Scrape Duration: 45.2ms

Endpoint: http://10.244.0.16:8081/metrics
State: UP
Labels:
  job="order-management"
  namespace="ordersystem"
  pod="order-management-6b8d4c7f9a-m4n8p"
Last Scrape: 12s ago
Scrape Duration: 38.7ms
```

**Verificación desde CLI:**
```bash
# Verificar que Prometheus puede alcanzar los endpoints
PRODUCT_POD=$(kubectl get pod -n ordersystem -l app=product-catalog -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n ordersystem $PRODUCT_POD -- curl -s http://localhost:8080/metrics | head -20

# Debe mostrar métricas en formato Prometheus:
# HELP productcatalog_products_total Total products in catalog
# TYPE productcatalog_products_total gauge
# productcatalog_products_total 3.0
```

---

### 2. Captura del dashboard de Grafana con los seis paneles funcionando

**Acceso a Grafana:**
```bash
# URL de Grafana
minikube service monitoring-grafana -n monitoring --url
# O si está configurado como NodePort:
http://$(minikube ip):30030

# Credenciales (definidas en values.yaml):
# Username: admin
# Password: devpassword
```

**Importar el dashboard:**
1. Login en Grafana
2. Click en "+" → "Import"
3. Click en "Upload JSON file"
4. Seleccionar `k8s/monitoring/dashboards/dashboard-microservicios.json`
5. Seleccionar datasource "Prometheus"
6. Click "Import"

**Los 6 paneles del dashboard:**

1. **Product Catalog Requests Total**
   - Tipo: Graph
   - Query: `rate(productcatalog_requests_get_all_seconds_count[5m])`
   - Muestra: Tasa de requests al catálogo

2. **Product Catalog Request Latency (p95)**
   - Tipo: Graph
   - Query: `histogram_quantile(0.95, sum(rate(productcatalog_requests_get_all_seconds_bucket[5m])) by (le))`
   - Muestra: Latencia del percentil 95

3. **Order Management Requests Total**
   - Tipo: Graph
   - Query: `rate(ordermanagement_orders_create_seconds_count[5m])`
   - Muestra: Tasa de creación de órdenes

4. **Order Management Request Latency (p95)**
   - Tipo: Graph
   - Query: `histogram_quantile(0.95, sum(rate(ordermanagement_orders_processing_time_seconds_bucket[5m])) by (le))`
   - Muestra: Latencia de procesamiento de órdenes

5. **Pending Orders (métrica de negocio)**
   - Tipo: Stat
   - Query: `ordermanagement_orders_pending`
   - Muestra: Cantidad actual de órdenes pendientes

6. **CPU Usage (ordersystem namespace)**
   - Tipo: Graph
   - Query: `sum(rate(container_cpu_usage_seconds_total{namespace="ordersystem"}[2m])) by (pod)`
   - Muestra: Uso de CPU por pod

**Para generar datos visibles:**
```bash
# Ejecutar carga para ver métricas en tiempo real
bash load-testing/stress.sh 180 10 normal
```

---

### 3. Captura de kubectl get pods mostrando todos los pods en Running

**Comando:**
```bash
kubectl get pods -n ordersystem
```

**Output esperado:**
```
NAME                                READY   STATUS    RESTARTS   AGE
order-management-6b8d4c7f9a-m4n8p  1/1     Running   0          15m
product-catalog-7d8f9c5b6d-x7k2l   1/1     Running   0          15m
```

**Verificación adicional - todos los namespaces:**
```bash
kubectl get pods --all-namespaces
```

**Output esperado (resumido):**
```
NAMESPACE     NAME                                                     READY   STATUS    RESTARTS   AGE
kube-system   coredns-5dd5756b68-8xjzm                                1/1     Running   0          30m
kube-system   etcd-minikube                                           1/1     Running   0          30m
kube-system   kube-apiserver-minikube                                 1/1     Running   0          30m
kube-system   kube-controller-manager-minikube                        1/1     Running   0          30m
kube-system   kube-proxy-qw9lp                                        1/1     Running   0          30m
kube-system   kube-scheduler-minikube                                 1/1     Running   0          30m
kube-system   metrics-server-7c66d45ddc-kzmvn                         1/1     Running   0          28m
kube-system   storage-provisioner                                     1/1     Running   0          30m
monitoring    alertmanager-monitoring-kube-prometheus-alertmanager-0  2/2     Running   0          20m
monitoring    monitoring-grafana-6d8b5d4f9c-7xkzp                    3/3     Running   0          20m
monitoring    monitoring-kube-prometheus-operator-7d9c9f8c9d-p4hjm   1/1     Running   0          20m
monitoring    monitoring-kube-state-metrics-6f8c7b9d8c-xt2km         1/1     Running   0          20m
monitoring    monitoring-prometheus-node-exporter-k8qlz              1/1     Running   0          20m
monitoring    prometheus-monitoring-kube-prometheus-prometheus-0     2/2     Running   0          20m
ordersystem   order-management-6b8d4c7f9a-m4n8p                      1/1     Running   0          15m
ordersystem   product-catalog-7d8f9c5b6d-x7k2l                       1/1     Running   0          15m
```

**Verificación de health de los pods:**
```bash
# Ver detalles de probes
kubectl describe pod -n ordersystem -l app=product-catalog | grep -A5 "Liveness\|Readiness"

# Output esperado:
#   Liveness:     http-get http://:8080/health delay=30s timeout=1s period=10s #success=1 #failure=3
#   Readiness:    http-get http://:8080/health delay=10s timeout=1s period=5s #success=1 #failure=3
```

---

### 4. Captura de al menos una alerta en estado FIRING durante alguna casuística

![alt text](image.png)
**Acceso a Prometheus Alerts:**
```bash
# URL de Prometheus Alerts
http://$(minikube ip):30090/alerts
```

**Para disparar una alerta (usando Casuística 1):**
```bash
# Causar que Product Catalog entre en CrashLoopBackOff
kubectl patch deployment product-catalog -n ordersystem --type='json' \
  -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/livenessProbe/httpGet/path", "value": "/healthz"}]'

# Esperar 1-2 minutos para que la alerta se dispare
sleep 120
```

**Evidencia esperada en Prometheus UI → Alerts:**

```
🔴 ProductCatalogDown (FIRING)
   Severity: critical
   Service: product-catalog
   Summary: Product Catalog service is down
   Description: El servicio Product Catalog no responde. Todos los endpoints del catálogo están inaccesibles.
   
   Labels:
     alertname: ProductCatalogDown
     namespace: ordersystem
     job: product-catalog
     severity: critical
     service: product-catalog
   
   Annotations:
     summary: Product Catalog service is down
     description: El servicio Product Catalog no responde. Todos los endpoints del catálogo están inaccesibles.
   
   State: FIRING
   Active Since: 2024-10-24 15:32:18 (1m 42s ago)
```

**Otras alertas que se pueden disparar:**

**Para HighOrderRejectionRate:**
```bash
# Vaciar el catálogo (Casuística 3)
kubectl delete pod -n ordersystem -l app=product-catalog

# Generar órdenes (todas serán rechazadas)
bash load-testing/stress.sh 240 5 normal

# Esperar 3 minutos para que la alerta se dispare
```

**Para ArtificialDelayEnabled:**
```bash
# Activar delay (Casuística 2)
PRODUCT_URL=http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')
curl -X POST "$PRODUCT_URL/api/products/admin/delay?ms=2000"

# La alerta se dispara en ~1 minuto
```

**Verificación desde CLI:**
```bash
# Ver alertas activas desde kubectl
kubectl get prometheusrules -n monitoring ordersystem-alerts -o yaml

# Ver el estado en Prometheus usando API
PROM_URL=http://$(minikube ip):30090
curl -s "$PROM_URL/api/v1/alerts" | jq '.data.alerts[] | select(.state=="firing")'
```

---

## Conclusiones

Este proyecto demuestra la implementación completa de un sistema de microservicios con observabilidad de producción:

1. **Instrumentación**: Métricas de negocio y técnicas usando Micrometer
2. **Monitoreo**: Prometheus scrapeando métricas automáticamente vía ServiceMonitors
3. **Visualización**: Dashboards de Grafana con paneles de latencia, throughput y negocio
4. **Alertas**: PrometheusRules con alertas técnicas y de negocio
5. **Diagnóstico**: Tres casuísticas que demuestran cómo detectar y resolver problemas reales

**Aprendizajes clave:**

- Sin observabilidad, diagnosticar problemas en microservicios es imposible
- Las métricas de negocio son tan importantes como las técnicas
- Los timeouts y probes correctamente configurados evitan fallos en cascada
- La latencia se propaga entre servicios, requiriendo monitoreo end-to-end
- Prometheus + Grafana + Kubernetes forman un stack poderoso de observabilidad

---

**Repositorio:** https://github.com/AlexandraGuadalupeQuispeChavez/microservices-order-k8s-monitor-.git

