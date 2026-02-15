package com.ecom360.client.domain.model;
import jakarta.persistence.*;
import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "client_payment")
public class ClientPayment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private Integer amount;
    @Column(name = "payment_method", nullable = false) private String paymentMethod;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist protected void onCreate() { createdAt = Instant.now(); }
    public UUID getId(){return id;} public void setId(UUID v){this.id=v;} public UUID getClientId(){return clientId;} public void setClientId(UUID v){this.clientId=v;} public UUID getStoreId(){return storeId;} public void setStoreId(UUID v){this.storeId=v;} public UUID getUserId(){return userId;} public void setUserId(UUID v){this.userId=v;} public Integer getAmount(){return amount;} public void setAmount(Integer v){this.amount=v;} public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String v){this.paymentMethod=v;} public String getNote(){return note;} public void setNote(String v){this.note=v;} public Instant getCreatedAt(){return createdAt;}
}
