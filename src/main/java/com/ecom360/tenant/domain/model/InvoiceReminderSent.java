package com.ecom360.tenant.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice_reminder_sent")
public class InvoiceReminderSent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "reminder_kind", nullable = false, length = 32)
  private String reminderKind;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public static InvoiceReminderSent record(UUID invoiceId, String reminderKind, LocalDate dueDate) {
    InvoiceReminderSent row = new InvoiceReminderSent();
    row.invoiceId = invoiceId;
    row.reminderKind = reminderKind;
    row.dueDate = dueDate;
    return row;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getReminderKind() {
    return reminderKind;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
