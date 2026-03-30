package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "app_permission")
public class AppPermission extends BaseEntity {

  @Column(nullable = false, unique = true, length = 100)
  private String code;

  @Column(nullable = false, length = 350)
  private String label;

  @Column(length = 80)
  private String category;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected AppPermission() {}

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
