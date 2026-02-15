package com.ecom360.notification.domain.model;
import jakarta.persistence.*;
import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "notification")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "business_id") private UUID businessId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String body;
    @Column(name = "action_url") private String actionUrl;
    @Column(name = "is_read", nullable = false) private Boolean isRead = false;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist protected void onCreate() { createdAt = Instant.now(); }
    public void markRead() { this.isRead = true; this.readAt = Instant.now(); }
    public UUID getId(){return id;} public void setId(UUID v){this.id=v;} public UUID getBusinessId(){return businessId;} public void setBusinessId(UUID v){this.businessId=v;} public UUID getUserId(){return userId;} public void setUserId(UUID v){this.userId=v;} public String getType(){return type;} public void setType(String v){this.type=v;} public String getTitle(){return title;} public void setTitle(String v){this.title=v;} public String getBody(){return body;} public void setBody(String v){this.body=v;} public String getActionUrl(){return actionUrl;} public void setActionUrl(String v){this.actionUrl=v;} public Boolean getIsRead(){return isRead;} public void setIsRead(Boolean v){this.isRead=v;} public Instant getReadAt(){return readAt;} public void setReadAt(Instant v){this.readAt=v;} public Instant getCreatedAt(){return createdAt;}
}
