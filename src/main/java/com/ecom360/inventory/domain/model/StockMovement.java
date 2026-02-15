package com.ecom360.inventory.domain.model;
import jakarta.persistence.*;
import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "stock_movement")
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "quantity_before", nullable = false) private Integer quantityBefore;
    @Column(name = "quantity_after", nullable = false) private Integer quantityAfter;
    private String reference;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist protected void onCreate() { createdAt = Instant.now(); }
    public static StockMovement record(UUID productId, UUID storeId, UUID userId, String type, int qty, int before, int after, String ref, String note) {
        StockMovement m = new StockMovement(); m.productId=productId; m.storeId=storeId; m.userId=userId; m.type=type; m.quantity=qty; m.quantityBefore=before; m.quantityAfter=after; m.reference=ref; m.note=note; return m;
    }
    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getProductId() { return productId; } public void setProductId(UUID v) { this.productId = v; }
    public UUID getStoreId() { return storeId; } public void setStoreId(UUID v) { this.storeId = v; }
    public UUID getUserId() { return userId; } public void setUserId(UUID v) { this.userId = v; }
    public String getType() { return type; } public void setType(String v) { this.type = v; }
    public Integer getQuantity() { return quantity; } public void setQuantity(Integer v) { this.quantity = v; }
    public Integer getQuantityBefore() { return quantityBefore; } public void setQuantityBefore(Integer v) { this.quantityBefore = v; }
    public Integer getQuantityAfter() { return quantityAfter; } public void setQuantityAfter(Integer v) { this.quantityAfter = v; }
    public String getReference() { return reference; } public void setReference(String v) { this.reference = v; }
    public String getNote() { return note; } public void setNote(String v) { this.note = v; }
    public Instant getCreatedAt() { return createdAt; }
}
