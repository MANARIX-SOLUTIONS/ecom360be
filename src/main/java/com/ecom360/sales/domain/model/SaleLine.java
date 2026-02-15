package com.ecom360.sales.domain.model;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "sale_line")
public class SaleLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sale_id", nullable = false) private UUID saleId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "product_name", nullable = false) private String productName;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "unit_price", nullable = false) private Integer unitPrice;
    @Column(name = "line_total", nullable = false) private Integer lineTotal;
    public static SaleLine create(UUID saleId, UUID productId, String name, int qty, int price) { SaleLine l = new SaleLine(); l.saleId=saleId; l.productId=productId; l.productName=name; l.quantity=qty; l.unitPrice=price; l.lineTotal=qty*price; return l; }
    public UUID getId(){return id;} public void setId(UUID v){this.id=v;} public UUID getSaleId(){return saleId;} public void setSaleId(UUID v){this.saleId=v;} public UUID getProductId(){return productId;} public void setProductId(UUID v){this.productId=v;} public String getProductName(){return productName;} public void setProductName(String v){this.productName=v;} public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){this.quantity=v;} public Integer getUnitPrice(){return unitPrice;} public void setUnitPrice(Integer v){this.unitPrice=v;} public Integer getLineTotal(){return lineTotal;} public void setLineTotal(Integer v){this.lineTotal=v;}
}
