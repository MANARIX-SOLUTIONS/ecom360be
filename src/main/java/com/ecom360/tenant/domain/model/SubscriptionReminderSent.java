package com.ecom360.tenant.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscription_reminder_sent")
public class SubscriptionReminderSent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "subscription_id", nullable = false)
  private UUID subscriptionId;

  @Column(name = "reminder_kind", nullable = false, length = 32)
  private String reminderKind;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public static SubscriptionReminderSent record(
      UUID subscriptionId, String reminderKind, LocalDate periodEnd) {
    SubscriptionReminderSent row = new SubscriptionReminderSent();
    row.subscriptionId = subscriptionId;
    row.reminderKind = reminderKind;
    row.periodEnd = periodEnd;
    return row;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSubscriptionId() {
    return subscriptionId;
  }

  public String getReminderKind() {
    return reminderKind;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
