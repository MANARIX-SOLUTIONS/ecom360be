package com.ecom360.tenant.domain.model;

import com.ecom360.shared.domain.model.AggregateRoot;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "business", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Business extends AggregateRoot {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  private String phone;

  @Column(columnDefinition = "TEXT")
  private String address;

  @Column(name = "logo_url")
  private String logoUrl;

  @Column(name = "tax_id")
  private String taxId;

  @Column(nullable = false)
  private String currency = "XOF";

  @Column(nullable = false)
  private String locale = "fr";

  @Column(nullable = false)
  private String status = "active";

  @Column(name = "trial_ends_at")
  private LocalDate trialEndsAt;

  protected Business() {}

  public static Business create(String name, String email) {
    Business b = new Business();
    b.name = name;
    b.email = email;
    b.status = "trial";
    b.currency = "XOF";
    b.locale = "fr";
    return b;
  }

  public void suspend() {
    this.status = "suspended";
  }

  public void activate() {
    this.status = "active";
  }

  public boolean isTrial() {
    return "trial".equals(status);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getLogoUrl() {
    return logoUrl;
  }

  public void setLogoUrl(String logoUrl) {
    this.logoUrl = logoUrl;
  }

  public String getTaxId() {
    return taxId;
  }

  public void setTaxId(String taxId) {
    this.taxId = taxId;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDate getTrialEndsAt() {
    return trialEndsAt;
  }

  public void setTrialEndsAt(LocalDate trialEndsAt) {
    this.trialEndsAt = trialEndsAt;
  }
}
