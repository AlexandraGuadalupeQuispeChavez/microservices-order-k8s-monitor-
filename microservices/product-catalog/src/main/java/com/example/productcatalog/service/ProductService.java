package com.example.productcatalog.service;

import com.example.productcatalog.model.Product;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ProductService {
    private final ConcurrentMap<String, Product> productStore = new ConcurrentHashMap<>();
    private final Counter productAddedCounter;
    private final Counter productRequestedCounter;
    private final Gauge productCountGauge;

    public ProductService(MeterRegistry meterRegistry) {
        this.productAddedCounter = meterRegistry.counter("productcatalog.products.added");
        this.productRequestedCounter = meterRegistry.counter("productcatalog.products.requested");
        this.productCountGauge = Gauge.builder("productcatalog.products.total", productStore, store -> store.size())
                .register(meterRegistry);
        // seed data
        addProduct(new Product("p1", "Laptop", "Gaming Laptop", 10));
        addProduct(new Product("p2", "Smartphone", "Flagship phone", 25));
        addProduct(new Product("p3", "Headphones", "Noise‑cancelling", 15));
    }

    public List<Product> getAllProducts() {
        productRequestedCounter.increment();
        return new ArrayList<>(productStore.values());
    }

    public Product getProductById(String id) {
        productRequestedCounter.increment();
        return productStore.get(id);
    }

    public Product addProduct(Product product) {
        if (product.getId() == null || product.getId().isBlank()) {
            throw new IllegalArgumentException("Product id must not be blank");
        }
        productStore.put(product.getId(), product);
        productAddedCounter.increment();
        return product;
    }

    public boolean isAvailable(String id) {
        Product p = productStore.get(id);
        return p != null && p.getQuantity() > 0;
    }
}
