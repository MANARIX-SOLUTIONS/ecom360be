package com.ecom360.catalog.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {
  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(nullable = false)
  private String name;

  private String sku;
  private String barcode;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "cost_price", nullable = false)
  private Integer costPrice;

  @Column(name = "sale_price", nullable = false)
  private Integer salePrice;

  @Column(nullable = false)
  private String unit = "pièce";

  @Column(name = "image_url")
  private String imageUrl;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public boolean belongsTo(UUID bizId) {
    return businessId.equals(bizId);
  }

  public boolean belongsToStore(UUID storeId) {
    return this.storeId.equals(storeId);
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public void setBusinessId(UUID v) {
    this.businessId = v;
  }

  public UUID getStoreId() {
    return storeId;
  }

  public void setStoreId(UUID v) {
    this.storeId = v;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(UUID v) {
    this.categoryId = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    this.name = v;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String v) {
    this.sku = v;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String v) {
    this.barcode = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    this.description = v;
  }

  public Integer getCostPrice() {
    return costPrice;
  }

  public void setCostPrice(Integer v) {
    this.costPrice = v;
  }

  public Integer getSalePrice() {
    return salePrice;
  }

  public void setSalePrice(Integer v) {
    this.salePrice = v;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String v) {
    this.unit = v;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String v) {
    this.imageUrl = v;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean v) {
    this.isActive = v;
  }
}
