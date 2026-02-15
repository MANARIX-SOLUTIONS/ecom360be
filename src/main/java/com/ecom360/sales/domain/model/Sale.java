package com.ecom360.sales.domain.model;
import jakarta.persistence.*;
import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "sale", uniqueConstraints = @UniqueConstraint(columnNames = "receipt_number"))
public class Sale {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "client_id") private UUID clientId;
    @Column(name = "receipt_number", nullable = false, unique = true) private String receiptNumber;
    @Column(name = "payment_method", nullable = false) private String paymentMethod;
    @Column(nullable = false) private Integer subtotal;
    @Column(name = "discount_amount", nullable = false) private Integer discountAmount = 0;
    @Column(nullable = false) private Integer total;
    @Column(name = "amount_received") private Integer amountReceived;
    @Column(name = "change_given") private Integer changeGiven;
    @Column(nullable = false) private String status;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist protected void onCreate() { createdAt = Instant.now(); }
    public void markVoided() { this.status = "voided"; }
    public boolean isCompleted() { return "completed".equals(status); }
    public boolean isCreditSale() { return "credit".equals(paymentMethod) && clientId != null; }
    public UUID getId(){return id;} public void setId(UUID v){this.id=v;} public UUID getBusinessId(){return businessId;} public void setBusinessId(UUID v){this.businessId=v;} public UUID getStoreId(){return storeId;} public void setStoreId(UUID v){this.storeId=v;} public UUID getUserId(){return userId;} public void setUserId(UUID v){this.userId=v;} public UUID getClientId(){return clientId;} public void setClientId(UUID v){this.clientId=v;} public String getReceiptNumber(){return receiptNumber;} public void setReceiptNumber(String v){this.receiptNumber=v;} public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String v){this.paymentMethod=v;} public Integer getSubtotal(){return subtotal;} public void setSubtotal(Integer v){this.subtotal=v;} public Integer getDiscountAmount(){return discountAmount;} public void setDiscountAmount(Integer v){this.discountAmount=v;} public Integer getTotal(){return total;} public void setTotal(Integer v){this.total=v;} public Integer getAmountReceived(){return amountReceived;} public void setAmountReceived(Integer v){this.amountReceived=v;} public Integer getChangeGiven(){return changeGiven;} public void setChangeGiven(Integer v){this.changeGiven=v;} public String getStatus(){return status;} public void setStatus(String v){this.status=v;} public String getNote(){return note;} public void setNote(String v){this.note=v;} public Instant getCreatedAt(){return createdAt;}
}
