package com.example.ordermanagement.service;

import com.example.ordermanagement.model.Order;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OrderService {
    private final ConcurrentMap<String, Order> orderStore = new ConcurrentHashMap<>();
    private final WebClient productCatalogClient;
    private final Counter ordersCreated;
    private final Counter ordersRejected;
    private final Timer orderProcessingTimer;
    private final Gauge pendingOrdersGauge;

    public OrderService(@Value("${catalog.service.url}") String catalogUrl,
                        MeterRegistry meterRegistry) {
        this.productCatalogClient = WebClient.builder()
                .baseUrl(catalogUrl)
                .build();
        this.ordersCreated = meterRegistry.counter("ordermanagement.orders.created");
        this.ordersRejected = meterRegistry.counter("ordermanagement.orders.rejected");
        this.orderProcessingTimer = meterRegistry.timer("ordermanagement.orders.processing.time");
        // Gauge reflecting current pending orders (size of store)
        this.pendingOrdersGauge = Gauge.builder("ordermanagement.orders.pending", orderStore, Map -> Map.size())
                .register(meterRegistry);
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orderStore.values());
    }

    public Order getOrderById(String id) {
        return orderStore.get(id);
    }

    public Order createOrder(Order order) {
        return orderProcessingTimer.record(() -> {
            // Validate each product using catalog service
            for (String productId : order.getProductIds()) {
                try {
                    Boolean available = productCatalogClient.get()
                            .uri("/api/products/{id}/availability", productId)
                            .retrieve()
                            .bodyToMono(Boolean.class)
                            .timeout(Duration.ofSeconds(2))
                            .block();
                    if (available == null || !available) {
                        order.setStatus("REJECTED");
                        ordersRejected.increment();
                        orderStore.put(order.getId(), order);
                        return order;
                    }
                } catch (WebClientResponseException e) {
                    // catalog service unavailable or error
                    order.setStatus("REJECTED");
                    ordersRejected.increment();
                    orderStore.put(order.getId(), order);
                    return order;
                } catch (Exception e) {
                    order.setStatus("REJECTED");
                    ordersRejected.increment();
                    orderStore.put(order.getId(), order);
                    return order;
                }
            }
            // All products available
            order.setStatus("CREATED");
            ordersCreated.increment();
            orderStore.put(order.getId(), order);
            return order;
        });
    }
}
