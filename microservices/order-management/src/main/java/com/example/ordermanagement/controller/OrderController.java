package com.example.ordermanagement.controller;

import com.example.ordermanagement.model.Order;
import com.example.ordermanagement.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final Timer getAllTimer;
    private final Timer getByIdTimer;
    private final Timer createTimer;

    public OrderController(OrderService orderService, MeterRegistry meterRegistry) {
        this.orderService = orderService;
        this.getAllTimer = meterRegistry.timer("ordermanagement.requests.get_all");
        this.getByIdTimer = meterRegistry.timer("ordermanagement.requests.get_by_id");
        this.createTimer = meterRegistry.timer("ordermanagement.requests.create");
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return getAllTimer.record(() -> ResponseEntity.ok(orderService.getAllOrders()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return getByIdTimer.record(() -> {
            Order o = orderService.getOrderById(id);
            if (o == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(o);
        });
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        return createTimer.record(() -> {
            Order created = orderService.createOrder(order);
            return ResponseEntity.ok(created);
        });
    }

    // Simple statistics endpoint
    @GetMapping("/stats")
    public ResponseEntity<String> stats() {
        long total = orderService.getAllOrders().size();
        return ResponseEntity.ok("Total orders: " + total);
    }
}
