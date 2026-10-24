#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------
# teardown.sh – Remove all resources created by setup.sh
# Usage: ./teardown.sh [--delete-cluster]
# ------------------------------------------------------------

DELETE_CLUSTER=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --delete-cluster)
      DELETE_CLUSTER=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: ./teardown.sh [--delete-cluster]"
      exit 1
      ;;
  esac
done

echo "Starting teardown process..."

# 1. Delete the monitoring stack (Helm release)
echo "Removing monitoring stack..."
helm uninstall monitoring --namespace monitoring || true

# 2. Delete the monitoring namespace
echo "Deleting monitoring namespace..."
kubectl delete namespace monitoring --wait=true || true

# 3. Delete the order system namespace (which contains deployments & services)
echo "Deleting ordersystem namespace..."
kubectl delete namespace ordersystem --wait=true || true

# 4. Optionally stop and delete Minikube cluster
if [ "$DELETE_CLUSTER" = true ]; then
  echo "Deleting Minikube cluster..."
  minikube delete
  echo "Minikube cluster deleted."
else
  echo "Minikube cluster preserved. Run with --delete-cluster to remove it."
fi

# 5. Clean up any local Docker images (optional)
# Uncomment if you want to remove local images
# docker rmi product-catalog:latest order-management:latest || true

echo "============================================"
echo "Teardown completed successfully!"
if [ "$DELETE_CLUSTER" = false ]; then
  echo "Note: Minikube cluster is still running."
  echo "To delete it, run: ./teardown.sh --delete-cluster"
fi
echo "============================================"
