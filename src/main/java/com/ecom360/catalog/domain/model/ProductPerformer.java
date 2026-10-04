package com.ecom360.catalog.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

/** Employé habilité à réaliser une prestation ou un forfait. */
@Entity
@Table(
    name = "product_performer",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_product_performer",
            columnNames = {"product_id", "business_user_id"}))
public class ProductPerformer extends BaseEntity {

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "business_user_id", nullable = false)
  private UUID businessUserId;

  protected ProductPerformer() {}

  public static ProductPerformer create(UUID productId, UUID businessUserId) {
    ProductPerformer link = new ProductPerformer();
    link.productId = productId;
    link.businessUserId = businessUserId;
    return link;
  }

  public UUID getProductId() {
    return productId;
  }

  public UUID getBusinessUserId() {
    return businessUserId;
  }
}
