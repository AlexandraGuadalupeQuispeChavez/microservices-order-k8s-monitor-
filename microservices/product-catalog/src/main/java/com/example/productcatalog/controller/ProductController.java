package com.example.productcatalog.controller;

import com.example.productcatalog.model.Product;
import com.example.productcatalog.service.ProductService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final Timer getAllTimer;
    private final Timer getByIdTimer;
    private final AtomicLong artificialDelayMs = new AtomicLong(0);

    public ProductController(ProductService productService, MeterRegistry meterRegistry) {
        this.productService = productService;
        this.getAllTimer = meterRegistry.timer("productcatalog.requests.get_all");
        this.getByIdTimer = meterRegistry.timer("productcatalog.requests.get_by_id");
        
        // Expose artificial delay as a Gauge metric
        Gauge.builder("productcatalog.artificial.delay.ms", artificialDelayMs, AtomicLong::get)
                .description("Artificial delay in milliseconds applied to product catalog responses")
                .register(meterRegistry);
    }
    
    private void applyDelay() {
        long delayMs = artificialDelayMs.get();
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return getAllTimer.record(() -> {
            applyDelay();
            return ResponseEntity.ok(productService.getAllProducts());
        });
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return getByIdTimer.record(() -> {
            applyDelay();
            Product p = productService.getProductById(id);
            if (p == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(p);
        });
    }

    @PostMapping
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        applyDelay();
        Product saved = productService.addProduct(product);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<Boolean> checkAvailability(@PathVariable String id) {
        applyDelay();
        Product p = productService.getProductById(id);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(p.getQuantity() > 0);
    }
    
    // Admin endpoint to configure artificial delay for testing
    @PostMapping("/admin/delay")
    public ResponseEntity<String> setDelay(@RequestParam(name = "ms", defaultValue = "0") long delayMs) {
        artificialDelayMs.set(delayMs);
        return ResponseEntity.ok("Artificial delay set to " + delayMs + "ms");
    }
    
    @GetMapping("/admin/delay")
    public ResponseEntity<Long> getDelay() {
        return ResponseEntity.ok(artificialDelayMs.get());
    }
}
