#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------
# setup.sh – Provision Minikube, install Helm chart, build images
# ------------------------------------------------------------

# 1. Verify prerequisites
command -v minikube >/dev/null 2>&1 || { echo "minikube not found, please install it first."; exit 1; }
command -v helm >/dev/null 2>&1 || { echo "helm not found, please install it first."; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker not found, please install it first."; exit 1; }

# 2. Start Minikube (use Docker driver for image building inside the cluster)
minikube start --driver=docker

# 3. Enable metrics‑server (required for HPA, not strictly needed for this demo)
minikube addons enable metrics-server

# 4. Create namespace if it does not exist
kubectl get ns ordersystem >/dev/null 2>&1 || kubectl create ns ordersystem

# 5. Build Docker images inside Minikube's Docker daemon
# This avoids pushing to a remote registry.
minikube ssh "docker load -i /tmp/product-catalog.tar" || true
minikube ssh "docker load -i /tmp/order-management.tar" || true
# Build locally and then copy into Minikube
docker build -t product-catalog:latest ./microservices/product-catalog
docker build -t order-management:latest ./microservices/order-management
# Export images to tar and import into Minikube
docker save product-catalog:latest -o product-catalog.tar
docker save order-management:latest -o order-management.tar
minikube cp product-catalog.tar /tmp/product-catalog.tar
minikube cp order-management.tar /tmp/order-management.tar
minikube ssh "docker load -i /tmp/product-catalog.tar"
minikube ssh "docker load -i /tmp/order-management.tar"
# Clean up temporary tar files
rm -f product-catalog.tar order-management.tar

# 6. Deploy kube‑prometheus‑stack using the provided values.yaml
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f k8s/monitoring/values.yaml

# 7. Apply Kubernetes manifests for our microservices
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/product-catalog-deployment.yaml
kubectl apply -f k8s/product-catalog-service.yaml
kubectl apply -f k8s/order-management-deployment.yaml
kubectl apply -f k8s/order-management-service.yaml

# 8. Apply monitoring resources (ServiceMonitors & alerts)
kubectl apply -f k8s/monitoring/product-catalog-servicemonitor.yaml
kubectl apply -f k8s/monitoring/order-management-servicemonitor.yaml
kubectl apply -f k8s/monitoring/alerts.yaml

# 9. Wait for pods to be Ready
echo "Waiting for pods to become Ready…"
kubectl wait --namespace ordersystem --for=condition=ready pod --selector=app=product-catalog --timeout=120s
kubectl wait --namespace ordersystem --for=condition=ready pod --selector=app=order-management --timeout=120s

# 10. Show useful URLs
PROMETHEUS_URL=$(kubectl -n monitoring get svc monitoring-prometheus -o jsonpath="{.spec.clusterIP}")
GRAFANA_URL=$(kubectl -n monitoring get svc monitoring-grafana -o jsonpath="{.spec.clusterIP}")
echo "---"
echo "Setup completed. Useful endpoints:"
echo "Product Catalog API: http://$(minikube ip):$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')"
echo "Order Management API: http://$(minikube ip):$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')"
echo "Prometheus (NodePort 30090): http://$(minikube ip):30090"
echo "Grafana (NodePort 30030, password: devpassword): http://$(minikube ip):30030"
