package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

/** Association employé-boutique pour le multi-boutique. */
@Entity
@Table(
    name = "business_user_store",
    uniqueConstraints = @UniqueConstraint(columnNames = {"business_user_id", "store_id"}))
public class BusinessUserStore extends BaseEntity {

  @Column(name = "business_user_id", nullable = false)
  private UUID businessUserId;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  protected BusinessUserStore() {}

  public static BusinessUserStore create(UUID businessUserId, UUID storeId) {
    BusinessUserStore bus = new BusinessUserStore();
    bus.businessUserId = businessUserId;
    bus.storeId = storeId;
    return bus;
  }

  public UUID getBusinessUserId() {
    return businessUserId;
  }

  public void setBusinessUserId(UUID v) {
    this.businessUserId = v;
  }

  public UUID getStoreId() {
    return storeId;
  }

  public void setStoreId(UUID v) {
    this.storeId = v;
  }
}
