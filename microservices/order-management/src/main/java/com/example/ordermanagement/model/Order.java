package com.example.ordermanagement.model;

import java.util.List;
import java.util.UUID;

public class Order {
    private String id;
    private List<String> productIds;
    private String status; // e.g., CREATED, REJECTED, COMPLETED

    public Order() {}

    public Order(List<String> productIds) {
        this.id = UUID.randomUUID().toString();
        this.productIds = productIds;
        this.status = "CREATED";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getProductIds() { return productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
