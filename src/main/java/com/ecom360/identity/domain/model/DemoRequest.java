package com.ecom360.identity.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_request")
public class DemoRequest {

  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_APPROVED = "approved";
  public static final String STATUS_REJECTED = "rejected";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String email;

  private String phone;

  @Column(name = "business_name", nullable = false)
  private String businessName;

  /** Null si le demandeur n'a pas défini de mot de passe (lien envoyé après validation). */
  @Column(name = "password_hash")
  private String passwordHash;

  @Column(columnDefinition = "TEXT")
  private String message;

  @Column(name = "job_title")
  private String jobTitle;

  private String city;

  private String sector;

  @Column(nullable = false)
  private String status = STATUS_PENDING;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public static DemoRequest create(
      String fullName,
      String email,
      String phone,
      String businessName,
      String passwordHashOrNull,
      String message,
      String jobTitle,
      String city,
      String sector) {
    DemoRequest d = new DemoRequest();
    d.fullName = fullName;
    d.email = email.trim().toLowerCase();
    d.phone = phone;
    d.businessName = businessName;
    d.passwordHash = passwordHashOrNull;
    d.message = message;
    d.jobTitle = jobTitle;
    d.city = city;
    d.sector = sector;
    d.status = STATUS_PENDING;
    return d;
  }

  public void markApproved(UUID reviewerUserId) {
    this.status = STATUS_APPROVED;
    this.reviewedAt = Instant.now();
    this.reviewedByUserId = reviewerUserId;
    this.rejectionReason = null;
  }

  public void markRejected(UUID reviewerUserId, String reason) {
    this.status = STATUS_REJECTED;
    this.reviewedAt = Instant.now();
    this.reviewedByUserId = reviewerUserId;
    this.rejectionReason = reason;
  }

  public UUID getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getBusinessName() {
    return businessName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getMessage() {
    return message;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public String getCity() {
    return city;
  }

  public String getSector() {
    return sector;
  }
}
