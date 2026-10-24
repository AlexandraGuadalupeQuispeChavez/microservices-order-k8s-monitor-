#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------
# stress.sh – Simple load testing script for the microservices
# ------------------------------------------------------------

# Usage: ./stress.sh <duration_seconds> <rps> [scenario]
#   duration_seconds: total time to run the test (default 60)
#   rps: requests per second (default 10)
#   scenario: optional, one of "normal", "slow", "crash" (default "normal")

DURATION=${1:-60}
RPS=${2:-10}
SCENARIO=${3:-normal}
BASE_URL="http://$(minikube ip)"
PRODUCT_URL="$BASE_URL:$(kubectl get svc product-catalog -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')"
ORDER_URL="$BASE_URL:$(kubectl get svc order-management -n ordersystem -o jsonpath='{.spec.ports[0].nodePort}')"

# Helper to pause between requests to achieve desired RPS
time_per_req=$(awk "BEGIN {print 1/$RPS}")

end_time=$((SECONDS + DURATION))

while [ $SECONDS -lt $end_time ]; do
  # 1) Get a product (simulate catalog access)
  curl -s "$PRODUCT_URL/api/products/1" >/dev/null &

  # 2) Create an order (order‑management calls product‑catalog internally)
  curl -s -X POST "$ORDER_URL/api/orders" -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}' >/dev/null &

  # Optional scenario manipulations
  case "$SCENARIO" in
    slow)
      # Activate artificial delay in product‑catalog (environment variable expected by the app)
      curl -s "$PRODUCT_URL/api/admin/delay?ms=2000" >/dev/null || true
      ;;
    crash)
      # Crash the product‑catalog pod (for testing CrashLoopBackOff)
      POD=$(kubectl get pods -n ordersystem -l app=product-catalog -o jsonpath="{.items[0].metadata.name}")
      kubectl exec -n ordersystem $POD -- kill -9 1 || true
      ;;
  esac

  # Wait to respect the RPS
  sleep $time_per_req
done

echo "Load test completed (duration=${DURATION}s, rps=${RPS}, scenario=${SCENARIO})"
